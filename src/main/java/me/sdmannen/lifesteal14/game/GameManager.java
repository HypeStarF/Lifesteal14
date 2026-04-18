package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class GameManager {

    private final JavaPlugin plugin;
    private final HeartManager heartManager;

    private GameState gameState;
    private boolean netherOpen;

    private BukkitTask graceTask;
    private BukkitTask netherTask;
    private BukkitTask revealTask;
    private BukkitTask revealCountdownTask;

    private BossBar revealBossBar;
    private int secondsUntilReveal;
    private int revealIntervalSeconds;

    public GameManager(JavaPlugin plugin, HeartManager heartManager) {
        this.plugin = plugin;
        this.heartManager = heartManager;
        this.gameState = GameState.WAITING;
        this.netherOpen = false;
    }

    public void startGame() {
        if (gameState != GameState.WAITING && gameState != GameState.ENDED) {
            return;
        }

        cancelScheduledTasks();
        removeBossBar();

        gameState = GameState.GRACE;
        netherOpen = false;

        Bukkit.broadcastMessage("§aSpelet har startat. Grace period är aktiv.");

        startGracePeriodTimer();
        startNetherTimer();
        startRevealSystem();
    }

    public void stopGame() {
        cancelScheduledTasks();
        removeBossBar();
        gameState = GameState.ENDED;
        Bukkit.broadcastMessage("§cSpelet har avslutats.");
    }

    public void shutdown() {
        cancelScheduledTasks();
        removeBossBar();
    }

    private void startGracePeriodTimer() {
        long ticks = 20L * 60L * 60L * plugin.getConfig().getLong("timers.grace-hours", 1L);

        graceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            graceTask = null;

            if (gameState == GameState.GRACE) {
                gameState = GameState.RUNNING;
                Bukkit.broadcastMessage("§cGrace period är slut. PvP är nu aktiverat.");
            }
        }, ticks);
    }

    private void startNetherTimer() {
        long ticks = 20L * 60L * 60L * 24L * plugin.getConfig().getLong("timers.nether-days", 8L);

        netherTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            netherTask = null;

            if (isActive()) {
                netherOpen = true;
                Bukkit.broadcastMessage("§6Nether är nu öppet.");
            }
        }, ticks);
    }

    private void startRevealSystem() {
        revealIntervalSeconds = plugin.getConfig().getInt("timers.reveal-interval-minutes", 60) * 60;
        secondsUntilReveal = revealIntervalSeconds;

        revealBossBar = Bukkit.createBossBar("§eReveal om " + formatTime(secondsUntilReveal), BarColor.YELLOW, BarStyle.SOLID);
        revealBossBar.setVisible(true);

        revealTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isActive()) {
                return;
            }

            doHourlyReveal();
            secondsUntilReveal = revealIntervalSeconds;
            updateRevealBar();
        }, revealIntervalSeconds * 20L, revealIntervalSeconds * 20L);

        revealCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isActive()) {
                return;
            }

            secondsUntilReveal = Math.max(0, secondsUntilReveal - 1);
            updateRevealBar();
        }, 20L, 20L);

        updateRevealBar();
    }

    private void doHourlyReveal() {
        Player leader = heartManager.getUniqueHighestHeartPlayerOnline();

        if (leader == null) {
            Bukkit.broadcastMessage("§7Ingen position avslöjas denna timme eftersom ledningen är delad.");
            return;
        }

        if (leader.getWorld().getEnvironment() == World.Environment.NETHER) {
            Bukkit.broadcastMessage("§6Ledaren är i Nether: §e" + leader.getName()
                    + " §7(" + leader.getLocation().getBlockX()
                    + ", " + leader.getLocation().getBlockY()
                    + ", " + leader.getLocation().getBlockZ() + ")");
            return;
        }

        Bukkit.broadcastMessage("§cLedaren avslöjas: §e" + leader.getName()
                + " §7(" + leader.getLocation().getBlockX()
                + ", " + leader.getLocation().getBlockY()
                + ", " + leader.getLocation().getBlockZ() + ")");
    }

    private void updateRevealBar() {
        if (revealBossBar == null) {
            return;
        }

        revealBossBar.removeAll();

        for (Player player : heartManager.getAliveOnlinePlayers()) {
            revealBossBar.addPlayer(player);
        }

        revealBossBar.setTitle("§eReveal om " + formatTime(secondsUntilReveal));

        if (revealIntervalSeconds <= 0) {
            revealBossBar.setProgress(1.0D);
            return;
        }

        double progress = Math.max(0.0D, Math.min(1.0D, (double) secondsUntilReveal / (double) revealIntervalSeconds));
        revealBossBar.setProgress(progress);
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void removeBossBar() {
        if (revealBossBar != null) {
            revealBossBar.removeAll();
            revealBossBar.setVisible(false);
            revealBossBar = null;
        }
    }

    private void cancelScheduledTasks() {
        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }

        if (netherTask != null) {
            netherTask.cancel();
            netherTask = null;
        }

        if (revealTask != null) {
            revealTask.cancel();
            revealTask = null;
        }

        if (revealCountdownTask != null) {
            revealCountdownTask.cancel();
            revealCountdownTask = null;
        }
    }

    public void checkWinCondition() {
        if (!isActive()) {
            return;
        }

        if (heartManager.getAlivePlayerCount() != 1) {
            return;
        }

        UUID winnerUuid = heartManager.getSingleRemainingPlayerUuid();
        if (winnerUuid == null) {
            return;
        }

        String winnerName = heartManager.getPlayerName(winnerUuid);

        cancelScheduledTasks();
        removeBossBar();
        gameState = GameState.ENDED;

        Bukkit.broadcastMessage("§6" + winnerName + " vann spelet!");
    }

    public GameState getGameState() {
        return gameState;
    }

    public boolean isGracePeriod() {
        return gameState == GameState.GRACE;
    }

    public boolean isRunning() {
        return gameState == GameState.RUNNING;
    }

    public boolean isActive() {
        return gameState == GameState.GRACE || gameState == GameState.RUNNING;
    }

    public boolean isNetherOpen() {
        return netherOpen;
    }
}