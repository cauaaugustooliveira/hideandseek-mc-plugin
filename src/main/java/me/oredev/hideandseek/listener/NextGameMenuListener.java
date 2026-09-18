package me.oredev.hideandseek.listener;

import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.player.PlayerManager;
import me.oredev.hideandseek.ui.GameMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class NextGameMenuListener implements Listener {
    private final PlayerManager players;
    private final GameMenu menu;
    private final ConfigManager config;

    public NextGameMenuListener(PlayerManager players, GameMenu menu, ConfigManager config) {
        this.players = players;
        this.menu = menu;
        this.config = config;
    }

    @EventHandler
    public void onUseMenuItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (!players.isNextGameMenuItem(event.getItem()) && !players.isInitialMenuItem(event.getItem()))) return;
        event.setCancelled(true);
        menu.openMain(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        players.giveInitialMenuItem(event.getPlayer(), config.initialMenuItem());
    }
}
