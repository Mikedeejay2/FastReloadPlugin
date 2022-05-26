package com.mikedeejay2.fastreload;

import com.mikedeejay2.fastreload.commands.FastReloadCommand;
import com.mikedeejay2.fastreload.config.FastReloadConfig;
import com.mikedeejay2.fastreload.listeners.ChatListener;
import com.mikedeejay2.fastreload.util.ExposedVariables;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Main reloading system class.
 * <p>
 * Algorithms here heavily utilize {@link ExposedVariables} and reflection.
 *
 * @author Mikedeejay2
 */
public class ReloadSystem implements FastReloadConfig.LoadListener {
    public static final File PLUGINS_DIRECTORY = new File("plugins");
    private final FastReload plugin;
    private final ConsoleCommandSender serverSender;
    private ExposedVariables exposed;
    private BiConsumer<CommandSender, String[]> reloadConsumer;
    private Predicate<CommandSender> permissionPredicate;
    private ChatListener chatListener;
    private FastReloadCommand commandExecutor;
    private List<String> pluginFilterList;
    private boolean filterWhitelist;
    private BukkitTask autoReloader;

    /**
     * Construct a new reloading system
     *
     * @param plugin A reference to the <code>FastReload</code> plugin
     */
    public ReloadSystem(FastReload plugin) {
        this.plugin = plugin;
        this.serverSender = plugin.getServer().getConsoleSender();
        initialize();
    }

