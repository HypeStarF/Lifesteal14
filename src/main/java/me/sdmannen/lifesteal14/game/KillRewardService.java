package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class KillRewardService {

    private final HeartManager heartManager;

    public KillRewardService(HeartManager heartManager) {
        this.heartManager = heartManager;
    }

    public void handlePlayerKill(Player dead, Player killer) {
        if (heartManager.isEliminated(dead)) {
            return;
        }

        heartManager.removeHearts(dead, 1);

        if (killer != null && !killer.getUniqueId().equals(dead.getUniqueId())) {
            heartManager.addHearts(killer, 1);
            killer.sendMessage("§aDu fick 1 hjärta för killen.");
        }

        int remainingHearts = heartManager.getHearts(dead);

        if (remainingHearts <= 0) {
            heartManager.eliminate(dead);
            Bukkit.broadcastMessage("§c" + dead.getName() + " är utslagen.");
        } else {
            dead.sendMessage("§cDu har nu " + remainingHearts + " hjärtan kvar.");
        }
    }
}