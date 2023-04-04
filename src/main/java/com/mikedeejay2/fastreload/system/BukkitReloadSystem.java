package com.mikedeejay2.fastreload.system;

import com.mikedeejay2.fastreload.FastReload;
import com.mikedeejay2.fastreload.util.BukkitFields;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main reloading system class.
 * <p>
 * Algorithms here heavily utilize {@link BukkitFields} and reflection.
 *
 * @author Mikedeejay2
 */
public class BukkitReloadSystem extends ReloadSystem {
    protected BukkitFields fields;

    /**
     * Construct a new reloading system
     *
     * @param plugin A reference to the <code>FastReload</code> plugin
     */
    public BukkitReloadSystem(FastReload plugin) {
        super(plugin);
        this.fields = new BukkitFields(plugin.getServer());
    }


    /**
     * Load all of the reload commands directly into the <code>knownCommands</code> map
     * in {@link org.bukkit.command.SimpleCommandMap}.
     * <p>
     * If a command already exists for a reload command, this will remove the existing
     * command before injecting the new command into its place.
     */
    @Override
    protected void loadCommands() {
        String[] overrideCommands = {"reload", "rl", "r"};
        Constructor<PluginCommand> pluginConstructor;
        try {
            // Manually get constructor using reflection because the constructor will be used once per plugin
            pluginConstructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            pluginConstructor.setAccessible(true);
            for(String commandStr : overrideCommands) {
                fields.knownCommands.remove(commandStr);
                PluginCommand command;
                command = pluginConstructor.newInstance(commandStr, plugin);
                command.setExecutor(commandExecutor);
                command.setTabCompleter(commandExecutor);
                fields.knownCommands.put(commandStr, command);
                fields.knownCommands.put(getFallback(plugin) + ":" + commandStr, command);
            }
        } catch(NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove all permissions from a plugin. This should be used when disabling a single
     * plugin, as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     */
    @Override
    protected void unregisterPermissions(Plugin selectedPlugin) {
        List<Permission> permissions = selectedPlugin.getDescription().getPermissions();

        PluginManager manager = plugin.getServer().getPluginManager();
        for(Permission permission : permissions) {
            manager.removePermission(permission);
            fields.defaultPerms.get(true).remove(permission);
            fields.defaultPerms.get(false).remove(permission);
        }
    }

    /**
     * Remove lookup names for a plugin. This removes all lookup names
     * from {@link SimplePluginManager}
     * <p>
     * This should be used when disabling a single plugin,
     * as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     *
     * @param selectedPlugin The plugin to remove lookups to
     */
    @Override
    protected void unregisterLookups(Plugin selectedPlugin) {
        fields.lookupNames.remove(selectedPlugin.getDescription().getName().toLowerCase());
        for (String provided : selectedPlugin.getDescription().getProvides()) {
            fields.lookupNames.remove(provided.toLowerCase());
        }
    }

    /**
     * Remove a plugin from {@link SimplePluginManager}.
     * <p>
     * This should be used when disabling a single plugin,
     * as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     *
     * @param selectedPlugin The plugin to be removed
     */
    @Override
    protected void unregisterPlugin(Plugin selectedPlugin) {
        fields.plugins.remove(selectedPlugin);
    }

    /**
     * Unregister all commands registered by a plugin.
     * This method does give the honor system to the plugin in question,
     * as even if the plugin had attempted to initialize a plugin at the start
     * but failed this method will still unregister the command that was specified
     * anyways. That means that the next time that the plugin loads up it will have
     * priority over the command.
     * <p>
     * This should be used when disabling a single plugin,
     * as {@link SimplePluginManager#disablePlugin(Plugin)} doesn't do this.
     *
     * @param selectedPlugin The plugin to unregister commands from
     */
    @Override
    protected void unregisterCommands(Plugin selectedPlugin) {
        Set<Map.Entry<String, Command>> origSet = fields.knownCommands.entrySet();
        for(Iterator<Map.Entry<String, Command>> i = origSet.iterator(); i.hasNext();) {
            Command command = i.next().getValue();
            if(!(command instanceof PluginCommand)) continue;
            PluginCommand pluginCommand = (PluginCommand) command;
            Plugin owningPlugin = pluginCommand.getPlugin();
            if(selectedPlugin != owningPlugin) continue;
            if(pluginCommand.isRegistered()) {
                pluginCommand.unregister(fields.commandMap);
            }
            i.remove();
        }
    }

    /**
     * Get the {@link PluginDescriptionFile} of a <code>File</code> representing a plugin jar file.
     *
     * @param pluginFile The plugin File to get the description from
     * @param throwErrors Whether to throw errors or not
     * @return The generated <code>PluginDescriptionFile</code>, null if error occurred
     */
    @Override
    protected PluginDescriptionFile getPluginDescription(File pluginFile, boolean throwErrors) {
        PluginLoader loader = getPluginLoader(pluginFile);
        if (loader == null) return null;

        PluginDescriptionFile curDescription = null;
        try {
            curDescription = loader.getPluginDescription(pluginFile);
        }
        catch (InvalidDescriptionException ex) {
            if(!throwErrors) return null;
            plugin.getServer().getLogger().log(
                Level.SEVERE,
                String.format("Could not load '%s' in folder '%s'", pluginFile.getPath(), PLUGINS_DIRECTORY.getPath()),
                ex);
        }
        return curDescription;
    }

    /**
     * Get the {@link PluginLoader} of a <code>File</code>. This method uses the same code from
     * {@link SimplePluginManager} to obtain the correct loader from the internal list of file associations representing
     * the different types of plugins that can be loaded.
     *
     * @param pluginFile The plugin file to get the <code>PluginLoader</code> for
     * @return The located <code>PluginLoader</code>, null if not found
     */
    protected PluginLoader getPluginLoader(File pluginFile) {
        for (Pattern filter : fields.fileAssociations.keySet()) {
            Matcher match = filter.matcher(pluginFile.getName());
            if (match.find()) {
                return fields.fileAssociations.get(filter);
            }
        }
        return null;
    }
}
