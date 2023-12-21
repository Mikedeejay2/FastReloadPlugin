package com.mikedeejay2.fastreload.system;

import com.mikedeejay2.fastreload.FastReload;
import com.mikedeejay2.fastreload.commands.FastReloadCommand;
import com.mikedeejay2.fastreload.config.FastReloadConfig;
import com.mikedeejay2.fastreload.listeners.ChatListener;
import com.mikedeejay2.fastreload.util.FieldsBase;
import com.mikedeejay2.fastreload.util.ReflectUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.BukkitTask;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Main reloading system class.
 *
 * @author Mikedeejay2
 */
public class ReloadSystem implements FastReloadConfig.LoadListener {
    public static final File PLUGINS_DIRECTORY = new File("plugins");
    protected final FastReload plugin;
    protected final ConsoleCommandSender serverSender;
    protected final FieldsBase fields;
    protected BiConsumer<CommandSender, String[]> reloadConsumer;
    protected Predicate<CommandSender> permissionPredicate;
    protected ChatListener chatListener;
    protected FastReloadCommand commandExecutor;
    protected List<String> pluginFilterList;
    protected boolean filterWhitelist;
    protected BukkitTask autoReloader;

    /**
     * Construct a new reloading system
     *
     * @param plugin A reference to the <code>FastReload</code> plugin
     */
    public ReloadSystem(FastReload plugin, FieldsBase fields) {
        this.plugin = plugin;
        this.fields = fields;
        this.serverSender = plugin.getServer().getConsoleSender();
        this.chatListener = new ChatListener(this::reload);
        this.commandExecutor = new FastReloadCommand(this::reload);
        this.permissionPredicate = plugin::checkPermission;
        loadCommands();
        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
        this.reloadConsumer = null;

        this.plugin.config().registerListener(this);
        this.plugin.config().registerListener(chatListener);
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
            double autoReloadWait = config.AUTO_RELOAD_WAIT.get();
            this.autoReloader = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, new AutoReloaderRunnable(plugin, this, autoReloadWait),
                autoReloadTime, autoReloadTime);
        }
    }

    /**
     * Load all of the reload commands directly into the <code>knownCommands</code> map
     * in {@link org.bukkit.command.SimpleCommandMap}.
     * <p>
     * If a command already exists for a reload command, this will remove the existing
     * command before injecting the new command into its place.
     */
    protected void loadCommands() {
        Constructor<PluginCommand> pluginConstructor;
        try {
            // Manually get constructor using reflection because the constructor will be used once per plugin
            pluginConstructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            pluginConstructor.setAccessible(true);
            for(String commandStr : new String[]{"reload", "rl", "r"}) {
                fields.knownCommands().remove(commandStr);
                PluginCommand command;
                command = pluginConstructor.newInstance(commandStr, plugin);
                command.setExecutor(commandExecutor);
                command.setTabCompleter(commandExecutor);
                fields.knownCommands().put(commandStr, command);
                fields.knownCommands().put(getFallback(plugin) + ":" + commandStr, command);
            }
        } catch(NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
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
    protected void reloadFull(final CommandSender sender, String[] args) {
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
    protected void vanillaReload(CommandSender sender)
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
    protected void reloadPlugins(final CommandSender sender, String[] args) {
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
    protected void reloadAllPlugins(final CommandSender sender) {
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
    protected void reloadPlugin(final CommandSender sender, String[] args) {
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
    protected void reloadPlugin(Plugin thePlugin) {
        disableAndUnregisterPlugin(thePlugin);
        loadAndEnablePlugin(thePlugin.getName());
    }

    /**
     * Disable and unregister a specific plugin.
     *
     * @param thePlugin The plugin to disable
     */
    protected void disableAndUnregisterPlugin(Plugin thePlugin) {
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
    protected void loadAndEnablePlugin(String pluginName) {
        Plugin newPlugin = loadPlugin(pluginName);
        if(newPlugin == null) return;
        plugin.getServer().getPluginManager().enablePlugin(newPlugin);
        try {
            ReflectUtil.invokeMethod("syncCommands", plugin.getServer(), plugin.getServer().getClass(), new Class[0], new Object[0]);
        } catch(NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
            plugin.getServer().getLogger().log(Level.SEVERE, "Could not sync commands for '" + plugin.getName() + "'", ex);
        }
    }

    /**
     * Helper method to disable a plugin using {@link SimplePluginManager#disablePlugin(Plugin)}.
     * This method DOES NOT fully unregister the plugin!
     *
     * @param selectedPlugin The plugin to disable
     */
    protected void disablePlugin(Plugin selectedPlugin) {
        plugin.getServer().getPluginManager().disablePlugin(selectedPlugin);
    }

    /**
     * Helper method to get the fallback string of a plugin.
     * This is used when getting some commands that are prefixed.
     *
     * @param selectedPlugin The plugin to get the fallback string from
     * @return The fallback string
     */
    protected String getFallback(Plugin selectedPlugin) {
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
    protected Plugin loadPlugin(String pluginName) {
        PluginManager manager = plugin.getServer().getPluginManager();

        File pluginFile = getPluginFile(pluginName);

        try {
            return manager.loadPlugin(pluginFile);
        } catch (InvalidPluginException | InvalidDescriptionException ex) {
            plugin.getServer().getLogger().log(Level.SEVERE, "Could not load '" + pluginFile.getPath() + "' in folder '" + PLUGINS_DIRECTORY.getPath() + "'", ex);
        } catch(NoClassDefFoundError ignored) {
            // ignored
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
    protected File getPluginFile(String pluginName) {
        for (File file : ReloadSystem.getPluginFiles()) {
            PluginDescriptionFile curDescription = getPluginDescription(file, true);
            if(curDescription == null) continue;

            if(pluginName.equals(curDescription.getName())) {
                return file;
            }
        }

        return null;
    }

    /**
     * Remove all permissions from a plugin. This should be used when disabling a single
     * plugin, as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     */
    protected void unregisterPermissions(Plugin selectedPlugin) {
        List<Permission> permissions = selectedPlugin.getDescription().getPermissions();

        PluginManager manager = plugin.getServer().getPluginManager();
        for(Permission permission : permissions) {
            manager.removePermission(permission);
            fields.defaultPerms().get(true).remove(permission);
            fields.defaultPerms().get(false).remove(permission);
        }
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
    protected void unregisterLookups(Plugin selectedPlugin) {
        fields.lookupNames().remove(selectedPlugin.getDescription().getName().toLowerCase());
        for (String provided : selectedPlugin.getDescription().getProvides()) {
            fields.lookupNames().remove(provided.toLowerCase());
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
    protected void unregisterPlugin(Plugin selectedPlugin) {
        fields.plugins().remove(selectedPlugin);
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
    protected void unregisterCommands(Plugin selectedPlugin) {
        Set<Map.Entry<String, Command>> origSet = fields.knownCommands().entrySet();
        for(Iterator<Map.Entry<String, Command>> i = origSet.iterator(); i.hasNext();) {
            Command command = i.next().getValue();
            if(!(command instanceof PluginCommand)) continue;
            PluginCommand pluginCommand = (PluginCommand) command;
            Plugin owningPlugin = pluginCommand.getPlugin();
            if(selectedPlugin != owningPlugin) continue;
            if(pluginCommand.isRegistered()) {
                pluginCommand.unregister(fields.commandMap());
            }
            i.remove();
        }
    }

    /**
     * Get the {@link PluginDescriptionFile} of a <code>File</code> representing a plugin jar file.
     *
     * @param pluginFile The plugin File to get the description from
     * @param throwErrors Whether to throw errors or not
     * @return The generated <code>PluginDescriptionFile</code>, null if error occurred
     */
    protected PluginDescriptionFile getPluginDescription(File pluginFile, boolean throwErrors) {
        PluginDescriptionFile curDescription = null;
        try(JarFile jarFile = new JarFile(pluginFile)) {
            JarEntry entry = jarFile.getJarEntry("plugin.yml");
            curDescription = createPluginDescription(jarFile, entry);
        } catch (IOException | InvalidDescriptionException ex) {
            if(!throwErrors) return null;
            plugin.getServer().getLogger().log(
                Level.SEVERE,
                String.format("Could not load '%s' in folder '%s'", pluginFile.getPath(), PLUGINS_DIRECTORY.getPath()),
                ex);
        }
        return curDescription;
    }

    /**
     * Create a {@link PluginDescriptionFile} from a provided {@link JarFile} and {@link JarEntry}.
     *
     * @param file The {@link JarFile} of the plugin.
     * @param config The {@link JarEntry} of the <code>plugin.yml</code> contained within the {@link JarFile}. In most
     *               cases this {@link JarEntry} should be the <code>plugin.yml</code>.
     * @return The create {@link PluginDescriptionFile}
     * @throws InvalidDescriptionException If the <code>plugin.yml</code> of the jar file is invalid.
     */
    protected PluginDescriptionFile createPluginDescription(JarFile file, JarEntry config) throws InvalidDescriptionException {
        PluginDescriptionFile descriptionFile;
        try(InputStream inputStream = file.getInputStream(config)) {
            descriptionFile = new PluginDescriptionFile(inputStream);
        } catch(IOException | YAMLException ex) {
            throw new InvalidDescriptionException(ex);
        }

        return descriptionFile;
    }

    /**
     * Get an array of all plugin files in the plugins directory.
     *
     * @return All plugin files
     */
    public static File[] getPluginFiles() {
        return PLUGINS_DIRECTORY.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
    }
}
