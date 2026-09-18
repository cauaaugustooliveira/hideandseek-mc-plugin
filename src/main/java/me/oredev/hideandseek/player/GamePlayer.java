package me.oredev.hideandseek.player;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.UUID;

public final class GamePlayer {
    private final UUID uniqueId;
    private final PlayerSnapshot snapshot;
    private PlayerRole role = PlayerRole.HIDER;
    private boolean alive = true;
    private int founds;
    private int hiderSeconds;
    private boolean hasBeenHider;
    private boolean hasBeenSeeker;

    public GamePlayer(UUID uniqueId, PlayerSnapshot snapshot) {
        this.uniqueId = uniqueId;
        this.snapshot = snapshot;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public PlayerSnapshot snapshot() {
        return snapshot;
    }

    public PlayerRole role() {
        return role;
    }

    public void role(PlayerRole role) {
        this.role = role;
        trackRole(role);
    }

    public boolean alive() {
        return alive;
    }

    public void eliminate() {
        alive = false;
        role = PlayerRole.SPECTATOR;
    }

    public void revive(PlayerRole role) {
        alive = true;
        this.role = role;
        trackRole(role);
    }

    public int founds() {
        return founds;
    }

    public void addFound() {
        founds++;
    }

    public void addHiderSecond() {
        hiderSeconds++;
    }

    public int hiderSeconds() {
        return hiderSeconds;
    }

    public boolean hasBeenHider() {
        return hasBeenHider;
    }

    public boolean hasBeenSeeker() {
        return hasBeenSeeker;
    }

    private void trackRole(PlayerRole role) {
        if (role == PlayerRole.HIDER) hasBeenHider = true;
        if (role == PlayerRole.SEEKER) hasBeenSeeker = true;
    }

    public record PlayerSnapshot(Location location, GameMode gameMode, ItemStack[] inventory, double health, int food,
                                 float saturation, int level, float exp, Collection<PotionEffect> effects,
                                 boolean invulnerable, boolean allowFlight, boolean flying) {
    }
}
