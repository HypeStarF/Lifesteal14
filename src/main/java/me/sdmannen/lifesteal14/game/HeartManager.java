package me.sdmannen.lifesteal14.game;

import me.sdmannen.lifesteal14.Main;
import me.sdmannen.lifesteal14.data.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class HeartManager {

    private final Main plugin;
    private final PlayerDataStore dataStore;
    private final int defaultHearts;

    private final Map<UUID, Integer> heartsCache = new HashMap<>();
    private final Map<UUID, Boolean> eliminatedCache = new HashMap<>();
    private final Map<UUID, Integer> killsCache = new HashMap<>();

    public HeartManager(Main plugin, PlayerDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.defaultHearts = plugin.getConfig().getInt("hearts.default", 10);
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        int hearts = dataStore.getHearts(uuid, defaultHearts);
        boolean eliminated = dataStore.isEliminated(uuid) || hearts <= 0;
        int kills = dataStore.getKills(uuid);

        heartsCache.put(uuid, hearts);
        eliminatedCache.put(uuid, eliminated);
        killsCache.put(uuid, kills);

        syncPlayer(player);
    }

    public void savePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        dataStore.setHearts(uuid, heartsCache.getOrDefault(uuid, defaultHearts));
        dataStore.setEliminated(uuid, eliminatedCache.getOrDefault(uuid, false));
        dataStore.setKills(uuid, killsCache.getOrDefault(uuid, 0));
        dataStore.save();
    }

    public void saveAll() {
        for (UUID uuid : heartsCache.keySet()) {
            dataStore.setHearts(uuid, heartsCache.getOrDefault(uuid, defaultHearts));
            dataStore.setEliminated(uuid, eliminatedCache.getOrDefault(uuid, false));
            dataStore.setKills(uuid, killsCache.getOrDefault(uuid, 0));
        }
        dataStore.save();
    }

    public int getHearts(Player player) {
        return heartsCache.getOrDefault(player.getUniqueId(), defaultHearts);
    }

    public int getHearts(UUID uuid) {
        return heartsCache.getOrDefault(uuid, defaultHearts);
    }

    public void setHearts(Player player, int amount) {
        int clamped = Math.max(0, amount);
        UUID uuid = player.getUniqueId();

        heartsCache.put(uuid, clamped);
        eliminatedCache.put(uuid, clamped <= 0);

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

    public boolean isEliminated(UUID uuid) {
        return eliminatedCache.getOrDefault(uuid, false);
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

    public int getKills(UUID uuid) {
        return killsCache.getOrDefault(uuid, 0);
    }

    public void addKill(Player player) {
        UUID uuid = player.getUniqueId();
        killsCache.put(uuid, getKills(player) + 1);
        savePlayer(player);
    }

    public void ensurePlayerExists(Player player) {
        UUID uuid = player.getUniqueId();

        if (!heartsCache.containsKey(uuid)) {
            heartsCache.put(uuid, dataStore.getHearts(uuid, defaultHearts));
        }

        if (!killsCache.containsKey(uuid)) {
            killsCache.put(uuid, dataStore.getKills(uuid));
        }

        if (!eliminatedCache.containsKey(uuid)) {
            boolean eliminated = dataStore.isEliminated(uuid) || heartsCache.getOrDefault(uuid, defaultHearts) <= 0;
            eliminatedCache.put(uuid, eliminated);
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

        player.removePotionEffect(PotionEffectType.STRENGTH);

        if (hearts <= 0) {
            maxHealth.setBaseValue(2.0D);

            if (player.getHealth() > 1.0D) {
                player.setHealth(1.0D);
            }

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
        applyHeartEffects(player, hearts);
    }

    private void applyHeartEffects(Player player, int hearts) {
        if (hearts == 4) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false, true));
        } else if (hearts == 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false, true));
        } else if (hearts > 0 && hearts <= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 2, false, false, true));
        }
    }

    public void syncAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ensurePlayerExists(player);
        }
    }

    public List<Player> getAliveOnlinePlayers() {
        List<Player> players = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isEliminated(player)) {
                players.add(player);
            }
        }

        return players;
    }

    public List<UUID> getAlivePlayerUuids() {
        List<UUID> uuids = new ArrayList<>();

        for (Map.Entry<UUID, Boolean> entry : eliminatedCache.entrySet()) {
            if (!entry.getValue()) {
                uuids.add(entry.getKey());
            }
        }

        return uuids;
    }

    public Player getUniqueHighestHeartPlayerOnline() {
        Player best = null;
        int bestHearts = Integer.MIN_VALUE;
        boolean tie = false;

        for (Player player : getAliveOnlinePlayers()) {
            int hearts = getHearts(player);

            if (hearts > bestHearts) {
                best = player;
                bestHearts = hearts;
                tie = false;
            } else if (hearts == bestHearts) {
                tie = true;
            }
        }

        return tie ? null : best;
    }

    public boolean isUniqueHighest(Player target) {
        Player uniqueHighest = getUniqueHighestHeartPlayerOnline();
        return uniqueHighest != null && uniqueHighest.getUniqueId().equals(target.getUniqueId());
    }

    public Player getUniqueLowestHeartPlayerOnline(UUID excludeUuid) {
        Player lowest = null;
        int lowestHearts = Integer.MAX_VALUE;
        boolean tie = false;

        for (Player player : getAliveOnlinePlayers()) {
            if (player.getUniqueId().equals(excludeUuid)) {
                continue;
            }

            int hearts = getHearts(player);

            if (hearts < lowestHearts) {
                lowest = player;
                lowestHearts = hearts;
                tie = false;
            } else if (hearts == lowestHearts) {
                tie = true;
            }
        }

        return tie ? null : lowest;
    }

    public int getAlivePlayerCount() {
        int count = 0;

        for (Map.Entry<UUID, Boolean> entry : eliminatedCache.entrySet()) {
            if (!entry.getValue()) {
                count++;
            }
        }

        return count;
    }

    public UUID getSingleRemainingPlayerUuid() {
        UUID winner = null;

        for (Map.Entry<UUID, Boolean> entry : eliminatedCache.entrySet()) {
            if (entry.getValue()) {
                continue;
            }

            if (winner != null) {
                return null;
            }

            winner = entry.getKey();
        }

        return winner;
    }

    public String getPlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString();
    }
}