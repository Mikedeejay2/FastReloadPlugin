package com.mikedeejay2.fastreload.util;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public final class PaperFields extends FieldsBase {

    public PaperFields(Server server) {
        List<Plugin> plugins = null;
        Map<String, Plugin> lookupNames = null;
        SimpleCommandMap commandMap = null;
        Map<String, Permission> permissions = null;
        Map<Boolean, Set<Permission>> defaultPerms = null;
        PluginManager pluginManager = null;
        Map<String, Command> knownCommands = null;

        try {
            final Class<?> classPaperPluginManagerImpl = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            final Class<?> classPaperPluginInstanceManager = Class.forName("io.papermc.paper.plugin.manager.PaperPluginInstanceManager");
            final Class<?> classPaperPermissionManager = Class.forName("io.papermc.paper.plugin.manager.PaperPermissionManager");

            final PluginManager bukkitManager = server.getPluginManager();
            pluginManager = ReflectUtil.getField("paperPluginManager", bukkitManager, SimplePluginManager.class, PluginManager.class);
            final Object paperPluginInstanceManager = ReflectUtil.getField("instanceManager", pluginManager, classPaperPluginManagerImpl);
            final Object permissionManager = ReflectUtil.getField("permissionManager", pluginManager, classPaperPluginManagerImpl);

            plugins = ReflectUtil.getField("plugins", paperPluginInstanceManager, classPaperPluginInstanceManager, List.class);
            lookupNames = ReflectUtil.getField("lookupNames", paperPluginInstanceManager, classPaperPluginInstanceManager, Map.class);
            commandMap = ReflectUtil.getField("commandMap", paperPluginInstanceManager, classPaperPluginInstanceManager, SimpleCommandMap.class);
            permissions = ReflectUtil.invokeMethod("permissions", permissionManager, classPaperPermissionManager, Map.class, new Class[0], new Object[0]);
            defaultPerms = ReflectUtil.invokeMethod("defaultPerms", permissionManager, classPaperPermissionManager, Map.class, new Class[0], new Object[0]);
            knownCommands = ReflectUtil.getField("knownCommands", commandMap, SimpleCommandMap.class, Map.class);
        } catch(IllegalAccessException | NoSuchFieldException | ClassNotFoundException | NoSuchMethodException |
                InvocationTargetException e) {
            e.printStackTrace();
        }
        this.plugins = plugins;
        this.lookupNames = lookupNames;
        this.commandMap = commandMap;
        this.permissions = permissions;
        this.defaultPerms = defaultPerms;
        this.pluginManager = pluginManager;
        this.knownCommands = knownCommands;
    }
}
