package me.oredev.hideandseek.game;

import me.oredev.hideandseek.arena.Arena;
import me.oredev.hideandseek.arena.ArenaBounds;
import me.oredev.hideandseek.arena.ArenaResetter;
import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.event.GameEventManager;
import me.oredev.hideandseek.event.GameEventRegistry;
import me.oredev.hideandseek.player.GamePlayer;
import me.oredev.hideandseek.player.PlayerManager;
import me.oredev.hideandseek.player.PlayerRole;
import me.oredev.hideandseek.ui.GameUi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class GameSession {
    private final GameManager manager;
    private final ConfigManager config;
    private final PlayerManager playerManager;
    private final GameUi ui;
    private final MessagesConfig messages;
    private final Arena arena;
    private final ArenaResetter resetter;
    private final CoinService coins;
    private final GameEventManager events;
    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private final Map<UUID, Integer> seekerRespawns = new HashMap<>();
    private final List<UUID> endingFireworks = new ArrayList<>();
    private final Random random = new Random();
    private final Set<UUID> activeCoins = new HashSet<>();
    private final Map<UUID, Integer> playerCoins = new HashMap<>();
    private final BukkitTask ticker;
    private GameState state = GameState.WAITING;
    private int remaining;
    private int coinSpawnRemaining;
    private int fastTicks;
    private int endingFireworkTicks;

    GameSession(JavaPlugin plugin, GameManager manager, ConfigManager config, PlayerManager playerManager, GameUi ui, MessagesConfig messages, Arena arena, ArenaResetter resetter, CoinService coins, GameEventRegistry registry) {
        this.manager = manager;
        this.config = config;
        this.playerManager = playerManager;
        this.ui = ui;
        this.messages = messages;
        this.arena = arena;
        this.resetter = resetter;
        this.coins = coins;
        this.events = new GameEventManager(plugin, this, config, messages, registry);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public Arena arena() {
        return arena;
    }

    public GameState state() {
        return state;
    }

    public boolean acceptingPlayers() {
        return state == GameState.WAITING || state == GameState.STARTING;
    }

    public boolean canJoin() {
        return players.size() < arena.maxPlayers();
    }

    public boolean join(Player player, GamePlayer originalState) {
        if (players.size() >= arena.maxPlayers()) return false;
        GamePlayer gamePlayer = originalState == null ? playerManager.capture(player) : originalState;
        players.put(player.getUniqueId(), gamePlayer);
        playerManager.prepare(player);
        player.teleport(arena.joinLocation());
        ui.show(player);
        updateUi();
        player.sendMessage(messages.get("game.joined", Map.of("arena", arena.name())));
        forEachOnline(online -> {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                online.sendMessage(messages.get("game.player-joined", Map.of(
                        "player", player.getName(),
                        "arena", arena.name(),
                        "players", String.valueOf(players.size()),
                        "max-players", String.valueOf(arena.maxPlayers())
                )));
            }
        });
        return true;
    }

    GamePlayer leaveForNextGame(Player player) {
        GamePlayer gamePlayer = players.remove(player.getUniqueId());
        if (gamePlayer == null) return null;
        ui.remove(player);
        manager.untrack(player.getUniqueId());
        afterPlayerRemoval();
        return gamePlayer;
    }

    public void leave(Player player, boolean voluntary) {
        GamePlayer gamePlayer = players.remove(player.getUniqueId());
        if (gamePlayer == null) return;
        ui.remove(player);
        if (state == GameState.ENDING) {
            playerManager.restoreAfterEnding(player, gamePlayer, config.globalLobby());
            playerManager.giveInitialMenuItem(player, config.initialMenuItem());
        }
        else playerManager.restore(player, gamePlayer, config.globalLobby());
        manager.untrack(player.getUniqueId());
        if (voluntary) player.sendMessage(messages.get("game.left"));
        afterPlayerRemoval();
    }

    public void disconnect(UUID playerId) {
        if (players.remove(playerId) != null) {
            manager.untrack(playerId);
            afterPlayerRemoval();
        }
    }

    private void afterPlayerRemoval() {
        if (players.isEmpty()) {
            reset();
            return;
        }
        if (state == GameState.STARTING && players.size() < arena.minPlayers()) cancelCountdown();
        if (state == GameState.SEEKING) checkWin();
        updateUi();
    }

    private void tick() {
        if (state == GameState.RESETTING) return;
        if (state == GameState.SEEKING) events.fastTick();
        if (state == GameState.ENDING) tickEndingFireworks();
        if (++fastTicks < 10) return;
        fastTicks = 0;
        tickSeekerRespawns();
        switch (state) {
            case WAITING -> {
            }
            case STARTING -> tickCountdown();
            case HIDING -> {
                tickHiderTime();
                tickCoins();
                if (--remaining <= 0) startSeeking();
            }
            case SEEKING -> {
                tickHiderTime();
                tickCoins();
                events.tick();
                if (--remaining <= 0) finish(WinCondition.HIDERS);
            }
            case ENDING -> {
                if (--remaining <= 0) reset();
            }
            default -> {
            }
        }
        updateUi();
    }

    private void startCountdown() {
        state = GameState.STARTING;
        remaining = config.countdown();
        announceCountdown();
    }

    private void tickCountdown() {
        if (players.size() < arena.minPlayers()) {
            cancelCountdown();
            return;
        }
        if (--remaining <= 0) startHiding();
        else announceCountdown();
    }

    private void announceCountdown() {
        broadcast(messages.get("game.countdown", Map.of("seconds", String.valueOf(remaining))));
        if (remaining <= 3) forEachOnline(player -> {
            player.showTitle(title("game.titles.countdown", Map.of("seconds", String.valueOf(remaining)), Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ZERO)));
            sound(player);
        });
    }

    private void cancelCountdown() {
        state = GameState.WAITING;
        broadcast(messages.get("game.countdown-cancelled"));
    }

    private void startHiding() {
        state = GameState.HIDING;
        remaining = config.hidingTime();
        clearCoins();
        coinSpawnRemaining = config.coinSpawnInterval();
        spawnCoin();
        List<GamePlayer> selected = new ArrayList<>(players.values());
        Collections.shuffle(selected);
        int seekerCount = config.seekersFor(selected.size());
        for (int index = 0; index < selected.size(); index++) {
            GamePlayer gamePlayer = selected.get(index);
            Player player = Bukkit.getPlayer(gamePlayer.uniqueId());
            boolean seeker = index < seekerCount;
            gamePlayer.role(seeker ? PlayerRole.SEEKER : PlayerRole.HIDER);
            if (player == null) continue;
            player.teleport(seeker ? arena.seekerWaitingSpawn() : arena.hiderSpawn());
            if (seeker) {
                applyLoadout(player, "seeker");
                player.sendMessage(messages.get("game.seeker"));
            } else {
                applyLoadout(player, "hider");
                player.sendMessage(messages.get("game.hider", Map.of("seconds", String.valueOf(remaining))));
            }
        }
        broadcastTitle("game.titles.hiding");
    }

    private void startSeeking() {
        state = GameState.SEEKING;
        remaining = arena.gameTime() > 0 ? arena.gameTime() : config.gameTime();
        events.beginSeeking();
        forEachOnline(player -> {
            GamePlayer gamePlayer = players.get(player.getUniqueId());
            if (gamePlayer != null && gamePlayer.role() == PlayerRole.SEEKER) player.teleport(arena.seekerSpawn());
        });
        broadcast(messages.get("game.seeker-released"));
        broadcastTitle("game.titles.seeking");
        forEachOnline(this::sound);
    }

    public boolean canHunt(UUID attackerId, UUID victimId) {
        if (state != GameState.SEEKING) return false;
        GamePlayer attacker = players.get(attackerId);
        GamePlayer victim = players.get(victimId);
        return attacker != null && victim != null && attacker.alive() && victim.alive() && attacker.role() == PlayerRole.SEEKER && victim.role() == PlayerRole.HIDER;
    }

    public boolean canCounterAttack(UUID attackerId, UUID victimId) {
        if (state != GameState.SEEKING) return false;
        GamePlayer attacker = players.get(attackerId);
        GamePlayer victim = players.get(victimId);
        return attacker != null && victim != null && attacker.alive() && victim.alive() && attacker.role() == PlayerRole.HIDER && victim.role() == PlayerRole.SEEKER;
    }

    public void handleFinalHit(UUID attackerId, UUID victimId) {
        if (!canHunt(attackerId, victimId)) return;
        GamePlayer attacker = players.get(attackerId);
        attacker.addFound();
        eliminateHider(victimId);
    }

    public void handleSeekerDeath(UUID seekerId) {
        if (state != GameState.SEEKING || seekerRespawns.containsKey(seekerId)) return;
        GamePlayer seeker = players.get(seekerId);
        if (seeker == null || seeker.role() != PlayerRole.SEEKER || !seeker.alive()) return;
        seekerRespawns.put(seekerId, 5);
        Player player = Bukkit.getPlayer(seekerId);
        if (player != null) {
            player.setHealth(Math.max(1.0, player.getHealth()));
            player.setGameMode(GameMode.SPECTATOR);
            player.showTitle(title("game.titles.seeker-respawn", Map.of("seconds", "5"), Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(300))));
        }
    }

    public void handleEnvironmentalDeath(UUID playerId) {
        GamePlayer player = players.get(playerId);
        if (player == null || !player.alive()) return;
        if (player.role() == PlayerRole.SEEKER) handleSeekerDeath(playerId);
        else if (player.role() == PlayerRole.HIDER) eliminateHider(playerId);
    }

    private void eliminateHider(UUID victimId) {
        GamePlayer victim = players.get(victimId);
        if (victim == null || !victim.alive() || victim.role() != PlayerRole.HIDER) return;
        victim.eliminate();
        boolean respawnsAsSeeker = livingHiders() > 0;
        Player victimPlayer = Bukkit.getPlayer(victimId);
        if (victimPlayer != null) {
            victimPlayer.setHealth(Math.max(1.0, victimPlayer.getHealth()));
            victimPlayer.setGameMode(GameMode.SPECTATOR);
            if (respawnsAsSeeker) {
                seekerRespawns.put(victimId, 5);
                victimPlayer.showTitle(title("game.titles.hider-respawn", Map.of("seconds", "5"), Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(300))));
            } else {
                victimPlayer.showTitle(title("game.titles.found", Map.of(), Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(300))));
                victimPlayer.sendMessage(messages.get("game.found-self"));
            }
        }
        String name = victimPlayer == null ? messages.format("game.unknown-player", Map.of()) : victimPlayer.getName();
        broadcast(messages.get("game.found", Map.of("player", name)));
        checkWin();
        updateUi();
    }

    private void tickSeekerRespawns() {
        if (state != GameState.SEEKING) return;
        for (UUID playerId : List.copyOf(seekerRespawns.keySet())) {
            int seconds = seekerRespawns.get(playerId) - 1;
            if (seconds > 0) {
                seekerRespawns.put(playerId, seconds);
                continue;
            }
            seekerRespawns.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            GamePlayer gamePlayer = players.get(playerId);
            if (gamePlayer == null) continue;
            boolean wasHider = gamePlayer.role() == PlayerRole.SPECTATOR;
            gamePlayer.revive(PlayerRole.SEEKER);
            player.teleport(arena.seekerSpawn());
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            applyLoadout(player, "seeker");
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.setSaturation(5);
            player.sendMessage(messages.get(wasHider ? "game.hider-respawned" : "game.seeker-respawned"));
        }
    }

    private void checkWin() {
        if (state != GameState.SEEKING) return;
        if (livingHiders() == 0) finish(WinCondition.SEEKERS);
        else if (seekers() == 0) finish(WinCondition.HIDERS);
    }

    private void finish(WinCondition winner) {
        if (state == GameState.ENDING || state == GameState.RESETTING) return;
        events.stopForGameEnd();
        state = GameState.ENDING;
        remaining = config.endingTime();
        endingFireworkTicks = 0;
        clearCoins();
        forEachOnline(player -> playerManager.prepareEnding(player, config.nextGameMenuItem()));
        boolean seekers = winner == WinCondition.SEEKERS;
        broadcast(messages.get(seekers ? "game.seekers-win" : "game.hiders-win"));
        broadcastTitle(seekers ? "game.titles.seekers-win" : "game.titles.hiders-win");
        sendMatchSummary();
        launchEndingFireworks();
        forEachOnline(this::sound);
    }

    public void forceStart() {
        if (state == GameState.WAITING || state == GameState.STARTING) startHiding();
    }

    public void stop() {
        if (state != GameState.RESETTING) reset();
    }

    private void reset() {
        if (state == GameState.RESETTING) return;
        events.stopForGameEnd();
        boolean finished = state == GameState.ENDING;
        state = GameState.RESETTING;
        ticker.cancel();
        for (GamePlayer gamePlayer : List.copyOf(players.values())) {
            Player player = Bukkit.getPlayer(gamePlayer.uniqueId());
            if (player != null) {
                ui.remove(player);
                if (finished) {
                    playerManager.restoreAfterEnding(player, gamePlayer, config.globalLobby());
                    playerManager.giveInitialMenuItem(player, config.initialMenuItem());
                }
                else playerManager.restore(player, gamePlayer, config.globalLobby());
            }
            manager.untrack(gamePlayer.uniqueId());
        }
        players.clear();
        seekerRespawns.clear();
        clearEndingFireworks();
        clearCoins();
        playerCoins.clear();
        resetter.reset(arena);
        manager.remove(this);
    }

    public boolean shouldFreeze(UUID playerId) {
        GamePlayer player = players.get(playerId);
        return state == GameState.HIDING && player != null && player.role() == PlayerRole.SEEKER;
    }

    public int livingHiders() {
        return (int) players.values().stream().filter(p -> p.alive() && p.role() == PlayerRole.HIDER).count();
    }

    public int seekers() {
        return (int) players.values().stream().filter(p -> p.alive() && p.role() == PlayerRole.SEEKER).count();
    }

    public int coins(UUID playerId) {
        return playerCoins.getOrDefault(playerId, 0);
    }

    public java.util.OptionalInt changeCoins(UUID playerId, GameManager.CoinOperation operation, int amount) {
        if ((state != GameState.HIDING && state != GameState.SEEKING) || !players.containsKey(playerId) || amount < 0) {
            return java.util.OptionalInt.empty();
        }
        int updated = switch (operation) {
            case SET -> amount;
            case GIVE -> (int) Math.min(Integer.MAX_VALUE, (long) coins(playerId) + amount);
            case REMOVE -> Math.max(0, coins(playerId) - amount);
        };
        playerCoins.put(playerId, updated);
        updateUi();
        return java.util.OptionalInt.of(updated);
    }

    public boolean setRole(UUID playerId, PlayerRole role) {
        if ((state != GameState.HIDING && state != GameState.SEEKING) || (role != PlayerRole.HIDER && role != PlayerRole.SEEKER)) return false;
        GamePlayer gamePlayer = players.get(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (gamePlayer == null || player == null) return false;
        seekerRespawns.remove(playerId);
        gamePlayer.revive(role);
        playerManager.prepare(player);
        Location spawn = role == PlayerRole.HIDER ? arena.hiderSpawn()
                : state == GameState.HIDING ? arena.seekerWaitingSpawn() : arena.seekerSpawn();
        player.teleport(spawn);
        applyLoadout(player, role.name().toLowerCase());
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(5);
        updateUi();
        return true;
    }

    public PlayerRole utilityRole(UUID playerId) {
        GamePlayer player = players.get(playerId);
        if ((state != GameState.HIDING && state != GameState.SEEKING) || player == null || !player.alive()) return null;
        return player.role() == PlayerRole.HIDER || player.role() == PlayerRole.SEEKER ? player.role() : null;
    }

    public boolean canUseFirework(UUID playerId, ItemStack item) {
        if (state != GameState.HIDING && state != GameState.SEEKING) return false;
        GamePlayer player = players.get(playerId);
        if (player == null || !player.alive() || (player.role() != PlayerRole.HIDER && player.role() != PlayerRole.SEEKER)) return false;
        return playerManager.isInfiniteFirework(item) && item.getType() == config.roleFirework(player.role().name().toLowerCase()).item().material();
    }

    public boolean hasEventFallProtection(UUID playerId) {
        return events.protectsFallDamage(playerId);
    }

    public boolean canUseFishingRod(UUID playerId, ItemStack item) {
        GamePlayer player = players.get(playerId);
        return state == GameState.SEEKING && player != null && player.alive() && player.role() == PlayerRole.SEEKER
                && playerManager.isFishingRodUtilityItem(item);
    }

    public boolean canUseKangaroo(UUID playerId, ItemStack item) {
        GamePlayer player = players.get(playerId);
        return (state == GameState.HIDING || state == GameState.SEEKING) && player != null && player.alive()
                && (player.role() == PlayerRole.HIDER || player.role() == PlayerRole.SEEKER)
                && playerManager.isKangarooUtilityItem(item);
    }

    public boolean applyEventDamage(Player player, double amount) {
        GamePlayer gamePlayer = players.get(player.getUniqueId());
        if (state != GameState.SEEKING || gamePlayer == null || !gamePlayer.alive()
                || (gamePlayer.role() != PlayerRole.HIDER && gamePlayer.role() != PlayerRole.SEEKER)) return false;
        double damage = Math.max(0.0, amount);
        if (damage == 0.0) return true;
        if (player.getHealth() <= damage) {
            handleEnvironmentalDeath(player.getUniqueId());
            return true;
        }
        player.setHealth(player.getHealth() - damage);
        return true;
    }

    boolean useBellUtility(Player seekerPlayer, ItemStack item) {
        GamePlayer seeker = players.get(seekerPlayer.getUniqueId());
        if (state != GameState.SEEKING || seeker == null || !seeker.alive() || seeker.role() != PlayerRole.SEEKER
                || !playerManager.isBellUtilityItem(item)) return false;
        int duration = config.bellUtilityDuration() * 20;
        for (GamePlayer gamePlayer : players.values()) {
            if (!gamePlayer.alive() || gamePlayer.role() != PlayerRole.HIDER) continue;
            Player hider = Bukkit.getPlayer(gamePlayer.uniqueId());
            if (hider != null) hider.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, false, false, false));
        }
        Sound sound = config.bellUtilitySound();
        if (sound != null) forEachOnline(player -> player.playSound(player.getLocation(), sound, 1f, 1f));
        item.subtract(1);
        return true;
    }

    boolean useKangarooUtility(Player player, ItemStack item) {
        if (!canUseKangaroo(player.getUniqueId(), item)) return false;
        String role = players.get(player.getUniqueId()).role().name().toLowerCase();
        Vector velocity;
        if (player.isSneaking()) {
            velocity = player.getLocation().getDirection().setY(0);
            if (velocity.lengthSquared() == 0) return false;
            velocity.normalize().multiply(config.kangarooForwardVelocity(role));
            velocity.setY(config.kangarooForwardYVelocity(role));
        } else {
            velocity = new Vector(0, config.kangarooVerticalVelocity(role), 0);
        }
        player.setVelocity(velocity);
        player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, player.getLocation().add(0, 0.2, 0), 12, 0.2, 0.1, 0.2, 0.04);
        Sound sound = config.kangarooUtilitySound(role);
        if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
        item.subtract(1);
        return true;
    }

    GameManager.UtilityPurchaseResult buySpeedUtility(Player player) {
        if (utilityRole(player.getUniqueId()) != PlayerRole.HIDER) return GameManager.UtilityPurchaseResult.UNAVAILABLE;
        int price = config.speedUtilityPrice();
        if (coins(player.getUniqueId()) < price) return GameManager.UtilityPurchaseResult.NOT_ENOUGH_COINS;
        int slot = config.speedUtilityHotbarSlot();
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir()) {
            player.getInventory().setItem(slot, playerManager.speedUtilityItem(config.speedUtilityItem()));
        } else if (playerManager.isSpeedUtilityItem(current) && current.getAmount() < current.getMaxStackSize()) {
            current.setAmount(current.getAmount() + 1);
        } else return GameManager.UtilityPurchaseResult.SPEED_SLOT_UNAVAILABLE;
        playerCoins.merge(player.getUniqueId(), -price, Integer::sum);
        updateUi();
        return GameManager.UtilityPurchaseResult.SUCCESS;
    }

    GameManager.UtilityPurchaseResult buyFishingRodUtility(Player player) {
        if (utilityRole(player.getUniqueId()) != PlayerRole.SEEKER) return GameManager.UtilityPurchaseResult.UNAVAILABLE;
        int price = config.fishingRodUtilityPrice();
        if (coins(player.getUniqueId()) < price) return GameManager.UtilityPurchaseResult.NOT_ENOUGH_COINS;
        int slot = config.fishingRodUtilityHotbarSlot();
        ItemStack current = player.getInventory().getItem(slot);
        if (current != null && !current.getType().isAir()) return GameManager.UtilityPurchaseResult.FISHING_ROD_SLOT_UNAVAILABLE;
        player.getInventory().setItem(slot, playerManager.fishingRodUtilityItem(config.fishingRodUtilityItem(), config.fishingRodUnbreakable()));
        playerCoins.merge(player.getUniqueId(), -price, Integer::sum);
        updateUi();
        return GameManager.UtilityPurchaseResult.SUCCESS;
    }

    GameManager.UtilityPurchaseResult buyBellUtility(Player player) {
        if (utilityRole(player.getUniqueId()) != PlayerRole.SEEKER) return GameManager.UtilityPurchaseResult.UNAVAILABLE;
        int price = config.bellUtilityPrice();
        if (coins(player.getUniqueId()) < price) return GameManager.UtilityPurchaseResult.NOT_ENOUGH_COINS;
        int slot = config.bellUtilityHotbarSlot();
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir()) {
            player.getInventory().setItem(slot, playerManager.bellUtilityItem(config.bellUtilityItem()));
        } else if (playerManager.isBellUtilityItem(current) && current.getAmount() < current.getMaxStackSize()) {
            current.setAmount(current.getAmount() + 1);
        } else return GameManager.UtilityPurchaseResult.BELL_SLOT_UNAVAILABLE;
        playerCoins.merge(player.getUniqueId(), -price, Integer::sum);
        updateUi();
        return GameManager.UtilityPurchaseResult.SUCCESS;
    }

    GameManager.UtilityPurchaseResult buyKangarooUtility(Player player) {
        PlayerRole role = utilityRole(player.getUniqueId());
        if (role != PlayerRole.HIDER && role != PlayerRole.SEEKER) return GameManager.UtilityPurchaseResult.UNAVAILABLE;
        String roleKey = role.name().toLowerCase();
        int price = config.kangarooUtilityPrice(roleKey);
        if (coins(player.getUniqueId()) < price) return GameManager.UtilityPurchaseResult.NOT_ENOUGH_COINS;
        int slot = config.kangarooUtilityHotbarSlot(roleKey);
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir()) {
            player.getInventory().setItem(slot, playerManager.kangarooUtilityItem(config.kangarooUtilityItem(roleKey)));
        } else if (playerManager.isKangarooUtilityItem(current) && current.getAmount() < current.getMaxStackSize()) {
            current.setAmount(current.getAmount() + 1);
        } else return GameManager.UtilityPurchaseResult.KANGAROO_SLOT_UNAVAILABLE;
        playerCoins.merge(player.getUniqueId(), -price, Integer::sum);
        updateUi();
        return GameManager.UtilityPurchaseResult.SUCCESS;
    }

    boolean pullHider(UUID seekerId, UUID hiderId) {
        GamePlayer seeker = players.get(seekerId);
        GamePlayer hider = players.get(hiderId);
        if (state != GameState.SEEKING || seeker == null || hider == null || !seeker.alive() || !hider.alive()
                || seeker.role() != PlayerRole.SEEKER || hider.role() != PlayerRole.HIDER) return false;
        Player seekerPlayer = Bukkit.getPlayer(seekerId);
        Player hiderPlayer = Bukkit.getPlayer(hiderId);
        if (seekerPlayer == null || hiderPlayer == null) return false;
        Vector pull = seekerPlayer.getLocation().toVector().subtract(hiderPlayer.getLocation().toVector());
        if (pull.lengthSquared() == 0) return false;
        hiderPlayer.setVelocity(pull.normalize().multiply(config.fishingRodPullStrength()));
        return true;
    }

    void collectCoin(Player player, Item coin) {
        if ((state != GameState.HIDING && state != GameState.SEEKING) || !activeCoins.remove(coin.getUniqueId())) return;
        coin.remove();
        int collected = playerCoins.merge(player.getUniqueId(), 1, Integer::sum);
        player.sendMessage(messages.get("game.coin-collected", Map.of("coins", String.valueOf(collected))));
        updateUi();
    }

    private void tickCoins() {
        if (!coins.enabled()) return;
        activeCoins.removeIf(id -> {
            var entity = Bukkit.getEntity(id);
            return !(entity instanceof Item item) || !item.isValid();
        });
        if (--coinSpawnRemaining <= 0) {
            spawnCoin();
            coinSpawnRemaining = config.coinSpawnInterval();
        }
    }

    private void tickHiderTime() {
        for (GamePlayer gamePlayer : players.values()) {
            if (gamePlayer.alive() && gamePlayer.role() == PlayerRole.HIDER) gamePlayer.addHiderSecond();
        }
    }

    private void sendMatchSummary() {
        int amount = config.summaryTopAmount();
        broadcast(messages.get("game.summary.header"));

        List<GamePlayer> seekers = players.values().stream().filter(GamePlayer::hasBeenSeeker)
                .sorted(java.util.Comparator.comparingInt(GamePlayer::founds).reversed()).limit(amount).toList();
        broadcast(messages.get("game.summary.seekers-title"));
        if (seekers.isEmpty()) {
            broadcast(messages.get("game.summary.none"));
        } else for (int index = 0; index < seekers.size(); index++) {
            GamePlayer seeker = seekers.get(index);
            broadcast(messages.get("game.summary.seeker-entry", Map.of(
                    "position", String.valueOf(index + 1), "player", playerName(seeker), "founds", String.valueOf(seeker.founds())
            )));
        }

        List<GamePlayer> hiders = players.values().stream().filter(GamePlayer::hasBeenHider)
                .sorted(java.util.Comparator.comparingInt(GamePlayer::hiderSeconds).reversed()).limit(amount).toList();
        broadcast(messages.get("game.summary.hiders-title"));
        if (hiders.isEmpty()) {
            broadcast(messages.get("game.summary.none"));
        } else for (int index = 0; index < hiders.size(); index++) {
            GamePlayer hider = hiders.get(index);
            int seconds = hider.hiderSeconds();
            broadcast(messages.get("game.summary.hider-entry", Map.of(
                    "position", String.valueOf(index + 1), "player", playerName(hider),
                    "time", messages.format("game.summary.time", Map.of(
                            "minutes", String.valueOf(seconds / 60), "seconds", String.valueOf(seconds % 60)
                    ))
            )));
        }
    }

    private String playerName(GamePlayer gamePlayer) {
        Player player = Bukkit.getPlayer(gamePlayer.uniqueId());
        return player == null ? messages.format("game.unknown-player", Map.of()) : player.getName();
    }

    private void tickEndingFireworks() {
        ConfigManager.EndingFireworksDefinition definition = config.endingFireworks();
        if (!definition.enabled() || (endingFireworkTicks += 2) < definition.intervalTicks()) return;
        endingFireworkTicks = 0;
        launchEndingFireworks(definition);
    }

    private void launchEndingFireworks() {
        launchEndingFireworks(config.endingFireworks());
    }

    private void launchEndingFireworks(ConfigManager.EndingFireworksDefinition definition) {
        ArenaBounds bounds = arena.bounds();
        if (!definition.enabled() || bounds == null || !bounds.isValid()) return;
        endingFireworks.removeIf(id -> {
            var entity = Bukkit.getEntity(id);
            return !(entity instanceof Firework firework) || !firework.isValid();
        });
        for (int index = 0; index < definition.amount(); index++) {
            int x = random(bounds.minX(), bounds.maxX());
            int z = random(bounds.minZ(), bounds.maxZ());
            int y = bounds.world().getHighestBlockYAt(x, z) + 1;
            Firework firework = bounds.world().spawn(new Location(bounds.world(), x + 0.5, y, z + 0.5), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.clearEffects();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(definition.colors().get(random.nextInt(definition.colors().size())))
                    .trail(definition.trail()).flicker(definition.flicker()).build());
            meta.setPower(definition.power());
            firework.setFireworkMeta(meta);
            firework.setShotAtAngle(true);
            firework.setVelocity(new Vector(0, definition.launchVelocity(), 0));
            endingFireworks.add(firework.getUniqueId());
        }
    }

    private void clearEndingFireworks() {
        for (UUID fireworkId : endingFireworks) {
            var entity = Bukkit.getEntity(fireworkId);
            if (entity != null) entity.remove();
        }
        endingFireworks.clear();
    }

    private int random(int minimum, int maximum) {
        return minimum >= maximum ? minimum : random.nextInt(maximum - minimum + 1) + minimum;
    }

    private void spawnCoin() {
        if (!coins.enabled() || activeCoins.size() >= config.maxActiveCoins()) return;
        Item coin = coins.spawn(arena.bounds());
        if (coin != null) activeCoins.add(coin.getUniqueId());
    }

    private void clearCoins() {
        for (UUID coinId : activeCoins) {
            var entity = Bukkit.getEntity(coinId);
            if (entity != null) entity.remove();
        }
        activeCoins.clear();
    }

    public Map<UUID, PlayerRole> roles() {
        Map<UUID, PlayerRole> roles = new LinkedHashMap<>();
        players.forEach((id, player) -> roles.put(id, player.role()));
        return roles;
    }

    public int players() {
        return players.size();
    }

    public List<Player> onlinePlayers() {
        return players.keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).toList();
    }

    public List<Player> livingPlayers() {
        return players.values().stream()
                .filter(GamePlayer::alive)
                .map(GamePlayer::uniqueId)
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<Player> playersWithRole(PlayerRole role) {
        return players.values().stream()
                .filter(player -> player.role() == role)
                .map(GamePlayer::uniqueId)
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    boolean startEvent(String eventId) {
        return state == GameState.SEEKING && events.forceStart(eventId);
    }

    boolean stopEvent() {
        return state == GameState.SEEKING && events.stopCurrent(true);
    }

    public String formattedTime() {
        return String.format("%02d:%02d", Math.max(0, remaining) / 60, Math.max(0, remaining) % 60);
    }

    public String stateLabel() {
        return messages.format("states." + state.name().toLowerCase(), Map.of());
    }

    private void updateUi() {
        forEachOnline(player -> ui.update(player, this));
    }

    private void broadcast(Component message) {
        forEachOnline(player -> player.sendMessage(message));
    }

    private void broadcastTitle(String key) {
        forEachOnline(player -> player.showTitle(title(key, Map.of(), Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(300)))));
    }

    private Title title(String key, Map<String, String> values, Title.Times times) {
        return Title.title(messages.component(key + ".title", values), messages.component(key + ".subtitle", values), times);
    }

    private void sound(Player player) {
        if (config.soundsEnabled()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.1f);
    }

    private void applyLoadout(Player player, String role) {
        player.getInventory().setItem(0, config.loadoutItem(role, "weapon"));
        player.getInventory().setChestplate(config.loadoutItem(role, "chestplate"));
        player.getInventory().setBoots(config.loadoutItem(role, "boots"));
        playerManager.giveRoleFirework(player, config.roleFirework(role));
        playerManager.giveUtilityShopOpener(player, config.utilityShopOpener(role));
    }

    private void forEachOnline(java.util.function.Consumer<Player> action) {
        players.keySet().forEach(id -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) action.accept(player);
        });
    }
}
