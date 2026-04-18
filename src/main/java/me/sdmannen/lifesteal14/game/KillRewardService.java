package me.sdmannen.lifesteal14.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class KillRewardService {

    private final JavaPlugin plugin;
    private final HeartManager heartManager;
    private final GameManager gameManager;

    public KillRewardService(JavaPlugin plugin, HeartManager heartManager, GameManager gameManager) {
        this.plugin = plugin;
        this.heartManager = heartManager;
        this.gameManager = gameManager;
    }

    public void handlePlayerKill(Player dead, Player killer) {
        if (heartManager.isPermanentlyEliminated(dead)) {
            return;
        }

        int normalTransfer = plugin.getConfig().getInt("hearts.base-death-loss", 1);
        int boostedTransfer = plugin.getConfig().getInt("hearts.highest-death-bonus", 2);

        boolean validKiller = killer != null && !killer.getUniqueId().equals(dead.getUniqueId());

        if (validKiller) {
            boolean deadWasUniqueHighest = heartManager.isUniqueHighest(dead);

            UUID uniqueLowestUuid = heartManager.getUniqueLowestHeartPlayerUuid();
            boolean killerWasUniqueLowest = uniqueLowestUuid != null && uniqueLowestUuid.equals(killer.getUniqueId());

            int transferAmount = normalTransfer;

            if (deadWasUniqueHighest && killerWasUniqueLowest) {
                transferAmount = 3;
            } else if (deadWasUniqueHighest || killerWasUniqueLowest) {
                transferAmount = boostedTransfer;
            }

            heartManager.removeHearts(dead, transferAmount);
            heartManager.addHearts(killer, transferAmount);
            heartManager.addKill(killer);

            if (deadWasUniqueHighest && killerWasUniqueLowest) {
                killer.sendMessage("§dBoth special heart rules applied. You stole +" + transferAmount + " hearts.");
            } else if (deadWasUniqueHighest) {
                killer.sendMessage("§6You killed the unique heart leader and stole +" + transferAmount + " hearts.");
            } else if (killerWasUniqueLowest) {
                killer.sendMessage("§aYou were uniquely lowest on hearts and stole +" + transferAmount + " hearts.");
            }

            int remainingHearts = heartManager.getHearts(dead);

            if (remainingHearts <= 0) {
                heartManager.eliminate(dead);
                Bukkit.broadcastMessage("§c" + dead.getName() + " is permanently eliminated.");
                gameManager.checkWinCondition();
            } else {
                dead.sendMessage("§cYou now have " + remainingHearts + " hearts remaining.");
            }

            return;
        }

        heartManager.addTemporaryPveLoss(dead, normalTransfer);

        int remainingHearts = heartManager.getHearts(dead);

        if (remainingHearts <= 0) {
            Bukkit.broadcastMessage("§e" + dead.getName() + " is temporarily knocked out until the next PvE regeneration.");
            dead.sendMessage("§eYou were temporarily knocked out by PvE. Your PvE-lost hearts will return at the next global regeneration.");
        } else {
            dead.sendMessage("§eYou lost " + normalTransfer + " temporary PvE heart(s). You now have " + remainingHearts + " hearts remaining.");
        }
    }
}