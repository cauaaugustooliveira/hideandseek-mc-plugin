package me.oredev.hideandseek.arena;

/**
 * Extension point for world-copy, snapshot, FAWE or SlimeWorld resets. The MVP changes no arena blocks.
 */
public interface ArenaResetter {
    void reset(Arena arena);
}
