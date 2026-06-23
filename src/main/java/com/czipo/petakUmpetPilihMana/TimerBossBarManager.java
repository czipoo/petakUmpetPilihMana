package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TimerBossBarManager {
    private final Map<UUID, BossBar> playerBars = new HashMap<>();
    private BossBar sharedBar;
    private int sharedMaxSeconds = 1;

    public void setPlayerTimer(Player player, String label, int remainingSeconds, int maxSeconds) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        BossBar bar = playerBars.get(uuid);
        String title = label + " " + formatTime(remainingSeconds);
        float progress = progress(remainingSeconds, maxSeconds);
        BarColor color = colorForProgress(remainingSeconds, maxSeconds);

        if (bar == null) {
            bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            bar.addPlayer(player);
            playerBars.put(uuid, bar);
        } else {
            bar.setTitle(title);
            bar.setProgress(progress);
            bar.setColor(color);
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
        bar.setProgress(progress);
    }

    public void startSharedTimer(Iterable<Player> players, String label, int maxSeconds) {
        removeShared();
        sharedMaxSeconds = Math.max(1, maxSeconds);
        sharedBar = Bukkit.createBossBar(
                label + " " + formatTime(maxSeconds),
                BarColor.GREEN,
                BarStyle.SOLID
        );
        sharedBar.setProgress(1.0);
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                sharedBar.addPlayer(player);
            }
        }
    }

    public void updateSharedTimer(String label, int remainingSeconds) {
        if (sharedBar == null) {
            return;
        }
        sharedBar.setTitle(label + " " + formatTime(remainingSeconds));
        sharedBar.setProgress(progress(remainingSeconds, sharedMaxSeconds));
        sharedBar.setColor(colorForProgress(remainingSeconds, sharedMaxSeconds));
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        BossBar bar = playerBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            if (bar.getPlayers().isEmpty()) {
                bar.removeAll();
            }
        }
    }

    public void removeShared() {
        if (sharedBar != null) {
            sharedBar.removeAll();
            sharedBar = null;
        }
    }

    public void removeAll() {
        for (BossBar bar : playerBars.values()) {
            bar.removeAll();
        }
        playerBars.clear();
        removeShared();
    }

    public static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static float progress(int remaining, int max) {
        return Math.max(0f, Math.min(1f, (float) remaining / Math.max(1, max)));
    }

    private static BarColor colorForProgress(int remaining, int max) {
        float ratio = (float) remaining / Math.max(1, max);
        if (ratio > 0.5f) {
            return BarColor.GREEN;
        }
        if (ratio > 0f) {
            return BarColor.YELLOW;
        }
        return BarColor.RED;
    }
}
