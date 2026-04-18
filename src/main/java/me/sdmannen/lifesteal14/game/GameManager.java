package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class GameManager {

    private static final long GAME_END_SECONDS = 14L * 24L * 60L * 60L;

    private final JavaPlugin plugin;
    private final HeartManager heartManager;
    private final LobbyCageManager lobbyCageManager;

    private GameState gameState;
    private boolean netherOpen;
    private String winnerName;

    private BukkitTask graceTask;
    private BukkitTask netherTask;
    private BukkitTask revealTask;
    private BukkitTask gameEndTask;

    private BossBar timerBossBar;

    private int secondsUntilReveal;
    private int revealIntervalSeconds;
    private int graceSecondsRemaining;
    private long netherSecondsRemaining;
    private long gameEndSecondsRemaining;

    public GameManager(JavaPlugin plugin, HeartManager heartManager) {
        this.plugin = plugin;
        this.heartManager = heartManager;
        this.lobbyCageManager = new LobbyCageManager(plugin);
        this.gameState = GameState.WAITING;
        this.netherOpen = false;
        this.winnerName = null;
    }

    public void initializeLobby() {
        if (!lobbyCageManager.isCreated()) {
            lobbyCageManager.createCage();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            prepareWaitingPlayer(player);
        }
    }

    public void handleJoin(Player player) {
        if (gameState == GameState.WAITING) {
            if (!lobbyCageManager.isCreated()) {
                lobbyCageManager.createCage();
            }
            prepareWaitingPlayer(player);
            return;
        }

        if (gameState == GameState.ENDED) {
            freezePlayer(player);
            if (winnerName != null) {
                player.sendTitle("§6§lVINNARE", "§f" + winnerName, 10, 80, 20);
            }
            return;
        }

        heartManager.loadPlayer(player);
    }

    private void prepareWaitingPlayer(Player player) {
        unfreezePlayer(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        lobbyCageManager.teleportToCage(player);
        heartManager.loadPlayer(player);
    }

    public void startGame() {
        if (gameState != GameState.WAITING) {
            return;
        }

        cancelScheduledTasks();
        removeBossBar();

        winnerName = null;
        gameState = GameState.GRACE;
        netherOpen = false;

        //graceSecondsRemaining = plugin.getConfig().getInt("timers.grace-hours", 1) * 60 * 60;
        graceSecondsRemaining = plugin.getConfig().getInt("timers.grace-hours", 1) * 60;
        revealIntervalSeconds = plugin.getConfig().getInt("timers.reveal-interval-minutes", 60) * 60;
        secondsUntilReveal = revealIntervalSeconds;
        netherSecondsRemaining = plugin.getConfig().getLong("timers.nether-days", 8L) * 24L * 60L * 60L;
        gameEndSecondsRemaining = GAME_END_SECONDS;

        lobbyCageManager.removeCage();

        for (Player player : Bukkit.getOnlinePlayers()) {
            unfreezePlayer(player);
            if (!heartManager.isEliminated(player)) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            player.setInvulnerable(false);
        }

        Bukkit.broadcastMessage("§aSpelet har startat. Grace period är aktiv.");

        startGracePeriodTimer();
        startNetherTimer();
        startGameEndTimer();
        startMainTimerLoop();
        createTimerBossBar();
        updateTimerBossBar();
    }

    public void stopGame() {
        cancelScheduledTasks();
        removeBossBar();
        gameState = GameState.ENDED;
        freezeAllPlayers();
        Bukkit.broadcastMessage("§cSpelet har avslutats.");
    }

    public void shutdown() {
        cancelScheduledTasks();
        removeBossBar();
    }

    private void startGracePeriodTimer() {
        long ticks = Math.max(1L, graceSecondsRemaining) * 20L;

        graceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            graceTask = null;

            if (gameState == GameState.GRACE) {
                graceSecondsRemaining = 0;
                gameState = GameState.RUNNING;
                secondsUntilReveal = revealIntervalSeconds;
                Bukkit.broadcastMessage("§cGrace period är slut. PvP är nu aktiverat.");
            }
        }, ticks);
    }

    private void startNetherTimer() {
        long ticks = Math.max(1L, netherSecondsRemaining) * 20L;

        netherTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            netherTask = null;

            if (isActive()) {
                netherSecondsRemaining = 0L;
                netherOpen = true;
                Bukkit.broadcastMessage("§6Nether är nu öppet.");
            }
        }, ticks);
    }

    private void startGameEndTimer() {
        long ticks = GAME_END_SECONDS * 20L;

        gameEndTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gameEndTask = null;

            if (!isActive()) {
                return;
            }

            gameEndSecondsRemaining = 0L;
            endGameNoWinner();
        }, ticks);
    }

    private void startMainTimerLoop() {
        revealTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameState == GameState.GRACE && graceSecondsRemaining > 0) {
                graceSecondsRemaining--;
            }

            if (!netherOpen && netherSecondsRemaining > 0) {
                netherSecondsRemaining--;
            }

            if (isActive() && gameEndSecondsRemaining > 0) {
                gameEndSecondsRemaining--;
            }

            if (gameState == GameState.RUNNING && canRunRevealCountdown()) {
                secondsUntilReveal = Math.max(0, secondsUntilReveal - 1);

                if (secondsUntilReveal <= 0) {
                    doHourlyReveal();
                    secondsUntilReveal = revealIntervalSeconds;
                }
            }

            updateTimerBossBar();
        }, 20L, 20L);
    }

    private void createTimerBossBar() {
        timerBossBar = Bukkit.createBossBar("§eGrace", BarColor.YELLOW, BarStyle.SOLID);
        timerBossBar.setVisible(true);
    }

    private void updateTimerBossBar() {
        if (timerBossBar == null) {
            return;
        }

        timerBossBar.removeAll();

        for (Player player : heartManager.getAliveOnlinePlayers()) {
            timerBossBar.addPlayer(player);
        }

        if (gameState == GameState.WAITING) {
            timerBossBar.setTitle("§7Väntar på start");
            timerBossBar.setProgress(1.0D);
            return;
        }

        if (gameState == GameState.ENDED) {
            timerBossBar.setTitle("§6Spelet är slut");
            timerBossBar.setProgress(1.0D);
            return;
        }

        if (gameState == GameState.GRACE) {
            timerBossBar.setColor(BarColor.YELLOW);
            timerBossBar.setTitle("§eGrace slutar om " + formatClock(graceSecondsRemaining));

            int totalGrace = plugin.getConfig().getInt("timers.grace-hours", 1) * 60 * 60;
            if (totalGrace <= 0) {
                timerBossBar.setProgress(1.0D);
            } else {
                double progress = Math.max(0.0D, Math.min(1.0D, (double) graceSecondsRemaining / (double) totalGrace));
                timerBossBar.setProgress(progress);
            }
            return;
        }

        Player leader = getOnlineHighestHeartLeader();
        timerBossBar.setColor(BarColor.YELLOW);

        if (leader == null) {
            UUID leaderUuid = heartManager.getUniqueHighestHeartPlayerUuid();

            if (leaderUuid == null) {
                timerBossBar.setTitle("§7Reveal pausad - delad ledning");
            } else {
                timerBossBar.setTitle("§7Reveal pausad - ledaren är offline");
            }

            timerBossBar.setProgress(1.0D);
            return;
        }

        timerBossBar.setTitle("§eReveal om " + formatClock(secondsUntilReveal) + " §7| §f" + leader.getName());

        if (revealIntervalSeconds <= 0) {
            timerBossBar.setProgress(1.0D);
            return;
        }

        double progress = Math.max(0.0D, Math.min(1.0D, (double) secondsUntilReveal / (double) revealIntervalSeconds));
        timerBossBar.setProgress(progress);
    }

    private Player getOnlineHighestHeartLeader() {
        UUID leaderUuid = heartManager.getUniqueHighestHeartPlayerUuid();
        if (leaderUuid == null) {
            return null;
        }

        Player player = Bukkit.getPlayer(leaderUuid);
        if (player == null || !player.isOnline()) {
            return null;
        }

        return player;
    }

    private boolean canRunRevealCountdown() {
        return getOnlineHighestHeartLeader() != null;
    }

    private void doHourlyReveal() {
        Player leader = getOnlineHighestHeartLeader();

        if (leader == null) {
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

    private void freezeAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            freezePlayer(player);
        }
    }

    private void freezePlayer(Player player) {
        player.setWalkSpeed(0.0F);
        player.setFlySpeed(0.0F);
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 250, false, false, false));
    }

    private void unfreezePlayer(Player player) {
        player.setWalkSpeed(0.2F);
        player.setFlySpeed(0.1F);
        player.setInvulnerable(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    private void removeBossBar() {
        if (timerBossBar != null) {
            timerBossBar.removeAll();
            timerBossBar.setVisible(false);
            timerBossBar = null;
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

        if (gameEndTask != null) {
            gameEndTask.cancel();
            gameEndTask = null;
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

        winnerName = heartManager.getPlayerName(winnerUuid);

        cancelScheduledTasks();
        removeBossBar();
        gameState = GameState.ENDED;

        Bukkit.broadcastMessage("§6" + winnerName + " vann spelet!");

        for (Player player : Bukkit.getOnlinePlayers()) {
            freezePlayer(player);
            player.sendTitle("§6§lVINNARE", "§f" + winnerName, 10, 100, 20);
        }
    }

    private void endGameNoWinner() {
        cancelScheduledTasks();
        removeBossBar();
        gameState = GameState.ENDED;
        winnerName = "Ingen vinnare";

        Bukkit.broadcastMessage("§cTiden är ute. Spelet är slut.");

        for (Player player : Bukkit.getOnlinePlayers()) {
            freezePlayer(player);
            player.sendTitle("§c§lGAME OVER", "§fIngen vinnare", 10, 100, 20);
        }
    }

    public String getRevealDisplay() {
        if (gameState == GameState.WAITING) {
            return "Ej startat";
        }

        if (gameState == GameState.GRACE) {
            return "Efter grace";
        }

        if (gameState == GameState.ENDED) {
            return "Av";
        }

        UUID leaderUuid = heartManager.getUniqueHighestHeartPlayerUuid();
        if (leaderUuid == null) {
            return "Delad";
        }

        Player onlineLeader = Bukkit.getPlayer(leaderUuid);
        if (onlineLeader == null || !onlineLeader.isOnline()) {
            return "Offline";
        }

        return formatClock(secondsUntilReveal);
    }

    public String getNetherDisplay() {
        if (gameState == GameState.WAITING) {
            return "Ej startat";
        }

        if (netherOpen) {
            return "Öppet";
        }

        if (gameState == GameState.ENDED) {
            return "Avslutat";
        }

        return formatLongTime(netherSecondsRemaining);
    }

    public String getGameEndDisplay() {
        if (gameState == GameState.WAITING) {
            return "14d";
        }

        if (gameState == GameState.ENDED) {
            return winnerName != null ? winnerName : "Avslutat";
        }

        return formatLongTime(gameEndSecondsRemaining);
    }

    private String formatClock(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatLongTime(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        }

        return String.format("%02d:%02d", hours, minutes);
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

    public LobbyCageManager getLobbyCageManager() {
        return lobbyCageManager;
    }
}