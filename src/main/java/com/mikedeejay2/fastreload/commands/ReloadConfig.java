package com.mikedeejay2.fastreload.commands;

import com.mikedeejay2.fastreload.FastReload;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadConfig implements CommandExecutor {
    private final FastReload plugin;

    public ReloadConfig(FastReload plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!sender.hasPermission(FastReload.CONFIG_RELOAD_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Reloading Fast Reload's configuration.");
        plugin.config().loadConfig();
        sender.sendMessage(ChatColor.GREEN + "Successfully reloaded configuration");
        return true;
    }
}
