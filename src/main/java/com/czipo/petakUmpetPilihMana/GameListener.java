package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GameListener implements Listener {
    private final PetakUmpetPilihMana plugin;
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<UUID> ghostPlayers = new HashSet<>();

    public GameListener(PetakUmpetPilihMana plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameRunning()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        Player hunter = gm.getHunter();

        if (victim.equals(hunter)) {
            if (killer != null && gm.isParticipant(killer) && !killer.equals(hunter)
                    && !ghostPlayers.contains(killer.getUniqueId())) {
                gm.addScore(killer.getUniqueId(), 5);
                killer.sendMessage("§c§l[BONUS] §a+5 Poin! Kamu membunuh HUNTER!");
                Bukkit.broadcastMessage("§c§l" + killer.getName() + " §cBERHASIL MEMBUNUH HUNTER! §e+5 POIN!");
            }
            endRoundWithWinner(true);
            return;
        }

        if (gm.isParticipant(victim) && !victim.equals(hunter)) {
            if (!eliminatedPlayers.contains(victim.getUniqueId())) {
                eliminatedPlayers.add(victim.getUniqueId());
                int penalty = gm.getNextDeathPenalty();
                gm.addScore(victim.getUniqueId(), penalty);
                Bukkit.broadcastMessage("§c" + victim.getName() + " tereliminasi! Skor: §l" + penalty);

                giveGhostGear(victim);
                ghostPlayers.add(victim.getUniqueId());
            }
        }

        if (killer != null && gm.isParticipant(killer)) {
            if (killer.equals(hunter) || ghostPlayers.contains(killer.getUniqueId())) {
                gm.addScore(killer.getUniqueId(), 1);
                if (ghostPlayers.contains(killer.getUniqueId())) {
                    killer.sendMessage("§7[GHOST] §a+1 Poin Kill!");
                } else {
                    killer.sendMessage("§a+1 Poin Kill!");
                }
            }
        }

        checkRoundEnd();
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
            attacker.sendMessage("§cGhost tidak bisa saling membunuh!");
            return;
        }

        if (attackerIsGhost && victimIsHunter) {
            event.setCancelled(true);
            attacker.sendMessage("§cGhost tidak bisa membunuh Hunter!");
            return;
        }

        if (attackerIsAliveHider && victimIsGhost) {
            event.setCancelled(true);
            attacker.sendMessage("§cHider tidak bisa membunuh Ghost!");
            return;
        }

        if (attackerIsAliveHider && victimIsAliveHider) {
            event.setCancelled(true);
            attacker.sendMessage("§cHider tidak bisa saling menyerang!");
            return;
        }

        if (attackerIsHunter && victimIsGhost) {
            event.setCancelled(true);
            attacker.sendMessage("§cGhost sudah aman dari Hunter!");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
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

                Location newTo = from.clone();
                newTo.setX(from.getX() + newDx);
                newTo.setY(to.getY());
                newTo.setZ(from.getZ() + newDz);
                newTo.setYaw(to.getYaw());
                newTo.setPitch(to.getPitch());

                event.setTo(newTo);
            }
        }

        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            pmm.handleStepEvents(p, to);
        }
    }

    private void checkRoundEnd() {
        GameManager gm = plugin.getGameManager();
        int totalHiders = gm.getParticipants().size() - 1;
        int currentDead = eliminatedPlayers.size();

        if (currentDead >= totalHiders) {
            endRoundWithWinner(false);
        }
    }

    public void endRoundWithWinner(boolean hiderWins) {
        GameManager gm = plugin.getGameManager();
        gm.cancelAllTasks();
        gm.setGameRunning(false);
        gm.setHidePhaseActive(false);
        gm.setAwaitingNextRound(true);
        eliminatedPlayers.clear();
        ghostPlayers.clear();
        plugin.getPilihManaManager().endWyrPhase();
        plugin.getPilihManaManager().resetParticipantEffects();
        plugin.getTimerBossBarManager().removeAll();
        clearParticipantInventories();

        if (hiderWins) {
            Bukkit.broadcastMessage("§a§l✦ HIDER MENANG! ✦");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§a§lHIDER MENANG!", "§fHider berhasil!", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        } else {
            Bukkit.broadcastMessage("§c§l✦ HUNTER MENANG! ✦");
            Player hunter = gm.getHunter();
            String subtitle = hunter != null ? "§e" + hunter.getName() + " §fberhasil menangkap semua hider!" : "§fSemua hider tertangkap!";
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§c§lHUNTER MENANG!", subtitle, 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.2f);
            }
        }

        ModMessages.sendToOps("§eGunakan §a/nextround §euntuk lanjut, atau §c/endgame §euntuk tutup tournament.");
    }

    public void giveHunterGear(Player p) {
        if (p == null || !p.isOnline()) {
            Bukkit.getLogger().warning("Cannot give hunter gear: Player is not online!");
            return;
        }
        p.getInventory().clear();
        p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 255), true);
        p.sendMessage("§e⚔ Kamu diberikan Netherite Sword & Kekuatan Maksimal!");
        Bukkit.broadcastMessage("§c§l" + p.getName() + " §csiap berburu!");
    }

    public void giveGhostGear(Player p) {
        if (p == null || !p.isOnline()) {
            return;
        }
        if (p.isDead()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> giveGhostGear(p), 5L);
            return;
        }
        p.getInventory().clear();
        p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 255), true);
        p.sendMessage("§7[GHOST] §f⚔ Kamu menjadi ghost! Dapatkan §c+1 Poin §funtuk setiap kill!");
    }

    public void clearParticipantInventories() {
        GameManager gm = plugin.getGameManager();
        for (Player p : gm.getParticipants()) {
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
        eliminatedPlayers.clear();
        ghostPlayers.clear();
    }
}
