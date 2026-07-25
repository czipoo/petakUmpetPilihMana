package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {
    private final Set<UUID> participantIds = new HashSet<>();
    private final Set<UUID> pastHunters = new HashSet<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    // Store the hunter UUID separately
    private UUID currentHunterUUID;
    private boolean gameRunning = false;
    private boolean awaitingNextRound = false;
    private boolean hidePhaseActive = false;
    private int deadCount = 0;
    private int gameTimerMinutes = 5;

    private BukkitTask hidePhaseTask;
    private BukkitTask gachaTask;
    private BukkitTask gameLoopTask;

    public boolean isStarting() {
        return !gameRunning && !awaitingNextRound;
    }

    public boolean isWaiting() {
        return !gameRunning && awaitingNextRound;
    }

    public boolean isPlaying() {
        return gameRunning;
    }

    public void enterWaiting() {
        gameRunning = false;
        hidePhaseActive = false;
        awaitingNextRound = true;
    }

    public void enterStarting() {
        gameRunning = false;
        hidePhaseActive = false;
        awaitingNextRound = false;
    }

    public boolean regis(Player p) {
        if (isParticipant(p)) {
            return false;
        }
        participantIds.add(p.getUniqueId());
        scores.putIfAbsent(p.getUniqueId(), 0);
        return true;
    }

    public boolean regisUUID(UUID uuid) {
        if (participantIds.contains(uuid)) return false;
        participantIds.add(uuid);
        scores.putIfAbsent(uuid, 0);
        return true;
    }

    public void unregis(Player p) {
        participantIds.remove(p.getUniqueId());
        if (currentHunterUUID != null && currentHunterUUID.equals(p.getUniqueId())) {
            currentHunterUUID = null;
        }
    }

    public void unregisUUID(UUID uuid) {
        participantIds.remove(uuid);
        if (currentHunterUUID != null && currentHunterUUID.equals(uuid)) {
            currentHunterUUID = null;
        }
    }

    /** Returns online participants as Player objects */
    public List<Player> getParticipants() {
        List<Player> result = new ArrayList<>();
        for (UUID id : participantIds) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) result.add(p);
        }
        return result;
    }

    public Set<UUID> getParticipantIds() {
        return participantIds;
    }

    public List<Player> getOnlineParticipants() {
        return getParticipants();
    }

    public boolean isParticipant(Player p) {
        return p != null && participantIds.contains(p.getUniqueId());
    }

    public boolean isParticipantUUID(UUID uuid) {
        return participantIds.contains(uuid);
    }

    public void setHunter(Player p) {
        this.currentHunterUUID = p.getUniqueId();
        pastHunters.add(p.getUniqueId());
    }

    public Player getHunter() {
        if (currentHunterUUID == null) return null;
        return Bukkit.getPlayer(currentHunterUUID);
    }

    public UUID getHunterUUID() {
        return currentHunterUUID;
    }

    public void addScore(UUID id, int amount) {
        scores.put(id, scores.getOrDefault(id, 0) + amount);
    }

    public Map<UUID, Integer> getScores() {
        return scores;
    }

    public void setGameRunning(boolean state) {
        this.gameRunning = state;
        if (state) {
            this.deadCount = 0;
            this.awaitingNextRound = false;
        }
    }

    public boolean isGameRunning() {
        return gameRunning;
    }


    public boolean isHidePhaseActive() {
        return hidePhaseActive;
    }

    public void setHidePhaseActive(boolean hidePhaseActive) {
        this.hidePhaseActive = hidePhaseActive;
    }

    public int getNextDeathPenalty() {
        deadCount++;
        int hiderCount = participantIds.size() - 1;
        int penalty = -(hiderCount - (deadCount - 1));
        return (penalty < -1) ? penalty : -1;
    }

    public Set<UUID> getPastHunters() {
        return pastHunters;
    }

    public void resetGameData() {
        pastHunters.clear();
        scores.clear();
        deadCount = 0;
        currentHunterUUID = null;
        enterStarting();
    }

    public void resetCurrentRoundHunter() {
        if (currentHunterUUID != null) {
            pastHunters.remove(currentHunterUUID);
            currentHunterUUID = null;
        }
        deadCount = 0;
        enterWaiting();
    }

    public void endTournament() {
        cancelAllTasks();
        pastHunters.clear();
        currentHunterUUID = null;
        deadCount = 0;
        participantIds.clear();
        enterStarting();
    }

    public void nextRound() {
        currentHunterUUID = null;
        deadCount = 0;
        gameRunning = false;
        awaitingNextRound = false;
    }

    public void resetDeadCount() {
        this.deadCount = 0;
    }

    public int getGameTimerMinutes() {
        return gameTimerMinutes;
    }

    public void setGameTimerMinutes(int minutes) {
        this.gameTimerMinutes = minutes;
    }

    public void setHidePhaseTask(BukkitTask task) {
        if (hidePhaseTask != null) {
            hidePhaseTask.cancel();
        }
        this.hidePhaseTask = task;
    }

    public void setGachaTask(BukkitTask task) {
        if (gachaTask != null) {
            gachaTask.cancel();
        }
        this.gachaTask = task;
    }

    public void setGameLoopTask(BukkitTask task) {
        if (gameLoopTask != null) {
            gameLoopTask.cancel();
        }
        this.gameLoopTask = task;
    }

    public void cancelAllTasks() {
        if (hidePhaseTask != null) {
            hidePhaseTask.cancel();
            hidePhaseTask = null;
        }
        if (gachaTask != null) {
            gachaTask.cancel();
            gachaTask = null;
        }
        if (gameLoopTask != null) {
            gameLoopTask.cancel();
            gameLoopTask = null;
        }
    }
}
