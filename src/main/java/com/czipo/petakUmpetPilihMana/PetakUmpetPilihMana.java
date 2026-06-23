package com.czipo.petakUmpetPilihMana;

import org.bukkit.plugin.java.JavaPlugin;

public final class PetakUmpetPilihMana extends JavaPlugin {
    private GameManager gameManager;
    private PilihManaManager pilihManaManager;
    private GameListener gameListener;
    private TimerBossBarManager timerBossBarManager;

    @Override
    public void onEnable() {
        this.gameManager = new GameManager();
        this.timerBossBarManager = new TimerBossBarManager();
        this.pilihManaManager = new PilihManaManager(this);
        this.gameListener = new GameListener(this);

        getCommand("regis").setExecutor(new AdminCommands(this));
        getCommand("regisall").setExecutor(new AdminCommands(this));
        getCommand("unregis").setExecutor(new AdminCommands(this));
        getCommand("listplayer").setExecutor(new AdminCommands(this));
        getCommand("start").setExecutor(new GameCommands(this));
        getCommand("nextround").setExecutor(new GameCommands(this));
        if (getCommand("resetgame") != null) getCommand("resetgame").setExecutor(new AdminCommands(this));
        if (getCommand("endgame") != null) getCommand("endgame").setExecutor(new AdminCommands(this));
        if (getCommand("listscore") != null) getCommand("listscore").setExecutor(new AdminCommands(this));
        if (getCommand("question") != null) getCommand("question").setExecutor(new GameCommands(this));

        getServer().getPluginManager().registerEvents(gameListener, this);

        getLogger().info("Petak Umpet Pilih Mana Enabled!");
    }

    public GameManager getGameManager() { return gameManager; }
    public PilihManaManager getPilihManaManager() { return pilihManaManager; }
    public GameListener getGameListener() { return gameListener; }
    public TimerBossBarManager getTimerBossBarManager() { return timerBossBarManager; }

    @Override
    public void onDisable() {
        if (timerBossBarManager != null) {
            timerBossBarManager.removeAll();
        }
        if (gameManager != null) {
            gameManager.cancelAllTasks();
        }
        if (pilihManaManager != null) {
            pilihManaManager.resetParticipantEffects();
        }
        getLogger().info("Petak Umpet Pilih Mana Disabled!");
    }
}
