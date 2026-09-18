package me.oredev.hideandseek.event;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class GameEventRegistry {
    private final Map<String, GameEvent> events = new LinkedHashMap<>();

    public void register(GameEvent event) {
        events.put(event.id().toLowerCase(Locale.ROOT), event);
    }

    public Optional<GameEvent> find(String id) {
        return Optional.ofNullable(events.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<GameEvent> events() {
        return events.values();
    }
}
