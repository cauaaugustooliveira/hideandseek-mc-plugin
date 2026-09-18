package me.oredev.hideandseek.game;

import me.oredev.hideandseek.arena.ArenaBounds;
import me.oredev.hideandseek.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class CoinService {
    private static final int SPAWN_ATTEMPTS = 40;
    private final ConfigManager config;
    private final NamespacedKey coinKey;

    public CoinService(JavaPlugin plugin, ConfigManager config) {
        this.config = config;
        this.coinKey = new NamespacedKey(plugin, "game_coin");
    }

    public boolean enabled() {
        return config.coinsEnabled();
    }

    public Item spawn(ArenaBounds bounds) {
        if (bounds == null || !bounds.isValid()) return null;
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int x = random(bounds.minX(), bounds.maxX());
            int y = random(bounds.minY(), bounds.maxY());
            int z = random(bounds.minZ(), bounds.maxZ());
            Block feet = bounds.world().getBlockAt(x, y, z);
            Block floor = bounds.world().getBlockAt(x, y - 1, z);
            if (!feet.isPassable() || !floor.getType().isSolid() || floor.isLiquid()) continue;
            Item item = bounds.world().dropItem(new Location(bounds.world(), x + 0.5, y + 0.15, z + 0.5), createItem());
            item.setPickupDelay(0);
            return item;
        }
        return null;
    }

    public boolean isCoin(Item item) {
        ItemStack stack = item.getItemStack();
        return stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(coinKey, PersistentDataType.BYTE);
    }

    private ItemStack createItem() {
        ConfigManager.MenuItem definition = config.coinItem();
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(definition.name());
        meta.lore(definition.lore());
        meta.getPersistentDataContainer().set(coinKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private int random(int minimum, int maximum) {
        return ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }
}
