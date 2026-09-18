package me.oredev.hideandseek.player;

import me.oredev.hideandseek.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.FireworkEffect;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

public final class PlayerManager {
    private final JavaPlugin plugin;
    private final NamespacedKey playMenuKey;
    private final NamespacedKey initialMenuKey;
    private final NamespacedKey utilityShopKey;
    private final NamespacedKey speedUtilityKey;
    private final NamespacedKey infiniteFireworkKey;
    private final NamespacedKey fishingRodUtilityKey;
    private final NamespacedKey bellUtilityKey;
    private final NamespacedKey kangarooUtilityKey;

    public PlayerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playMenuKey = new NamespacedKey(plugin, "next_game_menu_item");
        this.initialMenuKey = new NamespacedKey(plugin, "initial_game_menu_item");
        this.utilityShopKey = new NamespacedKey(plugin, "utility_shop_item");
        this.speedUtilityKey = new NamespacedKey(plugin, "speed_utility_item");
        this.infiniteFireworkKey = new NamespacedKey(plugin, "infinite_firework_item");
        this.fishingRodUtilityKey = new NamespacedKey(plugin, "fishing_rod_utility_item");
        this.bellUtilityKey = new NamespacedKey(plugin, "bell_utility_item");
        this.kangarooUtilityKey = new NamespacedKey(plugin, "kangaroo_utility_item");
    }

    public GamePlayer capture(Player player) {
        return new GamePlayer(player.getUniqueId(), new GamePlayer.PlayerSnapshot(player.getLocation().clone(), player.getGameMode(),
                player.getInventory().getContents().clone(), player.getHealth(), player.getFoodLevel(), player.getSaturation(),
                player.getLevel(), player.getExp(), player.getActivePotionEffects(), player.isInvulnerable(),
                player.getAllowFlight(), player.isFlying()));
    }

    public void prepare(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.setHealth(maxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setFireTicks(0);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGameMode(GameMode.ADVENTURE);
        for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
    }

    public void restore(Player player, GamePlayer gamePlayer, Location returnLocation) {
        GamePlayer.PlayerSnapshot state = gamePlayer.snapshot();
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setContents(state.inventory());
        for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        for (var effect : state.effects()) player.addPotionEffect(effect);
        restoreState(player, state);
        player.teleport(returnLocation != null ? returnLocation : state.location());
    }

    public void prepareEnding(Player player, ConfigManager.MenuItem menuItem) {
        player.closeInventory();
        player.getInventory().clear();
        player.setInvulnerable(true);
        player.setAllowFlight(true);
        player.setFlying(true);
        giveNextGameMenuItem(player, menuItem);
    }

    public void restoreAfterEnding(Player player, GamePlayer gamePlayer, Location returnLocation) {
        GamePlayer.PlayerSnapshot state = gamePlayer.snapshot();
        player.closeInventory();
        player.getInventory().clear();
        for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        for (var effect : state.effects()) player.addPotionEffect(effect);
        restoreState(player, state);
        player.teleport(returnLocation != null ? returnLocation : state.location());
    }

    public boolean isNextGameMenuItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(playMenuKey, PersistentDataType.BYTE);
    }

    public void giveInitialMenuItem(Player player, ConfigManager.MenuItem definition) {
        if (definition.material().isAir()) return;
        player.getInventory().setItem(Math.clamp(definition.slot(), 0, 8), configuredItem(definition, initialMenuKey));
    }

    public boolean isInitialMenuItem(ItemStack item) {
        return hasMarker(item, initialMenuKey);
    }

    public void giveUtilityShopOpener(Player player, ConfigManager.MenuItem definition) {
        if (definition.material().isAir()) return;
        player.getInventory().setItem(Math.clamp(definition.slot(), 0, 8), configuredItem(definition, utilityShopKey));
    }

    public boolean isUtilityShopOpener(ItemStack item) {
        return hasMarker(item, utilityShopKey);
    }

    public ItemStack speedUtilityItem(ConfigManager.MenuItem definition) {
        return configuredItem(definition, speedUtilityKey);
    }

    public boolean isSpeedUtilityItem(ItemStack item) {
        return hasMarker(item, speedUtilityKey);
    }

    public ItemStack fishingRodUtilityItem(ConfigManager.MenuItem definition, boolean unbreakable) {
        ItemStack item = configuredItem(definition, fishingRodUtilityKey);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(unbreakable);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isFishingRodUtilityItem(ItemStack item) {
        return hasMarker(item, fishingRodUtilityKey);
    }

    public ItemStack bellUtilityItem(ConfigManager.MenuItem definition) {
        return configuredItem(definition, bellUtilityKey);
    }

    public boolean isBellUtilityItem(ItemStack item) {
        return hasMarker(item, bellUtilityKey);
    }

    public ItemStack kangarooUtilityItem(ConfigManager.MenuItem definition) {
        ItemStack item = configuredItem(definition, kangarooUtilityKey);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isKangarooUtilityItem(ItemStack item) {
        return hasMarker(item, kangarooUtilityKey);
    }

    public void giveRoleFirework(Player player, ConfigManager.FireworkDefinition definition) {
        if (definition.item().material().isAir()) return;
        ItemStack item = configuredItem(definition.item(), infiniteFireworkKey);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof FireworkMeta firework) {
            firework.clearEffects();
            firework.addEffect(FireworkEffect.builder().withColor(definition.color()).build());
            firework.setPower(definition.power());
            item.setItemMeta(firework);
        }
        player.getInventory().setItem(Math.clamp(definition.item().slot(), 0, 8), item);
    }

    public boolean isInfiniteFirework(ItemStack item) {
        return hasMarker(item, infiniteFireworkKey);
    }

    public boolean launchInfiniteFirework(Player player, ItemStack item) {
        if (!(item.getItemMeta() instanceof FireworkMeta meta)) return false;
        Location launchLocation = player.getEyeLocation().clone().add(player.getEyeLocation().getDirection().normalize().multiply(0.5));
        Firework firework = player.getWorld().spawn(launchLocation, Firework.class);
        firework.setFireworkMeta(meta);
        firework.setShotAtAngle(true);
        firework.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.2));
        return true;
    }

    private void giveNextGameMenuItem(Player player, ConfigManager.MenuItem definition) {
        if (definition.material().isAir()) return;
        player.getInventory().setItem(Math.clamp(definition.slot(), 0, 8), configuredItem(definition, playMenuKey));
    }

    private ItemStack configuredItem(ConfigManager.MenuItem definition, NamespacedKey marker) {
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(definition.name());
        meta.lore(definition.lore());
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasMarker(ItemStack item, NamespacedKey marker) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    private void restoreState(Player player, GamePlayer.PlayerSnapshot state) {
        player.setGameMode(state.gameMode());
        player.setHealth(Math.min(state.health(), maxHealth(player)));
        player.setFoodLevel(state.food());
        player.setSaturation(state.saturation());
        player.setLevel(state.level());
        player.setExp(state.exp());
        player.setInvulnerable(state.invulnerable());
        player.setAllowFlight(state.allowFlight());
        player.setFlying(state.allowFlight() && state.flying());
    }

    private double maxHealth(Player player) {
        return player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }
}
