package com.mikedeejay2.fastreload;

import com.mikedeejay2.fastreload.commands.FastReloadCommand;
import com.mikedeejay2.fastreload.listeners.ChatListener;
import com.mikedeejay2.fastreload.util.ExposedVariables;
import com.mikedeejay2.fastreload.util.ReflectUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoadOrder;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

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
