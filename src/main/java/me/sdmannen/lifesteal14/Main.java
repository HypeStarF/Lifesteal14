package me.sdmannen.lifesteal14;

import me.sdmannen.lifesteal14.data.GameDataStore;
import me.sdmannen.lifesteal14.data.PlayerDataStore;
import me.sdmannen.lifesteal14.game.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private static Main instance;

    private GameManager gameManager;
    private HeartManager heartManager;
    private KillRewardService killRewardService;
    private PlayerDataStore playerDataStore;
    private GameDataStore gameDataStore;
    private ScoreboardManager scoreboardManager;
    private CombatTagService combatTagService;
    private DamageAttributionTracker damageAttributionTracker;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.playerDataStore = new PlayerDataStore(this);
        this.gameDataStore = new GameDataStore(this);
        this.heartManager = new HeartManager(this, playerDataStore);
        this.combatTagService = new CombatTagService(this);
        this.gameManager = new GameManager(this, heartManager, gameDataStore);
        this.killRewardService = new KillRewardService(this, heartManager, gameManager);
        this.scoreboardManager = new ScoreboardManager(this, heartManager, gameManager);
        this.damageAttributionTracker = new DamageAttributionTracker(gameManager);

        heartManager.loadAllKnownPlayersFromStore();
        gameManager.loadPersistentState();

        registerCommands();
        registerListeners();

        gameManager.restoreAfterRestart();
        scoreboardManager.updateAll();

        getLogger().info("Lifesteal14 enabled.");
        getLogger().info("Loaded game state: " + gameManager.getGameState());
        getLogger().info("Grace remaining: " + gameManager.getGraceSecondsRemaining());
        getLogger().info("Reveal remaining: " + gameManager.getSecondsUntilReveal());
        getLogger().info("Nether remaining: " + gameManager.getNetherSecondsRemaining());
        getLogger().info("Game end remaining: " + gameManager.getGameEndSecondsRemaining());
        getLogger().info("Nether open: " + gameManager.isNetherOpen());
        getLogger().info("Known players loaded: " + heartManager.getAllKnownPlayerUuids().size());
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
            HeartGameCommand commandExecutor = new HeartGameCommand(
                    gameManager,
                    heartManager,
                    scoreboardManager,
                    playerDataStore,
                    gameDataStore
            );
            getCommand("heartgame").setExecutor(commandExecutor);
            getCommand("heartgame").setTabCompleter(new HeartGameTabCompleter());
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new JoinListener(heartManager, gameManager), this);
        getServer().getPluginManager().registerEvents(new QuitListener(heartManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this, heartManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerDeathListener(gameManager, killRewardService, damageAttributionTracker),
                this
        );
        getServer().getPluginManager().registerEvents(new PvpListener(gameManager, combatTagService), this);
        getServer().getPluginManager().registerEvents(new NetherListener(gameManager), this);
        getServer().getPluginManager().registerEvents(damageAttributionTracker, this);
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

    public GameDataStore getGameDataStore() {
        return gameDataStore;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public CombatTagService getCombatTagService() {
        return combatTagService;
    }
    public DamageAttributionTracker getDamageAttributionTracker() {
        return damageAttributionTracker;
    }
}