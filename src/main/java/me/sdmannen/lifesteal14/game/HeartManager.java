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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class HeartManager {

    private final Main plugin;
    private final PlayerDataStore dataStore;
    private final int defaultHearts;

    private final Map<UUID, Integer> permanentHeartsCache = new HashMap<>();
    private final Map<UUID, Integer> temporaryPveLossCache = new HashMap<>();
    private final Map<UUID, Boolean> permanentEliminatedCache = new HashMap<>();
    private final Map<UUID, Integer> killsCache = new HashMap<>();

    public HeartManager(Main plugin, PlayerDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.defaultHearts = plugin.getConfig().getInt("hearts.default", 10);
    }

    public void loadAllKnownPlayersFromStore() {
        for (UUID uuid : dataStore.getAllKnownPlayerUuids()) {
            loadUuidIntoCache(uuid);
        }
    }

    public void loadPlayer(Player player) {
        loadUuidIntoCache(player.getUniqueId());
        syncPlayer(player);
    }

    public void reloadPlayerFromDisk(Player player) {
        dataStore.reload();
        loadPlayer(player);
    }

    private void loadUuidIntoCache(UUID uuid) {
        int permanentHearts = dataStore.getHearts(uuid, defaultHearts);
        int temporaryPveLoss = dataStore.getTemporaryPveLoss(uuid);
        boolean permanentlyEliminated = dataStore.isEliminated(uuid) || permanentHearts <= 0;
        int kills = dataStore.getKills(uuid);

        permanentHearts = Math.max(0, permanentHearts);
        temporaryPveLoss = Math.max(0, Math.min(temporaryPveLoss, permanentHearts));

        permanentHeartsCache.put(uuid, permanentHearts);
        temporaryPveLossCache.put(uuid, temporaryPveLoss);
        permanentEliminatedCache.put(uuid, permanentlyEliminated);
        killsCache.put(uuid, kills);
    }

    public void savePlayer(Player player) {
        savePlayer(player.getUniqueId());
    }

    private void savePlayer(UUID uuid) {
        dataStore.setHearts(uuid, permanentHeartsCache.getOrDefault(uuid, defaultHearts));
        dataStore.setTemporaryPveLoss(uuid, temporaryPveLossCache.getOrDefault(uuid, 0));
        dataStore.setEliminated(uuid, permanentEliminatedCache.getOrDefault(uuid, false));
        dataStore.setKills(uuid, killsCache.getOrDefault(uuid, 0));
        dataStore.save();
    }

    public void saveAll() {
        for (UUID uuid : getAllKnownPlayerUuids()) {
            dataStore.setHearts(uuid, permanentHeartsCache.getOrDefault(uuid, defaultHearts));
            dataStore.setTemporaryPveLoss(uuid, temporaryPveLossCache.getOrDefault(uuid, 0));
            dataStore.setEliminated(uuid, permanentEliminatedCache.getOrDefault(uuid, false));
            dataStore.setKills(uuid, killsCache.getOrDefault(uuid, 0));
        }
        dataStore.save();
    }

    public int getHearts(Player player) {
        return getHearts(player.getUniqueId());
    }

    public int getHearts(UUID uuid) {
        if (isPermanentlyEliminated(uuid)) {
            return 0;
        }

        int permanentHearts = permanentHeartsCache.getOrDefault(uuid, defaultHearts);
        int temporaryLoss = temporaryPveLossCache.getOrDefault(uuid, 0);
        return Math.max(0, permanentHearts - temporaryLoss);
    }

    public int getPermanentHearts(Player player) {
        return getPermanentHearts(player.getUniqueId());
    }

    public int getPermanentHearts(UUID uuid) {
        return permanentHeartsCache.getOrDefault(uuid, defaultHearts);
    }

    public int getTemporaryPveLoss(Player player) {
        return getTemporaryPveLoss(player.getUniqueId());
    }

    public int getTemporaryPveLoss(UUID uuid) {
        return temporaryPveLossCache.getOrDefault(uuid, 0);
    }

    public void setHearts(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int clamped = Math.max(0, amount);

        permanentHeartsCache.put(uuid, clamped);
        permanentEliminatedCache.put(uuid, clamped <= 0);

        int currentTemporaryLoss = temporaryPveLossCache.getOrDefault(uuid, 0);
        temporaryPveLossCache.put(uuid, Math.min(currentTemporaryLoss, clamped));

        if (clamped <= 0) {
            temporaryPveLossCache.put(uuid, 0);
        }

        syncPlayer(player);
        savePlayer(uuid);
    }

    public void addHearts(Player player, int amount) {
        setHearts(player, getPermanentHearts(player) + amount);
    }

    public void removeHearts(Player player, int amount) {
        setHearts(player, getPermanentHearts(player) - amount);
    }

    public void addTemporaryPveLoss(Player player, int amount) {
        UUID uuid = player.getUniqueId();

        if (isPermanentlyEliminated(uuid) || amount <= 0) {
            return;
        }

        int permanentHearts = getPermanentHearts(uuid);
        int currentLoss = getTemporaryPveLoss(uuid);
        int newLoss = Math.min(permanentHearts, currentLoss + amount);

        temporaryPveLossCache.put(uuid, newLoss);

        syncPlayer(player);
        savePlayer(uuid);
    }

    public void setTemporaryPveLoss(Player player, int amount) {
        UUID uuid = player.getUniqueId();

        if (isPermanentlyEliminated(uuid)) {
            temporaryPveLossCache.put(uuid, 0);
            syncPlayer(player);
            savePlayer(uuid);
            return;
        }

        int permanentHearts = getPermanentHearts(uuid);
        int clamped = Math.max(0, Math.min(amount, permanentHearts));

        temporaryPveLossCache.put(uuid, clamped);
        syncPlayer(player);
        savePlayer(uuid);
    }

    public int restoreAllTemporaryPveHearts() {
        int restoredPlayers = 0;

        for (UUID uuid : getAllKnownPlayerUuids()) {
            if (isPermanentlyEliminated(uuid)) {
                continue;
            }

            int temporaryLoss = temporaryPveLossCache.getOrDefault(uuid, 0);
            if (temporaryLoss <= 0) {
                continue;
            }

            temporaryPveLossCache.put(uuid, 0);
            restoredPlayers++;

            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                syncPlayer(online);
            }
        }

        if (restoredPlayers > 0) {
            saveAll();
        }

        return restoredPlayers;
    }

    public boolean isPermanentlyEliminated(Player player) {
        return isPermanentlyEliminated(player.getUniqueId());
    }

    public boolean isPermanentlyEliminated(UUID uuid) {
        return permanentEliminatedCache.getOrDefault(uuid, false);
    }

    public boolean isTemporarilyKnockedOut(Player player) {
        return isTemporarilyKnockedOut(player.getUniqueId());
    }

    public boolean isTemporarilyKnockedOut(UUID uuid) {
        return !isPermanentlyEliminated(uuid) && getHearts(uuid) <= 0;
    }

    public boolean isEliminated(Player player) {
        return isEliminated(player.getUniqueId());
    }

    public boolean isEliminated(UUID uuid) {
        return isPermanentlyEliminated(uuid) || isTemporarilyKnockedOut(uuid);
    }

    public void eliminate(Player player) {
        UUID uuid = player.getUniqueId();

        permanentHeartsCache.put(uuid, 0);
        temporaryPveLossCache.put(uuid, 0);
        permanentEliminatedCache.put(uuid, true);

        syncPlayer(player);
        savePlayer(uuid);
    }

    public void revivePlayer(Player player, int hearts) {
        UUID uuid = player.getUniqueId();
        int clampedHearts = Math.max(1, hearts);

        permanentHeartsCache.put(uuid, clampedHearts);
        temporaryPveLossCache.put(uuid, 0);
        permanentEliminatedCache.put(uuid, false);

        syncPlayer(player);
        savePlayer(uuid);
    }

    public int getKills(Player player) {
        return getKills(player.getUniqueId());
    }

    public int getKills(UUID uuid) {
        return killsCache.getOrDefault(uuid, 0);
    }

    public void addKill(Player player) {
        UUID uuid = player.getUniqueId();
        killsCache.put(uuid, getKills(player) + 1);
        savePlayer(uuid);
    }

    public void addKills(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int newAmount = Math.max(0, getKills(uuid) + amount);
        killsCache.put(uuid, newAmount);
        savePlayer(uuid);
    }

    public void ensurePlayerExists(Player player) {
        UUID uuid = player.getUniqueId();

        if (!permanentHeartsCache.containsKey(uuid)) {
            loadUuidIntoCache(uuid);
        }

        syncPlayer(player);
        savePlayer(uuid);
    }

    public void syncPlayer(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        int currentHearts = getHearts(player);

        player.removePotionEffect(PotionEffectType.STRENGTH);

        if (currentHearts <= 0) {
            maxHealth.setBaseValue(2.0D);

            if (!player.isDead()) {
                if (player.getHealth() > 1.0D) {
                    player.setHealth(1.0D);
                }
                player.setGameMode(GameMode.SPECTATOR);
            }

            return;
        }

        double maxHealthValue = currentHearts * 2.0D;
        maxHealth.setBaseValue(maxHealthValue);

        if (player.isDead()) {
            return;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        double currentHealth = player.getHealth();
        double newHealth = currentHealth <= 0.0D
                ? maxHealthValue
                : Math.min(currentHealth, maxHealthValue);

        player.setHealth(newHealth);
        applyHeartEffects(player, currentHearts);
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
            if (!isPermanentlyEliminated(player)) {
                players.add(player);
            }
        }

        return players;
    }

    public List<UUID> getAlivePlayerUuids() {
        List<UUID> uuids = new ArrayList<>();

        for (UUID uuid : getAllKnownPlayerUuids()) {
            if (!isPermanentlyEliminated(uuid)) {
                uuids.add(uuid);
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
        UUID highestUuid = getUniqueHighestHeartPlayerUuid();
        return highestUuid != null && highestUuid.equals(target.getUniqueId());
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

        for (UUID uuid : getAllKnownPlayerUuids()) {
            if (!isPermanentlyEliminated(uuid)) {
                count++;
            }
        }

        return count;
    }

    public UUID getSingleRemainingPlayerUuid() {
        UUID winner = null;

        for (UUID uuid : getAllKnownPlayerUuids()) {
            if (isPermanentlyEliminated(uuid)) {
                continue;
            }

            if (winner != null) {
                return null;
            }

            winner = uuid;
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

    public List<UUID> getAllKnownPlayerUuids() {
        Set<UUID> all = new HashSet<>(dataStore.getAllKnownPlayerUuids());
        all.addAll(permanentHeartsCache.keySet());
        return new ArrayList<>(all);
    }

    public UUID getUniqueHighestHeartPlayerUuid() {
        UUID best = null;
        int bestHearts = Integer.MIN_VALUE;
        boolean tie = false;

        for (UUID uuid : getAlivePlayerUuids()) {
            int hearts = getHearts(uuid);

            if (hearts > bestHearts) {
                best = uuid;
                bestHearts = hearts;
                tie = false;
            } else if (hearts == bestHearts) {
                tie = true;
            }
        }

        return tie ? null : best;
    }

    public UUID getUniqueLowestHeartPlayerUuid() {
        UUID lowest = null;
        int lowestHearts = Integer.MAX_VALUE;
        boolean tie = false;

        for (UUID uuid : getAlivePlayerUuids()) {
            int hearts = getHearts(uuid);

            if (hearts < lowestHearts) {
                lowest = uuid;
                lowestHearts = hearts;
                tie = false;
            } else if (hearts == lowestHearts) {
                tie = true;
            }
        }

        return tie ? null : lowest;
    }

    public UUID getUniqueHighestKillPlayerUuid() {
        UUID best = null;
        int bestKills = Integer.MIN_VALUE;
        boolean tie = false;

        for (UUID uuid : getAlivePlayerUuids()) {
            int kills = getKills(uuid);

            if (kills > bestKills) {
                best = uuid;
                bestKills = kills;
                tie = false;
            } else if (kills == bestKills) {
                tie = true;
            }
        }

        return tie ? null : best;
    }
}