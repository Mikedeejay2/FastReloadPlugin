package com.mikedeejay2.fastreload.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FastReloadCommand implements TabExecutor
{
    private final BiConsumer<CommandSender, String[]> reloader;

    public FastReloadCommand(BiConsumer<CommandSender, String[]> reloader)
    {
        this.reloader = reloader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        reloader.accept(sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if(args.length != 1) return null;
        List<String> tabCompletes = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                                          .map(Plugin::getName)
                                          .collect(Collectors.toList());
        return tabCompletes;
    }
}
