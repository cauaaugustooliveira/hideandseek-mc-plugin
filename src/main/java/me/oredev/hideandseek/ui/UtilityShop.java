package me.oredev.hideandseek.ui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.game.GameManager;
import me.oredev.hideandseek.player.PlayerManager;
import me.oredev.hideandseek.player.PlayerRole;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

public final class UtilityShop implements Listener {
    private final GameManager games;
    private final PlayerManager players;
    private final ConfigManager config;
    private final MessagesConfig messages;

    public UtilityShop(GameManager games, PlayerManager players, ConfigManager config, MessagesConfig messages) {
        this.games = games;
        this.players = players;
        this.config = config;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;
        if (players.isUtilityShopOpener(event.getItem())) {
            event.setCancelled(true);
            games.utilityRole(event.getPlayer()).ifPresent(role -> open(event.getPlayer(), role));
            return;
        }
        if (players.isBellUtilityItem(event.getItem())) {
            event.setCancelled(true);
            if (games.useBellUtility(event.getPlayer(), event.getItem())) {
                event.getPlayer().sendMessage(messages.get("utilities.bell-used", Map.of(
                        "seconds", String.valueOf(config.bellUtilityDuration())
                )));
            }
            return;
        }
        if (players.isKangarooUtilityItem(event.getItem())) {
            event.setCancelled(true);
            if (games.useKangarooUtility(event.getPlayer(), event.getItem())) {
                event.getPlayer().sendMessage(messages.get("utilities.kangaroo-used"));
            }
            return;
        }
        if (!players.isSpeedUtilityItem(event.getItem())) return;
        event.setCancelled(true);
        if (games.utilityRole(event.getPlayer()).orElse(null) != PlayerRole.HIDER) return;
        event.getPlayer().addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED,
                config.speedUtilityDuration() * 20,
                config.speedUtilityAmplifier(),
                true,
                true,
                true
        ));
        event.getItem().subtract(1);
        event.getPlayer().sendMessage(messages.get("utilities.speed-used", Map.of("seconds", String.valueOf(config.speedUtilityDuration()))));
    }

    public void open(org.bukkit.entity.Player player, PlayerRole role) {
        String roleKey = role.name().toLowerCase();
        Gui gui = Gui.gui()
                .title(config.menuTitle("utilities." + roleKey + ".menu"))
                .rows(config.menuRows("utilities." + roleKey + ".menu"))
                .disableAllInteractions()
                .create();
        ConfigManager.MenuItem utility = role == PlayerRole.HIDER ? config.speedUtilityItem() : config.fishingRodUtilityItem();
        ConfigManager.MenuItem bell = role == PlayerRole.SEEKER ? config.bellUtilityItem() : null;
        ConfigManager.MenuItem kangaroo = config.kangarooUtilityItem(roleKey);
        ConfigManager.MenuItem filler = config.utilityShopFiller(roleKey);
        ConfigManager.MenuItem close = config.utilityShopCloseItem(roleKey);
        int size = config.menuRows("utilities." + roleKey + ".menu") * 9;
        boolean utilityEnabled = !utility.material().isAir() && validSlot(utility.slot(), size);
        boolean bellEnabled = bell != null && !bell.material().isAir() && validSlot(bell.slot(), size);
        boolean kangarooEnabled = !kangaroo.material().isAir() && validSlot(kangaroo.slot(), size);
        boolean closeEnabled = !close.material().isAir() && validSlot(close.slot(), size);
        for (int slot = 0; slot < size; slot++) {
            if ((utilityEnabled && slot == utility.slot()) || (bellEnabled && slot == bell.slot()) || (kangarooEnabled && slot == kangaroo.slot())
                    || (closeEnabled && slot == close.slot()) || filler.material().isAir()) continue;
            gui.setItem(slot, ItemBuilder.from(filler.material()).name(filler.name()).lore(filler.lore()).asGuiItem());
        }
        if (utilityEnabled) {
            gui.setItem(utility.slot(), ItemBuilder.from(utility.material())
                    .name(utility.name())
                    .lore(utility.lore())
                    .asGuiItem(event -> {
                        if (role == PlayerRole.HIDER) buySpeed(player);
                        else buyFishingRod(player);
                    }));
        }
        if (bellEnabled) {
            gui.setItem(bell.slot(), ItemBuilder.from(bell.material())
                    .name(bell.name())
                    .lore(bell.lore())
                    .asGuiItem(event -> buyBell(player)));
        }
        if (kangarooEnabled) {
            gui.setItem(kangaroo.slot(), ItemBuilder.from(kangaroo.material())
                    .name(kangaroo.name())
                    .lore(kangaroo.lore())
                    .asGuiItem(event -> buyKangaroo(player, roleKey)));
        }
        if (closeEnabled) {
            gui.setItem(close.slot(), ItemBuilder.from(close.material())
                    .name(close.name())
                    .lore(close.lore())
                    .asGuiItem(event -> player.closeInventory()));
        }
        gui.open(player);
    }

    private boolean validSlot(int slot, int size) {
        return slot >= 0 && slot < size;
    }

    private void buySpeed(org.bukkit.entity.Player player) {
        GameManager.UtilityPurchaseResult result = games.buySpeedUtility(player);
        switch (result) {
            case SUCCESS -> player.sendMessage(messages.get("utilities.speed-purchased", Map.of(
                    "price", String.valueOf(config.speedUtilityPrice()),
                    "coins", String.valueOf(games.session(player).map(session -> session.coins(player.getUniqueId())).orElse(0))
            )));
            case NOT_ENOUGH_COINS -> player.sendMessage(messages.get("utilities.not-enough-coins", Map.of(
                    "price", String.valueOf(config.speedUtilityPrice()),
                    "coins", String.valueOf(games.session(player).map(session -> session.coins(player.getUniqueId())).orElse(0))
            )));
            case SPEED_SLOT_UNAVAILABLE -> player.sendMessage(messages.get("utilities.speed-slot-unavailable"));
            case FISHING_ROD_SLOT_UNAVAILABLE -> player.sendMessage(messages.get("utilities.fishing-rod-slot-unavailable"));
            case BELL_SLOT_UNAVAILABLE -> player.sendMessage(messages.get("utilities.bell-slot-unavailable"));
            case KANGAROO_SLOT_UNAVAILABLE -> player.sendMessage(messages.get("utilities.kangaroo-slot-unavailable"));
            case UNAVAILABLE -> player.sendMessage(messages.get("utilities.unavailable"));
        }
    }

    private void buyFishingRod(org.bukkit.entity.Player player) {
        GameManager.UtilityPurchaseResult result = games.buyFishingRodUtility(player);
        switch (result) {
            case SUCCESS -> player.sendMessage(messages.get("utilities.fishing-rod-purchased", Map.of(
                    "price", String.valueOf(config.fishingRodUtilityPrice()),
                    "coins", String.valueOf(games.session(player).map(session -> session.coins(player.getUniqueId())).orElse(0))
            )));
            case NOT_ENOUGH_COINS -> player.sendMessage(messages.get("utilities.not-enough-coins", Map.of(
                    "price", String.valueOf(config.fishingRodUtilityPrice()),
                    "coins", String.valueOf(games.session(player).map(session -> session.coins(player.getUniqueId())).orElse(0))
            )));
            case FISHING_ROD_SLOT_UNAVAILABLE -> player.sendMessage(messages.get("utilities.fishing-rod-slot-unavailable"));
            case SPEED_SLOT_UNAVAILABLE, BELL_SLOT_UNAVAILABLE, KANGAROO_SLOT_UNAVAILABLE, UNAVAILABLE -> player.sendMessage(messages.get("utilities.unavailable"));
        }
    }

    private void buyBell(org.bukkit.entity.Player player) {
        GameManager.UtilityPurchaseResult result = games.buyBellUtility(player);
        switch (result) {
            case SUCCESS -> player.sendMessage(messages.get("utilities.bell-purchased", Map.of(
                    "price", String.valueOf(config.bellUtilityPrice()),
                    "coins", String.valueOf(games.session(player).map(session -> session.coins(player.getUniqueId())).orElse(0))
            )));
            case NOT_ENOUGH_COINS -> player.sendMessage(messages.get("utilities.not-enough-coins", Map.of(
                    "price", String.valueOf(config.bellUtilityPrice()),
                    "coins", String.valueOf(games.session(player).map(session -> session.coins(player.getUniqueId())).orElse(0))
            )));
            case BELL_SLOT_UNAVAILABLE -> player.sendMessage(messages.get("utilities.bell-slot-unavailable"));
            case SPEED_SLOT_UNAVAILABLE, FISHING_ROD_SLOT_UNAVAILABLE, KANGAROO_SLOT_UNAVAILABLE, UNAVAILABLE -> player.sendMessage(messages.get("utilities.unavailable"));
        }
    }

    private void buyKangaroo(org.bukkit.entity.Player player, String role) {
        GameManager.UtilityPurchaseResult result = games.buyKangarooUtility(player);
        switch (result) {
            case SUCCESS -> player.sendMessage(messages.get("utilities.kangaroo-purchased", Map.of(
                    "price", String.valueOf(config.kangarooUtilityPrice(role)),
                    "coins", String.valueOf(games.session(player).map(session -> session.coins(player.getUniqueId())).orElse(0))
            )));
            case NOT_ENOUGH_COINS -> player.sendMessage(messages.get("utilities.not-enough-coins", Map.of(
                    "price", String.valueOf(config.kangarooUtilityPrice(role)),
                    "coins", String.valueOf(games.session(player).map(session -> session.coins(player.getUniqueId())).orElse(0))
            )));
            case KANGAROO_SLOT_UNAVAILABLE -> player.sendMessage(messages.get("utilities.kangaroo-slot-unavailable"));
            case SPEED_SLOT_UNAVAILABLE, FISHING_ROD_SLOT_UNAVAILABLE, BELL_SLOT_UNAVAILABLE, UNAVAILABLE -> player.sendMessage(messages.get("utilities.unavailable"));
        }
    }
}
