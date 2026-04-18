package me.sdmannen.lifesteal14.game;

import me.sdmannen.lifesteal14.data.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartManager {

    private static final int DEFAULT_HEARTS = 10;
    private static final int MIN_HEARTS = 0;

    private final PlayerDataStore dataStore;

    private final Map<UUID, Integer> heartsCache = new HashMap<>();
    private final Map<UUID, Boolean> eliminatedCache = new HashMap<>();
    private final Map<UUID, Integer> killsCache = new HashMap<>();

    public HeartManager(PlayerDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        heartsCache.put(uuid, dataStore.getHearts(uuid, DEFAULT_HEARTS));
        eliminatedCache.put(uuid, dataStore.isEliminated(uuid));
        killsCache.put(uuid, dataStore.getKills(uuid));

        syncPlayer(player);
    }

    public void savePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        dataStore.setHearts(uuid, heartsCache.getOrDefault(uuid, DEFAULT_HEARTS));
        dataStore.setEliminated(uuid, eliminatedCache.getOrDefault(uuid, false));
        dataStore.setKills(uuid, killsCache.getOrDefault(uuid, 0));
        dataStore.save();
    }

    public void saveAll() {
        for (UUID uuid : heartsCache.keySet()) {
            dataStore.setHearts(uuid, heartsCache.getOrDefault(uuid, DEFAULT_HEARTS));
            dataStore.setEliminated(uuid, eliminatedCache.getOrDefault(uuid, false));
            dataStore.setKills(uuid, killsCache.getOrDefault(uuid, 0));
        }
        dataStore.save();
    }

    public int getHearts(Player player) {
        return heartsCache.getOrDefault(player.getUniqueId(), DEFAULT_HEARTS);
    }

    public void setHearts(Player player, int amount) {
        int clamped = Math.max(MIN_HEARTS, amount);
        UUID uuid = player.getUniqueId();

        heartsCache.put(uuid, clamped);

        if (clamped <= 0) {
            eliminatedCache.put(uuid, true);
        }

        syncPlayer(player);
        savePlayer(player);
    }

    public void addHearts(Player player, int amount) {
        setHearts(player, getHearts(player) + amount);
    }

    public void removeHearts(Player player, int amount) {
        setHearts(player, getHearts(player) - amount);
    }

    public boolean isEliminated(Player player) {
        return eliminatedCache.getOrDefault(player.getUniqueId(), false);
    }

    public void eliminate(Player player) {
        UUID uuid = player.getUniqueId();
        heartsCache.put(uuid, 0);
        eliminatedCache.put(uuid, true);

        syncPlayer(player);
        savePlayer(player);
    }

    public int getKills(Player player) {
        return killsCache.getOrDefault(player.getUniqueId(), 0);
    }

    public void addKill(Player player) {
        UUID uuid = player.getUniqueId();
        killsCache.put(uuid, getKills(player) + 1);
        savePlayer(player);
    }

    public void ensurePlayerExists(Player player) {
        UUID uuid = player.getUniqueId();

        if (!heartsCache.containsKey(uuid)) {
            heartsCache.put(uuid, dataStore.getHearts(uuid, DEFAULT_HEARTS));
        }

        if (!eliminatedCache.containsKey(uuid)) {
            eliminatedCache.put(uuid, dataStore.isEliminated(uuid));
        }

        if (!killsCache.containsKey(uuid)) {
            killsCache.put(uuid, dataStore.getKills(uuid));
        }

        syncPlayer(player);
        savePlayer(player);
    }

    public void syncPlayer(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        int hearts = getHearts(player);

        if (hearts <= 0) {
            maxHealth.setBaseValue(2.0D);
            player.setHealth(1.0D);
            player.setGameMode(GameMode.SPECTATOR);
            return;
        }

        double maxHealthValue = hearts * 2.0D;
        maxHealth.setBaseValue(maxHealthValue);

        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        double newHealth = Math.min(player.getHealth(), maxHealthValue);
        if (newHealth <= 0) {
            newHealth = maxHealthValue;
        }

        player.setHealth(newHealth);
    }

    public void syncAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensurePlayerExists(player);
        }
    }
}