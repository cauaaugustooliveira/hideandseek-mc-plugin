package me.oredev.hideandseek.ui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import me.oredev.hideandseek.arena.Arena;
import me.oredev.hideandseek.arena.ArenaManager;
import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Triumph GUI menus for joining a random arena or choosing a specific map.
 */
public final class GameMenu {
    private final GameManager games;
    private final ArenaManager arenas;
    private final ConfigManager config;
    private final MessagesConfig messages;

    public GameMenu(GameManager games, ArenaManager arenas, ConfigManager config, MessagesConfig messages) {
        this.games = games;
        this.arenas = arenas;
        this.config = config;
        this.messages = messages;
    }

    public void openMain(Player player) {
        Gui gui = Gui.gui()
                .title(config.menuTitle("menus.main"))
                .rows(config.menuRows("menus.main"))
                .disableAllInteractions()
                .create();
        ConfigManager.MenuItem random = config.menuItem("menus.main.random", Map.of());
        ConfigManager.MenuItem maps = config.menuItem("menus.main.maps", Map.of());
        gui.setItem(random.slot(), ItemBuilder.from(random.material())
                .name(random.name())
                .lore(random.lore())
                        .flags(
                                ItemFlag.HIDE_ATTRIBUTES,
                                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                                ItemFlag.HIDE_ENCHANTS
                        )
                .asGuiItem(event -> joinRandom(player)));
        gui.setItem(maps.slot(), ItemBuilder.from(maps.material())
                .name(maps.name())
                .lore(maps.lore())
                .asGuiItem(event -> openMaps(player)));
        gui.open(player);
    }

    public void openMaps(Player player) {
        int rows = config.menuRows("menus.maps");
        List<Integer> arenaSlots = config.menuSlots("menus.maps.arena");
        PaginatedGui gui = Gui.paginated()
                .title(config.menuTitle("menus.maps"))
                .rows(rows)
                .pageSize(arenaSlots.size())
                .disableAllInteractions()
                .create();

        ConfigManager.MenuItem back = config.menuItem("menus.maps.back", Map.of());
        ConfigManager.MenuItem previous = config.menuItem("menus.maps.previous", Map.of());
        ConfigManager.MenuItem next = config.menuItem("menus.maps.next", Map.of());
        fillMapBackground(gui, rows, arenaSlots, back.slot(), previous.slot(), next.slot());

        List<Arena> available = availableArenas();
        if (available.isEmpty()) {
            ConfigManager.MenuItem empty = config.menuItem("menus.maps.empty", Map.of());
            gui.setItem(empty.slot(), ItemBuilder.from(empty.material())
                    .name(empty.name())
                    .lore(empty.lore())
                    .flags(
                            ItemFlag.HIDE_ATTRIBUTES,
                            ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                            ItemFlag.HIDE_ENCHANTS
                    )
                    .asGuiItem());
        } else {
            for (Arena arena : available) {
                Map<String, String> values = Map.of(
                        "arena", arena.name(),
                        "players", String.valueOf(games.playerCount(arena)),
                        "min_players", String.valueOf(arena.minPlayers()),
                        "max_players", String.valueOf(arena.maxPlayers())
                );
                ConfigManager.MenuItem arenaItem = config.menuItem("menus.maps.arena", values);
                gui.addItem(ItemBuilder.from(arenaItem.material())
                        .name(arenaItem.name())
                        .lore(arenaItem.lore())
                        .asGuiItem(event -> join(player, arena)));
            }
        }
        gui.setItem(back.slot(), ItemBuilder.from(back.material())
                .name(back.name())
                .lore(back.lore())
                .asGuiItem(event -> openMain(player)));
        updatePaginationButtons(gui, new int[]{1}, previous, next);
        gui.open(player);
    }

    private void updatePaginationButtons(PaginatedGui gui, int[] currentPage, ConfigManager.MenuItem previous, ConfigManager.MenuItem next) {
        if (currentPage[0] > 1) {
            gui.setItem(previous.slot(), ItemBuilder.from(previous.material())
                    .name(previous.name())
                    .lore(previous.lore())
                    .asGuiItem(event -> {
                        if (gui.previous()) {
                            currentPage[0]--;
                            updatePaginationButtons(gui, currentPage, previous, next);
                        }
                    }));
        } else gui.removeItem(previous.slot());

        if (currentPage[0] < gui.getPagesNum()) {
            gui.setItem(next.slot(), ItemBuilder.from(next.material())
                    .name(next.name())
                    .lore(next.lore())
                    .asGuiItem(event -> {
                        if (gui.next()) {
                            currentPage[0]++;
                            updatePaginationButtons(gui, currentPage, previous, next);
                        }
                    }));
        } else gui.removeItem(next.slot());
    }

    private void fillMapBackground(PaginatedGui gui, int rows, List<Integer> arenaSlots, int backSlot, int previousSlot, int nextSlot) {
        ConfigManager.MenuItem filler = config.menuItem("menus.maps.filler", Map.of());
        Set<Integer> reserved = new HashSet<>(arenaSlots);
        reserved.add(backSlot);
        reserved.add(previousSlot);
        reserved.add(nextSlot);
        for (int slot = 0; slot < rows * 9; slot++) {
            if (!reserved.contains(slot)) {
                if (filler.material().isAir()) {
                    gui.setItem(slot, new GuiItem(new ItemStack(Material.AIR)));
                } else {
                    gui.setItem(slot, ItemBuilder.from(filler.material()).name(filler.name()).lore(filler.lore()).asGuiItem());
                }
            }
        }
    }

    private void joinRandom(Player player) {
        List<Arena> available = new ArrayList<>(availableArenas());
        if (available.isEmpty()) {
            player.sendMessage(config.menuMessage("menus.messages.no-available-arena"));
            return;
        }
        Collections.shuffle(available);
        for (Arena arena : available) if (join(player, arena)) return;
        player.sendMessage(messages.get("commands.cannot-join"));
    }

    private boolean join(Player player, Arena arena) {
        if (games.isPlaying(player) && !games.canJoinAnotherGame(player)) {
            player.sendMessage(messages.get("error.already-playing"));
            return true;
        }
        if (!games.join(player, arena)) return false;
        player.closeInventory();
        return true;
    }

    private List<Arena> availableArenas() {
        return arenas.getArenas().stream().filter(Arena::isConfigured).toList();
    }
}
