package com.mikedeejay2.fastreload;

import com.mikedeejay2.fastreload.commands.FastReloadCommand;
import com.mikedeejay2.fastreload.listeners.ChatListener;
import com.mikedeejay2.fastreload.util.ExposedVariables;
import com.mikedeejay2.fastreload.util.ReflectUtil;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReloadSystem
{
    private final FastReload plugin;
    private ExposedVariables exposed;
    private BiConsumer<CommandSender, String[]> reloadConsumer;
    private Predicate<CommandSender> permissionPredicate;

    public ReloadSystem(FastReload plugin)
    {
        this.plugin = plugin;
        initialize();
    }

    protected void initialize()
    {
        plugin.getServer().getPluginManager().registerEvents(new ChatListener(this::reload), plugin);
        plugin.getCommand("fastreload").setExecutor(new FastReloadCommand(this::reload));
        this.exposed = new ExposedVariables(plugin.getServer());
        this.reloadConsumer = plugin.getConfig().getBoolean("Only Plugins") ? this::reloadPlugins : this::reloadFull;
        this.permissionPredicate = plugin::checkPermission;
    }

    public void reload(final CommandSender sender, String[] args)
    {
        if(!permissionPredicate.test(sender)) return;
        // Schedule task on sync because of Async chat event
        Bukkit.getScheduler().runTask(plugin, () -> reloadConsumer.accept(sender, args));
    }

    private void reloadFull(final CommandSender sender, String[] args)
    {
        sender.sendMessage(ChatColor.YELLOW + "The server is reloading...");
        long startTime = System.currentTimeMillis();
        plugin.getLogger().info(String.format("Player %s reloaded the server!", sender.getName()));

        plugin.getServer().reload();

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;
        sender.sendMessage(ChatColor.GREEN + "The server has successfully reloaded in " + differenceTime + "ms.");
    }

    private void reloadPlugins(final CommandSender sender, String[] args)
    {
        if(args == null || args.length == 0)
        {
            reloadAllPlugins(sender);
        }
        else
        {
            reloadPlugin(sender, args);
        }
    }

    private void reloadAllPlugins(final CommandSender sender)
    {
        sender.sendMessage(ChatColor.YELLOW + "The server is reloading all plugins...");
        plugin.getLogger().info(String.format(ChatColor.RED + "Player %s reloaded the server's plugins!", sender.getName()));

        PluginManager pluginManager = Bukkit.getPluginManager();
        Server server = plugin.getServer();

        long startTime = System.currentTimeMillis();

        // Manual removal of plugin commands start
        for(Plugin plugin : pluginManager.getPlugins())
        {
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

    private void reloadPlugin(final CommandSender sender, String[] args)
    {
        String pluginName = String.join(" ", args);
        sender.sendMessage(ChatColor.YELLOW + String.format("The server is reloading plugin \"%s\"...", pluginName));
        plugin.getLogger().info(String.format(ChatColor.RED + "Player %s reloaded the server's plugins!", sender.getName()));

        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin selectedPlugin = pluginManager.getPlugin(pluginName);

        if(selectedPlugin == null)
        {
            sender.sendMessage(ChatColor.RED + String.format("The plugin \"%s\" is not a valid plugin.", pluginName));
            return;
        }

        long startTime = System.currentTimeMillis();

        disablePlugin(selectedPlugin);
        removePlugin(selectedPlugin);
        unregisterCommands(selectedPlugin);
        removeLookups(selectedPlugin);
        removeDependencyRefs(selectedPlugin);
        removePermissions();
        enablePlugin(selectedPlugin);

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;

        sender.sendMessage(ChatColor.GREEN + String.format("The server has successfully reloaded plugin \"%s\" in %d ms.", pluginName, differenceTime));
    }

    private void enablePlugin(Plugin selectedPlugin)
    {
        Plugin plugin = loadPlugin(selectedPlugin.getDescription().getName());
        plugin.getServer().getPluginManager().enablePlugin(plugin);
    }

    private Plugin loadPlugin(String pluginName)
    {
        PluginManager manager = plugin.getServer().getPluginManager();
        Server server = plugin.getServer();
        File directory = new File("plugins");

        Validate.notNull(directory, "Directory cannot be null");
        Validate.isTrue(directory.isDirectory(), "Directory must be a directory");

        Set<Pattern> filters = exposed.fileAssociations.keySet();

        Map.Entry<String, File> plugins = null;

        // This is where it figures out all possible plugins
        PluginDescriptionFile description = null;

        for (File file : directory.listFiles())
        {
            PluginLoader loader = null;
            for (Pattern filter : filters)
            {
                Matcher match = filter.matcher(file.getName());
                if (match.find())
                {
                    loader = exposed.fileAssociations.get(filter);
                }
            }

            if (loader == null) continue;

            PluginDescriptionFile curDescription = null;
            try
            {
                curDescription = loader.getPluginDescription(file);
            }
            catch (InvalidDescriptionException ex)
            {
                server.getLogger().log(Level.SEVERE, "Could not load '" + file.getPath() + "' in folder '" + directory.getPath() + "'", ex);
                continue;
            }

            if(pluginName.equals(curDescription.getName()))
            {
                plugins = new AbstractMap.SimpleEntry<>(curDescription.getName(), file);
                description = curDescription;
                break;
            }
        }

        Collection<String> softDependencySet = description.getSoftDepend();
        if (softDependencySet != null && !softDependencySet.isEmpty())
        {
            for (String depend : softDependencySet)
            {
                exposed.dependencyGraph.putEdge(description.getName(), depend);
            }
        }

        Collection<String> dependencySet = description.getDepend();
        if (dependencySet != null && !dependencySet.isEmpty())
        {
            for (String depend : dependencySet)
            {
                exposed.dependencyGraph.putEdge(description.getName(), depend);
            }
        }

        Collection<String> loadBeforeSet = description.getLoadBefore();
        if (loadBeforeSet != null && !loadBeforeSet.isEmpty())
        {
            for (String loadBeforeTarget : loadBeforeSet)
            {
                exposed.dependencyGraph.putEdge(loadBeforeTarget, description.getName());
            }
        }

        File file = plugins.getValue();

        try
        {
            return manager.loadPlugin(file);
        }
        catch (InvalidPluginException | InvalidDescriptionException ex)
        {
            server.getLogger().log(Level.SEVERE, "Could not load '" + file.getPath() + "' in folder '" + directory.getPath() + "'", ex);
        }
        return null;
    }

    private void removePermissions()
    {
        List<Permission> permissions = plugin.getDescription().getPermissions();

        PluginManager manager = plugin.getServer().getPluginManager();
        for(Permission permission : permissions)
        {
            manager.removePermission(permission);
            exposed.defaultPerms.get(true).remove(permission);
            exposed.defaultPerms.get(false).remove(permission);
        }
    }

    private void disablePlugin(Plugin selectedPlugin)
    {
        plugin.getServer().getPluginManager().disablePlugin(selectedPlugin);
    }

    private void removeDependencyRefs(Plugin selectedPlugin)
    {
        PluginDescriptionFile description = selectedPlugin.getDescription();
        Collection<String> softDependencySet = description.getSoftDepend();
        if (!softDependencySet.isEmpty())
        {
            for (String depend : softDependencySet)
            {
                exposed.dependencyGraph.removeEdge(description.getName(), depend);
            }
        }

        Collection<String> dependencySet = description.getDepend();
        if (!dependencySet.isEmpty())
        {
            for (String depend : dependencySet)
            {
                exposed.dependencyGraph.removeEdge(description.getName(), depend);
            }
        }

        Collection<String> loadBeforeSet = description.getLoadBefore();
        if (!loadBeforeSet.isEmpty())
        {
            for (String loadBeforeTarget : loadBeforeSet)
            {
                exposed.dependencyGraph.removeEdge(loadBeforeTarget, description.getName());
            }
        }
    }

    private void removeLookups(Plugin selectedPlugin)
    {
        exposed.lookupNames.remove(selectedPlugin.getDescription().getName().toLowerCase());
        for (String provided : selectedPlugin.getDescription().getProvides())
        {
            exposed.lookupNames.remove(provided.toLowerCase());
        }
    }

    private void removePlugin(Plugin selectedPlugin)
    {
        exposed.plugins.remove(selectedPlugin);
    }

    private void callReloadEvent()
    {
        plugin.getServer().getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.RELOAD));
    }

    private boolean loadPlugins(Server server)
    {
        try
        {
            // Not using ReflectUtils here since it's maybe a few ms faster because of double invoking
            Method loadMethod = server.getClass().getDeclaredMethod("loadPlugins");
            Method enableMethod = server.getClass().getDeclaredMethod("enablePlugins", PluginLoadOrder.class);
            loadMethod.invoke(server);
            enableMethod.invoke(server, PluginLoadOrder.STARTUP);
            enableMethod.invoke(server, PluginLoadOrder.POSTWORLD);
        }
        catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
        {
            e.printStackTrace();
            return true;
        }
        return false;
    }

    private void unregisterCommands(Plugin plugin)
    {
        String fallbackPrefix = plugin.getDescription().getName().toLowerCase(java.util.Locale.ENGLISH).trim();
        for(String name : plugin.getDescription().getCommands().keySet())
        {
            Command mainCmd = exposed.commandMap.getCommand(name);
            if(mainCmd == null) continue;
            List<String> aliases = mainCmd.getAliases();

            mainCmd.unregister(exposed.commandMap);
            exposed.knownCommands.remove(name);
            exposed.knownCommands.remove(fallbackPrefix + ":" + name);

            for(String alias : aliases)
            {
                Command aliasCmd = exposed.commandMap.getCommand(alias);
                if(aliasCmd == null) continue;
                aliasCmd.unregister(exposed.commandMap);
                exposed.knownCommands.remove(alias);
                exposed.knownCommands.remove(fallbackPrefix + ":" + alias);
            }
        }
    }
}
