package me.sdmannen.lifesteal14.game;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final HeartManager heartManager;

    public JoinListener(HeartManager heartManager) {
        this.heartManager = heartManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        heartManager.loadPlayer(event.getPlayer());
    }
}