package me.sdmannen.lifesteal14.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerDataStore {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public PlayerDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create players.yml");
                e.printStackTrace();
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public int getHearts(UUID uuid, int defaultValue) {
        return config.getInt("players." + uuid + ".hearts", defaultValue);
    }

    public void setHearts(UUID uuid, int hearts) {
        config.set("players." + uuid + ".hearts", hearts);
    }

    public boolean isEliminated(UUID uuid) {
        return config.getBoolean("players." + uuid + ".eliminated", false);
    }

    public void setEliminated(UUID uuid, boolean eliminated) {
        config.set("players." + uuid + ".eliminated", eliminated);
    }

    public int getKills(UUID uuid) {
        return config.getInt("players." + uuid + ".kills", 0);
    }

    public void setKills(UUID uuid, int kills) {
        config.set("players." + uuid + ".kills", kills);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save players.yml");
            e.printStackTrace();
        }
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }
}