package me.sdmannen.lifesteal14.game;


import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;

public class PvpListener implements Listener {

    private final GameManager gameManager;

    public PvpListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (gameManager.isGracePeriod()) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP är avstängt under grace period.");
        }
    }
}