package me.oredev.hideandseek.command;

import me.oredev.hideandseek.arena.Arena;
import me.oredev.hideandseek.arena.ArenaManager;
import me.oredev.hideandseek.config.ConfigManager;
import me.oredev.hideandseek.config.MessagesConfig;
import me.oredev.hideandseek.game.GameManager;
import me.oredev.hideandseek.listener.BoundsSelectionManager;
import me.oredev.hideandseek.player.PlayerRole;
import me.oredev.hideandseek.ui.GameMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public final class HideAndSeekCommand implements CommandExecutor, TabCompleter {
    private final GameManager games;
    private final ArenaManager arenas;
    private final ConfigManager config;
    private final MessagesConfig messages;
    private final GameMenu menu;
    private final BoundsSelectionManager boundsSelection;

    public HideAndSeekCommand(GameManager games, ArenaManager arenas, ConfigManager config, MessagesConfig messages, GameMenu menu, BoundsSelectionManager boundsSelection) {
        this.games = games;
        this.arenas = arenas;
        this.config = config;
        this.messages = messages;
        this.menu = menu;
        this.boundsSelection = boundsSelection;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) menu.openMain(player);
            else sender.sendMessage(messages.get("commands.usage"));
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> menu(sender);
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "list" -> list(sender);
            case "admin" -> admin(sender, args);
            default -> false;
        };
    }

    private boolean menu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("error.player-only"));
            return true;
        }
        menu.openMain(player);
        return true;
    }

    private boolean join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("error.player-only"));
            return true;
        }
        if (!player.hasPermission("hideandseek.join")) {
            noPermission(sender);
            return true;
        }
        if (games.isPlaying(player) && !games.canJoinAnotherGame(player)) {
            player.sendMessage(messages.get("error.already-playing"));
            return true;
        }
        Optional<Arena> arena = args.length > 1 ? arenas.find(args[1]) : arenas.getArenas().size() == 1 ? arenas.getArenas().stream().findFirst() : Optional.empty();
        if (arena.isEmpty()) {
            player.sendMessage(args.length > 1 ? messages.get("error.no-arena") : messages.get("commands.select-arena"));
            return true;
        }
        if (!games.join(player, arena.get())) player.sendMessage(messages.get("commands.cannot-join"));
        return true;
    }

    private boolean leave(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        if (!games.isPlaying(player)) player.sendMessage(messages.get("error.not-playing"));
        else games.leave(player);
        return true;
    }

    private boolean list(CommandSender sender) {
        sender.sendMessage(messages.get("commands.list", Map.of("arenas", arenas.getArenas().isEmpty() ? messages.format("commands.no-arenas", Map.of()) : String.join(", ", arenas.getArenas().stream().map(Arena::name).toList()))));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hideandseek.admin")) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            sendAdminHelp(sender);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String name = args.length > 2 ? args[2] : null;
        if (action.equals("help")) {
            sendAdminHelp(sender);
            return true;
        }
        if (action.equals("reload")) {
            games.reload();
            sender.sendMessage(messages.get("commands.reloaded"));
            return true;
        }
        if (action.equals("create")) {
            if (!(sender instanceof Player player) || name == null) return usage(sender);
            if (arenas.find(name).isPresent()) {
                sender.sendMessage(messages.get("commands.arena-exists"));
                return true;
            }
            arenas.create(name, player.getLocation());
            sender.sendMessage(messages.get("commands.arena-created", Map.of("arena", name)));
            return true;
        }
        if (action.equals("setlobby")) {
            if (!(sender instanceof Player player)) return usage(sender);
            config.setGlobalLobby(player.getLocation());
            sender.sendMessage(messages.get("commands.lobby-updated"));
            return true;
        }
        if (action.equals("coins")) return coins(sender, args);
        if (action.equals("sethider") || action.equals("setseeker")) return setRole(sender, args, action);
        if (name == null || arenas.find(name).isEmpty()) {
            sender.sendMessage(messages.get("error.no-arena"));
            return true;
        }
        Arena arena = arenas.find(name).orElseThrow();
        if (action.equals("setbounds")) {
            if (!(sender instanceof Player player)) return usage(sender);
            boundsSelection.begin(player, arena);
            return true;
        }
        if (action.equals("event")) {
            if (args.length < 4) return usage(sender);
            boolean changed = args[3].equalsIgnoreCase("stop") ? games.stopEvent(arena) : games.startEvent(arena, args[3]);
            sender.sendMessage(messages.get(changed ? "commands.event-updated" : "commands.event-unavailable"));
            return true;
        }
        if (action.equals("delete")) {
            arenas.delete(name);
            sender.sendMessage(messages.get("commands.arena-deleted"));
            return true;
        }
        if (action.equals("save")) {
            arenas.save(arena);
            sender.sendMessage(messages.get("commands.arena-saved"));
            return true;
        }
        if (action.equals("forcestart")) {
            sender.sendMessage(messages.get(games.forceStart(arena) ? "commands.force-started" : "commands.no-players"));
            return true;
        }
        if (action.equals("stop")) {
            games.stop(arena);
            sender.sendMessage(messages.get("commands.stopped"));
            return true;
        }
        if (!(sender instanceof Player player)) return usage(sender);
        ArenaManager.SpawnType type = switch (action) {
            case "setjoinlocation" -> ArenaManager.SpawnType.JOIN_LOCATION;
            case "sethiderspawn" -> ArenaManager.SpawnType.HIDER;
            case "setseekerwaitspawn" -> ArenaManager.SpawnType.SEEKER_WAITING;
            case "setseekerspawn" -> ArenaManager.SpawnType.SEEKER;
            default -> null;
        };
        if (type == null) return usage(sender);
        arenas.updateSpawn(name, type, player.getLocation());
        sender.sendMessage(messages.get("commands.spawn-updated"));
        return true;
    }

    private boolean coins(CommandSender sender, String[] args) {
        if (args.length < 5) return usage(sender);
        GameManager.CoinOperation operation = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "set" -> GameManager.CoinOperation.SET;
            case "give" -> GameManager.CoinOperation.GIVE;
            case "remove" -> GameManager.CoinOperation.REMOVE;
            default -> null;
        };
        if (operation == null) return usage(sender);
        Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null) {
            sender.sendMessage(messages.get("error.player-not-found"));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[4]);
        } catch (NumberFormatException exception) {
            return usage(sender);
        }
        if (amount < 0) return usage(sender);
        OptionalInt updated = games.changeCoins(target, operation, amount);
        if (updated.isEmpty()) {
            sender.sendMessage(messages.get("commands.coins-unavailable"));
            return true;
        }
        sender.sendMessage(messages.get("commands.coins-updated", Map.of(
                "player", target.getName(), "coins", String.valueOf(updated.getAsInt())
        )));
        return true;
    }

    private boolean setRole(CommandSender sender, String[] args, String action) {
        if (args.length < 3) return usage(sender);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(messages.get("error.player-not-found"));
            return true;
        }
        PlayerRole role = action.equals("sethider") ? PlayerRole.HIDER : PlayerRole.SEEKER;
        if (!games.setRole(target, role)) {
            sender.sendMessage(messages.get("commands.role-unavailable"));
            return true;
        }
        sender.sendMessage(messages.get("commands.role-updated", Map.of(
                "player", target.getName(), "role", messages.format("roles." + role.name().toLowerCase(Locale.ROOT), Map.of())
        )));
        return true;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(messages.get("commands.invalid-usage"));
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        messages.list("commands.admin-help", Map.of()).forEach(sender::sendMessage);
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(messages.get("error.no-permission"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("menu", "join", "leave", "list", "admin"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("admin"))
            return filter(List.of("help", "reload", "create", "delete", "setlobby", "setjoinlocation", "sethiderspawn", "setseekerwaitspawn", "setseekerspawn", "setbounds", "event", "coins", "sethider", "setseeker", "save", "forcestart", "stop"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("coins"))
            return filter(List.of("set", "give", "remove"), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("coins"))
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("sethider") || args[1].equalsIgnoreCase("setseeker")))
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        if ((args.length == 2 && args[0].equalsIgnoreCase("join")) || (args.length == 3 && args[0].equalsIgnoreCase("admin") && !args[1].equalsIgnoreCase("setlobby")))
            return filter(arenas.getArenas().stream().map(Arena::name).toList(), args[args.length - 1]);
        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("event")) {
            List<String> options = new ArrayList<>(games.eventIds());
            options.add("stop");
            return filter(options, args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))).toList();
    }
}
