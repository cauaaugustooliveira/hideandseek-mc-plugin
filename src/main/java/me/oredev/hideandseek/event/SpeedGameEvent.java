package me.oredev.hideandseek.event;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpeedGameEvent implements GameEvent {
    private final Map<UUID, PotionEffect> previousEffects = new HashMap<>();

    @Override
    public String id() {
        return "speed";
    }

    @Override
    public GameEvent create() {
        return new SpeedGameEvent();
    }

    @Override
    public void start(GameEventContext context) {
        int amplifier = Math.max(0, context.definition().integer("amplifier", 1));
        int duration = Math.max(1, context.definition().durationSeconds()) * 20;
        for (Player player : context.livingPlayers()) {
            PotionEffect previous = player.getPotionEffect(PotionEffectType.SPEED);
            if (previous != null) previousEffects.put(player.getUniqueId(), previous);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier, true, true, true));
        }
    }

    @Override
    public void stop(GameEventContext context) {
        for (Player player : context.participants()) {
            player.removePotionEffect(PotionEffectType.SPEED);
            PotionEffect previous = previousEffects.remove(player.getUniqueId());
            if (previous != null) player.addPotionEffect(previous);
        }
        previousEffects.clear();
    }
}
