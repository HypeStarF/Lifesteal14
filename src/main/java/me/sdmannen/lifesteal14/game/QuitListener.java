package me.sdmannen.lifesteal14.game;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitListener implements Listener {

    private final HeartManager heartManager;

    public QuitListener(HeartManager heartManager) {
        this.heartManager = heartManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        heartManager.savePlayer(event.getPlayer());
    }
}