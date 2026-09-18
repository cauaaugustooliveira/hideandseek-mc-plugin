package me.oredev.hideandseek.game;

import me.oredev.hideandseek.arena.Arena;
import me.oredev.hideandseek.arena.ArenaManager;
import me.oredev.hideandseek.arena.ArenaResetter;
import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.event.GameEventRegistry;
import me.oredev.hideandseek.event.FloorIsLavaEvent;
import me.oredev.hideandseek.event.SpeedGameEvent;
import me.oredev.hideandseek.event.SwapEvent;
import me.oredev.hideandseek.event.TornadoEvent;
import me.oredev.hideandseek.player.GamePlayer;
import me.oredev.hideandseek.player.PlayerRole;
import me.oredev.hideandseek.player.PlayerManager;
import me.oredev.hideandseek.ui.GameUi;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class GameManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PlayerManager players;
    private final ArenaManager arenas;
    private final GameUi ui;
    private final MessagesConfig messages;
    private final ArenaResetter resetter;
    private final CoinService coins;
    private final GameEventRegistry events = new GameEventRegistry();
    private final Map<String, GameSession> sessions = new HashMap<>();
    private final Map<UUID, GameSession> playerSessions = new HashMap<>();

    public GameManager(JavaPlugin plugin, ConfigManager config, PlayerManager players, ArenaManager arenas, GameUi ui, MessagesConfig messages, ArenaResetter resetter) {
        this.plugin = plugin;
        this.config = config;
        this.players = players;
        this.arenas = arenas;
        this.ui = ui;
        this.messages = messages;
        this.resetter = resetter;
        this.coins = new CoinService(plugin, config);
        events.register(new SpeedGameEvent());
        events.register(new TornadoEvent());
        events.register(new SwapEvent());
        events.register(new FloorIsLavaEvent());
    }

    public boolean join(Player player, Arena arena) {
        if (!arena.isConfigured()) return false;
        GameSession target = sessions.computeIfAbsent(arena.name().toLowerCase(), ignored -> new GameSession(plugin, this, config, players, ui, messages, arena, resetter, coins, events));
        if (!target.acceptingPlayers() || !target.canJoin()) return false;

        GameSession current = playerSessions.get(player.getUniqueId());
        GamePlayer originalState = null;
        if (current != null) {
            if (current.state() != GameState.ENDING) return false;
            originalState = current.leaveForNextGame(player);
            if (originalState == null) return false;
        }
        if (!target.join(player, originalState)) return false;
        playerSessions.put(player.getUniqueId(), target);
        return true;
    }

    public boolean canJoinAnotherGame(Player player) {
        return session(player).map(current -> current.state() == GameState.ENDING).orElse(false);
    }

    public boolean handleCoinPickup(Player player, Item coin) {
        if (!coins.isCoin(coin)) return false;
        session(player).ifPresent(session -> session.collectCoin(player, coin));
        return true;
    }

    public boolean canUseFirework(Player player, ItemStack item) {
        return session(player).map(session -> session.canUseFirework(player.getUniqueId(), item)).orElse(false);
    }

    public boolean canUseFishingRod(Player player, ItemStack item) {
        return session(player).map(session -> session.canUseFishingRod(player.getUniqueId(), item)).orElse(false);
    }

    public boolean canUseKangaroo(Player player, ItemStack item) {
        return session(player).map(session -> session.canUseKangaroo(player.getUniqueId(), item)).orElse(false);
    }

    public Optional<PlayerRole> utilityRole(Player player) {
        return session(player).map(session -> session.utilityRole(player.getUniqueId()));
    }

    public UtilityPurchaseResult buySpeedUtility(Player player) {
        return session(player).map(session -> session.buySpeedUtility(player)).orElse(UtilityPurchaseResult.UNAVAILABLE);
    }

    public UtilityPurchaseResult buyFishingRodUtility(Player player) {
        return session(player).map(session -> session.buyFishingRodUtility(player)).orElse(UtilityPurchaseResult.UNAVAILABLE);
    }

    public UtilityPurchaseResult buyBellUtility(Player player) {
        return session(player).map(session -> session.buyBellUtility(player)).orElse(UtilityPurchaseResult.UNAVAILABLE);
    }

    public boolean useBellUtility(Player player, ItemStack item) {
        return session(player).map(session -> session.useBellUtility(player, item)).orElse(false);
    }

    public UtilityPurchaseResult buyKangarooUtility(Player player) {
        return session(player).map(session -> session.buyKangarooUtility(player)).orElse(UtilityPurchaseResult.UNAVAILABLE);
    }

    public boolean useKangarooUtility(Player player, ItemStack item) {
        return session(player).map(session -> session.useKangarooUtility(player, item)).orElse(false);
    }

    public OptionalInt changeCoins(Player player, CoinOperation operation, int amount) {
        return session(player).map(session -> session.changeCoins(player.getUniqueId(), operation, amount)).orElseGet(OptionalInt::empty);
    }

    public boolean setRole(Player player, PlayerRole role) {
        return session(player).map(session -> session.setRole(player.getUniqueId(), role)).orElse(false);
    }

    public boolean pullHider(Player seeker, Player hider) {
        return session(seeker)
                .filter(session -> session == playerSessions.get(hider.getUniqueId()))
                .map(session -> session.pullHider(seeker.getUniqueId(), hider.getUniqueId()))
                .orElse(false);
    }

    public boolean startEvent(Arena arena, String eventId) {
        GameSession session = sessions.get(arena.name().toLowerCase());
        return session != null && session.startEvent(eventId);
    }

    public boolean stopEvent(Arena arena) {
        GameSession session = sessions.get(arena.name().toLowerCase());
        return session != null && session.stopEvent();
    }

    public List<String> eventIds() {
        return events.events().stream().map(event -> event.id()).toList();
    }

    public void leave(Player player) {
        Optional.ofNullable(playerSessions.get(player.getUniqueId())).ifPresent(session -> session.leave(player, true));
    }

    public void disconnect(Player player) {
        Optional.ofNullable(playerSessions.get(player.getUniqueId())).ifPresent(session -> session.disconnect(player.getUniqueId()));
    }

    public Optional<GameSession> session(Player player) {
        return Optional.ofNullable(playerSessions.get(player.getUniqueId()));
    }

    public boolean isPlaying(Player player) {
        return playerSessions.containsKey(player.getUniqueId());
    }

    public int playerCount(Arena arena) {
        GameSession session = sessions.get(arena.name().toLowerCase());
        return session == null ? 0 : session.players();
    }

    public boolean shouldFreeze(Player player) {
        return session(player).map(s -> s.shouldFreeze(player.getUniqueId())).orElse(false);
    }

    public boolean canTakeFallDamage(Player player) {
        return session(player).map(s -> s.state() == GameState.SEEKING && !s.hasEventFallProtection(player.getUniqueId())).orElse(false);
    }

    public boolean canHunt(Player attacker, Player victim) {
        return session(attacker).filter(s -> s == playerSessions.get(victim.getUniqueId())).map(s -> s.canHunt(attacker.getUniqueId(), victim.getUniqueId())).orElse(false);
    }

    public boolean canCounterAttack(Player attacker, Player victim) {
        return session(attacker).filter(s -> s == playerSessions.get(victim.getUniqueId())).map(s -> s.canCounterAttack(attacker.getUniqueId(), victim.getUniqueId())).orElse(false);
    }

    public void handleFinalHit(Player attacker, Player victim) {
        session(attacker).filter(s -> s == playerSessions.get(victim.getUniqueId())).ifPresent(s -> s.handleFinalHit(attacker.getUniqueId(), victim.getUniqueId()));
    }

    public void handleSeekerDeath(Player seeker) {
        session(seeker).ifPresent(s -> s.handleSeekerDeath(seeker.getUniqueId()));
    }

    public void handleEnvironmentalDeath(Player player) {
        session(player).ifPresent(s -> s.handleEnvironmentalDeath(player.getUniqueId()));
    }

    public boolean forceStart(Arena arena) {
        GameSession session = sessions.get(arena.name().toLowerCase());
        if (session == null) return false;
        session.forceStart();
        return true;
    }

    public void stop(Arena arena) {
        Optional.ofNullable(sessions.get(arena.name().toLowerCase())).ifPresent(GameSession::stop);
    }

    public void reload() {
        config.reload();
        messages.reload();
    }

    void untrack(UUID playerId) {
        playerSessions.remove(playerId);
    }

    void remove(GameSession session) {
        sessions.remove(session.arena().name().toLowerCase(), session);
    }

    public void shutdown() {
        for (GameSession session : sessions.values().toArray(GameSession[]::new)) session.stop();
        sessions.clear();
        playerSessions.clear();
    }

    public enum UtilityPurchaseResult {SUCCESS, NOT_ENOUGH_COINS, SPEED_SLOT_UNAVAILABLE, FISHING_ROD_SLOT_UNAVAILABLE, BELL_SLOT_UNAVAILABLE, KANGAROO_SLOT_UNAVAILABLE, UNAVAILABLE}

    public enum CoinOperation {SET, GIVE, REMOVE}
}
