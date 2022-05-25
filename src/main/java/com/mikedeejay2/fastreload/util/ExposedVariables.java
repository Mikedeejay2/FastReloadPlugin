package com.mikedeejay2.fastreload.util;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Container class holding all private/protected variables from CraftBukkit
 * that need to be accessed for Fast Reload. These variables are accessed via
 * instance and therefore modifying them modifies the variables in CraftBukkit
 * too.
 * <p>
 * This class should only be constructed once upon a startup / reload as
 * there isn't any reason to do so any other time because it would waste performance
 * and CraftBukkit variable instances don't change throughout runtime.
 * <p>
 * This class simply acts as a law breaker to Java's protective rules so that
 * Fast Reload can actually inject itself into the code without the use of a Spigot
 * patch or similar. While it's not the most efficient or good practice
 * implementation it works just fine for what this plugin is aiming for.
 *
 * @author Mikedeejay2
 */
public final class ExposedVariables {
    public final Map<Pattern, PluginLoader> fileAssociations;
    public final List<Plugin> plugins;
    public final Map<String, Plugin> lookupNames;
    public final SimpleCommandMap commandMap;
    public final Map<String, Permission> permissions;
    public final Map<Boolean, Set<Permission>> defaultPerms;
    public final PluginManager pluginManager;
    public final Map<String, Command> knownCommands;

    public ExposedVariables(Server server) {
        Map<Pattern, PluginLoader> fileAssociations = null;
        List<Plugin> plugins = null;
        Map<String, Plugin>lookupNames = null;
        SimpleCommandMap commandMap = null;
        Map<String, Permission> permissions = null;
        Map<Boolean, Set<Permission>> defaultPerms = null;
        PluginManager pluginManager = null;
        Map<String, Command> knownCommands = null;

        try {
            pluginManager = server.getPluginManager();
            fileAssociations = ReflectUtil.getField("fileAssociations", pluginManager, SimplePluginManager.class, Map.class);
            plugins = ReflectUtil.getField("plugins", pluginManager, SimplePluginManager.class, List.class);
            lookupNames = ReflectUtil.getField("lookupNames", pluginManager, SimplePluginManager.class, Map.class);
            commandMap = ReflectUtil.getField("commandMap", pluginManager, SimplePluginManager.class, SimpleCommandMap.class);
            permissions = ReflectUtil.getField("permissions", pluginManager, SimplePluginManager.class, Map.class);
            defaultPerms = ReflectUtil.getField("defaultPerms", pluginManager, SimplePluginManager.class, Map.class);
            knownCommands = ReflectUtil.getField("knownCommands", commandMap, SimpleCommandMap.class, Map.class);
        } catch(IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        this.fileAssociations = fileAssociations;
        this.plugins = plugins;
        this.lookupNames = lookupNames;
        this.commandMap = commandMap;
        this.permissions = permissions;
        this.defaultPerms = defaultPerms;
        this.pluginManager = pluginManager;
        this.knownCommands = knownCommands;
    }
}
