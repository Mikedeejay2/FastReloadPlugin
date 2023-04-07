package com.mikedeejay2.fastreload.system;


import com.mikedeejay2.fastreload.FastReload;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * An internal runnable for checking plugin files for changes
 *
 * @author Mikedeejay2
 */
public class AutoReloaderRunnable implements Runnable {
    private final FastReload plugin;
    private final ReloadSystem system;
    private final ConsoleCommandSender serverSender;

    /**
     * The list of last modified values. Key = File, value = Time last modified (since last queried)
     */
    private final Map<File, Long> lastModified = new HashMap<>();

    public AutoReloaderRunnable(FastReload plugin, ReloadSystem system) {
        this.plugin = plugin;
        this.system = system;
        this.serverSender = plugin.getServer().getConsoleSender();
    }

    @Override
    public void run() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        for(File pluginFile : ReloadSystem.getPluginFiles()) {
            // If the file is currently being moved and is incomplete we do not want this to throw errors
            // The file will be ready next time
            final PluginDescriptionFile description = system.getPluginDescription(pluginFile, false);
            if(description == null) continue;
            final long modifiedDate = getModifiedDate(pluginFile);
            final String pluginName = description.getName();

            // If map doesn't contain the key, add it to the map
            if(!lastModified.containsKey(pluginFile)) {
                lastModified.put(pluginFile, modifiedDate);
                // If the plugin is already loaded there's no need to load it again
                if(pluginManager.isPluginEnabled(pluginName)) continue;

                Bukkit.getScheduler().runTask(plugin, () -> autoLoadPlugin(pluginName));
            } else if(lastModified.get(pluginFile) != modifiedDate) { // If times don't match, reload
                Plugin curPlugin = pluginManager.getPlugin(pluginName);

                Bukkit.getScheduler().runTask(plugin, () -> autoReloadPlugin(pluginName, curPlugin));
                lastModified.put(pluginFile, modifiedDate);
            }
        }
    }

    /**
     * Method called when a plugin is loaded automatically
     *
     * @param pluginName The name of the plugin to be loaded
     */
    private void autoLoadPlugin(String pluginName) {
        serverSender.sendMessage(ChatColor.YELLOW + String.format("Found new plugin \"%s\", loading...", pluginName));
        long startTime = System.currentTimeMillis();

        system.loadAndEnablePlugin(pluginName);

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;
        serverSender.sendMessage(ChatColor.GREEN + String.format("The server has successfully loaded plugin \"%s\" in %d ms.", pluginName, differenceTime));
    }

    /**
     * Method called when a plugin is reloaded automatically
     *
     * @param pluginName The name of the plugin to be reloaded
     * @param curPlugin The instance of the plugin to be reloaded
     */
    private void autoReloadPlugin(String pluginName, Plugin curPlugin) {
        serverSender.sendMessage(ChatColor.YELLOW + String.format("Detected plugin \"%s\" has been updated, reloading...", pluginName));
        long startTime = System.currentTimeMillis();

        system.reloadPlugin(curPlugin);

        long endTime = System.currentTimeMillis();
        long differenceTime = endTime - startTime;

        serverSender.sendMessage(ChatColor.GREEN + String.format("The server has successfully reloaded plugin \"%s\" in %d ms.", pluginName, differenceTime));
    }

    /**
     * Get the last modified date of a <code>File</code>. This is returned as a long in milliseconds.
     *
     * @param file The file to check
     * @return The retrieved last modified time long, -1 if error occurred
     */
    private long getModifiedDate(File file) {
        try {
            return Files.getLastModifiedTime(Paths.get(file.getPath())).toMillis();
        } catch(IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
