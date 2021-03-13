package com.mikedeejay2.fastreload.listeners;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.function.BiConsumer;

public class ChatListener implements Listener
{
    private final BiConsumer<CommandSender, String[]> reloader;

    public ChatListener(final BiConsumer<CommandSender, String[]> reloader)
    {
        this.reloader = reloader;
    }

    @EventHandler
    public void chatEvent(AsyncPlayerChatEvent event)
    {
        switch(event.getMessage())
        {
            case "r":
            case "rl":
            case "reload":
                event.setCancelled(true);
                break;
            default:
                return;
        }
        Player player = event.getPlayer();
        reloader.accept(player, null);
    }
}
