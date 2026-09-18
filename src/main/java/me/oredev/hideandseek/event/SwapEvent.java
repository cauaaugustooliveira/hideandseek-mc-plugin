package me.oredev.hideandseek.event;

import me.oredev.hideandseek.game.GameState;
import me.oredev.hideandseek.arena.ArenaBounds;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class SwapEvent implements GameEvent {
    private final Random random = new Random();
    private int elapsedTicks;
    private int lastCountdown;
    private boolean complete;

    @Override
    public String id() {
        return "swap";
    }

    @Override
    public GameEvent create() {
        return new SwapEvent();
    }

    @Override
    public boolean canStart(GameEventContext context) {
        if (context.session().state() != GameState.SEEKING || context.arena() == null
                || Bukkit.getWorld(context.arena().worldName()) == null) return false;
        return eligibleHiders(context).size() >= minimumHiders(context);
    }

    @Override
    public boolean announcesLifecycle() {
        return false;
    }

    @Override
    public void start(GameEventContext context) {
        announce(context, "events.swap.started", Map.of());
        int seconds = Math.max(0, context.definition().warningSeconds());
        lastCountdown = seconds + 1;
        showCountdown(context, seconds);
    }

    @Override
    public void tick(GameEventContext context) {
        if (context.session().state() != GameState.SEEKING) {
            complete = true;
            return;
        }
        elapsedTicks += 2;
        int countdownSeconds = Math.max(0, context.definition().warningSeconds());
        int remaining = countdownSeconds - elapsedTicks / 20;
        if (remaining > 0) {
            if (remaining != lastCountdown) showCountdown(context, remaining);
            return;
        }
        performSwap(context);
        complete = true;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void stop(GameEventContext context) {
        complete = true;
    }

    private void performSwap(GameEventContext context) {
        List<Player> hiders = eligibleHiders(context);
        if (hiders.size() < minimumHiders(context)) {
            announce(context, "events.swap.cancelled", Map.of());
            debug(context, "Cancelled: fewer than the configured minimum hiders remain.");
            return;
        }

        Map<UUID, Location> originalPositions = new HashMap<>();
        for (Player hider : hiders) originalPositions.put(hider.getUniqueId(), hider.getLocation().clone());

        List<Player> order = new ArrayList<>(hiders);
        Collections.shuffle(order, random);
        int rotation = 1 + random.nextInt(order.size() - 1);
        Map<UUID, Location> destinations = new HashMap<>();
        for (int index = 0; index < order.size(); index++) {
            Location destination = originalPositions.get(order.get((index + rotation) % order.size()).getUniqueId());
            destinations.put(order.get(index).getUniqueId(), destination.clone());
        }

        for (Player hider : order) playEffect(context, originalPositions.get(hider.getUniqueId()));
        for (Player hider : order) {
            Location destination = safeTeleportDestination(context, destinations.get(hider.getUniqueId()));
            if (!hider.isOnline() || destination == null) {
                debug(context, "Skipped invalid destination for " + hider.getUniqueId());
                continue;
            }
            hider.setVelocity(new Vector());
            hider.setFallDistance(0);
            hider.teleport(destination);
            if (context.definition().bool("reset-velocity", true)) hider.setVelocity(new Vector());
            hider.setFallDistance(0);
            playEffect(context, destination);
        }
        announce(context, "events.swap.swapped", Map.of());
        debug(context, "Swapped " + order.size() + " hiders with a rotation of " + rotation + ".");
    }

    private List<Player> eligibleHiders(GameEventContext context) {
        World arenaWorld = context.arena() == null ? null : Bukkit.getWorld(context.arena().worldName());
        if (arenaWorld == null) return List.of();
        Set<UUID> living = new HashSet<>();
        for (Player player : context.livingPlayers()) living.add(player.getUniqueId());
        return context.hiders().stream()
                .filter(Player::isOnline)
                .filter(player -> living.contains(player.getUniqueId()))
                .filter(player -> player.getWorld().equals(arenaWorld))
                .toList();
    }

    private int minimumHiders(GameEventContext context) {
        return Math.max(2, context.definition().integer("minimum-hiders", 2));
    }

    private Location safeTeleportDestination(GameEventContext context, Location location) {
        if (location == null || location.getWorld() == null) return null;
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1
                || !world.isChunkLoaded(x >> 4, z >> 4)) return null;
        ArenaBounds bounds = context.arena().bounds();
        if (bounds != null && bounds.isValid() && (x < bounds.minX() || x > bounds.maxX()
                || z < bounds.minZ() || z > bounds.maxZ())) return null;

        // A player may be jumping when the original locations are captured. Search a
        // few blocks below so the recipient is placed on that player's floor, never mid-air.
        for (int groundY = y - 1; groundY >= Math.max(world.getMinHeight(), y - 3); groundY--) {
            Block ground = world.getBlockAt(x, groundY, z);
            Block feet = world.getBlockAt(x, groundY + 1, z);
            Block head = world.getBlockAt(x, groundY + 2, z);
            if (!ground.getType().isSolid() || !feet.isPassable() || !head.isPassable()) continue;
            return new Location(world, x + 0.5, groundY + 1, z + 0.5, location.getYaw(), location.getPitch());
        }
        return null;
    }

    private void showCountdown(GameEventContext context, int seconds) {
        lastCountdown = seconds;
        if (seconds <= 0) return;
        Map<String, String> values = Map.of("count", String.valueOf(seconds));
        for (Player player : context.participants()) {
            player.showTitle(Title.title(context.messages().component("events.swap.countdown.title", values),
                    context.messages().component("events.swap.countdown.subtitle", values),
                    Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(900), Duration.ofMillis(50))));
            if (context.definition().bool("sound.enabled", true)) {
                Sound sound = context.config().sound("events.swap.sound.countdown", Sound.BLOCK_NOTE_BLOCK_PLING);
                if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
            }
        }
    }

    private void playEffect(GameEventContext context, Location location) {
        if (location == null || location.getWorld() == null) return;
        if (context.definition().bool("visuals.particles", true)) {
            Particle particle = particle(context);
            int count = Math.max(0, context.definition().integer("visuals.particle-count", 30));
            location.getWorld().spawnParticle(particle, location, count, 0.35, 0.6, 0.35, 0.08);
        }
        if (context.definition().bool("sound.enabled", true)) {
            Sound sound = context.config().sound("events.swap.sound.swap", Sound.ENTITY_ENDERMAN_TELEPORT);
            if (sound != null) location.getWorld().playSound(location, sound, 1f, 1f);
        }
    }

    private Particle particle(GameEventContext context) {
        String configured = context.definition().settings() == null ? "PORTAL"
                : context.definition().settings().getString("visuals.particle", "PORTAL");
        try {
            return Particle.valueOf(configured.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Particle.PORTAL;
        }
    }

    private void announce(GameEventContext context, String path, Map<String, String> values) {
        String chat = context.messages().format(path + ".chat", values);
        for (Player player : context.participants()) {
            if (!chat.isEmpty()) player.sendMessage(context.messages().get(path + ".chat", values));
            player.showTitle(Title.title(context.messages().component(path + ".title", values),
                    context.messages().component(path + ".subtitle", values),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(250))));
        }
    }

    private void debug(GameEventContext context, String message) {
        if (context.config().eventDebug()) context.plugin().getLogger().info("[Swap] " + message);
    }
}
