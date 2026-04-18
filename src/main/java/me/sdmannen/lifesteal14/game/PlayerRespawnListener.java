package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerRespawnListener implements Listener {

    private final JavaPlugin plugin;
    private final HeartManager heartManager;

    public PlayerRespawnListener(JavaPlugin plugin, HeartManager heartManager) {
        this.plugin = plugin;
        this.heartManager = heartManager;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            heartManager.syncPlayer(event.getPlayer());
        }, 1L);
    }
}