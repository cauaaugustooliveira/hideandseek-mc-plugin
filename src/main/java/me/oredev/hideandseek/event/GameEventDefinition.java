package me.oredev.hideandseek.event;

import org.bukkit.configuration.ConfigurationSection;

public record GameEventDefinition(String id, boolean enabled, int weight, int warningSeconds, int durationSeconds,
                                  ConfigurationSection settings) {
    public int integer(String path, int fallback) {
        return settings == null ? fallback : settings.getInt(path, fallback);
    }

    public double decimal(String path, double fallback) {
        return settings == null ? fallback : settings.getDouble(path, fallback);
    }

    public boolean bool(String path, boolean fallback) {
        return settings == null ? fallback : settings.getBoolean(path, fallback);
    }
}
