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

    private final JavaPlugin plugin;
    private final HeartManager heartManager;
    private final LobbyCageManager lobbyCageManager;

    private GameState gameState;
    private boolean netherOpen;
    private String winnerName;

    private BukkitTask graceTask;
    private BukkitTask netherTask;
    private BukkitTask revealTask;

    private BossBar revealBossBar;
    private int secondsUntilReveal;
    private int revealIntervalSeconds;
    private int graceSecondsRemaining;
    private long netherSecondsRemaining;

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
        startRevealSystem();
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
        graceSecondsRemaining = plugin.getConfig().getInt("timers.grace-hours", 1) * 60 * 60;
        long ticks = graceSecondsRemaining * 20L;

        graceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            graceTask = null;

            if (gameState == GameState.GRACE) {
                graceSecondsRemaining = 0;
                gameState = GameState.RUNNING;
                Bukkit.broadcastMessage("§cGrace period är slut. PvP är nu aktiverat.");
            }
        }, ticks);
    }

    private void startNetherTimer() {
        netherSecondsRemaining = plugin.getConfig().getLong("timers.nether-days", 8L) * 24L * 60L * 60L;
        long ticks = netherSecondsRemaining * 20L;

        netherTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            netherTask = null;

            if (isActive()) {
                netherSecondsRemaining = 0L;
                netherOpen = true;
                Bukkit.broadcastMessage("§6Nether är nu öppet.");
            }
        }, ticks);
    }

    private void startRevealSystem() {
        revealIntervalSeconds = plugin.getConfig().getInt("timers.reveal-interval-minutes", 60) * 60;
        secondsUntilReveal = revealIntervalSeconds;

        revealBossBar = Bukkit.createBossBar("§eReveal pausad", BarColor.YELLOW, BarStyle.SOLID);
        revealBossBar.setVisible(true);

        revealTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameState == GameState.GRACE && graceSecondsRemaining > 0) {
                graceSecondsRemaining--;
            }

            if (!netherOpen && netherSecondsRemaining > 0) {
                netherSecondsRemaining--;
            }

            if (!isActive()) {
                updateRevealBar();
                return;
            }

            if (canRunRevealCountdown()) {
                secondsUntilReveal = Math.max(0, secondsUntilReveal - 1);

                if (secondsUntilReveal <= 0) {
                    doHourlyReveal();
                    secondsUntilReveal = revealIntervalSeconds;
                }
            }

            updateRevealBar();
        }, 20L, 20L);

        updateRevealBar();
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
        return isActive() && getOnlineHighestHeartLeader() != null;
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

    private void updateRevealBar() {
        if (revealBossBar == null) {
            return;
        }

        revealBossBar.removeAll();

        for (Player player : heartManager.getAliveOnlinePlayers()) {
            revealBossBar.addPlayer(player);
        }

        if (!isActive()) {
            revealBossBar.setTitle("§7Ingen aktiv match");
            revealBossBar.setProgress(1.0D);
            return;
        }

        Player leader = getOnlineHighestHeartLeader();

        if (leader == null) {
            UUID leaderUuid = heartManager.getUniqueHighestHeartPlayerUuid();

            if (leaderUuid == null) {
                revealBossBar.setTitle("§7Reveal pausad - delad ledning");
            } else {
                revealBossBar.setTitle("§7Reveal pausad - ledaren är offline");
            }

            revealBossBar.setProgress(1.0D);
            return;
        }

        revealBossBar.setTitle("§eReveal om " + formatClock(secondsUntilReveal) + " §7| §f" + leader.getName());

        if (revealIntervalSeconds <= 0) {
            revealBossBar.setProgress(1.0D);
            return;
        }

        double progress = Math.max(0.0D, Math.min(1.0D, (double) secondsUntilReveal / (double) revealIntervalSeconds));
        revealBossBar.setProgress(progress);
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

    public String getGraceDisplay() {
        return switch (gameState) {
            case WAITING -> "Väntar";
            case ENDED -> "Avslutat";
            case RUNNING -> "Slut";
            case GRACE -> formatClock(Math.max(0, graceSecondsRemaining));
        };
    }

    public String getRevealDisplay() {
        if (!isActive()) {
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
        if (gameState == GameState.ENDED) {
            return winnerName != null ? winnerName : "Avslutat";
        }

        if (isActive()) {
            return "1 kvar";
        }

        return "Ej startat";
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