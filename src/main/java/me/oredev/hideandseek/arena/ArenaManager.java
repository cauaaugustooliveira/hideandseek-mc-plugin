package me.oredev.hideandseek.arena;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ArenaManager {
    private final File directory;
    private final ArenaLoader loader = new ArenaLoader();
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(JavaPlugin plugin) {
        directory = new File(plugin.getDataFolder(), "arenas");
        directory.mkdirs();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) for (File file : files) {
            Arena arena = loader.load(file);
            arenas.put(key(arena.name()), arena);
        }
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Optional<Arena> find(String name) {
        return Optional.ofNullable(arenas.get(key(name)));
    }

    public Arena create(String name, Location location) {
        Arena arena = new Arena(name, location.getWorld().getName(), location, location, location, location, null, null, 2, 10, 60, 600);
        arenas.put(key(name), arena);
        save(arena);
        return arena;
    }

    public void updateSpawn(String name, SpawnType type, Location location) {
        Arena old = find(name).orElseThrow();
        Arena updated = switch (type) {
            case JOIN_LOCATION ->
                    new Arena(old.name(), old.worldName(), location, old.hiderSpawn(), old.seekerWaitingSpawn(), old.seekerSpawn(), old.bounds(), old.floorIsLavaY(), old.minPlayers(), old.maxPlayers(), old.hideTime(), old.gameTime());
            case HIDER ->
                    new Arena(old.name(), old.worldName(), old.joinLocation(), location, old.seekerWaitingSpawn(), old.seekerSpawn(), old.bounds(), old.floorIsLavaY(), old.minPlayers(), old.maxPlayers(), old.hideTime(), old.gameTime());
            case SEEKER_WAITING ->
                    new Arena(old.name(), old.worldName(), old.joinLocation(), old.hiderSpawn(), location, old.seekerSpawn(), old.bounds(), old.floorIsLavaY(), old.minPlayers(), old.maxPlayers(), old.hideTime(), old.gameTime());
            case SEEKER ->
                    new Arena(old.name(), old.worldName(), old.joinLocation(), old.hiderSpawn(), old.seekerWaitingSpawn(), location, old.bounds(), old.floorIsLavaY(), old.minPlayers(), old.maxPlayers(), old.hideTime(), old.gameTime());
        };
        arenas.put(key(name), updated);
        save(updated);
    }

    public void updateBounds(String name, ArenaBounds bounds) {
        Arena old = find(name).orElseThrow();
        Arena updated = new Arena(old.name(), old.worldName(), old.joinLocation(), old.hiderSpawn(), old.seekerWaitingSpawn(), old.seekerSpawn(),
                bounds, old.floorIsLavaY(), old.minPlayers(), old.maxPlayers(), old.hideTime(), old.gameTime());
        arenas.put(key(name), updated);
        save(updated);
    }

    public boolean delete(String name) {
        Arena removed = arenas.remove(key(name));
        return removed != null && new File(directory, removed.name() + ".yml").delete();
    }

    public void save(Arena arena) {
        try {
            loader.save(new File(directory, arena.name() + ".yml"), arena);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save arena " + arena.name(), e);
        }
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public enum SpawnType {JOIN_LOCATION, HIDER, SEEKER_WAITING, SEEKER}
}
