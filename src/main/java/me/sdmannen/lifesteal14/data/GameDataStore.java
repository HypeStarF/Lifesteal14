package me.sdmannen.lifesteal14.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class GameDataStore {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public GameDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "game.yml");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create game.yml");
                e.printStackTrace();
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String getGameState() {
        return config.getString("game.state", "WAITING");
    }

    public void setGameState(String state) {
        config.set("game.state", state);
    }

    public boolean isNetherOpen() {
        return config.getBoolean("game.nether-open", false);
    }

    public void setNetherOpen(boolean netherOpen) {
        config.set("game.nether-open", netherOpen);
    }

    public String getWinnerName() {
        return config.getString("game.winner-name", null);
    }

    public void setWinnerName(String winnerName) {
        config.set("game.winner-name", winnerName);
    }

    public int getSecondsUntilReveal() {
        return config.getInt("timers.seconds-until-reveal", 0);
    }

    public void setSecondsUntilReveal(int value) {
        config.set("timers.seconds-until-reveal", value);
    }

    public int getRevealIntervalSeconds() {
        return config.getInt("timers.reveal-interval-seconds", 0);
    }

    public void setRevealIntervalSeconds(int value) {
        config.set("timers.reveal-interval-seconds", value);
    }

    public int getGraceSecondsRemaining() {
        return config.getInt("timers.grace-seconds-remaining", 0);
    }

    public void setGraceSecondsRemaining(int value) {
        config.set("timers.grace-seconds-remaining", value);
    }

    public long getNetherSecondsRemaining() {
        return config.getLong("timers.nether-seconds-remaining", 0L);
    }

    public void setNetherSecondsRemaining(long value) {
        config.set("timers.nether-seconds-remaining", value);
    }

    public long getGameEndSecondsRemaining() {
        return config.getLong("timers.game-end-seconds-remaining", 0L);
    }

    public void setGameEndSecondsRemaining(long value) {
        config.set("timers.game-end-seconds-remaining", value);
    }

    public boolean isCageCreated() {
        return config.getBoolean("cage.created", false);
    }

    public void setCageCreated(boolean created) {
        config.set("cage.created", created);
    }

    public void setCageBounds(String worldName, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        config.set("cage.world", worldName);
        config.set("cage.minX", minX);
        config.set("cage.maxX", maxX);
        config.set("cage.minY", minY);
        config.set("cage.maxY", maxY);
        config.set("cage.minZ", minZ);
        config.set("cage.maxZ", maxZ);
    }

    public String getCageWorld() {
        return config.getString("cage.world", null);
    }

    public int getCageMinX() {
        return config.getInt("cage.minX");
    }

    public int getCageMaxX() {
        return config.getInt("cage.maxX");
    }

    public int getCageMinY() {
        return config.getInt("cage.minY");
    }

    public int getCageMaxY() {
        return config.getInt("cage.maxY");
    }

    public int getCageMinZ() {
        return config.getInt("cage.minZ");
    }

    public int getCageMaxZ() {
        return config.getInt("cage.maxZ");
    }

    public void setCageCenter(Location center) {
        if (center == null || center.getWorld() == null) {
            config.set("cage.center", null);
            return;
        }

        config.set("cage.center.world", center.getWorld().getName());
        config.set("cage.center.x", center.getX());
        config.set("cage.center.y", center.getY());
        config.set("cage.center.z", center.getZ());
        config.set("cage.center.yaw", center.getYaw());
        config.set("cage.center.pitch", center.getPitch());
    }

    public Location getCageCenter() {
        String worldName = config.getString("cage.center.world");
        if (worldName == null) {
            return null;
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = config.getDouble("cage.center.x");
        double y = config.getDouble("cage.center.y");
        double z = config.getDouble("cage.center.z");
        float yaw = (float) config.getDouble("cage.center.yaw");
        float pitch = (float) config.getDouble("cage.center.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save game.yml");
            e.printStackTrace();
        }
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }
}