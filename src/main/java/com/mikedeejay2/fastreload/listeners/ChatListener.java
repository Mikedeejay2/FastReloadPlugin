package com.mikedeejay2.fastreload.listeners;

import com.mikedeejay2.fastreload.FastReload;
import com.mikedeejay2.fastreload.config.FastReloadConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.function.BiConsumer;

/**
 * Chat event listener for using fast reload operations without the
 * use of commands.
 *
 * @author Mikedeejay2
 */
public class ChatListener implements Listener, FastReloadConfig.LoadListener {
    private final BiConsumer<CommandSender, String[]> reloader;
    private boolean shouldExecute;

    public ChatListener(final BiConsumer<CommandSender, String[]> reloader) {
        this.reloader = reloader;
    }

    @EventHandler
    public void chatEvent(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if(!shouldExecute || !player.hasPermission(FastReload.RELOAD_PERMISSION)) return;
        switch(event.getMessage()) {
            case "r":
            case "rl":
            case "reload":
                event.setCancelled(true);
                break;
            default:
                return;
        }
        reloader.accept(player, null);
    }

    @Override
    public void onConfigLoad(FastReloadConfig config) {
        shouldExecute = config.IN_CHAT_RELOAD.get();
    }
}
