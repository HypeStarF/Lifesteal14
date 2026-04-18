package me.sdmannen.lifesteal14.game;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTagService {

    private final long assistWindowMillis;
    private final Map<UUID, CombatTag> lastDamagerMap = new HashMap<>();

    public CombatTagService(JavaPlugin plugin) {
        long seconds = plugin.getConfig().getLong("combat.assist-window-seconds", 15L);
        this.assistWindowMillis = Math.max(1L, seconds) * 1000L;
    }

    public void tag(Player victim, Player attacker) {
        if (victim == null || attacker == null) {
            return;
        }

        if (victim.getUniqueId().equals(attacker.getUniqueId())) {
            return;
        }

        lastDamagerMap.put(victim.getUniqueId(), new CombatTag(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    public UUID getRecentKiller(Player victim) {
        CombatTag tag = lastDamagerMap.get(victim.getUniqueId());
        if (tag == null) {
            return null;
        }

        long age = System.currentTimeMillis() - tag.timestamp;
        if (age > assistWindowMillis) {
            lastDamagerMap.remove(victim.getUniqueId());
            return null;
        }

        return tag.attackerUuid;
    }

    public void clear(Player victim) {
        lastDamagerMap.remove(victim.getUniqueId());
    }

    private static class CombatTag {
        private final UUID attackerUuid;
        private final long timestamp;

        private CombatTag(UUID attackerUuid, long timestamp) {
            this.attackerUuid = attackerUuid;
            this.timestamp = timestamp;
        }
    }
}