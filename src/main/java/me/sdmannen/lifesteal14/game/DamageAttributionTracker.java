package me.sdmannen.lifesteal14.game;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class DamageAttributionTracker implements Listener {

    private static final long ATTRIBUTION_WINDOW_MILLIS = 15_000L;

    private final GameManager gameManager;
    private final Map<UUID, DamageRecord> lastDamageMap = new HashMap<>();

    public DamageAttributionTracker(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamagedByEntity(EntityDamageByEntityEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = getResponsiblePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }

        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        long now = System.currentTimeMillis();
        lastDamageMap.put(
                victim.getUniqueId(),
                new DamageRecord(attacker.getUniqueId(), now + ATTRIBUTION_WINDOW_MILLIS)
        );

        cleanupExpired(now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        lastDamageMap.remove(uuid);

        Iterator<Map.Entry<UUID, DamageRecord>> iterator = lastDamageMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DamageRecord> entry = iterator.next();
            if (entry.getValue().attackerUuid.equals(uuid)) {
                iterator.remove();
            }
        }
    }

    public Player getAttributedKiller(Player victim) {
        long now = System.currentTimeMillis();
        DamageRecord record = lastDamageMap.get(victim.getUniqueId());

        if (record == null) {
            return null;
        }

        if (record.expiresAtMillis < now) {
            lastDamageMap.remove(victim.getUniqueId());
            return null;
        }

        Player attacker = victim.getServer().getPlayer(record.attackerUuid);
        if (attacker == null || !attacker.isOnline()) {
            return null;
        }

        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return null;
        }

        return attacker;
    }

    public void clearVictim(Player victim) {
        lastDamageMap.remove(victim.getUniqueId());
    }

    private void cleanupExpired(long now) {
        Iterator<Map.Entry<UUID, DamageRecord>> iterator = lastDamageMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DamageRecord> entry = iterator.next();
            if (entry.getValue().expiresAtMillis < now) {
                iterator.remove();
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

    private static class DamageRecord {
        private final UUID attackerUuid;
        private final long expiresAtMillis;

        private DamageRecord(UUID attackerUuid, long expiresAtMillis) {
            this.attackerUuid = attackerUuid;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}