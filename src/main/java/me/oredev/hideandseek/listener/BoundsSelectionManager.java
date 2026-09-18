package me.oredev.hideandseek.listener;

import me.oredev.hideandseek.arena.Arena;
import me.oredev.hideandseek.arena.ArenaBounds;
import me.oredev.hideandseek.arena.ArenaManager;
import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BoundsSelectionManager implements Listener {
    private final ArenaManager arenas;
    private final ConfigManager config;
    private final MessagesConfig messages;
    private final NamespacedKey wandKey;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public BoundsSelectionManager(JavaPlugin plugin, ArenaManager arenas, ConfigManager config, MessagesConfig messages) {
        this.arenas = arenas;
        this.config = config;
        this.messages = messages;
        this.wandKey = new NamespacedKey(plugin, "bounds_wand");
    }

    public void begin(Player player, Arena arena) {
        selections.put(player.getUniqueId(), new Selection(arena.name(), null));
        player.getInventory().addItem(createWand());
        player.sendMessage(messages.get("coins.bounds-selection-started", Map.of("arena", arena.name())));
    }

    @EventHandler
    public void onSelect(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem()) || event.getClickedBlock() == null) return;
        Selection selection = selections.get(event.getPlayer().getUniqueId());
        if (selection == null) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Location location = event.getClickedBlock().getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selections.put(event.getPlayer().getUniqueId(), new Selection(selection.arenaName(), location));
            event.getPlayer().sendMessage(messages.get("coins.bounds-first-position"));
            return;
        }
        if (selection.first() == null) {
            event.getPlayer().sendMessage(messages.get("coins.bounds-first-required"));
            return;
        }
        Arena arena = arenas.find(selection.arenaName()).orElse(null);
        if (arena == null || !arena.worldName().equals(location.getWorld().getName()) || !selection.first().getWorld().equals(location.getWorld())) {
            event.getPlayer().sendMessage(messages.get("coins.bounds-invalid-world"));
            return;
        }
        arenas.updateBounds(arena.name(), new ArenaBounds(selection.first(), location));
        selections.remove(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(messages.get("coins.bounds-saved", Map.of("arena", arena.name())));
    }

    private ItemStack createWand() {
        ConfigManager.MenuItem definition = config.boundsWand();
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(definition.name());
        meta.lore(definition.lore());
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private record Selection(String arenaName, Location first) {
    }
}
