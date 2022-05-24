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
import org.bukkit.command.PluginCommand;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main reloading system class.
 * <p>
 * Algorithms here heavily utilize {@link ExposedVariables} and reflection.
 *
 * @author Mikedeejay2
 */
public class ReloadSystem implements FastReloadConfig.LoadListener {
    private final FastReload plugin;
    private ExposedVariables exposed;
    private BiConsumer<CommandSender, String[]> reloadConsumer;
    private Predicate<CommandSender> permissionPredicate;
    private ChatListener chatListener;
    private FastReloadCommand commandExecutor;

    /**
     * Construct a new reloading system
     *
     * @param plugin A reference to the <code>FastReload</code> plugin
     */
    public ReloadSystem(FastReload plugin) {
        this.plugin = plugin;
        this.plugin.config().registerListener(this);
        initialize();
    }

    @Override
    public void onConfigLoad(FastReloadConfig config) {
        this.reloadConsumer = config.ONLY_PLUGINS.get() ? this::reloadPlugins : this::reloadFull;
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
        plugin.getLogger().info(String.format("Player %s reloaded the server!", sender.getName()));

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
        plugin.getLogger().info(String.format(ChatColor.RED + "Player %s reloaded the server's plugins!", sender.getName()));

        PluginManager pluginManager = Bukkit.getPluginManager();
        Server server = plugin.getServer();

        long startTime = System.currentTimeMillis();

        // Manual removal of plugin commands start
        for(Plugin plugin : pluginManager.getPlugins()) {
            unregisterCommands(plugin);
        }
        // Manual removal of plugin commands end

        // Manual reload of plugins start
        pluginManager.clearPlugins();
        if(loadPlugins(server)) return;
        // Manual reload of plugins end

        // Call the server load event. Probably not required but there just in case a plugin uses for something.
        callReloadEvent();

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
        plugin.getLogger().info(String.format(ChatColor.RED + "Player %s reloaded the server's plugins!", sender.getName()));

        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin selectedPlugin = pluginManager.getPlugin(pluginName);

        if(selectedPlugin == null) {
            sender.sendMessage(ChatColor.RED + String.format("The plugin \"%s\" is not a valid plugin.", pluginName));
            return;
        }

        long startTime = System.currentTimeMillis();

        disablePlugin(selectedPlugin);
        unregisterPlugin(selectedPlugin);
        unregisterCommands(selectedPlugin);
        unregisterLookups(selectedPlugin);
        unregisterPermissions(selectedPlugin);
        enablePlugin(selectedPlugin);

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;

        sender.sendMessage(ChatColor.GREEN + String.format("The server has successfully reloaded plugin \"%s\" in %d ms.", pluginName, differenceTime));
    }

    /**
     * Enable a selected plugin. This method will find the given plugin on disk,
     * load it as new, and then enable it on the server.
     * <p>
     * Note that the plugin should be fully disabled and completely removed from
     * CraftBukkit before calling this method! If not, bad things will probably happen.
     *
     * @param selectedPlugin The plugin to load
     */
    private void enablePlugin(Plugin selectedPlugin) {
        Plugin plugin = loadPlugin(selectedPlugin.getDescription().getName());
        plugin.getServer().getPluginManager().enablePlugin(plugin);
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
        Server server = plugin.getServer();
        File directory = new File("plugins");

        Set<Pattern> filters = exposed.fileAssociations.keySet();

        File pluginFile = null;

        for (File file : directory.listFiles()) {
            PluginLoader loader = null;
            for (Pattern filter : filters) {
                Matcher match = filter.matcher(file.getName());
                if (match.find()) {
                    loader = exposed.fileAssociations.get(filter);
                }
            }

            if (loader == null) continue;

            PluginDescriptionFile curDescription;
            try {
                curDescription = loader.getPluginDescription(file);
            }
            catch (InvalidDescriptionException ex) {
                server.getLogger().log(Level.SEVERE, "Could not load '" + file.getPath() + "' in folder '" + directory.getPath() + "'", ex);
                continue;
            }

            if(pluginName.equals(curDescription.getName())) {
                pluginFile = file;
                break;
            }
        }

        try {
            return manager.loadPlugin(pluginFile);
        } catch (InvalidPluginException | InvalidDescriptionException ex) {
            server.getLogger().log(Level.SEVERE, "Could not load '" + pluginFile.getPath() + "' in folder '" + directory.getPath() + "'", ex);
        }
        return null;
    }
}
