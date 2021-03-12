package com.mikedeejay2.fastreload;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.function.Consumer;

public class ChatListener implements Listener
{
    private final Consumer<CommandSender> reloader;

    public ChatListener(final Consumer<CommandSender> reloader)
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
                break;
            default:
                return;
        }
        Player player = event.getPlayer();
        reloader.accept(player);
    }
}
