package me.oredev.hideandseek.ui;

import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.game.GameSession;
import me.oredev.hideandseek.player.PlayerRole;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Small Paper API adapter for this game's fixed HUD; it is not a generic scoreboard framework.
 */
public final class GameUi {
    private static final String[] ENTRIES = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e"};
    private final ConfigManager config;
    private final MessagesConfig messages;
    private final Map<UUID, View> views = new HashMap<>();

    public GameUi(ConfigManager config, MessagesConfig messages) {
        this.config = config;
        this.messages = messages;
    }

    public void show(Player player) {
        View view = new View();
        views.put(player.getUniqueId(), view);
        if (config.scoreboardEnabled()) {
            createScoreboard(player, view, "scoreboards.waiting", messages.lines("scoreboards.waiting.lines"));
        }
    }

    public void update(Player player, GameSession session) {
        View view = views.get(player.getUniqueId());
        if (view == null) return;
        String key = "scoreboards." + session.state().name().toLowerCase();
        List<String> templates = messages.lines(key + ".lines");
        if (templates.size() > ENTRIES.length) templates = templates.subList(0, ENTRIES.length);
        if (!key.equals(view.stateKey) || view.teams.length != templates.size())
            createScoreboard(player, view, key, templates);
        String time = session.formattedTime();
        Map<String, String> values = Map.of("arena", session.arena().name(), "state", session.stateLabel(), "hiders", String.valueOf(session.livingHiders()), "seekers", String.valueOf(session.seekers()), "time", time, "players", String.valueOf(session.players()), "min_players", String.valueOf(session.arena().minPlayers()), "max_players", String.valueOf(session.arena().maxPlayers()), "coins", String.valueOf(session.coins(player.getUniqueId())));
        if (view.teams != null) for (int i = 0; i < templates.size(); i++) {
            String line = replace(templates.get(i), values);
            if (!line.equals(view.lines[i])) {
                view.teams[i].prefix(messages.deserialize(line));
                view.lines[i] = line;
            }
        }
        String title = replace(messages.format(key + ".title", values), values);
        if (!title.equals(view.title)) {
            view.objective.displayName(messages.deserialize(title));
            view.title = title;
        }
        updateNametags(player, session, view);
    }

    public void remove(Player player) {
        views.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void createScoreboard(Player player, View view, String key, List<String> configuredLines) {
        List<String> lines = configuredLines.size() > ENTRIES.length ? configuredLines.subList(0, ENTRIES.length) : configuredLines;
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("has_" + player.getEntityId(), Criteria.DUMMY, messages.component(key + ".title"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Team[] teams = new Team[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            teams[i] = board.registerNewTeam("l" + i);
            teams[i].addEntry(ENTRIES[i]);
            objective.getScore(ENTRIES[i]).setScore(lines.size() - i);
        }
        view.board = board;
        view.objective = objective;
        view.teams = teams;
        view.lines = new String[lines.size()];
        view.stateKey = key;
        view.title = "";
        view.nametagSignature = "";
        player.setScoreboard(board);
    }

    private void updateNametags(Player viewer, GameSession session, View view) {
        Map<UUID, PlayerRole> roles = session.roles();
        PlayerRole viewerRole = roles.get(viewer.getUniqueId());
        String signature = viewerRole + ":" + roles;

        Team hiddenHiders = team(view.board, "hs_hidden");
        Team redSeekers = team(view.board, "hs_seekers");
        clear(hiddenHiders);
        clear(redSeekers);
        hiddenHiders.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        redSeekers.color(NamedTextColor.RED);

        roles.forEach((playerId, role) -> {
            Player subject = Bukkit.getPlayer(playerId);
            if (subject == null) return;
            if (viewerRole == PlayerRole.SEEKER && role == PlayerRole.HIDER) hiddenHiders.addEntry(subject.getName());
            if (viewerRole == PlayerRole.HIDER && role == PlayerRole.SEEKER) redSeekers.addEntry(subject.getName());
        });
        view.nametagSignature = signature;
    }

    private Team team(Scoreboard board, String name) {
        Team existing = board.getTeam(name);
        return existing == null ? board.registerNewTeam(name) : existing;
    }

    private void clear(Team team) {
        for (String entry : new HashSet<>(team.getEntries())) team.removeEntry(entry);
    }

    private String replace(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> value : values.entrySet())
            result = result.replace("{" + value.getKey() + "}", value.getValue());
        return result;
    }

    private static final class View {
        Scoreboard board;
        Objective objective;
        Team[] teams = new Team[0];
        String[] lines = new String[0];
        String stateKey = "";
        String title = "";
        String nametagSignature = "";
    }
}
