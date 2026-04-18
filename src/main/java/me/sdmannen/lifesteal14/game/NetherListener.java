package me.sdmannen.lifesteal14.game;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

public class NetherListener implements Listener {

    private final GameManager gameManager;

    public NetherListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }

        if (event.getTo().getWorld().getEnvironment() != World.Environment.NETHER) {
            return;
        }

        if (gameManager.isNetherOpen()) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage("§cNether är låst tills dag 8.");
    }
}