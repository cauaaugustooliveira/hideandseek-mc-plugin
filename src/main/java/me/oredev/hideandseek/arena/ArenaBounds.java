package me.oredev.hideandseek.arena;

import org.bukkit.Location;
import org.bukkit.World;

public record ArenaBounds(Location first, Location second) {
    public boolean isValid() {
        return first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    public World world() {
        return first.getWorld();
    }

    public int minX() {
        return Math.min(first.getBlockX(), second.getBlockX());
    }

    public int maxX() {
        return Math.max(first.getBlockX(), second.getBlockX());
    }

    public int minY() {
        return Math.min(first.getBlockY(), second.getBlockY());
    }

    public int maxY() {
        return Math.max(first.getBlockY(), second.getBlockY());
    }

    public int minZ() {
        return Math.min(first.getBlockZ(), second.getBlockZ());
    }

    public int maxZ() {
        return Math.max(first.getBlockZ(), second.getBlockZ());
    }
}