    @Override
    public void onConfigLoad(FastReloadConfig config) {
        this.reloadConsumer = config.ONLY_PLUGINS.get() ? this::reloadPlugins : this::reloadFull;
        this.pluginFilterList = config.FILTER_LIST.get()
            .stream().map(String::toLowerCase).collect(Collectors.toList());
        this.filterWhitelist = config.FILTER_MODE.get().equalsIgnoreCase("whitelist");

        if(config.AUTO_RELOAD_PLUGINS.get()) {
            if(this.autoReloader != null) autoReloader.cancel();
            int autoReloadTime = config.AUTO_RELOAD_TIME.get();
            this.autoReloader = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new AutoReloaderRunnable(), autoReloadTime, autoReloadTime);
        }
    }

    /**
     * Command to disable all runnables in the reload system. Currently, this includes the automatic plugin reloader if
     * it is enabled in the configuration file
     */
    public void disable() {
        if(this.autoReloader != null) autoReloader.cancel();
        autoReloader = null;
    }

    /**
     * Initialize commands, listeners, and variables.
     */
    protected void initialize() {
        this.exposed = new ExposedVariables(plugin.getServer());
        this.chatListener = new ChatListener(this::reload);
        this.commandExecutor = new FastReloadCommand(this::reload);
        this.permissionPredicate = plugin::checkPermission;
        loadCommands();
        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
        this.reloadConsumer = null;

        this.plugin.config().registerListener(this);
        this.plugin.config().registerListener(chatListener);
    }

    /**
     * Load all of the reload commands directly into the <code>knownCommands</code> map
     * in {@link org.bukkit.command.SimpleCommandMap}.
     * <p>
     * If a command already exists for a reload command, this will remove the existing
     * command before injecting the new command into its place.
     */
    private void loadCommands() {
        String[] overrideCommands = {"reload", "rl", "r"};
        Constructor<PluginCommand> pluginConstructor;
        try {
            // Manually get constructor using reflection because the constructor will be used once per plugin
            pluginConstructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            pluginConstructor.setAccessible(true);
            for(String commandStr : overrideCommands) {
                exposed.knownCommands.remove(commandStr);
                PluginCommand command;
                command = pluginConstructor.newInstance(commandStr, plugin);
                command.setExecutor(commandExecutor);
                command.setTabCompleter(commandExecutor);
                exposed.knownCommands.put(commandStr, command);
                exposed.knownCommands.put(getFallback(plugin) + ":" + commandStr, command);
            }
        } catch(NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * Submit a <code>CommandSender</code> request to reload the server, taking in arguments as
     * well in case they were specifying a specific command.
     *
     * @param sender The <code>CommandSender</code> requesting the reload
     * @param args   The String of arguments, possibly the name of the plugin, possibly null
     */
    public void reload(final CommandSender sender, String[] args) {
        if(!permissionPredicate.test(sender)) return;
        // Schedule task on sync because of Async chat event
        Bukkit.getScheduler().runTask(plugin, () -> reloadConsumer.accept(sender, args));
    }

    /**
     * Full reload of the server using {@link ReloadSystem#vanillaReload(CommandSender)} unless a plugin name
     * is detected.
     * <p>
     * If <code>args</code> is not empty, it will instead attempt to reload just the plugin
     * specified.
     * <p>
     * However, this method is only ran if "Only Plugins" in commands is set to false.
     *
     * @param sender The <code>CommandSender</code> requesting the reload
     * @param args   The String of arguments, possibly the name of the plugin, possibly null
     */
    private void reloadFull(final CommandSender sender, String[] args) {
        if(args == null || args.length == 0) {
            vanillaReload(sender);
        } else {
            reloadPlugin(sender, args);
        }
    }

    /**
     * Full reload the server. This method doesn't do anything special, it just calls
     * {@link Server#reload()}.
     * <p>
     * However, this method is only ran if "Only Plugins" in commands is set to false.
     *
     * @param sender The <code>CommandSender</code> requesting the reload
     */
    private void vanillaReload(CommandSender sender)
    {
        sender.sendMessage(ChatColor.YELLOW + "The server is reloading...");
        long startTime = System.currentTimeMillis();
        serverSender.sendMessage(String.format("Player %s reloaded the server!", sender.getName()));

        plugin.getServer().reload();

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;
        sender.sendMessage(ChatColor.GREEN + "The server has successfully reloaded in " + differenceTime + "ms.");
    }

    /**
     * Reload specifically the plugins on the server.
     * <p>
     * If <code>args</code> is not empty, it will instead attempt to reload just the plugin
     * specified.
     *
     * @param sender The <code>CommandSender</code> requesting the reload
     * @param args   The String of arguments, possibly the name of the plugin, possibly null
     */
    private void reloadPlugins(final CommandSender sender, String[] args) {
        if(args == null || args.length == 0) {
            reloadAllPlugins(sender);
        } else {
            reloadPlugin(sender, args);
        }
    }

    /**
     * Reload all plugins on the server.
     * <p>
     * This does essentially just what {@link Bukkit#reload()} does but only reloading
     * plugins and nothing else.
     *
     * @param sender The <code>CommandSender</code> requesting the reload
     */
    private void reloadAllPlugins(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "The server is reloading all plugins...");
        serverSender.sendMessage(String.format(ChatColor.RED + "Player %s reloaded the server's plugins!", sender.getName()));

        PluginManager pluginManager = Bukkit.getPluginManager();

        long startTime = System.currentTimeMillis();

        // Reload all commands, take into account the black/whitelist filter in the config
        for(Plugin curPlugin : pluginManager.getPlugins()) {
            if(filterWhitelist ^ pluginFilterList.contains(curPlugin.getName().toLowerCase())) {
                continue;
            }
            reloadPlugin(curPlugin);
        }

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;
        sender.sendMessage(ChatColor.GREEN + "The server has successfully reloaded all plugins in " + differenceTime + "ms.");
    }

    /**
     * Reload a single plugin specified in <code>args</code>.
     * If <code>args</code> is not a plugin it will notify the sender and return.
     * <p>
     * This method might be the worst offender here to accessing private/protected variables
     * in CraftBukkit code, as there is no singleton plugin unregister method anywhere.
     * What that means is that this method utilizes methods which emulate the act of a single
     * plugin being unregistered and reloaded.
     *
     * @param sender The <code>CommandSender</code> requesting the reload
     * @param args   The String of arguments, possibly the name of the plugin, possibly null
     */
    private void reloadPlugin(final CommandSender sender, String[] args) {
        String pluginName = String.join(" ", args);
        sender.sendMessage(ChatColor.YELLOW + String.format("The server is reloading plugin \"%s\"...", pluginName));
        serverSender.sendMessage(String.format(ChatColor.RED + "Player %s reloaded the server's plugins!", sender.getName()));

        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin selectedPlugin = pluginManager.getPlugin(pluginName);

        if(selectedPlugin == null) {
            sender.sendMessage(ChatColor.RED + String.format("The plugin \"%s\" is not a valid plugin.", pluginName));
            return;
        }

        long startTime = System.currentTimeMillis();

        reloadPlugin(selectedPlugin);

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;

        sender.sendMessage(ChatColor.GREEN + String.format("The server has successfully reloaded plugin \"%s\" in %d ms.", pluginName, differenceTime));
    }

    /**
     * Reload a specified plugin. This method does the following in order
     * <ol>
     *     <li>Disable the plugin.</li>
     *     <li>Unregister the plugin (commands, lookups, permissions)</li>
     *     <li>Load the plugin from the file</li>
     *     <li>Enable the plugin</li>
     * </ol>
     *
     * @param thePlugin The plugin to reload
     */
    private void reloadPlugin(Plugin thePlugin) {
        disableAndUnregisterPlugin(thePlugin);
        loadAndEnablePlugin(thePlugin.getName());
    }

    /**
     * Disable and unregister a specific plugin.
     *
     * @param thePlugin The plugin to disable
     */
    private void disableAndUnregisterPlugin(Plugin thePlugin) {
        disablePlugin(thePlugin);
        unregisterPlugin(thePlugin);
        unregisterCommands(thePlugin);
        unregisterLookups(thePlugin);
        unregisterPermissions(thePlugin);
    }

    /**
     * Enable a selected plugin. This method will find the given plugin on disk,
     * load it as new, and then enable it on the server.
     * <p>
     * Note that the plugin should be fully disabled and completely removed from
     * CraftBukkit before calling this method! If not, bad things will probably happen.
     *
     * @param pluginName The name of the plugin to load
     */
    private void loadAndEnablePlugin(String pluginName) {
        Plugin newPlugin = loadPlugin(pluginName);
        if(newPlugin == null) return;
        plugin.getServer().getPluginManager().enablePlugin(newPlugin);
    }

    /**
     * Remove all permissions from a plugin. This should be used when disabling a single
     * plugin, as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     */
    private void unregisterPermissions(Plugin selectedPlugin) {
        List<Permission> permissions = selectedPlugin.getDescription().getPermissions();

        PluginManager manager = plugin.getServer().getPluginManager();
        for(Permission permission : permissions) {
            manager.removePermission(permission);
            exposed.defaultPerms.get(true).remove(permission);
            exposed.defaultPerms.get(false).remove(permission);
        }
    }

    /**
     * Helper method to disable a plugin using {@link SimplePluginManager#disablePlugin(Plugin)}.
     * This method DOES NOT fully unregister the plugin!
     *
     * @param selectedPlugin The plugin to disable
     */
    private void disablePlugin(Plugin selectedPlugin) {
        plugin.getServer().getPluginManager().disablePlugin(selectedPlugin);
    }

    /**
     * Remove lookup names for a plugin. This removes all lookup names
     * from {@link SimplePluginManager}
     * <p>
     * This should be used when disabling a single plugin,
     * as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     *
     * @param selectedPlugin The plugin to remove lookups to
     */
    private void unregisterLookups(Plugin selectedPlugin) {
        exposed.lookupNames.remove(selectedPlugin.getDescription().getName().toLowerCase());
        for (String provided : selectedPlugin.getDescription().getProvides()) {
            exposed.lookupNames.remove(provided.toLowerCase());
        }
    }

    /**
     * Remove a plugin from {@link SimplePluginManager}.
     * <p>
     * This should be used when disabling a single plugin,
     * as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     *
     * @param selectedPlugin The plugin to be removed
     */
    private void unregisterPlugin(Plugin selectedPlugin) {
        exposed.plugins.remove(selectedPlugin);
    }

    /**
     * Call the {@link ServerLoadEvent} to the server in the edge case that a plugin
     * uses the reload event to do something.
     */
    private void callReloadEvent() {
        plugin.getServer().getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.RELOAD));
    }

    /**
     * Load all plugins into the server. This method uses reflection to call a method in
     * <code>CraftServer</code> to load all plugins and enable them.
     *
     * @param server A reference to the server
     * @return Whether the plugins loading was successful
     */
    private boolean loadPlugins(Server server) {
        try {
            // Not using ReflectUtils here since it's maybe a ms faster because of double invoking
            Method loadMethod = server.getClass().getDeclaredMethod("loadPlugins");
            Method enableMethod = server.getClass().getDeclaredMethod("enablePlugins", PluginLoadOrder.class);
            loadMethod.invoke(server);
            enableMethod.invoke(server, PluginLoadOrder.STARTUP);
            enableMethod.invoke(server, PluginLoadOrder.POSTWORLD);
        } catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return true;
        }
        return false;
    }

    /**
     * Unregister all commands registered by a plugin.
     * This method does give the honor system to the plugin in question,
     * as even if the plugin had attempted to initialize a plugin at the start
     * but failed this method will still unregister the command that was specified
     * anyways. That means that the next time that the plugin loads up it will have
     * priority over the command.
     * <p>
     * This should be used when disabling a single plugin,
     * as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     *
     * @param selectedPlugin The plugin to unregister commands from
     */
    private void unregisterCommands(Plugin selectedPlugin) {
        Set<Map.Entry<String, Command>> origSet = exposed.knownCommands.entrySet();
        for(Iterator<Map.Entry<String, Command>> i = origSet.iterator(); i.hasNext();) {
            Command command = i.next().getValue();
            if(!(command instanceof PluginCommand)) continue;
            PluginCommand pluginCommand = (PluginCommand) command;
            Plugin owningPlugin = pluginCommand.getPlugin();
            if(selectedPlugin != owningPlugin) continue;
            if(pluginCommand.isRegistered()) {
                pluginCommand.unregister(exposed.commandMap);
            }
            i.remove();
        }
    }

    /**
     * Helper method to get the fallback string of a plugin.
     * This is used when getting some commands that are prefixed.
     *
     * @param selectedPlugin The plugin to get the fallback string from
     * @return The fallback string
     */
    private String getFallback(Plugin selectedPlugin) {
        return selectedPlugin.getDescription().getName().toLowerCase(Locale.ENGLISH).trim();
    }

    /**
     * Load a plugin from the file system. This method loads a plugin from new by
     * iterating through the plugins folder and reading the name of the plugin until
     * it reaches a plugin with the specified name.
     *
     * @param pluginName The name of the plugin to be loaded
     * @return The loaded plugin, null if not found
     */
    private Plugin loadPlugin(String pluginName) {
        PluginManager manager = plugin.getServer().getPluginManager();

        File pluginFile = getPluginFile(pluginName);

        try {
            return manager.loadPlugin(pluginFile);
        } catch (InvalidPluginException | InvalidDescriptionException ex) {
            plugin.getServer().getLogger().log(Level.SEVERE, "Could not load '" + pluginFile.getPath() + "' in folder '" + PLUGINS_DIRECTORY.getPath() + "'", ex);
        }
        return null;
    }

    /**
     * Get the file of an already loaded Plugin. This method iterates through all files in the plugins directory until
     * it finds the jar file with the same name as the loaded plugin.
     *
     * @param pluginName The name of the plugin to find the file for
     * @return The located file, null if none found
     */
    private File getPluginFile(String pluginName) {
        for (File file : PLUGINS_DIRECTORY.listFiles()) {
            PluginDescriptionFile curDescription = getPluginDescription(file, true);
            if(curDescription == null) continue;

            if(pluginName.equals(curDescription.getName())) {
                return file;
            }
        }

        return null;
    }

    /**
     * Get the {@link PluginDescriptionFile} of a <code>File</code> representing a plugin jar file.
     *
     * @param pluginFile The plugin File to get the description from
     * @param throwErrors Whether to throw errors or not
     * @return The generated <code>PluginDescriptionFile</code>, null if error occurred
     */
    private PluginDescriptionFile getPluginDescription(File pluginFile, boolean throwErrors) {
        PluginLoader loader = getPluginLoader(pluginFile);
        if (loader == null) return null;

        PluginDescriptionFile curDescription = null;
        try {
            curDescription = loader.getPluginDescription(pluginFile);
        }
        catch (InvalidDescriptionException ex) {
            if(!throwErrors) return null;
            plugin.getServer().getLogger().log(
                Level.SEVERE,
                String.format("Could not load '%s' in folder '%s'", pluginFile.getPath(), PLUGINS_DIRECTORY.getPath()),
                ex);
        }
        return curDescription;
    }

    /**
     * Get the {@link PluginLoader} of a <code>File</code>. This method uses the same code from
     * {@link SimplePluginManager} to obtain the correct loader from the internal list of file associations representing
     * the different types of plugins that can be loaded.
     *
     * @param pluginFile The plugin file to get the <code>PluginLoader</code> for
     * @return The located <code>PluginLoader</code>, null if not found
     */
    private PluginLoader getPluginLoader(File pluginFile) {
        for (Pattern filter : exposed.fileAssociations.keySet()) {
            Matcher match = filter.matcher(pluginFile.getName());
            if (match.find()) {
                return exposed.fileAssociations.get(filter);
            }
        }
        return null;
    }

    /**
     * An internal runnable for checking plugin files for changes
     *
     * @author Mikedeejay2
     */
    private class AutoReloaderRunnable implements Runnable {
        /**
         * The list of last modified values. Key = File name, value = Time last modified (since last queried)
         */
        private final Map<String, Long> lastModified = new HashMap<>();

        @Override
        public void run() {
            PluginManager pluginManager = Bukkit.getPluginManager();

            for(File pluginFile : PLUGINS_DIRECTORY.listFiles()) {
                if(getPluginLoader(pluginFile) == null) continue;
                // If the file is currently being moved and is incomplete we do not want this to throw errors
                // The file will be ready next time
                final PluginDescriptionFile description = getPluginDescription(pluginFile, false);
                if(description == null) continue;
                final long modifiedDate = getModifiedDate(pluginFile);
                final String pluginName = description.getName();

                // If map doesn't contain the key, add it to the map
                if(!lastModified.containsKey(pluginName)) {
                    lastModified.put(pluginName, modifiedDate);
                    // If the plugin is already loaded there's no need to load it again
                    if(pluginManager.isPluginEnabled(pluginName)) continue;

                    Bukkit.getScheduler().runTask(plugin, () -> autoLoadPlugin(pluginName));
                } else if(lastModified.get(pluginName) != modifiedDate) { // If times don't match, reload
                    Plugin curPlugin = pluginManager.getPlugin(pluginName);

                    Bukkit.getScheduler().runTask(plugin, () -> autoReloadPlugin(pluginName, curPlugin));
                    lastModified.put(pluginName, modifiedDate);
                }
            }
        }

        /**
         * Method called when a plugin is loaded automatically
         *
         * @param pluginName The name of the plugin to be loaded
         */
        private void autoLoadPlugin(String pluginName) {
            serverSender.sendMessage(ChatColor.YELLOW + String.format("Found new plugin \"%s\", loading...", pluginName));
            long startTime = System.currentTimeMillis();

            loadAndEnablePlugin(pluginName);

            long endTime = System.currentTimeMillis();
            long differenceTime = endTime - startTime;
            serverSender.sendMessage(ChatColor.GREEN + String.format("The server has successfully loaded plugin \"%s\" in %d ms.", pluginName, differenceTime));
        }

        /**
         * Method called when a plugin is reloaded automatically
         *
         * @param pluginName The name of the plugin to be reloaded
         * @param curPlugin The instance of the plugin to be reloaded
         */
        private void autoReloadPlugin(String pluginName, Plugin curPlugin) {
            serverSender.sendMessage(ChatColor.YELLOW + String.format("Detected plugin \"%s\" has been updated, reloading...", pluginName));
            long startTime = System.currentTimeMillis();

            reloadPlugin(curPlugin);

            long endTime = System.currentTimeMillis();
            long differenceTime = endTime - startTime;

            serverSender.sendMessage(ChatColor.GREEN + String.format("The server has successfully reloaded plugin \"%s\" in %d ms.", pluginName, differenceTime));
        }

        /**
         * Get the last modified date of a <code>File</code>. This is returned as a long in milliseconds.
         *
         * @param file The file to check
         * @return The retrieved last modified time long, -1 if error occurred
         */
        private long getModifiedDate(File file) {
            try {
                return Files.getLastModifiedTime(Paths.get(file.getPath())).toMillis();
            } catch(IOException e) {
                e.printStackTrace();
                return -1;
            }
        }
    }
}
