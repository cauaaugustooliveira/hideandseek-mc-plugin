package me.oredev.hideandseek.listener;

import me.oredev.hideandseek.game.GameManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class DamageListener implements Listener {
    private final GameManager games;

    public DamageListener(GameManager games) {
        this.games = games;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!games.isPlaying(player)) {
            if (event instanceof EntityDamageByEntityEvent damage && isPlayerAttack(damage)) event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (!games.canTakeFallDamage(player)) {
                event.setCancelled(true);
                return;
            }
            if (event.getFinalDamage() >= player.getHealth()) {
                event.setCancelled(true);
                games.handleEnvironmentalDeath(player);
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 0.5f);
            }
            return;
        }
        // Player melee damage is classified below. Every other damage source remains blocked in the arena.
        if (event instanceof EntityDamageByEntityEvent damage && damage.getDamager() instanceof Player) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;
        if (!games.isPlaying(attacker) && !games.isPlaying(victim)) return;
        if (games.canCounterAttack(attacker, victim)) {
            if (event.getFinalDamage() >= victim.getHealth()) {
                event.setCancelled(true);
                games.handleSeekerDeath(victim);
                victim.playSound(victim.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 0.5f);
            }
            return;
        }
        if (!games.canHunt(attacker, victim)) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() >= victim.getHealth()) {
            event.setCancelled(true);
            games.handleFinalHit(attacker, victim);
            victim.playSound(victim.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 0.5f);
        }
    }

    private boolean isPlayerAttack(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Player
                || event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }
}
