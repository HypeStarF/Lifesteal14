package me.sdmannen.lifesteal14;

import me.sdmannen.lifesteal14.data.PlayerDataStore;
import me.sdmannen.lifesteal14.game.GameManager;
import me.sdmannen.lifesteal14.game.HeartGameCommand;
import me.sdmannen.lifesteal14.game.HeartManager;
import me.sdmannen.lifesteal14.game.JoinListener;
import me.sdmannen.lifesteal14.game.KillRewardService;
import me.sdmannen.lifesteal14.game.NetherListener;
import me.sdmannen.lifesteal14.game.PlayerDeathListener;
import me.sdmannen.lifesteal14.game.PlayerRespawnListener;
import me.sdmannen.lifesteal14.game.PvpListener;
import me.sdmannen.lifesteal14.game.QuitListener;
import me.sdmannen.lifesteal14.game.ScoreboardManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private static Main instance;

    private GameManager gameManager;
    private HeartManager heartManager;
    private KillRewardService killRewardService;
    private PlayerDataStore playerDataStore;
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.playerDataStore = new PlayerDataStore(this);
        this.heartManager = new HeartManager(this, playerDataStore);
        this.gameManager = new GameManager(this, heartManager);
        this.killRewardService = new KillRewardService(this, heartManager, gameManager);
        this.scoreboardManager = new ScoreboardManager(this, heartManager);

        registerCommands();
        registerListeners();

        heartManager.syncAllOnlinePlayers();
        scoreboardManager.updateAll();

        getLogger().info("Lifesteal14 enabled.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }

        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
        }

        if (heartManager != null) {
            heartManager.saveAll();
        }

        getLogger().info("Lifesteal14 disabled.");
    }

    private void registerCommands() {
        if (getCommand("heartgame") != null) {
            getCommand("heartgame").setExecutor(new HeartGameCommand(gameManager, heartManager, scoreboardManager));
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new JoinListener(heartManager), this);
        getServer().getPluginManager().registerEvents(new QuitListener(heartManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this, heartManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(gameManager, killRewardService), this);
        getServer().getPluginManager().registerEvents(new PvpListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new NetherListener(gameManager), this);
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

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}