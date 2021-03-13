package com.mikedeejay2.fastreload.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FastReloadCommand implements CommandExecutor
{
    private final BiConsumer<CommandSender, String[]> reloader;

    public FastReloadCommand(BiConsumer<CommandSender, String[]> reloader)
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
        reloader.accept(sender, args);
        return true;
    }
}
