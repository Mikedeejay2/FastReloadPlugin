package com.mikedeejay2.fastreload.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FastReloadConfig {
    private final FileConfiguration config;
    private final List<ConfigVal<?>> configValues = new ArrayList<>();
    private final List<LoadListener> loadListeners = new ArrayList<>();

    public final ConfigVal<Boolean> ONLY_PLUGINS        = new ConfigVal<>(c -> c.getBoolean("Only Plugins", true));
    public final ConfigVal<Boolean> AUTO_RELOAD_PLUGINS = new ConfigVal<>(c -> c.getBoolean("Auto Reload Plugins", true));
    public final ConfigVal<Integer> AUTO_RELOAD_TIME    = new ConfigVal<>(c -> c.getInt("Auto Reload Check Time", 20));
    public final ConfigVal<Boolean> IN_CHAT_RELOAD      = new ConfigVal<>(c -> c.getBoolean("In Chat Reload", true));
    public final ConfigVal<String> FILTER_MODE          = new ConfigVal<>(c -> c.getString("Reload Filter Mode", "Blacklist"));
    public final ConfigVal<List<String>> FILTER_LIST    = new ConfigVal<>(c -> c.getStringList("Filter List"));

    public FastReloadConfig(FileConfiguration config) {
        this.config = config;
    }

    public void loadConfig() {
        configValues.forEach(c -> c.load(config));
        loadListeners.forEach(this::doListen);
    }

    private void doListen(LoadListener listener) {
        listener.onConfigLoad(this);
    }

    public void registerListener(LoadListener listener) {
        loadListeners.add(listener);
    }

    public class ConfigVal<T> {
        private final Function<FileConfiguration, T> loader;
        private T value;

        public ConfigVal(Function<FileConfiguration, T> loader) {
            this.loader = loader;
            configValues.add(this);
        }

        private void load(FileConfiguration config) {
            value = loader.apply(config);
        }

        public T get() {
            return value;
        }
    }

    public interface LoadListener {
        void onConfigLoad(FastReloadConfig config);
    }
}
