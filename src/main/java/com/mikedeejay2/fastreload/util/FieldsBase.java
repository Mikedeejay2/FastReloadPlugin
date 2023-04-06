package com.mikedeejay2.fastreload.util;

import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class FieldsBase {
    protected List<Plugin> plugins;
    protected Map<String, Plugin> lookupNames;
    protected SimpleCommandMap commandMap;
    protected Map<String, Permission> permissions;
    protected Map<Boolean, Set<Permission>> defaultPerms;
    protected PluginManager pluginManager;
    protected Map<String, Command> knownCommands;

    public List<Plugin> plugins() {
        return plugins;
    }

    public Map<String, Plugin> lookupNames() {
        return lookupNames;
    }

    public SimpleCommandMap commandMap() {
        return commandMap;
    }

    public Map<String, Permission> permissions() {
        return permissions;
    }

    public Map<Boolean, Set<Permission>> defaultPerms() {
        return defaultPerms;
    }

    public PluginManager pluginManager() {
        return pluginManager;
    }

    public Map<String, Command> knownCommands() {
        return knownCommands;
    }
}
