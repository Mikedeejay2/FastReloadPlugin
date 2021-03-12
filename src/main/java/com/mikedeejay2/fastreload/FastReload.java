package com.mikedeejay2.fastreload;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

public final class FastReload extends JavaPlugin
{
    public static final Permission RELOAD_PERMISSION = new Permission("fastreload");

    @Override
    public void onEnable()
    {
        this.getServer().getPluginManager().registerEvents(new ChatListener(this::reload), this);
        this.getCommand("fastreload").setExecutor(new FastReloadCommand(this::reload));
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
        player.sendMessage(ChatColor.YELLOW + "The server is reloading...");
        this.getLogger().info(String.format("Player %s reloaded the server!", player.getName()));
        this.getServer().reload();
        player.sendMessage(ChatColor.GREEN + "The server has successfully reloaded.");
    }
}
