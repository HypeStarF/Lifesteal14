package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HeartGameCommand implements CommandExecutor {

    private final GameManager gameManager;
    private final HeartManager heartManager;
    private final ScoreboardManager scoreboardManager;

    public HeartGameCommand(GameManager gameManager, HeartManager heartManager, ScoreboardManager scoreboardManager) {
        this.gameManager = gameManager;
        this.heartManager = heartManager;
        this.scoreboardManager = scoreboardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("§e/heartgame start");
            sender.sendMessage("§e/heartgame stop");
            sender.sendMessage("§e/heartgame status");
            sender.sendMessage("§e/heartgame leaderboard");
            sender.sendMessage("§e/heartgame sethearts <player> <amount>");
            sender.sendMessage("§e/heartgame hearts <player>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                gameManager.startGame();
                sender.sendMessage("§aStartade spelet.");
            }
            case "stop" -> {
                gameManager.stopGame();
                sender.sendMessage("§cStoppade spelet.");
            }
            case "status" -> {
                sender.sendMessage("§eGameState: " + gameManager.getGameState());
                sender.sendMessage("§eNether open: " + gameManager.isNetherOpen());
            }
            case "leaderboard" -> sender.sendMessage(scoreboardManager.buildLeaderboardMessage());
            case "sethearts" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cAnvänd: /heartgame sethearts <player> <amount>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cSpelaren är inte online.");
                    return true;
                }

                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cAntalet måste vara ett heltal.");
                    return true;
                }

                heartManager.setHearts(target, amount);
                scoreboardManager.updateAll();
                sender.sendMessage("§aSatte " + target.getName() + " till " + amount + " hjärtan.");
            }
            case "hearts" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cAnvänd: /heartgame hearts <player>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cSpelaren är inte online.");
                    return true;
                }

                sender.sendMessage("§e" + target.getName() + " har " + heartManager.getHearts(target) + " hjärtan.");
            }
            default -> sender.sendMessage("§cOkänt kommando.");
        }

        return true;
    }
}