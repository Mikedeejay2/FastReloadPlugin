package com.mikedeejay2.fastreload;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.function.Consumer;

public class FastReloadCommand implements CommandExecutor
{
    private final Consumer<CommandSender> reloader;

    public FastReloadCommand(Consumer<CommandSender> reloader)
    {
        this.reloader = reloader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if(args.length > 0)
        {
            sender.sendMessage(ChatColor.RED + "Error: This command does not support arguments.");
            return false;
        }
        reloader.accept(sender);
        return true;
    }
}
