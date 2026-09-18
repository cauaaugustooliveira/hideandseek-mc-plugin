package me.oredev.hideandseek.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

public final class MessagesConfig {
    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessagesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        if (plugin.getResource("messages.yml") != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8));
            config.addDefaults(defaults);
            config.options().copyDefaults(true);
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not update missing defaults in messages.yml: " + e.getMessage());
            }
        }
    }

    public Component get(String key, Map<String, String> placeholders) {
        return miniMessage.deserialize(config.getString("prefix", "") + " " + format(key, placeholders));
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    public Component component(String key, Map<String, String> placeholders) {
        return miniMessage.deserialize(format(key, placeholders));
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component deserialize(String markup) {
        return miniMessage.deserialize(markup);
    }

    public String format(String key, Map<String, String> placeholders) {
        String message = config.getString(key, "<red>Mensagem ausente: " + key + "</red>");
        for (Map.Entry<String, String> entry : placeholders.entrySet())
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        return message;
    }

    public List<String> lines(String key) {
        return config.getStringList(key);
    }

    public List<Component> components(String key, Map<String, String> placeholders) {
        return lines(key).stream().map(line -> miniMessage.deserialize(replace(line, placeholders))).toList();
    }

    public List<Component> list(String key, Map<String, String> placeholders) {
        String prefix = config.getString("prefix", "");
        return lines(key).stream().map(line -> miniMessage.deserialize(prefix + " " + replace(line, placeholders))).toList();
    }

    private String replace(String message, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        return message;
    }
}
