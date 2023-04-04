package com.mikedeejay2.fastreload;

import com.mikedeejay2.fastreload.commands.ReloadConfigCommand;
import com.mikedeejay2.fastreload.config.FastReloadConfig;
import com.mikedeejay2.fastreload.system.BukkitReloadSystem;
import com.mikedeejay2.fastreload.system.ReloadSystem;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fast Reload Plugin main class.
 * <p>
 * For system code go to {@link ReloadSystem}
 *
 * @author Mikedeejay2
 */
public final class FastReload extends JavaPlugin {
    public static final Permission RELOAD_PERMISSION = new Permission("fastreload.use");
    public static final Permission CONFIG_RELOAD_PERMISSION = new Permission("fastreload.reloadconfig");
    private ReloadSystem reloadSystem;
    private FastReloadConfig config;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.config = new FastReloadConfig(this.getConfig());
        this.reloadSystem = new BukkitReloadSystem(this);
        this.config.loadConfig();

        this.getCommand("fastreloadrc").setExecutor(new ReloadConfigCommand(this));
    }

    @Override
    public void onDisable() {
        this.reloadSystem.disable();
    }

    /**
     * Check whether a player has the permission to use Fast Reload.
     * If they don't then send a message saying that they don't have permission.
     *
     * @param sender The <code>CommandSender</code> to check
     * @return True if the sender has the permission, false if not
     */
    public boolean checkPermission(CommandSender sender) {
        if(!sender.hasPermission(FastReload.RELOAD_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
            return false;
        }
        return true;
    }

    public ReloadSystem getReloadSystem() {
        return reloadSystem;
    }

    public FastReloadConfig config() {
        return config;
    }
}
