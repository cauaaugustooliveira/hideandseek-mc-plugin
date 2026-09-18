package me.oredev.hideandseek.listener;

import me.oredev.hideandseek.game.GameManager;
import me.oredev.hideandseek.player.PlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class InfiniteFireworkListener implements Listener {
    private final PlayerManager players;
    private final GameManager games;

    public InfiniteFireworkListener(GameManager games, PlayerManager players) {
        this.games = games;
        this.players = players;
    }

    @EventHandler
    public void onUseFirework(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (!games.canUseFirework(event.getPlayer(), item)) return;
        if (players.launchInfiniteFirework(event.getPlayer(), item)) event.setCancelled(true);
    }
}
