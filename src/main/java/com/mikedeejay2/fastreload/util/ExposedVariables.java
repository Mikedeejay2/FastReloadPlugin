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

public class ExposedVariables
{
    public final Map<Pattern, PluginLoader> fileAssociations;
    public final List<Plugin> plugins;
    public final Map<String, Plugin> lookupNames;
    public final SimpleCommandMap commandMap;
    public final Map<String, Permission> permissions;
    public final Map<Boolean, Set<Permission>> defaultPerms;
    public final PluginManager pluginManager;
    public final Map<String, Command> knownCommands;

    public ExposedVariables(Server server)
    {
        Map<Pattern, PluginLoader> fileAssociations = null;
        List<Plugin> plugins = null;
        Map<String, Plugin> lookupNames = null;
        SimpleCommandMap commandMap = null;
        Map<String, Permission> permissions = null;
        Map<Boolean, Set<Permission>> defaultPerms = null;
        PluginManager pluginManager = null;
        Map<String, Command> knownCommands = null;

        try
        {
            pluginManager = server.getPluginManager();
            fileAssociations = ReflectUtil.getField("fileAssociations", pluginManager, SimplePluginManager.class, Map.class);
            plugins = ReflectUtil.getField("plugins", pluginManager, SimplePluginManager.class, List.class);
            lookupNames = ReflectUtil.getField("lookupNames", pluginManager, SimplePluginManager.class, Map.class);
            commandMap = ReflectUtil.getField("commandMap", pluginManager, SimplePluginManager.class, SimpleCommandMap.class);
            permissions = ReflectUtil.getField("permissions", pluginManager, SimplePluginManager.class, Map.class);
            defaultPerms = ReflectUtil.getField("defaultPerms", pluginManager, SimplePluginManager.class, Map.class);
            knownCommands = ReflectUtil.getField("knownCommands", commandMap, SimpleCommandMap.class, Map.class);
        }
        catch(IllegalAccessException | NoSuchFieldException e)
        {
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
