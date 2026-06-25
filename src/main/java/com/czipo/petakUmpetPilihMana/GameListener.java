package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameListener implements Listener {
    private final PetakUmpetPilihMana plugin;
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<UUID> ghostPlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> pendingGhostTasks = new HashMap<>();
    private boolean roundEnded = false;

    public GameListener(PetakUmpetPilihMana plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getGameManager();

        cancelPendingGhostTask(player.getUniqueId());
        plugin.getTimerBossBarManager().removePlayer(player);
        plugin.getPilihManaManager().onPlayerDisconnect(player);

        if (gm.isParticipant(player)) {
            gm.unregis(player);
            ModMessages.sendToOps("§e" + player.getName() + " §7disconnect — otomatis di-unregis. §fRegis ulang manual jika perlu.");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameRunning() || roundEnded) {
            return;
        }

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        Player hunter = gm.getHunter();

        // Hunter mati → respawn di spawn, bukan hider menang
        if (victim.equals(hunter)) {
            if (killer != null && gm.isParticipant(killer) && !killer.equals(hunter)
                    && !ghostPlayers.contains(killer.getUniqueId())) {
                gm.addScore(killer.getUniqueId(), 5);
                killer.sendMessage("§c§l[BONUS] §a+5 Poin! Kamu membunuh HUNTER!");
                Bukkit.broadcastMessage("§c§l" + killer.getName() + " §cBERHASIL MEMBUNUH HUNTER! §e+5 POIN!");
            }
            Bukkit.broadcastMessage("§e" + hunter.getName() + " §f(Hunter) mati dan akan respawn!");
            return;
        }

        // Ghost mati → respawn di spawn
        if (gm.isParticipant(victim) && ghostPlayers.contains(victim.getUniqueId())) {
            if (killer != null && gm.isParticipant(killer)) {
                awardKillScore(killer, hunter);
            }
            return;
        }

        // Hider mati → eliminated, jadi ghost
        if (gm.isParticipant(victim) && !victim.equals(hunter)) {
            if (!eliminatedPlayers.contains(victim.getUniqueId())) {
                eliminatedPlayers.add(victim.getUniqueId());
                int penalty = gm.getNextDeathPenalty();
                gm.addScore(victim.getUniqueId(), penalty);
                Bukkit.broadcastMessage("§c" + victim.getName() + " tereliminasi! Skor: §l" + penalty);

                int totalHiders = countAliveHidersAtRoundStart(gm);
                if (eliminatedPlayers.size() >= totalHiders) {
                    if (killer != null && gm.isParticipant(killer)) {
                        awardKillScore(killer, hunter);
                    }
                    endRoundWithWinner(false);
                    return;
                }

                ghostPlayers.add(victim.getUniqueId());
            }
        }

        if (killer != null && gm.isParticipant(killer)) {
            awardKillScore(killer, hunter);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameRunning() || roundEnded) {
            return;
        }

        Player player = event.getPlayer();
        Player hunter = gm.getHunter();

        // Hunter respawn → beri gear lagi setelah delay kecil
        if (player.equals(hunter)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && gm.isGameRunning() && !roundEnded) {
                    giveHunterGear(player);
                }
            }, 5L);
            return;
        }

        // Ghost respawn → beri ghost gear lagi setelah delay kecil
        if (gm.isParticipant(player) && ghostPlayers.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && gm.isGameRunning() && !roundEnded) {
                    applyGhostGear(player);
                }
            }, 5L);
        }
    }

    private int countAliveHidersAtRoundStart(GameManager gm) {
        return Math.max(1, gm.getParticipants().size() - 1);
    }

    private void awardKillScore(Player killer, Player hunter) {
        if (killer.equals(hunter) || ghostPlayers.contains(killer.getUniqueId())) {
            plugin.getGameManager().addScore(killer.getUniqueId(), 1);
            if (ghostPlayers.contains(killer.getUniqueId())) {
                killer.sendMessage("§7[GHOST] §a+1 Poin Kill!");
            } else {
                killer.sendMessage("§a+1 Poin Kill!");
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameRunning()) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (gm.isHidePhaseActive() && gm.isParticipant(attacker) && gm.isParticipant(victim)) {
            event.setCancelled(true);
            return;
        }

        Player hunter = gm.getHunter();
        boolean attackerIsGhost = ghostPlayers.contains(attacker.getUniqueId());
        boolean victimIsGhost = ghostPlayers.contains(victim.getUniqueId());
        boolean attackerIsHunter = hunter != null && attacker.equals(hunter);
        boolean victimIsHunter = hunter != null && victim.equals(hunter);
        boolean attackerIsAliveHider = gm.isParticipant(attacker) && !attackerIsGhost && !attackerIsHunter;
        boolean victimIsAliveHider = gm.isParticipant(victim) && !victimIsGhost && !victimIsHunter;

        if (attackerIsGhost && victimIsGhost) {
            event.setCancelled(true);
            attacker.sendMessage("§cGhost tidak bisa saling menyerang!");
            return;
        }

        if ((attackerIsGhost && victimIsHunter) || (attackerIsHunter && victimIsGhost)) {
            event.setCancelled(true);
            attacker.sendMessage("§cHunter dan Ghost tidak bisa saling menyerang!");
            return;
        }

        if (attackerIsAliveHider && victimIsAliveHider) {
            event.setCancelled(true);
            attacker.sendMessage("§cHider tidak bisa saling menyerang!");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to == null) {
            return;
        }

        PilihManaManager pmm = plugin.getPilihManaManager();

        if (pmm.isPlayerFrozen(p)) {
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(from.setDirection(to.getDirection()));
            }
            return;
        }

        if (pmm.getActiveNoSprints().contains(p.getUniqueId())) {
            if (p.isSprinting()) {
                p.setSprinting(false);
            }
        }

        boolean swapAD = pmm.getActiveADSwaps().contains(p.getUniqueId());
        boolean swapWS = pmm.getActiveWSSwaps().contains(p.getUniqueId());

        if (swapAD || swapWS) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();

            if (Math.abs(dx) > 0.0001 || Math.abs(dz) > 0.0001) {
                double yaw = Math.toRadians(from.getYaw());
                double fx = -Math.sin(yaw);
                double fz = Math.cos(yaw);
                double rx = -fz;
                double rz = fx;

                double forwardSpeed = dx * fx + dz * fz;
                double rightSpeed = dx * rx + dz * rz;

                if (swapAD) {
                    rightSpeed = -rightSpeed;
                }
                if (swapWS) {
                    forwardSpeed = -forwardSpeed;
                }

                double newDx = forwardSpeed * fx + rightSpeed * rx;
                double newDz = forwardSpeed * fz + rightSpeed * rz;

                org.bukkit.Location newTo = from.clone();
                newTo.setX(from.getX() + newDx);
                newTo.setY(to.getY());
                newTo.setZ(from.getZ() + newDz);
                newTo.setYaw(to.getYaw());
                newTo.setPitch(to.getPitch());

                event.setTo(newTo);
            }
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        GameManager gm = plugin.getGameManager();
        if (gm.isParticipant(event.getPlayer()) && event.getBucket() == Material.WATER_BUCKET) {
            Player p = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET));
                }
            }, 1L);
        }
    }

    public void endRoundWithWinner(boolean hiderWins) {
        if (roundEnded) {
            return;
        }

        roundEnded = true;
        cancelAllPendingGhostTasks();

        GameManager gm = plugin.getGameManager();
        gm.cancelAllTasks();
        gm.enterWaiting();
        eliminatedPlayers.clear();
        ghostPlayers.clear();
        plugin.getPilihManaManager().endWyrPhase();
        cleanupParticipants();
        plugin.getTimerBossBarManager().removeAll();

        if (hiderWins) {
            Bukkit.broadcastMessage("§a§l★ HIDER MENANG! ★");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§a§lHIDER MENANG!", "§fHider berhasil!", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        } else {
            Bukkit.broadcastMessage("§c§l★ HUNTER MENANG! ★");
            Player hunter = gm.getHunter();
            String subtitle = hunter != null
                    ? "§e" + hunter.getName() + " §fberhasil menangkap semua hider!"
                    : "§fSemua hider tertangkap!";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§c§lHUNTER MENANG!", subtitle, 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }

        ModMessages.sendToOps("§eGunakan §a/nextround §euntuk lanjut, atau §c/endgame §euntuk tutup tournament.");
    }

    public void giveHunterGear(Player p) {
        if (p == null || !p.isOnline()) {
            return;
        }
        boolean hasSword = p.getInventory().contains(Material.DIAMOND_SWORD);
        if (!hasSword) {
            p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
        }
        
        boolean hasStrength = p.hasPotionEffect(PotionEffectType.STRENGTH);
        if (!hasStrength) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 0), true);
        }
        
        if (!hasSword || !hasStrength) {
            p.sendMessage("§e⚔ Kamu diberikan Diamond Sword & Strength!");
        }
    }

    private boolean applyGhostGear(Player p) {
        GameManager gm = plugin.getGameManager();
        if (p == null || !p.isOnline() || roundEnded || !gm.isGameRunning()) {
            return false;
        }

        int totalHiders = countAliveHidersAtRoundStart(gm);
        if (eliminatedPlayers.size() >= totalHiders) {
            return false;
        }

        boolean hasSword = p.getInventory().contains(Material.DIAMOND_SWORD);
        if (!hasSword) {
            p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
        }
        
        boolean hasStrength = p.hasPotionEffect(PotionEffectType.STRENGTH);
        if (!hasStrength) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 0), true);
        }
        
        if (!hasSword || !hasStrength) {
            p.sendMessage("§7[GHOST] §f👻 Kamu menjadi ghost! Dapatkan §c+1 Poin §funtuk setiap kill!");
        }
        return true;
    }

    private void cancelPendingGhostTask(UUID playerId) {
        BukkitTask task = pendingGhostTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelAllPendingGhostTasks() {
        for (BukkitTask task : pendingGhostTasks.values()) {
            task.cancel();
        }
        pendingGhostTasks.clear();
    }

    public void cleanupParticipants() {
        clearParticipantInventories();
        plugin.getPilihManaManager().resetParticipantEffects();
    }

    public void clearParticipantInventories() {
        for (Player p : plugin.getGameManager().getParticipants()) {
            if (p.isOnline()) {
                p.getInventory().clear();
                p.removePotionEffect(PotionEffectType.STRENGTH);
            }
        }
    }

    public Set<UUID> getGhostPlayers() {
        return ghostPlayers;
    }

    public void resetForNewRound() {
        roundEnded = false;
        cancelAllPendingGhostTasks();
        eliminatedPlayers.clear();
        ghostPlayers.clear();
    }

    public void cancelRoundState() {
        roundEnded = true;
        cancelAllPendingGhostTasks();
    }
}
