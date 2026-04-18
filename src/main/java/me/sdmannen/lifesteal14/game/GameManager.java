package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class GameManager {

    private final JavaPlugin plugin;
    private final HeartManager heartManager;

    private GameState gameState;
    private boolean netherOpen;

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

        gameState = GameState.GRACE;
        netherOpen = false;

        Bukkit.broadcastMessage("§aSpelet har startat. Grace period är aktiv.");

        startGracePeriodTimer();
        startNetherTimer();
    }

    public void stopGame() {
        gameState = GameState.ENDED;
        Bukkit.broadcastMessage("§cSpelet har avslutats.");
    }

    private void startGracePeriodTimer() {
        long ticks = 20L * 60L * 60L; // 1 timme

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (gameState == GameState.GRACE) {
                gameState = GameState.RUNNING;
                Bukkit.broadcastMessage("§cGrace period är slut. PvP är nu aktiverat.");
            }
        }, ticks);
    }

    private void startNetherTimer() {
        long ticks = 20L * 60L * 60L * 24L * 8L; // 8 dagar

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            netherOpen = true;
            Bukkit.broadcastMessage("§6Nether är nu öppet.");
        }, ticks);
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

    public boolean isNetherOpen() {
        return netherOpen;
    }
}
