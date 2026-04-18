package me.sdmannen.lifesteal14.game;

import me.sdmannen.lifesteal14.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScoreboardManager {

    private final Main plugin;
    private final HeartManager heartManager;

    public ScoreboardManager(Main plugin, HeartManager heartManager) {
        this.plugin = plugin;
        this.heartManager = heartManager;

        startUpdater();
    }

    private void startUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    public void shutdown() {
        // Ingen bossbar här längre
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateSidebar(player);
        }
    }

    public void updateSidebar(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("lifesteal", "dummy", "§6§lLifesteal");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        LeaderData highest = getHighestHearts();
        LeaderData mostKills = getMostKills();
        LeaderData lowest = getLowestHearts();

        setLine(objective, " ", 9);
        setLine(objective, "§eFlest hjärtan:", 8);
        setLine(objective, formatLeaderLine(highest, LeaderType.HEARTS), 7);

        setLine(objective, "  ", 6);
        setLine(objective, "§cFlest kills:", 5);
        setLine(objective, formatLeaderLine(mostKills, LeaderType.KILLS), 4);

        setLine(objective, "   ", 3);
        setLine(objective, "§bMinst hjärtan:", 2);
        setLine(objective, formatLeaderLine(lowest, LeaderType.HEARTS), 1);

        player.setScoreboard(scoreboard);
    }

    private void setLine(Objective objective, String text, int score) {
        String unique = text + "§" + Integer.toHexString(score);
        objective.getScore(unique).setScore(score);
    }

    private String formatLeaderLine(LeaderData data, LeaderType type) {
        if (data == null || data.players.isEmpty()) {
            return "§7Ingen";
        }

        if (data.players.size() > 1) {
            return "§7Delat";
        }

        String name = heartManager.getPlayerName(data.players.get(0));

        if (type == LeaderType.KILLS) {
            return "§f" + shorten(name) + " §7(" + data.value + ")";
        }

        return "§f" + shorten(name) + " §c(" + data.value + "❤)";
    }

    private String shorten(String input) {
        if (input.length() <= 16) {
            return input;
        }
        return input.substring(0, 16);
    }

    public String buildLeaderboardMessage() {
        StringBuilder sb = new StringBuilder();

        LeaderData highest = getHighestHearts();
        LeaderData mostKills = getMostKills();
        LeaderData lowest = getLowestHearts();

        sb.append("§6§lLeaderboard\n");
        sb.append("§eFlest hjärtan: ").append(formatLeaderboardEntry(highest, LeaderType.HEARTS)).append("\n");
        sb.append("§cFlest kills: ").append(formatLeaderboardEntry(mostKills, LeaderType.KILLS)).append("\n");
        sb.append("§bMinst hjärtan: ").append(formatLeaderboardEntry(lowest, LeaderType.HEARTS));

        return sb.toString();
    }

    private String formatLeaderboardEntry(LeaderData data, LeaderType type) {
        if (data == null || data.players.isEmpty()) {
            return "§7Ingen";
        }

        List<String> names = new ArrayList<>();
        for (UUID uuid : data.players) {
            names.add(heartManager.getPlayerName(uuid));
        }

        String joined = String.join("§7, §f", names);

        if (type == LeaderType.KILLS) {
            return "§f" + joined + " §7(" + data.value + ")";
        }

        return "§f" + joined + " §7(" + data.value + "❤)";
    }

    private LeaderData getHighestHearts() {
        List<UUID> all = heartManager.getAllKnownPlayerUuids();
        if (all.isEmpty()) {
            return null;
        }

        int best = Integer.MIN_VALUE;
        List<UUID> winners = new ArrayList<>();

        for (UUID uuid : all) {
            if (heartManager.isEliminated(uuid)) {
                continue;
            }

            int hearts = heartManager.getHearts(uuid);

            if (hearts > best) {
                best = hearts;
                winners.clear();
                winners.add(uuid);
            } else if (hearts == best) {
                winners.add(uuid);
            }
        }

        if (winners.isEmpty()) {
            return null;
        }

        return new LeaderData(best, winners);
    }

    private LeaderData getLowestHearts() {
        List<UUID> all = heartManager.getAllKnownPlayerUuids();
        if (all.isEmpty()) {
            return null;
        }

        int lowest = Integer.MAX_VALUE;
        List<UUID> losers = new ArrayList<>();

        for (UUID uuid : all) {
            if (heartManager.isEliminated(uuid)) {
                continue;
            }

            int hearts = heartManager.getHearts(uuid);

            if (hearts < lowest) {
                lowest = hearts;
                losers.clear();
                losers.add(uuid);
            } else if (hearts == lowest) {
                losers.add(uuid);
            }
        }

        if (losers.isEmpty()) {
            return null;
        }

        return new LeaderData(lowest, losers);
    }

    private LeaderData getMostKills() {
        List<UUID> all = heartManager.getAllKnownPlayerUuids();
        if (all.isEmpty()) {
            return null;
        }

        int best = Integer.MIN_VALUE;
        List<UUID> winners = new ArrayList<>();

        for (UUID uuid : all) {
            if (heartManager.isEliminated(uuid)) {
                continue;
            }

            int kills = heartManager.getKills(uuid);

            if (kills > best) {
                best = kills;
                winners.clear();
                winners.add(uuid);
            } else if (kills == best) {
                winners.add(uuid);
            }
        }

        if (winners.isEmpty()) {
            return null;
        }

        return new LeaderData(best, winners);
    }

    private enum LeaderType {
        HEARTS,
        KILLS
    }

    private static class LeaderData {
        private final int value;
        private final List<UUID> players;

        private LeaderData(int value, List<UUID> players) {
            this.value = value;
            this.players = players;
        }
    }
}