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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameManager {

    private static final long GAME_END_SECONDS = 14L * 24L * 60L * 60L;
    private static final long DAY_SECONDS = 24L * 60L * 60L;
    private static final int FINAL_DAY_REVEAL_INTERVAL_SECONDS = 10 * 60;

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

    private int autosaveTickCounter;

    public GameManager(JavaPlugin plugin, HeartManager heartManager, GameDataStore gameDataStore) {
        this.plugin = plugin;
        this.heartManager = heartManager;
        this.gameDataStore = gameDataStore;
        this.lobbyCageManager = new LobbyCageManager(plugin);
        this.gameState = GameState.WAITING;
        this.netherOpen = false;
        this.winnerName = null;
        this.autosaveTickCounter = 0;
    }

    public void loadPersistentState() {
        gameDataStore.reload();

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
                if (lobbyCageManager.isCreated() && lobbyCageManager.getWorld() != null) {
                    lobbyCageManager.rebuildCage();
                } else {
                    lobbyCageManager.createCage();
                    saveState();
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    prepareWaitingPlayer(player);
                }
            }

            case GRACE, RUNNING -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    heartManager.loadPlayer(player);
                    unfreezePlayer(player);

                    if (heartManager.isEliminated(player)) {
                        heartManager.syncPlayer(player);
                    } else {
                        player.setGameMode(GameMode.SURVIVAL);
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
        } else {
            lobbyCageManager.rebuildCage();
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
            } else {
                lobbyCageManager.rebuildCage();
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

        if (heartManager.isEliminated(player)) {
            heartManager.syncPlayer(player);
        } else {
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(false);
        }

        updateTimerBossBar();
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
        autosaveTickCounter = 0;

        graceSecondsRemaining = plugin.getConfig().getInt("timers.grace-hours", 1) * 60 * 60;
        revealIntervalSeconds = plugin.getConfig().getInt("timers.reveal-interval-minutes", 60) * 60;
        secondsUntilReveal = revealIntervalSeconds;
        netherSecondsRemaining = plugin.getConfig().getLong("timers.nether-days", 8L) * DAY_SECONDS;
        gameEndSecondsRemaining = GAME_END_SECONDS;

        lobbyCageManager.removeCage();

        for (Player player : Bukkit.getOnlinePlayers()) {
            unfreezePlayer(player);

            if (!heartManager.isEliminated(player)) {
                player.setGameMode(GameMode.SURVIVAL);
            }

            player.setInvulnerable(false);
        }

        if (plugin.getConfig().getBoolean("messages.broadcast-game-start", true)) {
            Bukkit.broadcastMessage("§aSpelet har startat. Grace period är aktiv.");
        }

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

        if (plugin.getConfig().getBoolean("messages.broadcast-game-stop", true)) {
            Bukkit.broadcastMessage("§cSpelet har avslutats.");
        }

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

    public void forceReveal() {
        if (gameState != GameState.RUNNING) {
            return;
        }

        doHourlyReveal();
        secondsUntilReveal = getCurrentRevealIntervalSeconds();
        updateTimerBossBar();
        saveState();
    }

    public int forcePveRegen() {
        int restored = heartManager.restoreAllTemporaryPveHearts();
        saveState();
        return restored;
    }

    public boolean setTimer(String timerName, long valueSeconds) {
        long clamped = Math.max(0L, valueSeconds);

        switch (timerName.toLowerCase()) {
            case "grace" -> graceSecondsRemaining = (int) Math.min(Integer.MAX_VALUE, clamped);
            case "reveal" -> secondsUntilReveal = (int) Math.min(Integer.MAX_VALUE, clamped);
            case "revealinterval" -> revealIntervalSeconds = (int) Math.min(Integer.MAX_VALUE, clamped);
            case "nether" -> netherSecondsRemaining = clamped;
            case "gameend" -> gameEndSecondsRemaining = clamped;
            default -> {
                return false;
            }
        }

        restartTimersFromCurrentState();
        updateTimerBossBar();
        saveState();
        return true;
    }

    private void restartTimersFromCurrentState() {
        cancelScheduledTasks();

        if (gameState == GameState.GRACE && graceSecondsRemaining > 0) {
            startGracePeriodTimer();
        }

        if (isActive() && !netherOpen && netherSecondsRemaining > 0) {
            startNetherTimer();
        }

        if (isActive() && gameEndSecondsRemaining > 0) {
            startGameEndTimer();
        }

        if (isActive()) {
            startMainTimerLoop();
        }
    }

    private void startGracePeriodTimer() {
        long ticks = Math.max(1L, graceSecondsRemaining) * 20L;

        graceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            graceTask = null;

            if (gameState == GameState.GRACE) {
                graceSecondsRemaining = 0;
                gameState = GameState.RUNNING;
                secondsUntilReveal = getCurrentRevealIntervalSeconds();

                if (plugin.getConfig().getBoolean("messages.broadcast-grace-end", true)) {
                    Bukkit.broadcastMessage("§cGrace period är slut. PvP är nu aktiverat.");
                }

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

                if (plugin.getConfig().getBoolean("messages.broadcast-nether-open", true)) {
                    Bukkit.broadcastMessage("§6Nether är nu öppet.");
                }

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
            endGameByTimeout();
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

            long previousGameEndSeconds = gameEndSecondsRemaining;

            if (isActive() && gameEndSecondsRemaining > 0) {
                gameEndSecondsRemaining--;
            }

            if (isActive()
                    && previousGameEndSeconds > gameEndSecondsRemaining
                    && gameEndSecondsRemaining > 0
                    && gameEndSecondsRemaining < GAME_END_SECONDS
                    && gameEndSecondsRemaining % DAY_SECONDS == 0) {

                int restoredPlayers = heartManager.restoreAllTemporaryPveHearts();

                if (plugin.getConfig().getBoolean("messages.broadcast-pve-regen", true)) {
                    if (restoredPlayers > 0) {
                        Bukkit.broadcastMessage("§aGlobal PvE-regeneration aktiverades. Alla temporärt förlorade PvE-hjärtan har återställts.");
                    } else {
                        Bukkit.broadcastMessage("§aGlobal PvE-regeneration aktiverades. Inga PvE-hjärtan behövde återställas.");
                    }
                }
            }

            if (gameState == GameState.RUNNING && canRunRevealCountdown()) {
                secondsUntilReveal = Math.max(0, secondsUntilReveal - 1);

                if (secondsUntilReveal <= 0) {
                    doHourlyReveal();
                    secondsUntilReveal = getCurrentRevealIntervalSeconds();
                }
            }

            updateTimerBossBar();

            autosaveTickCounter++;
            int autosaveSeconds = Math.max(1, plugin.getConfig().getInt("debug.autosave-seconds", 5));
            if (autosaveTickCounter >= autosaveSeconds) {
                autosaveTickCounter = 0;
                saveState();
            }
        }, 20L, 20L);
    }

    private void createTimerBossBar() {
        if (timerBossBar != null) {
            timerBossBar.removeAll();
            timerBossBar.setVisible(false);
        }

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
            timerBossBar.setColor(BarColor.YELLOW);
            timerBossBar.setTitle("§7Väntar på start");
            timerBossBar.setProgress(1.0D);
            return;
        }

        if (gameState == GameState.ENDED) {
            timerBossBar.setColor(BarColor.YELLOW);
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

        int currentRevealInterval = getCurrentRevealIntervalSeconds();

        if (currentRevealInterval <= 0) {
            timerBossBar.setProgress(1.0D);
            return;
        }

        double progress = Math.max(0.0D, Math.min(1.0D, (double) secondsUntilReveal / (double) currentRevealInterval));
        timerBossBar.setProgress(progress);
    }

    private Player getOnlineHighestHeartLeader() {
        UUID leaderUuid = heartManager.getUniqueHighestHeartPlayerUuid();
        if (leaderUuid == null) {
            return null;
        }

        Player player = Bukkit.getPlayer(leaderUuid);
        if (player == null || !player.isOnline() || heartManager.isEliminated(player)) {
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

        if (!plugin.getConfig().getBoolean("messages.broadcast-reveal", true)) {
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
    private void endGameByTimeout() {
        cancelScheduledTasks();
        removeBossBar();
        gameState = GameState.ENDED;

        UUID winnerUuid = resolveWinnerByHeartsThenKills();

        if (winnerUuid != null) {
            winnerName = heartManager.getPlayerName(winnerUuid);

            Bukkit.broadcastMessage("§6Tiden är ute. " + winnerName + " vinner spelet!");

            for (Player player : Bukkit.getOnlinePlayers()) {
                freezePlayer(player);
                player.sendTitle("§6§lVINNARE", "§f" + winnerName, 10, 100, 20);
            }
        } else {
            winnerName = "Ingen vinnare";

            Bukkit.broadcastMessage("§cTiden är ute. Spelet slutade oavgjort.");

            for (Player player : Bukkit.getOnlinePlayers()) {
                freezePlayer(player);
                player.sendTitle("§c§lGAME OVER", "§fOavgjort", 10, 100, 20);
            }
        }

        saveState();
    }

    private UUID resolveWinnerByHeartsThenKills() {
        List<UUID> candidates = new ArrayList<>();
        int bestHearts = Integer.MIN_VALUE;

        for (UUID uuid : heartManager.getAlivePlayerUuids()) {
            int hearts = heartManager.getHearts(uuid);

            if (hearts > bestHearts) {
                bestHearts = hearts;
                candidates.clear();
                candidates.add(uuid);
            } else if (hearts == bestHearts) {
                candidates.add(uuid);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        List<UUID> killWinners = new ArrayList<>();
        int bestKills = Integer.MIN_VALUE;

        for (UUID uuid : candidates) {
            int kills = heartManager.getKills(uuid);

            if (kills > bestKills) {
                bestKills = kills;
                killWinners.clear();
                killWinners.add(uuid);
            } else if (kills == bestKills) {
                killWinners.add(uuid);
            }
        }

        if (killWinners.size() == 1) {
            return killWinners.get(0);
        }

        return null;
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

        return formatLongTime(secondsUntilReveal);
    }

    public String getNextPveRegenDisplay() {
        if (gameState == GameState.WAITING) {
            return "Ej startat";
        }

        if (gameState == GameState.ENDED) {
            return "Av";
        }

        if (gameEndSecondsRemaining <= DAY_SECONDS) {
            return "Ingen";
        }

        long remainder = gameEndSecondsRemaining % DAY_SECONDS;
        long untilNext = remainder == 0L ? DAY_SECONDS : remainder;

        return formatLongTime(untilNext);
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
    private int getCurrentRevealIntervalSeconds() {
        if (gameEndSecondsRemaining > 0 && gameEndSecondsRemaining <= DAY_SECONDS) {
            return FINAL_DAY_REVEAL_INTERVAL_SECONDS;
        }

        return Math.max(1, revealIntervalSeconds);
    }

    private String formatClock(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

//    private String formatLongTime(long totalSeconds) {
//        long days = totalSeconds / 86400;
//        long hours = (totalSeconds % 86400) / 3600;
//        long minutes = (totalSeconds % 3600) / 60;
//
//        if (days > 0) {
//            return days + "d " + hours + "h";
//        }
//
//        return String.format("%02d:%02d", hours, minutes);
//    }
    private String formatLongTime(long totalSeconds) {
        long clamped = Math.max(0L, totalSeconds);

        long days = clamped / 86400;
        long hours = (clamped % 86400) / 3600;
        long minutes = (clamped % 3600) / 60;
        long seconds = clamped % 60;

        // Fall 1: Har dagar → visa D/H/M
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }

        // Fall 2: Har timmar → visa H/M/S
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }

        // Fall 3: Under 1 timme → visa M/S
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }

        // Fall 4: Bara sekunder
        return seconds + "s";
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

    public String getWinnerName() {
        return winnerName;
    }
}