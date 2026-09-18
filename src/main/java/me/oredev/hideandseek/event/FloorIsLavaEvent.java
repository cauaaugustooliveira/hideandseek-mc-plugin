package me.oredev.hideandseek.event;

import me.oredev.hideandseek.arena.ArenaBounds;
import me.oredev.hideandseek.game.GameState;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class FloorIsLavaEvent implements GameEvent {
    private final List<BlockDisplay> tiles = new ArrayList<>();
    private final Map<UUID, Integer> damageCooldowns = new HashMap<>();
    private final Set<UUID> ignitedPlayers = new HashSet<>();
    private final Random random = new Random();
    private int elapsedTicks;
    private int particleTicks;
    private int soundTicks;

    @Override
    public String id() {
        return "floor-is-lava";
    }

    @Override
    public GameEvent create() {
        return new FloorIsLavaEvent();
    }

    @Override
    public boolean announcesLifecycle() {
        return false;
    }

    @Override
    public boolean announcesFinish() {
        return true;
    }

    @Override
    public boolean canStart(GameEventContext context) {
        ArenaBounds bounds = context.arena().bounds();
        if (context.session().state() != GameState.SEEKING || bounds == null || !bounds.isValid()
                || bounds.world() == null || Bukkit.getWorld(context.arena().worldName()) == null) return false;
        double lavaY = lavaY(context);
        return lavaY >= bounds.world().getMinHeight() && lavaY <= bounds.world().getMaxHeight()
                && !activePlayers(context).isEmpty();
    }

    @Override
    public void warningTick(GameEventContext context, int secondsRemaining) {
        Map<String, String> values = Map.of("count", String.valueOf(secondsRemaining));
        for (Player player : context.participants()) {
            player.showTitle(Title.title(context.messages().component("events.floor-is-lava.countdown.title", values),
                    context.messages().component("events.floor-is-lava.countdown.subtitle", values),
                    Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(900), Duration.ofMillis(50))));
            if (context.definition().bool("sound.enabled", true)) {
                Sound sound = context.config().sound("events.floor-is-lava.sound.countdown", Sound.BLOCK_NOTE_BLOCK_PLING);
                if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
            }
        }
    }

    @Override
    public void start(GameEventContext context) {
        createSurface(context);
        announceStarted(context);
        debug(context, "Created " + tiles.size() + " lava tiles at Y=" + lavaY(context));
    }

    @Override
    public void tick(GameEventContext context) {
        elapsedTicks += 2;
        applyLavaPhysics(context);
        updateVisuals(context);
        damageCooldowns.entrySet().removeIf(entry -> entry.getValue() <= elapsedTicks);
    }

    @Override
    public void stop(GameEventContext context) {
        for (BlockDisplay tile : tiles) if (tile.isValid()) tile.remove();
        tiles.clear();
        damageCooldowns.clear();
        for (UUID playerId : ignitedPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.setFireTicks(0);
        }
        ignitedPlayers.clear();
        debug(context, "Lava surface cleaned up.");
    }

    private void createSurface(GameEventContext context) {
        ArenaBounds bounds = context.arena().bounds();
        int tileSize = Math.max(1, context.definition().integer("visuals.tile-size", 16));
        double y = lavaY(context);
        for (int x = bounds.minX(); x <= bounds.maxX(); x += tileSize) {
            int width = Math.min(tileSize, bounds.maxX() - x + 1);
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z += tileSize) {
                int length = Math.min(tileSize, bounds.maxZ() - z + 1);
                BlockDisplay tile = bounds.world().spawn(new Location(bounds.world(), x, y, z), BlockDisplay.class);
                tile.setBlock(Bukkit.createBlockData(Material.LAVA));
                tile.setGravity(false);
                tile.setPersistent(false);
                tile.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(width, 0.12f, length), new Quaternionf()));
                tiles.add(tile);
            }
        }
    }

    private void applyLavaPhysics(GameEventContext context) {
        ArenaBounds bounds = context.arena().bounds();
        double lavaY = lavaY(context);
        double margin = Math.max(0.0, context.definition().decimal("physics.danger-margin", 0.25));
        int interval = Math.max(1, context.definition().integer("damage.interval-ticks", 20));
        int fireTicks = Math.max(0, context.definition().integer("damage.fire-ticks", 40));
        double damage = Math.max(0.0, context.definition().decimal("damage.amount", 2.0));
        double knockUp = Math.max(0.0, context.definition().decimal("physics.knock-up", 0.30));
        for (Player player : activePlayers(context)) {
            if (!isExposed(bounds, player.getLocation(), lavaY, margin)) continue;
            if (fireTicks > 0) {
                player.setFireTicks(Math.max(player.getFireTicks(), fireTicks));
                ignitedPlayers.add(player.getUniqueId());
            }
            if (knockUp > 0.0 && player.getVelocity().getY() < knockUp) {
                Vector velocity = player.getVelocity().clone();
                velocity.setY(knockUp);
                player.setVelocity(velocity);
            }
            if (damageCooldowns.getOrDefault(player.getUniqueId(), 0) > elapsedTicks) continue;
            if (!context.session().applyEventDamage(player, damage)) continue;
            damageCooldowns.put(player.getUniqueId(), elapsedTicks + interval);
            player.getWorld().spawnParticle(Particle.LAVA, player.getLocation().add(0, 0.2, 0), 2, 0.2, 0.1, 0.2, 0.02);
            playPop(context, player);
            if (context.session().state() != GameState.SEEKING) return;
        }
    }

    private void updateVisuals(GameEventContext context) {
        if (!context.definition().bool("visuals.enabled", true)) return;
        int particleInterval = Math.max(1, context.definition().integer("visuals.particle-interval-ticks", 5));
        if ((particleTicks += 2) >= particleInterval) {
            particleTicks = 0;
            ArenaBounds bounds = context.arena().bounds();
            int amount = Math.max(0, context.definition().integer("visuals.particles-per-update", 8));
            for (int index = 0; index < amount; index++) {
                double x = bounds.minX() + random.nextDouble() * (bounds.maxX() - bounds.minX() + 1);
                double z = bounds.minZ() + random.nextDouble() * (bounds.maxZ() - bounds.minZ() + 1);
                bounds.world().spawnParticle(Particle.LAVA, x, lavaY(context) + 0.15, z, 1, 0.12, 0.02, 0.12, 0.01);
            }
        }
        int soundInterval = Math.max(2, context.definition().integer("sound.interval-ticks", 40));
        if ((soundTicks += 2) >= soundInterval) {
            soundTicks = 0;
            Sound sound = context.config().sound("events.floor-is-lava.sound.ambient", Sound.BLOCK_LAVA_AMBIENT);
            if (sound != null) for (Player player : activePlayers(context)) player.playSound(player.getLocation(), sound, 0.5f, 1f);
        }
    }

    private boolean isExposed(ArenaBounds bounds, Location location, double lavaY, double margin) {
        if (!location.getWorld().equals(bounds.world()) || location.getX() < bounds.minX() || location.getX() >= bounds.maxX() + 1
                || location.getZ() < bounds.minZ() || location.getZ() >= bounds.maxZ() + 1 || location.getY() > lavaY + margin) return false;
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int fromY = Math.max(bounds.world().getMinHeight(), location.getBlockY() + 1);
        int toY = Math.min(bounds.world().getMaxHeight(), (int) Math.floor(lavaY));
        for (int y = fromY; y <= toY; y++) {
            Block block = bounds.world().getBlockAt(x, y, z);
            if (block.getType().isSolid()) return false;
        }
        return true;
    }

    private List<Player> activePlayers(GameEventContext context) {
        return context.livingPlayers().stream().filter(player -> player.getGameMode() != org.bukkit.GameMode.SPECTATOR)
                .filter(player -> player.getWorld().equals(context.arena().bounds().world())).toList();
    }

    private double lavaY(GameEventContext context) {
        Double arenaOverride = context.arena().floorIsLavaY();
        return arenaOverride == null ? context.definition().decimal("lava-y", 64.0) : arenaOverride;
    }

    private void announceStarted(GameEventContext context) {
        for (Player player : context.participants()) {
            player.showTitle(Title.title(context.messages().component("events.floor-is-lava.started.title"),
                    context.messages().component("events.floor-is-lava.started.subtitle"),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(250))));
        }
    }

    private void playPop(GameEventContext context, Player player) {
        if (!context.definition().bool("sound.enabled", true)) return;
        Sound sound = context.config().sound("events.floor-is-lava.sound.pop", Sound.BLOCK_LAVA_POP);
        if (sound != null) player.playSound(player.getLocation(), sound, 0.8f, 1f);
    }

    private void debug(GameEventContext context, String message) {
        if (context.config().eventDebug()) context.plugin().getLogger().info("[FloorIsLava] " + message);
    }
}
