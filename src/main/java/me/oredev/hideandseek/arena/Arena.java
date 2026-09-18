package me.oredev.hideandseek.arena;

import org.bukkit.Location;

public record Arena(String name, String worldName, Location joinLocation, Location hiderSpawn, Location seekerWaitingSpawn,
                    Location seekerSpawn, ArenaBounds bounds, Double floorIsLavaY,
                    int minPlayers, int maxPlayers, int hideTime, int gameTime) {
    public boolean isConfigured() {
        return joinLocation != null && hiderSpawn != null && seekerWaitingSpawn != null && seekerSpawn != null;
    }
}
