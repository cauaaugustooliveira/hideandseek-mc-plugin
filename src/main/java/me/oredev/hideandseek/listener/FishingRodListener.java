package me.oredev.hideandseek.listener;

import me.oredev.hideandseek.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public final class FishingRodListener implements Listener {
    private final GameManager games;

    public FishingRodListener(GameManager games) {
        this.games = games;
    }

    @EventHandler
    public void onCatch(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || !(event.getCaught() instanceof Player hider)) return;
        ItemStack hook = event.getPlayer().getInventory().getItemInMainHand();
        if (!games.canUseFishingRod(event.getPlayer(), hook) || !games.pullHider(event.getPlayer(), hider)) return;
        if (hook.getAmount() > 1) hook.setAmount(hook.getAmount() - 1);
        else event.getPlayer().getInventory().setItemInMainHand(null);
    }
}
