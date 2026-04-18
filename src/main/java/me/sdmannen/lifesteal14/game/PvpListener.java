package me.sdmannen.lifesteal14.game;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

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

        Player attacker = getResponsiblePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }

        if (gameManager.isGracePeriod()) {
            event.setCancelled(true);

            if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
                attacker.sendMessage("§cPvP är avstängt under grace period.");
            }
        }
    }

    private Player getResponsiblePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }

        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }

        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }

        return null;
    }
}