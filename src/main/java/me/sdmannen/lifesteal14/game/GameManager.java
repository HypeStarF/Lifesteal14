package me.sdmannen.lifesteal14.game;

import me.sdmannen.lifesteal14.data.GameDataStore;
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
    private final GameDataStore gameDataStore;

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

    public GameManager(JavaPlugin plugin, HeartManager heartManager, GameDataStore gameDataStore) {
        this.plugin = plugin;
        this.heartManager = heartManager;
        this.gameDataStore = gameDataStore;
        this.lobbyCageManager = new LobbyCageManager(plugin);
        this.gameState = GameState.WAITING;
        this.netherOpen = false;
        this.winnerName = null;
    }

    public void loadPersistentState() {
        try {
            this.gameState = GameState.valueOf(gameDataStore.getGameState());
        } catch (IllegalArgumentException ex) {
            this.gameState = GameState.WAITING;
        }

        this.netherOpen = gameDataStore.isNetherOpen();
        this.winnerName = gameDataStore.getWinnerName();
        this.secondsUntilReveal = gameDataStore.getSecondsUntilReveal();
        this.revealIntervalSeconds = gameDataStore.getRevealIntervalSeconds();
        this.graceSecondsRemaining = gameDataStore.getGraceSecondsRemaining();
        this.netherSecondsRemaining = gameDataStore.getNetherSecondsRemaining();
        this.gameEndSecondsRemaining = gameDataStore.getGameEndSecondsRemaining();

        String cageWorldName = gameDataStore.getCageWorld();
        if (cageWorldName != null) {
            World world = Bukkit.getWorld(cageWorldName);
            if (world != null) {
                lobbyCageManager.restoreState(
                        world,
                        gameDataStore.getCageMinX(),
                        gameDataStore.getCageMaxX(),
                        gameDataStore.getCageMinY(),
                        gameDataStore.getCageMaxY(),
                        gameDataStore.getCageMinZ(),
                        gameDataStore.getCageMaxZ(),
                        gameDataStore.getCageCenter(),
                        gameDataStore.isCageCreated()
                );
            }
        }
    }

    public void restoreAfterRestart() {
        switch (gameState) {
            case WAITING -> {
                if (!lobbyCageManager.isCreated()) {
                    lobbyCageManager.createCage();
                    saveState();
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    prepareWaitingPlayer(player);
                }
            }
            case GRACE, RUNNING -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    unfreezePlayer(player);
                    heartManager.loadPlayer(player);
                    if (!heartManager.isEliminated(player)) {
                        player.setInvulnerable(false);
                    }
                }

                createTimerBossBar();
                updateTimerBossBar();

                if (gameState == GameState.GRACE && graceSecondsRemaining > 0) {
                    startGracePeriodTimer();
                }

                if (!netherOpen && netherSecondsRemaining > 0) {
                    startNetherTimer();
                }

                if (gameEndSecondsRemaining > 0) {
                    startGameEndTimer();
                }

                startMainTimerLoop();
            }
            case ENDED -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    heartManager.loadPlayer(player);
                    freezePlayer(player);
                    if (winnerName != null) {
                        player.sendTitle("§6§lVINNARE", "§f" + winnerName, 10, 80, 20);
                    }
                }
            }
        }
    }

    public void initializeLobby() {
        if (!lobbyCageManager.isCreated()) {
            lobbyCageManager.createCage();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            prepareWaitingPlayer(player);
        }

        saveState();
    }

    public void handleJoin(Player player) {
        if (gameState == GameState.WAITING) {
            if (!lobbyCageManager.isCreated()) {
                lobbyCageManager.createCage();
                saveState();
            }
            prepareWaitingPlayer(player);
            return;
        }

        if (gameState == GameState.ENDED) {
            heartManager.loadPlayer(player);
            freezePlayer(player);
            if (winnerName != null) {
                player.sendTitle("§6§lVINNARE", "§f" + winnerName, 10, 80, 20);
            }
            return;
        }

        heartManager.loadPlayer(player);
        unfreezePlayer(player);
        if (!heartManager.isEliminated(player)) {
            player.setInvulnerable(false);
        }
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

        graceSecondsRemaining = plugin.getConfig().getInt("timers.grace-hours", 1) * 60 * 60;
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
        saveState();
    }

    public void stopGame() {
        cancelScheduledTasks();
        removeBossBar();
        gameState = GameState.ENDED;
        freezeAllPlayers();
        Bukkit.broadcastMessage("§cSpelet har avslutats.");
        saveState();
    }

    public void shutdown() {
        cancelScheduledTasks();
        removeBossBar();
        saveState();
    }

    public void saveState() {
        gameDataStore.setGameState(gameState.name());
        gameDataStore.setNetherOpen(netherOpen);
        gameDataStore.setWinnerName(winnerName);
        gameDataStore.setSecondsUntilReveal(secondsUntilReveal);
        gameDataStore.setRevealIntervalSeconds(revealIntervalSeconds);
        gameDataStore.setGraceSecondsRemaining(graceSecondsRemaining);
        gameDataStore.setNetherSecondsRemaining(netherSecondsRemaining);
        gameDataStore.setGameEndSecondsRemaining(gameEndSecondsRemaining);

        if (lobbyCageManager.getWorld() != null) {
            gameDataStore.setCageBounds(
                    lobbyCageManager.getWorld().getName(),
                    lobbyCageManager.getMinX(),
                    lobbyCageManager.getMaxX(),
                    lobbyCageManager.getMinY(),
                    lobbyCageManager.getMaxY(),
                    lobbyCageManager.getMinZ(),
                    lobbyCageManager.getMaxZ()
            );
        }

        gameDataStore.setCageCenter(lobbyCageManager.getCenter());
        gameDataStore.setCageCreated(lobbyCageManager.isCreated());
        gameDataStore.save();
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
                saveState();
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
                saveState();
            }
        }, ticks);
    }

    private void startGameEndTimer() {
        long ticks = Math.max(1L, gameEndSecondsRemaining) * 20L;

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
            saveState();
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

        saveState();
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

        saveState();
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

    public int getSecondsUntilReveal() {
        return secondsUntilReveal;
    }

    public int getRevealIntervalSeconds() {
        return revealIntervalSeconds;
    }

    public int getGraceSecondsRemaining() {
        return graceSecondsRemaining;
    }

    public long getNetherSecondsRemaining() {
        return netherSecondsRemaining;
    }

    public long getGameEndSecondsRemaining() {
        return gameEndSecondsRemaining;
    }
}