package me.oredev.hideandseek.event;

import me.oredev.hideandseek.arena.ArenaBounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class TornadoEvent implements GameEvent {
    private final Random random = new Random();
    private final List<Debris> debris = new ArrayList<>();
    private final Map<UUID, Integer> capturedUntil = new HashMap<>();
    private final Map<UUID, Integer> launchCooldowns = new HashMap<>();
    private final Map<UUID, Integer> fallProtection = new HashMap<>();
    private Location center;
    private Location destination;
    private int elapsedTicks;
    private int renderTicks;
    private int soundTicks;

    @Override
    public String id() {
        return "tornado";
    }

    @Override
    public GameEvent create() {
        return new TornadoEvent();
    }

    @Override
    public boolean canStart(GameEventContext context) {
        ArenaBounds bounds = context.arena().bounds();
        if (context.session().state() != me.oredev.hideandseek.game.GameState.SEEKING || bounds == null || !bounds.isValid()
                || context.livingPlayers().isEmpty()) return false;
        double topRadius = context.definition().decimal("size.top-radius", 12.0);
        return bounds.maxX() - bounds.minX() > topRadius * 2 + 4 && bounds.maxZ() - bounds.minZ() > topRadius * 2 + 4;
    }

    @Override
    public void start(GameEventContext context) {
        center = selectSpawn(context);
        destination = selectDestination(context);
        if (center == null || destination == null) throw new IllegalStateException("Could not find a valid tornado path");
        spawnDebris(context);
        debug(context, "Spawned at " + coordinates(center) + "; destination: " + coordinates(destination));
    }

    @Override
    public void tick(GameEventContext context) {
        elapsedTicks += 2;
        move(context);
        applyPhysics(context);
        updateDebris(context);
        if (++renderTicks >= Math.max(1, context.definition().integer("visuals.render-interval-ticks", 4) / 2)) {
            renderTicks = 0;
            render(context);
        }
        if (context.definition().bool("visuals.sound.enabled", true)) playAmbientSound(context);
        launchCooldowns.entrySet().removeIf(entry -> entry.getValue() <= elapsedTicks);
        fallProtection.entrySet().removeIf(entry -> entry.getValue() <= elapsedTicks);
    }

    @Override
    public void stop(GameEventContext context) {
        for (Debris piece : debris) if (piece.display().isValid()) piece.display().remove();
        debris.clear();
        capturedUntil.clear();
        launchCooldowns.clear();
        fallProtection.clear();
        center = null;
        destination = null;
        debug(context, "Cleanup completed");
    }

    @Override
    public boolean protectsFallDamage(UUID playerId) {
        return fallProtection.getOrDefault(playerId, 0) > elapsedTicks;
    }

    private void move(GameEventContext context) {
        Vector direction = destination.toVector().subtract(center.toVector());
        direction.setY(0);
        double distance = direction.length();
        double threshold = context.definition().decimal("movement.destination-threshold", 1.5);
        if (distance <= threshold) {
            destination = selectDestination(context);
            debug(context, "New destination: " + coordinates(destination));
            return;
        }
        double speed = context.definition().decimal("movement.speed", 0.18);
        center.add(direction.normalize().multiply(Math.min(speed, distance)));
    }

    private void applyPhysics(GameEventContext context) {
        double influence = context.definition().decimal("physics.influence-radius", 20.0);
        double influenceSquared = influence * influence;
        double coreRadius = context.definition().decimal("physics.core-radius", 2.5);
        double maxHeight = context.definition().decimal("physics.max-player-height", 18.0);
        double pullStrength = context.definition().decimal("physics.pull-strength", 0.18);
        double pullMax = context.definition().decimal("physics.pull-max", 0.45);
        double spinStrength = context.definition().decimal("physics.spin-strength", 0.28);
        double spinMax = context.definition().decimal("physics.spin-max", 0.55);
        double liftStrength = context.definition().decimal("physics.lift-strength", 0.12);
        double liftMax = context.definition().decimal("physics.lift-max", 0.45);
        double maxVelocity = context.definition().decimal("physics.max-velocity", 2.4);

        for (Player player : context.livingPlayers()) {
            if (!player.getWorld().equals(center.getWorld())) continue;
            Location location = player.getLocation();
            if (capturedUntil.getOrDefault(player.getUniqueId(), 0) > elapsedTicks) {
                orbit(context, player, location, maxVelocity);
                continue;
            }
            if (capturedUntil.remove(player.getUniqueId()) != null) {
                launch(context, player, location, maxVelocity);
                continue;
            }
            double dx = center.getX() - location.getX();
            double dz = center.getZ() - location.getZ();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared > influenceSquared) continue;
            double distance = Math.sqrt(distanceSquared);
            Vector radial = distance == 0 ? randomHorizontal() : new Vector(dx / distance, 0, dz / distance);
            double closeness = 1.0 - Math.min(1.0, distance / influence);
            Vector tangent = new Vector(-radial.getZ(), 0, radial.getX());
            double pull = Math.min(pullMax, pullStrength * (0.35 + closeness));
            double spin = Math.min(spinMax, spinStrength * (0.35 + closeness));
            double lift = location.getY() - center.getY() < maxHeight ? Math.min(liftMax, liftStrength * closeness * closeness) : 0;
            Vector velocity = player.getVelocity().clone().add(radial.multiply(pull)).add(tangent.multiply(spin)).add(new Vector(0, lift, 0));
            player.setVelocity(clamp(velocity, maxVelocity));
            if (distance <= coreRadius && launchCooldowns.getOrDefault(player.getUniqueId(), 0) <= elapsedTicks) capture(context, player);
        }
    }

    private void capture(GameEventContext context, Player player) {
        int duration = Math.max(0, context.definition().integer("physics.capture-duration-ticks", 40));
        if (duration == 0) {
            launch(context, player, player.getLocation(), context.definition().decimal("physics.max-velocity", 2.4));
            return;
        }
        capturedUntil.put(player.getUniqueId(), elapsedTicks + duration);
        debug(context, "Player captured: " + player.getUniqueId());
    }

    private void orbit(GameEventContext context, Player player, Location location, double maxVelocity) {
        Vector towardCenter = new Vector(center.getX() - location.getX(), 0, center.getZ() - location.getZ());
        double distance = towardCenter.length();
        if (distance == 0) towardCenter = randomHorizontal();
        else towardCenter.normalize();
        Vector tangent = new Vector(-towardCenter.getZ(), 0, towardCenter.getX());
        double targetRadius = Math.max(0.5, context.definition().decimal("physics.orbit-radius", 2.5));
        double radiusCorrection = Math.max(-0.45, Math.min(0.45, (distance - targetRadius) * 0.22));
        double orbitSpeed = context.definition().decimal("physics.orbit-speed", 0.75);
        double orbitLift = context.definition().decimal("physics.orbit-lift", 0.18);
        Vector velocity = tangent.multiply(orbitSpeed).add(towardCenter.multiply(radiusCorrection)).add(new Vector(0, orbitLift, 0));
        player.setVelocity(clamp(velocity, maxVelocity));
    }

    private void launch(GameEventContext context, Player player, Location location, double maxVelocity) {
        Vector outward = new Vector(location.getX() - center.getX(), 0, location.getZ() - center.getZ());
        if (outward.lengthSquared() == 0) outward = randomHorizontal();
        double horizontal = context.definition().decimal("physics.launch-strength", 1.6);
        double vertical = context.definition().decimal("physics.launch-height", 1.0);
        Vector velocity = clamp(player.getVelocity().clone().add(outward.normalize().multiply(horizontal)).add(new Vector(0, vertical, 0)), maxVelocity);
        player.setVelocity(velocity);
        launchCooldowns.put(player.getUniqueId(), elapsedTicks + context.definition().integer("physics.launch-cooldown-ticks", 40));
        fallProtection.put(player.getUniqueId(), elapsedTicks + context.definition().integer("physics.fall-protection-ticks", 100));
        debug(context, "Player launched: " + player.getUniqueId());
    }

    private void render(GameEventContext context) {
        int layers = Math.max(1, context.definition().integer("size.layers", 16));
        int particles = Math.max(1, context.definition().integer("visuals.particles-per-layer", 8));
        double height = context.definition().decimal("size.height", 32.0);
        double baseRadius = context.definition().decimal("size.base-radius", 3.0);
        double topRadius = context.definition().decimal("size.top-radius", 12.0);
        double rotation = elapsedTicks * context.definition().decimal("visuals.rotation-speed", 0.06);
        for (int layer = 0; layer < layers; layer++) {
            double progress = layers == 1 ? 0 : (double) layer / (layers - 1);
            double radius = baseRadius + (topRadius - baseRadius) * progress;
            double y = center.getY() + height * progress;
            for (int particle = 0; particle < particles; particle++) {
                double angle = rotation + layer * 0.75 + particle * (Math.PI * 2 / particles) + (random.nextDouble() - 0.5) * 0.22;
                double irregularRadius = radius + (random.nextDouble() - 0.5) * 0.65;
                Location point = new Location(center.getWorld(), center.getX() + Math.cos(angle) * irregularRadius,
                        y + (random.nextDouble() - 0.5) * 0.35, center.getZ() + Math.sin(angle) * irregularRadius);
                center.getWorld().spawnParticle(layer % 3 == 0 ? Particle.ASH : Particle.CLOUD, point, 1, 0.08, 0.08, 0.08, 0.01);
            }
        }
    }

    private void spawnDebris(GameEventContext context) {
        if (!context.definition().bool("visuals.debris.enabled", true)) return;
        int amount = Math.max(0, context.definition().integer("visuals.debris.amount", 12));
        List<String> configuredMaterials = context.definition().settings() == null ? List.of()
                : context.definition().settings().getStringList("visuals.debris.materials");
        List<Material> materials = configuredMaterials.stream()
                .map(Material::matchMaterial).filter(material -> material != null && material.isBlock()).toList();
        if (materials.isEmpty()) materials = List.of(Material.OAK_LEAVES, Material.DIRT, Material.COBBLESTONE);
        double topRadius = context.definition().decimal("size.top-radius", 12.0);
        double height = context.definition().decimal("size.height", 32.0);
        for (int index = 0; index < amount; index++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 2.0 + random.nextDouble() * Math.max(1.0, topRadius - 2.0);
            double debrisHeight = 2.0 + random.nextDouble() * Math.max(1.0, height - 4.0);
            BlockDisplay display = center.getWorld().spawn(center, BlockDisplay.class);
            display.setBlock(Bukkit.createBlockData(materials.get(random.nextInt(materials.size()))));
            display.setGravity(false);
            display.setPersistent(false);
            debris.add(new Debris(display, angle, radius, debrisHeight, 0.08 + random.nextDouble() * 0.08, random.nextDouble() * Math.PI * 2));
        }
    }

    private void updateDebris(GameEventContext context) {
        for (Debris piece : debris) {
            if (!piece.display().isValid()) continue;
            piece.angle += piece.angularVelocity;
            double y = center.getY() + piece.height + Math.sin(elapsedTicks * 0.08 + piece.verticalOffset) * 0.8;
            piece.display().teleport(new Location(center.getWorld(), center.getX() + Math.cos(piece.angle) * piece.radius, y,
                    center.getZ() + Math.sin(piece.angle) * piece.radius));
        }
    }

    private void playAmbientSound(GameEventContext context) {
        int interval = Math.max(2, context.definition().integer("visuals.sound.interval-ticks", 30));
        if ((soundTicks += 2) < interval) return;
        soundTicks = 0;
        Sound sound = context.config().sound("events.tornado.visuals.sound.ambient", Sound.ENTITY_PHANTOM_FLAP);
        double radius = context.definition().decimal("physics.influence-radius", 20.0) * 1.5;
        for (Player player : context.participants()) {
            if (player.getWorld().equals(center.getWorld()) && player.getLocation().distanceSquared(center) <= radius * radius)
                player.playSound(player.getLocation(), sound, 0.7f, 0.65f);
        }
    }

    private Location selectSpawn(GameEventContext context) {
        int attempts = Math.max(1, context.definition().integer("spawn-attempts", 10));
        double minimumDistance = context.definition().decimal("minimum-spawn-distance", 10.0);
        Location best = null;
        double bestDistance = -1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            Location candidate = randomEdgePoint(context);
            double nearest = nearestPlayerDistanceSquared(context, candidate);
            if (nearest >= minimumDistance * minimumDistance) return candidate;
            if (nearest > bestDistance) {
                best = candidate;
                bestDistance = nearest;
            }
        }
        return best;
    }

    private Location selectDestination(GameEventContext context) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Location candidate = randomEdgePoint(context);
            if (center == null || candidate.distanceSquared(center) > 36) return candidate;
        }
        return randomEdgePoint(context);
    }

    private Location randomEdgePoint(GameEventContext context) {
        ArenaBounds bounds = context.arena().bounds();
        int margin = Math.max(1, (int) Math.ceil(context.definition().decimal("size.top-radius", 12.0)));
        int minX = bounds.minX() + margin;
        int maxX = bounds.maxX() - margin;
        int minZ = bounds.minZ() + margin;
        int maxZ = bounds.maxZ() - margin;
        boolean alongX = random.nextBoolean();
        int x = alongX ? (random.nextBoolean() ? minX : maxX) : random(minX, maxX);
        int z = alongX ? random(minZ, maxZ) : (random.nextBoolean() ? minZ : maxZ);
        double configuredY = context.definition().decimal("spawn-y", -1.0);
        double y = configuredY >= bounds.world().getMinHeight() && configuredY <= bounds.world().getMaxHeight()
                ? configuredY
                : bounds.world().getHighestBlockYAt(x, z) + 1;
        return new Location(bounds.world(), x + 0.5, y, z + 0.5);
    }

    private double nearestPlayerDistanceSquared(GameEventContext context, Location point) {
        return context.livingPlayers().stream().filter(player -> player.getWorld().equals(point.getWorld()))
                .mapToDouble(player -> player.getLocation().distanceSquared(point)).min().orElse(Double.MAX_VALUE);
    }

    private Vector clamp(Vector vector, double maximum) {
        return vector.lengthSquared() > maximum * maximum ? vector.normalize().multiply(maximum) : vector;
    }

    private Vector randomHorizontal() {
        double angle = random.nextDouble() * Math.PI * 2;
        return new Vector(Math.cos(angle), 0, Math.sin(angle));
    }

    private int random(int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    private String coordinates(Location location) {
        return String.format("%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ());
    }

    private void debug(GameEventContext context, String message) {
        if (context.config().eventDebug()) context.plugin().getLogger().info("[Tornado] " + message);
    }

    private static final class Debris {
        private final BlockDisplay display;
        private double angle;
        private final double radius;
        private final double height;
        private final double angularVelocity;
        private final double verticalOffset;

        private Debris(BlockDisplay display, double angle, double radius, double height, double angularVelocity, double verticalOffset) {
            this.display = display;
            this.angle = angle;
            this.radius = radius;
            this.height = height;
            this.angularVelocity = angularVelocity;
            this.verticalOffset = verticalOffset;
        }

        private BlockDisplay display() {
            return display;
        }
    }
}
