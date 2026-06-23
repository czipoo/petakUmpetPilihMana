package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TimerBossBarManager {
    private final Map<UUID, BossBar> playerBars = new HashMap<>();
    private BossBar sharedBar;
    private final Set<UUID> sharedViewers = new HashSet<>();
    private int sharedMaxSeconds = 1;

    public void setPlayerTimer(Player player, String label, int remainingSeconds, int maxSeconds) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        float ratio = (float) remainingSeconds / Math.max(1, maxSeconds);
        String title = label + " " + formatTime(remainingSeconds);
        float progress = progress(remainingSeconds, maxSeconds);
        BarColor color = smoothColor(ratio);

        BossBar bar = playerBars.get(uuid);
        if (bar == null) {
            bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            bar.setProgress(progress);
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
    }

    public void startSharedTimer(Iterable<Player> players, String label, int maxSeconds) {
        removeShared();
        sharedMaxSeconds = Math.max(1, maxSeconds);
        sharedBar = Bukkit.createBossBar(
                label + " " + formatTime(maxSeconds),
                smoothColor(1f),
                BarStyle.SOLID
        );
        sharedBar.setProgress(1.0);
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                sharedViewers.add(player.getUniqueId());
                sharedBar.addPlayer(player);
            }
        }
    }

    public void updateSharedTimer(String label, int remainingSeconds) {
        if (sharedBar == null) {
            return;
        }

        float ratio = (float) remainingSeconds / Math.max(1, sharedMaxSeconds);
        sharedBar.setTitle(label + " " + formatTime(remainingSeconds));
        sharedBar.setProgress(progress(remainingSeconds, sharedMaxSeconds));
        sharedBar.setColor(smoothColor(ratio));
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        sharedViewers.remove(player.getUniqueId());
        BossBar bar = playerBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            if (bar.getPlayers().isEmpty()) {
                bar.removeAll();
            }
        }
        if (sharedBar != null) {
            sharedBar.removePlayer(player);
        }
    }

    public void pauseSharedTimer(String label, int remainingSeconds) {
        updateSharedTimer(label, remainingSeconds);
    }

    public int getSharedMaxSeconds() {
        return sharedMaxSeconds;
    }

    public boolean hasSharedTimer() {
        return sharedBar != null;
    }

    public void removeShared() {
        if (sharedBar != null) {
            sharedBar.removeAll();
            sharedBar = null;
        }
        sharedViewers.clear();
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

    static BarColor smoothColor(float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));

        if (ratio >= 0.999f) {
            return BarColor.GREEN;
        }
        if (ratio <= 0.001f) {
            return BarColor.RED;
        }
        if (Math.abs(ratio - 0.5f) < 0.025f) {
            return BarColor.YELLOW;
        }

        if (ratio > 0.5f) {
            float blend = (ratio - 0.5f) / 0.5f;
            if (blend >= 0.80f) {
                return BarColor.GREEN;
            }
            if (blend >= 0.60f) {
                return BarColor.GREEN;
            }
            if (blend >= 0.40f) {
                return BarColor.YELLOW;
            }
            if (blend >= 0.20f) {
                return BarColor.YELLOW;
            }
            return BarColor.YELLOW;
        }

        float blend = ratio / 0.5f;
        if (blend >= 0.80f) {
            return BarColor.YELLOW;
        }
        if (blend >= 0.60f) {
            return BarColor.YELLOW;
        }
        if (blend >= 0.40f) {
            return BarColor.YELLOW;
        }
        if (blend >= 0.20f) {
            return BarColor.RED;
        }
        return BarColor.RED;
    }
}
