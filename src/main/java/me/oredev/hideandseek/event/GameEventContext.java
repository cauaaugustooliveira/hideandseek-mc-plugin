package me.oredev.hideandseek.event;

import me.oredev.hideandseek.arena.Arena;
import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.game.GameSession;
import me.oredev.hideandseek.player.PlayerRole;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public record GameEventContext(JavaPlugin plugin, GameSession session, ConfigManager config, MessagesConfig messages,
                               GameEventDefinition definition) {
    public Arena arena() {
        return session.arena();
    }

    public List<Player> participants() {
        return session.onlinePlayers();
    }

    public List<Player> livingPlayers() {
        return session.livingPlayers();
    }

    public List<Player> hiders() {
        return session.playersWithRole(PlayerRole.HIDER);
    }

    public List<Player> seekers() {
        return session.playersWithRole(PlayerRole.SEEKER);
    }

    public List<Player> spectators() {
        return session.playersWithRole(PlayerRole.SPECTATOR);
    }
}
