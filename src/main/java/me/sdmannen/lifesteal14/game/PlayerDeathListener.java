package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

public class PlayerDeathListener implements Listener {

    private final GameManager gameManager;
    private final KillRewardService killRewardService;
    private final CombatTagService combatTagService;

    public PlayerDeathListener(GameManager gameManager, KillRewardService killRewardService, CombatTagService combatTagService) {
        this.gameManager = gameManager;
        this.killRewardService = killRewardService;
        this.combatTagService = combatTagService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameManager.isActive()) {
            combatTagService.clear(event.getEntity());
            return;
        }

        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        if (killer == null) {
            UUID recentKillerUuid = combatTagService.getRecentKiller(dead);
            if (recentKillerUuid != null) {
                killer = Bukkit.getPlayer(recentKillerUuid);
            }
        }

        killRewardService.handlePlayerKill(dead, killer);
        combatTagService.clear(dead);
    }
}