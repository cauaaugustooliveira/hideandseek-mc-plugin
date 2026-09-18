package me.oredev.hideandseek;

import me.oredev.hideandseek.arena.ArenaManager;
import me.oredev.hideandseek.arena.ArenaResetter;
import me.oredev.hideandseek.command.HideAndSeekCommand;
import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.game.GameManager;
import me.oredev.hideandseek.listener.DamageListener;
import me.oredev.hideandseek.listener.FishingRodListener;
import me.oredev.hideandseek.listener.InfiniteFireworkListener;
import me.oredev.hideandseek.listener.BoundsSelectionManager;
import me.oredev.hideandseek.listener.NextGameMenuListener;
import me.oredev.hideandseek.listener.PlayerListener;
import me.oredev.hideandseek.listener.ProtectionListener;
import me.oredev.hideandseek.player.PlayerManager;
import me.oredev.hideandseek.ui.GameUi;
import me.oredev.hideandseek.ui.GameMenu;
import me.oredev.hideandseek.ui.UtilityShop;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Hideandseek extends JavaPlugin {
    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        ConfigManager config = new ConfigManager(this);
        MessagesConfig messages = new MessagesConfig(this);
        ArenaManager arenas = new ArenaManager(this);
        ArenaResetter resetter = arena -> { /* Block changes are protected in the MVP; swap this with a snapshot/world reset implementation later. */ };
        PlayerManager players = new PlayerManager(this);
        gameManager = new GameManager(this, config, players, arenas,
                new GameUi(config, messages), messages, resetter);

        GameMenu menu = new GameMenu(gameManager, arenas, config, messages);
        UtilityShop utilityShop = new UtilityShop(gameManager, players, config, messages);
        BoundsSelectionManager boundsSelection = new BoundsSelectionManager(this, arenas, config, messages);
        HideAndSeekCommand command = new HideAndSeekCommand(gameManager, arenas, config, messages, menu, boundsSelection);
        PluginCommand registered = getCommand("hideandseek");
        if (registered == null) throw new IllegalStateException("hideandseek command missing from plugin.yml");
        registered.setExecutor(command);
        registered.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new DamageListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new NextGameMenuListener(players, menu, config), this);
        getServer().getPluginManager().registerEvents(boundsSelection, this);
        getServer().getPluginManager().registerEvents(utilityShop, this);
        getServer().getPluginManager().registerEvents(new InfiniteFireworkListener(gameManager, players), this);
        getServer().getPluginManager().registerEvents(new FishingRodListener(gameManager), this);
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.shutdown();
    }
}
