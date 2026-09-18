package me.oredev.hideandseek.listener;

import me.oredev.hideandseek.game.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final GameManager games;

    public PlayerListener(GameManager games) {
        this.games = games;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (games.shouldFreeze(event.getPlayer()) && moved(event)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        games.disconnect(event.getPlayer());
    }

    private boolean moved(PlayerMoveEvent event) {
        return event.getTo() != null && (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getY() != event.getTo().getY() || event.getFrom().getZ() != event.getTo().getZ());
    }
}
