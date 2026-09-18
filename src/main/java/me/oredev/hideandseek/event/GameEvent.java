package me.oredev.hideandseek.event;

public interface GameEvent {
    String id();

    default GameEvent create() {
        return this;
    }

    default boolean canStart(GameEventContext context) {
        return true;
    }

    default void start(GameEventContext context) {
    }

    default void warningTick(GameEventContext context, int secondsRemaining) {
    }

    default void tick(GameEventContext context) {
    }

    default void stop(GameEventContext context) {
    }

    default boolean isComplete() {
        return false;
    }

    default boolean announcesLifecycle() {
        return true;
    }

    default boolean announcesStart() {
        return announcesLifecycle();
    }

    default boolean announcesFinish() {
        return announcesLifecycle();
    }

    default boolean protectsFallDamage(java.util.UUID playerId) {
        return false;
    }
}
