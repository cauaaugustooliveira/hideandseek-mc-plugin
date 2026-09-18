package me.oredev.hideandseek.event;

import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.game.GameSession;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class GameEventManager {
    private final JavaPlugin plugin;
    private final GameSession session;
    private final ConfigManager config;
    private final MessagesConfig messages;
    private final GameEventRegistry registry;
    private Phase phase = Phase.IDLE;
    private int remaining;
    private GameEventDefinition pending;
    private GameEvent active;
    private GameEventContext context;

    public GameEventManager(JavaPlugin plugin, GameSession session, ConfigManager config, MessagesConfig messages, GameEventRegistry registry) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.messages = messages;
        this.registry = registry;
    }

    public void beginSeeking() {
        stopForGameEnd();
        if (!config.eventsEnabled()) return;
        phase = Phase.COOLDOWN;
        remaining = config.eventStartDelay();
        debug("Next event attempt in " + remaining + "s");
    }

    public void tick() {
        if (!config.eventsEnabled()) {
            stopForGameEnd();
            return;
        }
        switch (phase) {
            case COOLDOWN -> {
                if (--remaining <= 0) selectNext();
            }
            case WARNING -> {
                if (--remaining <= 0) startPending();
                else notifyWarning(pending, remaining);
            }
            case ACTIVE -> {
                if (--remaining <= 0) finishActive(true);
            }
            case IDLE -> {
            }
        }
    }

    public void fastTick() {
        if (phase != Phase.ACTIVE || active == null) return;
        invoke("tick", () -> active.tick(context));
        if (active != null && active.isComplete()) finishActive(true);
    }

    public boolean forceStart(String eventId) {
        GameEvent event = registry.find(eventId).orElse(null);
        if (event == null) return false;
        stopCurrent(false);
        GameEventDefinition definition = config.eventDefinition(event.id());
        GameEventContext candidate = new GameEventContext(plugin, session, config, messages, definition);
        if (!canStart(event, candidate)) return false;
        pending = definition;
        start(event, candidate);
        return true;
    }

    public boolean stopCurrent(boolean announce) {
        boolean hadEvent = phase == Phase.WARNING || phase == Phase.ACTIVE;
        if (active != null) stopActive(announce);
        pending = null;
        if (phase != Phase.IDLE) {
            phase = Phase.COOLDOWN;
            remaining = randomInterval();
        }
        return hadEvent;
    }

    public void stopForGameEnd() {
        if (active != null) stopActive(false);
        pending = null;
        active = null;
        context = null;
        phase = Phase.IDLE;
        remaining = 0;
    }

    public boolean protectsFallDamage(java.util.UUID playerId) {
        return active != null && active.protectsFallDamage(playerId);
    }

    private void selectNext() {
        List<Candidate> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (GameEvent event : registry.events()) {
            GameEventDefinition definition = config.eventDefinition(event.id());
            if (!definition.enabled() || definition.weight() <= 0) continue;
            GameEventContext candidate = new GameEventContext(plugin, session, config, messages, definition);
            if (!canStart(event, candidate)) continue;
            candidates.add(new Candidate(event, definition));
            totalWeight += definition.weight();
        }
        if (totalWeight == 0) {
            scheduleCooldown();
            return;
        }
        int selected = ThreadLocalRandom.current().nextInt(totalWeight);
        Candidate chosen = candidates.getLast();
        for (Candidate candidate : candidates) {
            selected -= candidate.definition().weight();
            if (selected < 0) {
                chosen = candidate;
                break;
            }
        }
        pending = chosen.definition();
        if (pending.warningSeconds() <= 0) {
            start(chosen.event(), new GameEventContext(plugin, session, config, messages, pending));
            return;
        }
        phase = Phase.WARNING;
        remaining = pending.warningSeconds();
        announce("events.warning", pending);
        notifyWarning(chosen.event(), remaining);
        debug("Warning started for " + pending.id());
    }

    private void startPending() {
        if (pending == null) {
            scheduleCooldown();
            return;
        }
        GameEvent event = registry.find(pending.id()).orElse(null);
        if (event == null) {
            scheduleCooldown();
            return;
        }
        GameEventContext candidate = new GameEventContext(plugin, session, config, messages, pending);
        if (!canStart(event, candidate)) {
            scheduleCooldown();
            return;
        }
        start(event, candidate);
    }

    private void start(GameEvent event, GameEventContext candidate) {
        active = event.create();
        context = candidate;
        pending = null;
        phase = Phase.ACTIVE;
        remaining = candidate.definition().durationSeconds();
        invoke("start", () -> active.start(context));
        if (active == null) return;
        if (active.announcesStart()) announce("events.started", context.definition());
        debug("Event started: " + event.id());
        if (remaining <= 0) finishActive(true);
    }

    private void finishActive(boolean announce) {
        if (active == null) {
            scheduleCooldown();
            return;
        }
        GameEventDefinition definition = context.definition();
        boolean announcesFinish = active.announcesFinish();
        stopActive(false);
        if (announce && announcesFinish) announce("events.finished", definition);
        debug("Event finished: " + definition.id());
        scheduleCooldown();
    }

    private void stopActive(boolean announce) {
        GameEventDefinition definition = context.definition();
        GameEvent event = active;
        active = null;
        invoke("stop", () -> event.stop(context));
        context = null;
        if (announce && event.announcesFinish()) announce("events.finished", definition);
    }

    private void scheduleCooldown() {
        pending = null;
        active = null;
        context = null;
        phase = Phase.COOLDOWN;
        remaining = randomInterval();
        debug("Next event attempt in " + remaining + "s");
    }

    private boolean canStart(GameEvent event, GameEventContext candidate) {
        try {
            return event.canStart(candidate);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[Events] Event " + event.id() + " failed while checking availability", exception);
            return false;
        }
    }

    private void notifyWarning(GameEvent event, int secondsRemaining) {
        if (event == null || pending == null) return;
        try {
            event.warningTick(new GameEventContext(plugin, session, config, messages, pending), secondsRemaining);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[Events] Event " + event.id() + " failed during warning", exception);
        }
    }

    private void notifyWarning(GameEventDefinition definition, int secondsRemaining) {
        if (definition == null) return;
        registry.find(definition.id()).ifPresent(event -> notifyWarning(event, secondsRemaining));
    }

    private void invoke(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            String id = active == null ? "unknown" : active.id();
            plugin.getLogger().log(Level.WARNING, "[Events] Event " + id + " failed during " + operation, exception);
            if (active != null) finishActive(false);
        }
    }

    private void announce(String path, GameEventDefinition definition) {
        Map<String, String> values = Map.of(
                "event", messages.format("events.names." + definition.id(), Map.of()),
                "duration", String.valueOf(definition.durationSeconds()),
                "warning", String.valueOf(definition.warningSeconds()),
                "players", String.valueOf(session.players())
        );
        String chat = messages.format(path + ".chat", values);
        if (!chat.isEmpty()) for (Player player : session.onlinePlayers()) player.sendMessage(messages.get(path + ".chat", values));
        for (Player player : session.onlinePlayers()) {
            player.showTitle(Title.title(messages.component(path + ".title", values), messages.component(path + ".subtitle", values),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(250))));
            Sound sound = config.eventSound(path.substring("events.".length()));
            if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }

    private int randomInterval() {
        return ThreadLocalRandom.current().nextInt(config.eventIntervalMinimum(), config.eventIntervalMaximum() + 1);
    }

    private void debug(String message) {
        if (config.eventDebug()) plugin.getLogger().info("[Events] " + message);
    }

    private enum Phase {IDLE, WARNING, ACTIVE, COOLDOWN}

    private record Candidate(GameEvent event, GameEventDefinition definition) {
    }
}
