package me.oredev.hideandseek.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class ArenaLoader {
    public Arena load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String name = yaml.getString("name", file.getName().replace(".yml", ""));
        String worldName = yaml.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        Location legacyLobby = location(yaml.getConfigurationSection("lobby"), world);
        Location joinLocation = location(yaml.getConfigurationSection("join_location"), world);
        Location seekerSpawn = location(yaml.getConfigurationSection("seeker_spawn"), world);
        Location seekerWaitingSpawn = location(yaml.getConfigurationSection("seeker_waiting_spawn"), world);
        ArenaBounds bounds = new ArenaBounds(location(yaml.getConfigurationSection("bounds.first"), world), location(yaml.getConfigurationSection("bounds.second"), world));
        // Existing arena files used their per-arena lobby as the entry location.
        if (joinLocation == null) joinLocation = legacyLobby;
        if (seekerWaitingSpawn == null) seekerWaitingSpawn = seekerSpawn;
        return new Arena(name, worldName, joinLocation,
                location(yaml.getConfigurationSection("hider_spawn"), world), seekerWaitingSpawn, seekerSpawn, bounds.isValid() ? bounds : null,
                yaml.isSet("events.floor-is-lava.lava-y") ? yaml.getDouble("events.floor-is-lava.lava-y") : null,
                yaml.getInt("min_players", 2), yaml.getInt("max_players", 10), yaml.getInt("hide_time", 60), yaml.getInt("game_time", 600));
    }

    public void save(File file, Arena arena) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", arena.name());
        yaml.set("world", arena.worldName());
        write(yaml, "join_location", arena.joinLocation());
        write(yaml, "hider_spawn", arena.hiderSpawn());
        write(yaml, "seeker_waiting_spawn", arena.seekerWaitingSpawn());
        write(yaml, "seeker_spawn", arena.seekerSpawn());
        if (arena.bounds() != null) {
            write(yaml, "bounds.first", arena.bounds().first());
            write(yaml, "bounds.second", arena.bounds().second());
        }
        if (arena.floorIsLavaY() != null) yaml.set("events.floor-is-lava.lava-y", arena.floorIsLavaY());
        yaml.set("min_players", arena.minPlayers());
        yaml.set("max_players", arena.maxPlayers());
        yaml.set("hide_time", arena.hideTime());
        yaml.set("game_time", arena.gameTime());
        yaml.save(file);
    }

    private Location location(ConfigurationSection section, World world) {
        if (section == null || world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private void write(YamlConfiguration yaml, String path, Location location) {
        if (location == null) return;
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }
}
