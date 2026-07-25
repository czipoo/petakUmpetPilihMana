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

        GameCommands gameCommands = new GameCommands(this);
        AdminCommands adminCommands = new AdminCommands(this);

        getCommand("regis").setExecutor(adminCommands);
        getCommand("regisall").setExecutor(adminCommands);
        getCommand("unregis").setExecutor(adminCommands);
        getCommand("listplayer").setExecutor(adminCommands);
        getCommand("start").setExecutor(gameCommands);
        getCommand("nextround").setExecutor(gameCommands);
        if (getCommand("resetgame") != null) getCommand("resetgame").setExecutor(adminCommands);
        if (getCommand("endgame") != null) getCommand("endgame").setExecutor(adminCommands);
        if (getCommand("listscore") != null) getCommand("listscore").setExecutor(adminCommands);
        if (getCommand("setquestion") != null) getCommand("setquestion").setExecutor(gameCommands);
        if (getCommand("settimer") != null) {
            getCommand("settimer").setExecutor(gameCommands);
            getCommand("settimer").setTabCompleter(gameCommands);
        }
        if (getCommand("commandinfo") != null) getCommand("commandinfo").setExecutor(adminCommands);

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
