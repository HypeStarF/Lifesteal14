package me.sdmannen.lifesteal14.game;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final GameManager gameManager;
    private final KillRewardService killRewardService;
    private final DamageAttributionTracker damageAttributionTracker;

    public PlayerDeathListener(
            GameManager gameManager,
            KillRewardService killRewardService,
            DamageAttributionTracker damageAttributionTracker
    ) {
        this.gameManager = gameManager;
        this.killRewardService = killRewardService;
        this.damageAttributionTracker = damageAttributionTracker;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameManager.isActive()) {
            return;
        }

        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        if (killer == null || killer.getUniqueId().equals(dead.getUniqueId())) {
            killer = damageAttributionTracker.getAttributedKiller(dead);
        }

        killRewardService.handlePlayerKill(dead, killer);
        damageAttributionTracker.clearVictim(dead);
    }
}