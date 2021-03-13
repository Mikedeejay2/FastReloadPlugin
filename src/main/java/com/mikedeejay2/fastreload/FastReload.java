package com.mikedeejay2.fastreload;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

public final class FastReload extends JavaPlugin
{
    public static final Permission RELOAD_PERMISSION = new Permission("fastreload.use");
    private ReloadSystem reloadSystem;

    @Override
    public void onEnable()
    {
        this.saveDefaultConfig();
        this.reloadSystem = new ReloadSystem(this);
    }

    @Override
    public void onDisable()
    {

    }

    public boolean checkPermission(CommandSender sender)
    {
        if(!sender.hasPermission(FastReload.RELOAD_PERMISSION))
        {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
            return false;
        }
        return true;
    }
}
