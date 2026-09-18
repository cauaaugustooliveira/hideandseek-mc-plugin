package me.oredev.hideandseek.config;

import me.oredev.hideandseek.event.GameEventDefinition;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        migrateNextGameMenu();
        migrateRoleUtilities();
        copyMissingDefaults();
    }

    public void reload() {
        plugin.reloadConfig();
        migrateNextGameMenu();
        migrateRoleUtilities();
        copyMissingDefaults();
    }

    private void copyMissingDefaults() {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public int countdown() {
        return plugin.getConfig().getInt("game.countdown", 10);
    }

    public int hidingTime() {
        return plugin.getConfig().getInt("game.hiding-time", 60);
    }

    public int gameTime() {
        return plugin.getConfig().getInt("game.game-time", 600);
    }

    public int endingTime() {
        return Math.max(0, plugin.getConfig().getInt("game.ending-time", 5));
    }

    public int summaryTopAmount() {
        return Math.clamp(plugin.getConfig().getInt("game.summary.top-amount", 3), 1, 10);
    }

    public EndingFireworksDefinition endingFireworks() {
        String path = "game.ending-fireworks";
        List<Color> colors = plugin.getConfig().getStringList(path + ".colors").stream()
                .map(this::color).filter(java.util.Objects::nonNull).toList();
        if (colors.isEmpty()) colors = List.of(Color.fromRGB(255, 215, 0), Color.RED, Color.fromRGB(85, 255, 255));
        return new EndingFireworksDefinition(
                plugin.getConfig().getBoolean(path + ".enabled", true),
                Math.max(2, plugin.getConfig().getInt(path + ".interval-ticks", 12)),
                Math.clamp(plugin.getConfig().getInt(path + ".amount", 2), 1, 10),
                Math.clamp(plugin.getConfig().getDouble(path + ".launch-velocity", 1.0), 0.1, 4.0),
                Math.clamp(plugin.getConfig().getInt(path + ".power", 1), 0, 4),
                plugin.getConfig().getBoolean(path + ".trail", true),
                plugin.getConfig().getBoolean(path + ".flicker", true),
                colors
        );
    }

    public boolean coinsEnabled() {
        return plugin.getConfig().getBoolean("coins.enabled", true);
    }

    public int coinSpawnInterval() {
        return Math.max(1, plugin.getConfig().getInt("coins.spawn-interval", 10));
    }

    public int maxActiveCoins() {
        return Math.max(1, plugin.getConfig().getInt("coins.max-active", 5));
    }

    public boolean scoreboardEnabled() {
        return plugin.getConfig().getBoolean("ui.scoreboard", true);
    }

    public boolean soundsEnabled() {
        return plugin.getConfig().getBoolean("sounds.enabled", true);
    }

    public boolean eventsEnabled() {
        return plugin.getConfig().getBoolean("events.enabled", true);
    }

    public boolean eventDebug() {
        return plugin.getConfig().getBoolean("debug.events", false);
    }

    public int eventStartDelay() {
        return Math.max(0, plugin.getConfig().getInt("events.start-delay", 60));
    }

    public int eventIntervalMinimum() {
        return Math.max(0, plugin.getConfig().getInt("events.interval.min", 90));
    }

    public int eventIntervalMaximum() {
        return Math.max(eventIntervalMinimum(), plugin.getConfig().getInt("events.interval.max", 180));
    }

    public GameEventDefinition eventDefinition(String id) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("events." + id);
        return new GameEventDefinition(id,
                section == null || section.getBoolean("enabled", true),
                section == null ? 0 : Math.max(0, section.getInt("weight", 0)),
                section == null ? plugin.getConfig().getInt("events.default-warning-time", 5)
                        : Math.max(0, section.getInt("warning-time", plugin.getConfig().getInt("events.default-warning-time", 5))),
                section == null ? 0 : Math.max(0, section.getInt("duration", 0)),
                section);
    }

    @SuppressWarnings("deprecation")
    public Sound eventSound(String messagePath) {
        String configured = plugin.getConfig().getString("events." + messagePath + "-sound", "none");
        return parseSound(configured, null);
    }

    @SuppressWarnings("deprecation")
    public Sound sound(String path, Sound fallback) {
        String configured = plugin.getConfig().getString(path);
        return parseSound(configured, fallback);
    }

    @SuppressWarnings("deprecation")
    private Sound parseSound(String configured, Sound fallback) {
        if (configured == null || configured.equalsIgnoreCase("none")) return null;
        try {
            return Sound.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public Location globalLobby() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("lobby");
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public void setGlobalLobby(Location location) {
        plugin.getConfig().set("lobby.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.x", location.getX());
        plugin.getConfig().set("lobby.y", location.getY());
        plugin.getConfig().set("lobby.z", location.getZ());
        plugin.getConfig().set("lobby.yaw", location.getYaw());
        plugin.getConfig().set("lobby.pitch", location.getPitch());
        plugin.saveConfig();
    }

    public int menuRows(String path) {
        return Math.clamp(plugin.getConfig().getInt(path + ".rows", 1), 1, 6);
    }

    public Component menuTitle(String path) {
        return miniMessage.deserialize(plugin.getConfig().getString(path + ".title", ""));
    }

    public MenuItem menuItem(String path, Map<String, String> placeholders) {
        String materialName = plugin.getConfig().getString(path + ".material", "BARRIER");
        Material material = "none".equalsIgnoreCase(materialName) ? Material.AIR : Material.matchMaterial(materialName);
        if (material == null) material = Material.BARRIER;
        String name = replace(plugin.getConfig().getString(path + ".name", ""), placeholders);
        List<Component> lore = plugin.getConfig().getStringList(path + ".lore").stream()
                .map(line -> withoutItalic(miniMessage.deserialize(replace(line, placeholders)))).toList();
        return new MenuItem(plugin.getConfig().getInt(path + ".slot", 0), material, withoutItalic(miniMessage.deserialize(name)), lore);
    }

    public List<Integer> menuSlots(String path) {
        return plugin.getConfig().getIntegerList(path + ".slots").stream()
                .filter(slot -> slot >= 0)
                .distinct()
                .toList();
    }

    public Component menuMessage(String path) {
        return miniMessage.deserialize(plugin.getConfig().getString(path, ""));
    }

    public MenuItem nextGameMenuItem() {
        return menuItem("next-game-menu-item", Map.of());
    }

    public MenuItem initialMenuItem() {
        return menuItem("initial-menu-item", Map.of());
    }

    public MenuItem coinItem() {
        return menuItem("coins.item", Map.of());
    }

    public MenuItem boundsWand() {
        return menuItem("coins.bounds-wand", Map.of());
    }

    public MenuItem utilityShopOpener(String role) {
        return menuItem("utilities." + role + ".opener", Map.of());
    }

    public MenuItem utilityShopFiller(String role) {
        return menuItem("utilities." + role + ".menu.filler", Map.of());
    }

    public MenuItem utilityShopCloseItem(String role) {
        return menuItem("utilities." + role + ".menu.close", Map.of());
    }

    public MenuItem speedUtilityItem() {
        return menuItem("utilities.hider.speed", Map.of(
                "price", String.valueOf(speedUtilityPrice()),
                "seconds", String.valueOf(speedUtilityDuration())
        ));
    }

    public int speedUtilityPrice() {
        return Math.max(0, plugin.getConfig().getInt("utilities.hider.speed.price", 3));
    }

    public int speedUtilityDuration() {
        return Math.max(1, plugin.getConfig().getInt("utilities.hider.speed.duration-seconds", 10));
    }

    public int speedUtilityAmplifier() {
        return Math.max(0, plugin.getConfig().getInt("utilities.hider.speed.amplifier", 1));
    }

    public int speedUtilityHotbarSlot() {
        return Math.clamp(plugin.getConfig().getInt("utilities.hider.speed.hotbar-slot", 1), 0, 8);
    }

    public MenuItem emptyUtilityItem(String role) {
        return menuItem("utilities." + role + ".empty", Map.of());
    }

    public MenuItem fishingRodUtilityItem() {
        return menuItem("utilities.seeker.fishing-rod", Map.of("price", String.valueOf(fishingRodUtilityPrice())));
    }

    public int fishingRodUtilityPrice() {
        return Math.max(0, plugin.getConfig().getInt("utilities.seeker.fishing-rod.price", 3));
    }

    public int fishingRodUtilityHotbarSlot() {
        return Math.clamp(plugin.getConfig().getInt("utilities.seeker.fishing-rod.hotbar-slot", 1), 0, 8);
    }

    public double fishingRodPullStrength() {
        return Math.clamp(plugin.getConfig().getDouble("utilities.seeker.fishing-rod.pull-strength", 1.25), 0.1, 4.0);
    }

    public boolean fishingRodUnbreakable() {
        return plugin.getConfig().getBoolean("utilities.seeker.fishing-rod.unbreakable", true);
    }

    public MenuItem bellUtilityItem() {
        return menuItem("utilities.seeker.bell", Map.of("price", String.valueOf(bellUtilityPrice()),
                "seconds", String.valueOf(bellUtilityDuration())));
    }

    public int bellUtilityPrice() {
        return Math.max(0, plugin.getConfig().getInt("utilities.seeker.bell.price", 5));
    }

    public int bellUtilityDuration() {
        return Math.max(1, plugin.getConfig().getInt("utilities.seeker.bell.duration-seconds", 2));
    }

    public int bellUtilityHotbarSlot() {
        return Math.clamp(plugin.getConfig().getInt("utilities.seeker.bell.hotbar-slot", 2), 0, 8);
    }

    public Sound bellUtilitySound() {
        return sound("utilities.seeker.bell.sound", Sound.BLOCK_BELL_USE);
    }

    public MenuItem kangarooUtilityItem(String role) {
        return menuItem("utilities." + role + ".kangaroo", Map.of("price", String.valueOf(kangarooUtilityPrice(role))));
    }

    public int kangarooUtilityPrice(String role) {
        return Math.max(0, plugin.getConfig().getInt("utilities." + role + ".kangaroo.price", 5));
    }

    public int kangarooUtilityHotbarSlot(String role) {
        return Math.clamp(plugin.getConfig().getInt("utilities." + role + ".kangaroo.hotbar-slot", 2), 0, 8);
    }

    public double kangarooVerticalVelocity(String role) {
        return Math.clamp(plugin.getConfig().getDouble("utilities." + role + ".kangaroo.vertical-velocity", 1.25), 0.1, 5.0);
    }

    public double kangarooForwardVelocity(String role) {
        return Math.clamp(plugin.getConfig().getDouble("utilities." + role + ".kangaroo.forward-velocity", 1.7), 0.1, 5.0);
    }

    public double kangarooForwardYVelocity(String role) {
        return Math.clamp(plugin.getConfig().getDouble("utilities." + role + ".kangaroo.forward-y-velocity", 0.55), 0.0, 5.0);
    }

    public Sound kangarooUtilitySound(String role) {
        return sound("utilities." + role + ".kangaroo.sound", Sound.ENTITY_FIREWORK_ROCKET_LAUNCH);
    }

    public FireworkDefinition roleFirework(String role) {
        String path = "fireworks." + role;
        Color color = color(plugin.getConfig().getString(path + ".color"));
        return new FireworkDefinition(menuItem(path, Map.of()), color == null ? Color.WHITE : color,
                Math.clamp(plugin.getConfig().getInt(path + ".power", 1), 0, 127));
    }

    private void migrateNextGameMenu() {
        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("lobby-menu-item");
        if (legacy == null || plugin.getConfig().isConfigurationSection("next-game-menu-item")) return;
        plugin.getConfig().set("next-game-menu-item", legacy.getValues(false));
        plugin.getConfig().set("lobby-menu-item", null);
        plugin.saveConfig();
    }

    private void migrateRoleUtilities() {
        ConfigurationSection utilities = plugin.getConfig().getConfigurationSection("utilities");
        if (utilities == null || utilities.isConfigurationSection("hider") || !utilities.isConfigurationSection("menu")) return;
        for (String path : List.of("menu", "opener", "speed")) {
            ConfigurationSection section = utilities.getConfigurationSection(path);
            if (section == null) continue;
            plugin.getConfig().set("utilities.hider." + path, section.getValues(false));
            plugin.getConfig().set("utilities." + path, null);
        }
        plugin.saveConfig();
    }

    private String replace(String value, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        return value;
    }

    private Component withoutItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack loadoutItem(String role, String item) {
        String path = "loadouts." + role + "." + item;
        Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "AIR"));
        if (material == null || material.isAir()) return null;

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        ConfigurationSection enchantments = plugin.getConfig().getConfigurationSection(path + ".enchantments");
        if (enchantments != null) for (String key : enchantments.getKeys(false)) {
            Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (enchantment != null) meta.addEnchant(enchantment, enchantments.getInt(key), true);
        }
        if (meta instanceof LeatherArmorMeta leather) {
            Color color = color(plugin.getConfig().getString(path + ".color"));
            if (color != null) leather.setColor(color);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private Color color(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) return null;
        return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
    }

    public int seekersFor(int players) {
        NavigableMap<Integer, Integer> scaling = new TreeMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("seekers.scaling");
        if (section != null) for (String key : section.getKeys(false)) {
            try {
                scaling.put(Integer.parseInt(key), section.getInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(1, Math.min(players - 1, scaling.floorEntry(players) == null ? 1 : scaling.floorEntry(players).getValue()));
    }

    public record MenuItem(int slot, Material material, Component name, List<Component> lore) { }

    public record FireworkDefinition(MenuItem item, Color color, int power) { }

    public record EndingFireworksDefinition(boolean enabled, int intervalTicks, int amount, double launchVelocity,
                                            int power, boolean trail, boolean flicker, List<Color> colors) { }
}
