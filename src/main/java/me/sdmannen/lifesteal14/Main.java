package me.sdmannen.lifesteal14;

import me.sdmannen.lifesteal14.data.PlayerDataStore;
import me.sdmannen.lifesteal14.game.*;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.plugin.java.JavaPlugin;


public final class Main extends JavaPlugin {

    private static Main instance;

    private GameManager gameManager;
    private HeartManager heartManager;
    private KillRewardService killRewardService;
    private PlayerDataStore playerDataStore;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.playerDataStore = new PlayerDataStore(this);
        this.heartManager = new HeartManager(playerDataStore);
        this.killRewardService = new KillRewardService(heartManager);
        this.gameManager = new GameManager(this, heartManager);

        registerCommands();
        registerListeners();

        heartManager.syncAllOnlinePlayers();

        getLogger().info("HeartGame enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("HeartGame disabled.");
    }

    private void registerCommands() {
        if (getCommand("heartgame") != null) {
            getCommand("heartgame").setExecutor(new HeartGameCommand(gameManager, heartManager));
        }
        if (heartManager != null) {
            heartManager.saveAll();
        }
        getLogger().info("Plugin disabled.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new JoinListener(heartManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(gameManager, killRewardService), this);
        getServer().getPluginManager().registerEvents(new PvpListener(gameManager), this);
    }

    public static Main getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public HeartManager getHeartManager() {
        return heartManager;
    }

    public KillRewardService getKillRewardService() {
        return killRewardService;
    }

    public PlayerDataStore getPlayerDataStore() {
        return playerDataStore;
    }
}
