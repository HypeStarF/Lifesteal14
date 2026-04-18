package me.sdmannen.lifesteal14.game;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final HeartManager heartManager;
    private final GameManager gameManager;

    public JoinListener(HeartManager heartManager, GameManager gameManager) {
        this.heartManager = heartManager;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        heartManager.ensurePlayerExists(event.getPlayer());
        gameManager.handleJoin(event.getPlayer());
    }
}