package me.sdmannen.lifesteal14.game;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;


public class PlayerDeathListener implements Listener {

    private final GameManager gameManager;
    private final KillRewardService killRewardService;

    public PlayerDeathListener(GameManager gameManager, KillRewardService killRewardService) {
        this.gameManager = gameManager;
        this.killRewardService = killRewardService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        killRewardService.handlePlayerKill(dead, killer);
    }
}