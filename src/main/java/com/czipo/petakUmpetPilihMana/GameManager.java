package com.czipo.petakUmpetPilihMana;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {
    private final List<Player> participants = new ArrayList<>();
    private final Set<UUID> pastHunters = new HashSet<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    private Player currentHunter;
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
        participants.add(p);
        scores.putIfAbsent(p.getUniqueId(), 0);
        return true;
    }

    public void unregis(Player p) {
        participants.removeIf(part -> part.getUniqueId().equals(p.getUniqueId()));
    }

    public List<Player> getParticipants() {
        return participants;
    }

    public List<Player> getOnlineParticipants() {
        return participants.stream().filter(Player::isOnline).toList();
    }

    public boolean isParticipant(Player p) {
        return p != null && participants.stream()
                .anyMatch(part -> part.getUniqueId().equals(p.getUniqueId()));
    }

    public void setHunter(Player p) {
        this.currentHunter = p;
        pastHunters.add(p.getUniqueId());
    }

    public Player getHunter() {
        return currentHunter;
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
        int hiderCount = participants.size() - 1;
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
        currentHunter = null;
        enterStarting();
    }

    public void resetCurrentRoundHunter() {
        if (currentHunter != null) {
            pastHunters.remove(currentHunter.getUniqueId());
            currentHunter = null;
        }
        deadCount = 0;
        enterWaiting();
    }

    public void endTournament() {
        cancelAllTasks();
        pastHunters.clear();
        currentHunter = null;
        deadCount = 0;
        enterStarting();
    }

    public void nextRound() {
        currentHunter = null;
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
