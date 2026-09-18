package me.oredev.hideandseek.listener;

import me.oredev.hideandseek.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public final class ProtectionListener implements Listener {
    private final GameManager games;

    public ProtectionListener(GameManager games) {
        this.games = games;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (games.isPlaying(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (games.isPlaying(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (games.isPlaying(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (games.handleCoinPickup(player, event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (games.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && games.isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (games.canUseFirework(event.getPlayer(), event.getItem())) return;
        if (games.canUseFishingRod(event.getPlayer(), event.getItem())) return;
        if (games.canUseKangaroo(event.getPlayer(), event.getItem())) return;
        if (games.isPlaying(event.getPlayer())) event.setCancelled(true);
    }
}
