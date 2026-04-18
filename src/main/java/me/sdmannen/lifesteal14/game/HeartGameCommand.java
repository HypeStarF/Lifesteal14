package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
        if (!sender.hasPermission("lifesteal14.admin")) {
            sender.sendMessage("§cDu har inte tillåtelse.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);

            case "start" -> {
                gameManager.startGame();
                scoreboardManager.updateAll();
                sender.sendMessage("§aStartade spelet.");
            }

            case "stop" -> {
                gameManager.stopGame();
                scoreboardManager.updateAll();
                sender.sendMessage("§cStoppade spelet.");
            }

            case "status" -> sendStatus(sender);

            case "leaderboard" -> sender.sendMessage(scoreboardManager.buildLeaderboardMessage());

            case "sidebar" -> {
                scoreboardManager.updateAll();
                sender.sendMessage("§aScoreboard uppdaterad för alla online-spelare.");
            }

            case "sethearts" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cAnvänd: /heartgame sethearts <player> <amount>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                Integer amount = parseInt(sender, args[2], "§cAntalet måste vara ett heltal.");
                if (amount == null) {
                    return true;
                }

                heartManager.setHearts(target, amount);
                scoreboardManager.updateAll();
                sender.sendMessage("§aSatte permanenta hjärtan för " + target.getName() + " till " + amount + ".");
            }

            case "settemp" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cAnvänd: /heartgame settemp <player> <amount>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                Integer amount = parseInt(sender, args[2], "§cAntalet måste vara ett heltal.");
                if (amount == null) {
                    return true;
                }

                heartManager.setTemporaryPveLoss(target, amount);
                scoreboardManager.updateAll();
                sender.sendMessage("§aSatte temporär PvE-loss för " + target.getName() + " till " + amount + ".");
            }

            case "hearts" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cAnvänd: /heartgame hearts <player>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                sendPlayerHearts(sender, target);
            }

            case "debug" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cAnvänd: /heartgame debug <player>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                sendPlayerDebug(sender, target);
            }

            case "sync" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cAnvänd: /heartgame sync <player>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                heartManager.syncPlayer(target);
                scoreboardManager.updateAll();
                sender.sendMessage("§aSynkade " + target.getName() + ".");
            }

            case "reload" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cAnvänd: /heartgame reload <player>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                heartManager.loadPlayer(target);
                scoreboardManager.updateAll();
                sender.sendMessage("§aLaddade om " + target.getName() + " från cache/datafil.");
            }

            case "saveall" -> {
                heartManager.saveAll();
                sender.sendMessage("§aSparade all spelar-data.");
            }

            case "restorepve" -> {
                int restored = heartManager.restoreAllTemporaryPveHearts();
                scoreboardManager.updateAll();
                sender.sendMessage("§aÅterställde PvE-hjärtan. Påverkade spelare: §f" + restored);
            }

            case "eliminate" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cAnvänd: /heartgame eliminate <player>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                heartManager.eliminate(target);
                scoreboardManager.updateAll();
                sender.sendMessage("§cEliminerade " + target.getName() + " permanent.");
            }

            case "revive" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cAnvänd: /heartgame revive <player> <hearts>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                Integer hearts = parseInt(sender, args[2], "§cHjärtan måste vara ett heltal.");
                if (hearts == null) {
                    return true;
                }

                heartManager.revivePlayer(target, hearts);
                scoreboardManager.updateAll();
                sender.sendMessage("§aÅterupplivade " + target.getName() + " med " + hearts + " hjärtan.");
            }

            case "killadd" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cAnvänd: /heartgame killadd <player> <amount>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                Integer amount = parseInt(sender, args[2], "§cAntalet måste vara ett heltal.");
                if (amount == null) {
                    return true;
                }

                heartManager.addKills(target, amount);
                scoreboardManager.updateAll();
                sender.sendMessage("§aLade till " + amount + " kills på " + target.getName() + ".");
            }

            case "tpcage" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cAnvänd: /heartgame tpcage <player>");
                    return true;
                }

                Player target = requireOnlinePlayer(sender, args[1]);
                if (target == null) {
                    return true;
                }

                gameManager.getLobbyCageManager().teleportToCage(target);
                sender.sendMessage("§aTeleportade " + target.getName() + " till glassburen.");
            }

            default -> sender.sendMessage("§cOkänt kommando. Kör /heartgame help");
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lHeartGame debug-kommandon");
        sender.sendMessage("§e/heartgame help");
        sender.sendMessage("§e/heartgame start");
        sender.sendMessage("§e/heartgame stop");
        sender.sendMessage("§e/heartgame status");
        sender.sendMessage("§e/heartgame leaderboard");
        sender.sendMessage("§e/heartgame sidebar");
        sender.sendMessage("§e/heartgame hearts <player>");
        sender.sendMessage("§e/heartgame debug <player>");
        sender.sendMessage("§e/heartgame sethearts <player> <amount>");
        sender.sendMessage("§e/heartgame settemp <player> <amount>");
        sender.sendMessage("§e/heartgame sync <player>");
        sender.sendMessage("§e/heartgame reload <player>");
        sender.sendMessage("§e/heartgame saveall");
        sender.sendMessage("§e/heartgame restorepve");
        sender.sendMessage("§e/heartgame eliminate <player>");
        sender.sendMessage("§e/heartgame revive <player> <hearts>");
        sender.sendMessage("§e/heartgame killadd <player> <amount>");
        sender.sendMessage("§e/heartgame tpcage <player>");
    }

    private void sendStatus(CommandSender sender) {
        LobbyCageManager cage = gameManager.getLobbyCageManager();

        sender.sendMessage("§6§lGame status");
        sender.sendMessage("§eState: §f" + gameManager.getGameState());
        sender.sendMessage("§eActive: §f" + gameManager.isActive());
        sender.sendMessage("§eGrace: §f" + gameManager.isGracePeriod());
        sender.sendMessage("§eRunning: §f" + gameManager.isRunning());
        sender.sendMessage("§eNether open: §f" + gameManager.isNetherOpen());
        sender.sendMessage("§eReveal display: §f" + gameManager.getRevealDisplay());
        sender.sendMessage("§eNether display: §f" + gameManager.getNetherDisplay());
        sender.sendMessage("§eGame ends display: §f" + gameManager.getGameEndDisplay());

        sender.sendMessage("§7");
        sender.sendMessage("§6§lTimers");
        sender.sendMessage("§eGrace seconds remaining: §f" + gameManager.getGraceSecondsRemaining());
        sender.sendMessage("§eReveal seconds remaining: §f" + gameManager.getSecondsUntilReveal());
        sender.sendMessage("§eReveal interval seconds: §f" + gameManager.getRevealIntervalSeconds());
        sender.sendMessage("§eNether seconds remaining: §f" + gameManager.getNetherSecondsRemaining());
        sender.sendMessage("§eGame end seconds remaining: §f" + gameManager.getGameEndSecondsRemaining());

        sender.sendMessage("§7");
        sender.sendMessage("§6§lPlayers");
        sender.sendMessage("§eAlive count: §f" + heartManager.getAlivePlayerCount());
        sender.sendMessage("§eKnown players: §f" + heartManager.getAllKnownPlayerUuids().size());

        sender.sendMessage("§7");
        sender.sendMessage("§6§lLobby cage");
        sender.sendMessage("§eCreated: §f" + cage.isCreated());
        sender.sendMessage("§eWorld: §f" + (cage.getWorld() != null ? cage.getWorld().getName() : "null"));
        sender.sendMessage("§eCenter: §f" + formatLocation(cage.getCenter()));
        sender.sendMessage("§eBounds: §f"
                + "X[" + cage.getMinX() + ".." + cage.getMaxX() + "] "
                + "Y[" + cage.getMinY() + ".." + cage.getMaxY() + "] "
                + "Z[" + cage.getMinZ() + ".." + cage.getMaxZ() + "]");
    }

    private void sendPlayerHearts(CommandSender sender, Player target) {
        sender.sendMessage("§6§lHjärtstatus: §f" + target.getName());
        sender.sendMessage("§eNuvarande hjärtan: §f" + heartManager.getHearts(target));
        sender.sendMessage("§ePermanenta hjärtan: §f" + heartManager.getPermanentHearts(target));
        sender.sendMessage("§eTemporär PvE-loss: §f" + heartManager.getTemporaryPveLoss(target));
        sender.sendMessage("§ePermanently eliminated: §f" + heartManager.isPermanentlyEliminated(target));
        sender.sendMessage("§eTemporarily knocked out: §f" + heartManager.isTemporarilyKnockedOut(target));
        sender.sendMessage("§eKills: §f" + heartManager.getKills(target));
    }

    private void sendPlayerDebug(CommandSender sender, Player target) {
        Location loc = target.getLocation();
        World world = loc.getWorld();

        sender.sendMessage("§6§lDebug: §f" + target.getName());
        sender.sendMessage("§eUUID: §f" + target.getUniqueId());
        sender.sendMessage("§eOnline: §f" + target.isOnline());
        sender.sendMessage("§eDead: §f" + target.isDead());
        sender.sendMessage("§eValid: §f" + target.isValid());
        sender.sendMessage("§eGameMode: §f" + target.getGameMode());
        sender.sendMessage("§eInvulnerable: §f" + target.isInvulnerable());
        sender.sendMessage("§eHealth: §f" + target.getHealth());
        sender.sendMessage("§eMax health attr: §f" +
                (target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                        ? target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue()
                        : "null"));
        sender.sendMessage("§eFood: §f" + target.getFoodLevel());
        sender.sendMessage("§eWalk speed: §f" + target.getWalkSpeed());
        sender.sendMessage("§eFly speed: §f" + target.getFlySpeed());
        sender.sendMessage("§eWorld: §f" + (world != null ? world.getName() : "null"));
        sender.sendMessage("§eDimension: §f" + (world != null ? world.getEnvironment() : "null"));
        sender.sendMessage("§eXYZ: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

        sendPlayerHearts(sender, target);
    }

    private Player requireOnlinePlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage("§cSpelaren är inte online: " + name);
        }
        return target;
    }

    private Integer parseInt(CommandSender sender, String raw, String errorMessage) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(errorMessage);
            return null;
        }
    }
    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }

        return location.getWorld().getName()
                + " "
                + location.getBlockX()
                + ", "
                + location.getBlockY()
                + ", "
                + location.getBlockZ();
    }
}