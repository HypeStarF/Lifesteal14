package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HeartGameTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help",
            "start",
            "stop",
            "status",
            "debugglobal",
            "validate",
            "leaderboard",
            "sidebar",
            "hearts",
            "debug",
            "sethearts",
            "settemp",
            "sync",
            "reload",
            "saveall",
            "restorepve",
            "forcepveregen",
            "forcereveal",
            "forcegraceend",
            "settimer",
            "eliminate",
            "revive",
            "killadd",
            "tpcage"
    );

    private static final List<String> HEART_VALUES = List.of(
            "0", "1", "2", "3", "4", "5", "6", "8", "10", "12", "14", "16", "20"
    );

    private static final List<String> KILL_VALUES = List.of(
            "0", "1", "2", "3", "5", "10"
    );

    private static final List<String> TIMER_VALUES = List.of(
            "grace", "reveal", "revealinterval", "nether", "gameend"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lifesteal14.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterByPrefix(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            return switch (sub) {
                case "hearts", "debug", "sethearts", "settemp", "sync", "reload",
                     "eliminate", "revive", "killadd", "tpcage" -> onlinePlayerNames(args[1]);
                case "settimer" -> filterByPrefix(TIMER_VALUES, args[1]);
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "sethearts", "revive" -> filterByPrefix(HEART_VALUES, args[2]);
                case "settemp" -> filterByPrefix(List.of("0", "1", "2", "3", "4", "5"), args[2]);
                case "killadd" -> filterByPrefix(KILL_VALUES, args[2]);
                case "settimer" -> filterByPrefix(List.of("0", "1", "10", "60", "300", "600", "3600", "86400"), args[2]);
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames(String input) {
        List<String> names = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }

        return filterByPrefix(names, input);
    }

    private List<String> filterByPrefix(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();

        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }

        return matches;
    }
}