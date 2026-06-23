package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class GameLoopTask extends BukkitRunnable {
    private final PetakUmpetPilihMana plugin;
    private static final int HUNT_MAX_SECONDS = 300;
    private static final int WYR_MAX_SECONDS = 15;

    private int totalSeconds = HUNT_MAX_SECONDS;
    private boolean isWyrActive = false;
    private int wyrCountdown = WYR_MAX_SECONDS;

    public GameLoopTask(PetakUmpetPilihMana plugin) {
        this.plugin = plugin;
    }

    public void startWyrPhase() {
        this.isWyrActive = true;
        this.wyrCountdown = WYR_MAX_SECONDS;
        plugin.getPilihManaManager().triggerPilihMana(this);
        plugin.getTimerBossBarManager().startSharedTimer(
                plugin.getGameManager().getOnlineParticipants(),
                "Waktu Menjawab",
                WYR_MAX_SECONDS
        );
    }

    private void endWyrPhase() {
        plugin.getPilihManaManager().endWyrPhase();
        plugin.getTimerBossBarManager().removeShared();
        plugin.getTimerBossBarManager().startSharedTimer(
                plugin.getGameManager().getOnlineParticipants(),
                "Waktu Bermain",
                totalSeconds
        );
        plugin.getTimerBossBarManager().updateSharedTimer("Waktu Bermain", totalSeconds);
        this.isWyrActive = false;
    }

    public void endWyrEarly() {
        if (isWyrActive) {
            endWyrPhase();
        }
    }

    public int getWyrCountdown() {
        return wyrCountdown;
    }

    @Override
    public void run() {
        GameManager gm = plugin.getGameManager();
        TimerBossBarManager bossBars = plugin.getTimerBossBarManager();

        if (!gm.isGameRunning()) {
            this.cancel();
            gm.setGameLoopTask(null);
            plugin.getPilihManaManager().endWyrPhase();
            bossBars.removeAll();
            return;
        }

        if (isWyrActive) {
            if (wyrCountdown <= 0) {
                endWyrPhase();
                return;
            }

            bossBars.updateSharedTimer("Waktu Menjawab", wyrCountdown);
            plugin.getPilihManaManager().refreshOpenChoiceDialogs(wyrCountdown);

            if (wyrCountdown <= 5) {
                for (Player p : gm.getOnlineParticipants()) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (6 - wyrCountdown) * 0.1f);
                }
            }

            wyrCountdown--;
        } else {
            if (totalSeconds <= 0) {
                this.cancel();
                gm.setGameLoopTask(null);
                bossBars.removeAll();
                plugin.getGameListener().endRoundWithWinner(true);
                return;
            }

            bossBars.updateSharedTimer("Waktu Bermain", totalSeconds);

            if (totalSeconds == 300 || totalSeconds == 240 || totalSeconds == 180 || totalSeconds == 120 || totalSeconds == 60) {
                startWyrPhase();
                totalSeconds--;
            } else {
                totalSeconds--;
            }
        }
    }
}
