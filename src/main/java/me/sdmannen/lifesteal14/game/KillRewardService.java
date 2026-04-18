package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KillRewardService {

    private final JavaPlugin plugin;
    private final HeartManager heartManager;
    private final GameManager gameManager;

    private final Map<String, Long> recentKills = new HashMap<>();

    public KillRewardService(JavaPlugin plugin, HeartManager heartManager, GameManager gameManager) {
        this.plugin = plugin;
        this.heartManager = heartManager;
        this.gameManager = gameManager;
    }

    public void handlePlayerKill(Player dead, Player killer) {
        if (heartManager.isEliminated(dead)) {
            return;
        }

        int baseDeathLoss = plugin.getConfig().getInt("hearts.base-death-loss", 1);
        int baseKillerGain = plugin.getConfig().getInt("hearts.base-killer-gain", 1);
        int highestDeathBonus = plugin.getConfig().getInt("hearts.highest-death-bonus", 2);
        int lowestBonus = plugin.getConfig().getInt("hearts.lowest-bonus", 2);

        boolean deadWasUniqueHighest = heartManager.isUniqueHighest(dead);
        Player uniqueLowest = heartManager.getUniqueLowestHeartPlayerOnline(dead.getUniqueId());

        heartManager.removeHearts(dead, baseDeathLoss);

        boolean validKiller = killer != null && !killer.getUniqueId().equals(dead.getUniqueId());

        if (validKiller) {



                if (baseKillerGain > 0) {
                    heartManager.addHearts(killer, baseKillerGain);
                }

                if (deadWasUniqueHighest && highestDeathBonus > 0) {
                    heartManager.addHearts(killer, highestDeathBonus);
                    killer.sendMessage("§6Du fick +" + highestDeathBonus + " hjärtan eftersom ledaren dog.");
                }

                heartManager.addKill(killer);
                markKill(killer.getUniqueId(), dead.getUniqueId());
            } else {
                killer.sendMessage("§cAnti-farm: ingen bonus för att döda samma spelare igen så snart.");
            }


        if (uniqueLowest != null && lowestBonus > 0) {
            heartManager.addHearts(uniqueLowest, lowestBonus);
            uniqueLowest.sendMessage("§aDu fick +" + lowestBonus + " hjärtan eftersom du låg ensam sist.");
        }

        int remainingHearts = heartManager.getHearts(dead);

        if (remainingHearts <= 0) {
            heartManager.eliminate(dead);
            Bukkit.broadcastMessage("§c" + dead.getName() + " är utslagen.");
            gameManager.checkWinCondition();
        } else {
            dead.sendMessage("§cDu har nu " + remainingHearts + " hjärtan kvar.");
        }
    }



    private void markKill(UUID killer, UUID victim) {
        String key = killer + ":" + victim;
        recentKills.put(key, System.currentTimeMillis());
    }
}