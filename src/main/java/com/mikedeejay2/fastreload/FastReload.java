package com.mikedeejay2.fastreload;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoadOrder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public final class FastReload extends JavaPlugin
{
    public static final Permission RELOAD_PERMISSION = new Permission("fastreload");
    private FileConfiguration config;

    @Override
    public void onEnable()
    {
        this.getServer().getPluginManager().registerEvents(new ChatListener(this::reload), this);
        this.getCommand("fastreload").setExecutor(new FastReloadCommand(this::reload));
        this.saveDefaultConfig();
        this.config = this.getConfig();
    }

    @Override
    public void onDisable()
    {

    }

    public void reload(final CommandSender player)
    {
        if(!player.hasPermission(RELOAD_PERMISSION))
        {
            player.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
        }
        Bukkit.getScheduler().runTask(this, () -> reloadInternal(player));
    }

    private void reloadInternal(final CommandSender sender)
    {
        if(config.getBoolean("Only Plugins"))
        {
            reloadPlugins(sender);
        }
        else
        {
            fullReload(sender);
        }
    }

    private void fullReload(final CommandSender sender)
    {
        sender.sendMessage(ChatColor.YELLOW + "The server is reloading...");
        long startTime = System.currentTimeMillis();
        this.getLogger().info(String.format("Player %s reloaded the server!", sender.getName()));
        this.getServer().reload();
        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;
        sender.sendMessage(ChatColor.GREEN + "The server has successfully reloaded in " + differenceTime + "ms.");
    }

    private void reloadPlugins(final CommandSender sender)
    {
        sender.sendMessage(ChatColor.YELLOW + "The server is reloading all plugins...");
        this.getLogger().info(String.format(ChatColor.RED + "Player %s reloaded the server's plugins!", sender.getName()));
        PluginManager pluginManager = Bukkit.getPluginManager();
        Server server = this.getServer();
        for(Plugin plugin : pluginManager.getPlugins())
        {
            String pluginName = plugin.getName();
            pluginManager.disablePlugin(pluginManager.getPlugin(pluginName));
            pluginManager.enablePlugin(pluginManager.getPlugin(pluginName));
        }

        long startTime = System.currentTimeMillis();

        // Manual removal of plugin commands start
        SimpleCommandMap commands = null;
        Map<String, Command> knownCommands = null;
        try
        {
            Method cmdMapMethod = server.getClass().getDeclaredMethod("getCommandMap");
            commands = (SimpleCommandMap) cmdMapMethod.invoke(server);
            Field knownCmdsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCmdsField.setAccessible(true);
            knownCommands = (Map<String, Command>) knownCmdsField.get(commands);
        }
        catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException | NoSuchFieldException e)
        {
            e.printStackTrace();
            return;
        }

        for(Plugin plugin : pluginManager.getPlugins())
        {
            String fallbackPrefix = plugin.getDescription().getName().toLowerCase(java.util.Locale.ENGLISH).trim();
            for(String name : plugin.getDescription().getCommands().keySet())
            {
                Command mainCmd = commands.getCommand(name);
                if(mainCmd == null) continue;
                List<String> aliases = mainCmd.getAliases();

                mainCmd.unregister(commands);
                knownCommands.remove(name);
                knownCommands.remove(fallbackPrefix + ":" + name);

                for(String alias : aliases)
                {
                    Command aliasCmd = commands.getCommand(alias);
                    if(aliasCmd == null) continue;
                    aliasCmd.unregister(commands);
                    knownCommands.remove(alias);
                    knownCommands.remove(fallbackPrefix + ":" + alias);
                }
            }
        }
        // Manual removal of plugin commands end

        // Manual reload of plugins start
        pluginManager.clearPlugins();

        try
        {

            Method reloadMethod = server.getClass().getDeclaredMethod("loadPlugins");
            Method enableMethod = server.getClass().getDeclaredMethod("enablePlugins", PluginLoadOrder.class);
            reloadMethod.invoke(server);
            enableMethod.invoke(server, PluginLoadOrder.STARTUP);
            enableMethod.invoke(server, PluginLoadOrder.POSTWORLD);
        }
        catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
        {
            e.printStackTrace();
            return;
        }
        // Manual reload of plugins end

        // Call the server load event. Probably not required but there just in case a plugin uses for something.
        this.getServer().getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.RELOAD));

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;
        sender.sendMessage(ChatColor.GREEN + "The server has successfully reloaded all plugins in " + differenceTime + "ms.");
    }
}
