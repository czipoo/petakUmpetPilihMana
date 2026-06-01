package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
        if (!gm.isGameRunning()) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Hider mati
        if (gm.getParticipants().contains(victim) && !victim.equals(gm.getHunter())) {
            if (!eliminatedPlayers.contains(victim.getUniqueId())) {
                eliminatedPlayers.add(victim.getUniqueId());
                int penalty = gm.getNextDeathPenalty();
                gm.addScore(victim.getUniqueId(), penalty);
                Bukkit.broadcastMessage("§c" + victim.getName() + " tereliminasi! Skor: §l" + penalty);

                giveGhostGear(victim);
                ghostPlayers.add(victim.getUniqueId());
            }
        }

        // Scoring untuk killer
        if (killer != null && gm.getParticipants().contains(killer)) {
            if (killer.equals(gm.getHunter()) || ghostPlayers.contains(killer.getUniqueId())) {
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
        if (!gm.isGameRunning()) return;

        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // RULE 1: Ghost tidak bisa saling membunuh
        if (ghostPlayers.contains(attacker.getUniqueId()) && ghostPlayers.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage("§cGhost tidak bisa saling membunuh!");
            return;
        }

        // RULE 2: Ghost tidak bisa bunuh hunter
        if (ghostPlayers.contains(attacker.getUniqueId()) && victim.equals(gm.getHunter())) {
            event.setCancelled(true);
            attacker.sendMessage("§cGhost tidak bisa membunuh Hunter!");
            return;
        }

        // RULE 3: Hider alive tidak bisa bunuh ghost
        if (!ghostPlayers.contains(attacker.getUniqueId()) && !attacker.equals(gm.getHunter())
                && ghostPlayers.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage("§cHider tidak bisa membunuh Ghost!");
            return;
        }

        // RULE 4: Hider alive tidak bisa bunuh hunter
        if (!ghostPlayers.contains(attacker.getUniqueId()) && !attacker.equals(gm.getHunter())
                && victim.equals(gm.getHunter())) {
            event.setCancelled(true);
            attacker.sendMessage("§cHider tidak bisa menyakiti Hunter!");
            return;
        }

        // RULE 5: Hunter tidak bisa bunuh ghost
        if (attacker.equals(gm.getHunter()) && ghostPlayers.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage("§cGhost sudah aman dari Hunter!");
            return;
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        PilihManaManager pmm = plugin.getPilihManaManager();
        String message = event.getMessage().trim();
        if (message.equals("1") || message.equals("2")) {
            int choice = Integer.parseInt(message);
            // Check global question
            if (pmm.isChoiceActive() && plugin.getGameManager().getParticipants().contains(p)) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> pmm.registerChoice(p, choice));
            }
            // Check test question
            else if (pmm.getActiveTestQuestions().containsKey(p.getUniqueId())) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> pmm.registerTestChoice(p, choice));
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        PilihManaManager pmm = plugin.getPilihManaManager();

        // 1. Sprint cancellation (#9 A - "tidak bisa sprint")
        if (pmm.getActiveNoSprints().contains(p.getUniqueId())) {
            if (p.isSprinting()) {
                p.setSprinting(false);
            }
        }

        // 2. AD / WS control swap
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

                // Project movement vector onto Forward and Right
                double forwardSpeed = dx * fx + dz * fz;
                double rightSpeed = dx * rx + dz * rz;

                if (swapAD) rightSpeed = -rightSpeed;
                if (swapWS) forwardSpeed = -forwardSpeed;

                // Reconstruct movement vector in world space
                double newDx = forwardSpeed * fx + rightSpeed * rx;
                double newDz = forwardSpeed * fz + rightSpeed * rz;

                Location newTo = from.clone();
                newTo.setX(from.getX() + newDx);
                newTo.setY(to.getY()); // preserve vertical
                newTo.setZ(from.getZ() + newDz);
                newTo.setYaw(to.getYaw());
                newTo.setPitch(to.getPitch());

                event.setTo(newTo);
            }
        }

        // 3. Ticking visual particles (Flame, snowflakes, sound steps hearing)
        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            pmm.handleStepEvents(p, to);
        }
    }

    private void checkRoundEnd() {
        GameManager gm = plugin.getGameManager();
        int totalHiders = gm.getParticipants().size() - 1;
        int currentDead = eliminatedPlayers.size();

        if (currentDead >= totalHiders) {
            gm.setGameRunning(false);
            eliminatedPlayers.clear();
            ghostPlayers.clear();
            plugin.getPilihManaManager().resetAllActiveEffects();
            Bukkit.broadcastMessage("§6§lRONDE SELESAI! §fSemua hider tertangkap.");
        }
    }

    public void giveHunterGear(Player p) {
        if (p == null || !p.isOnline()) {
            Bukkit.getLogger().warning("Cannot give hunter gear: Player is not online!");
            return;
        }
        p.getInventory().clear();
        p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 255), true); // Max strength
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
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 255), true); // Max strength
        p.sendMessage("§7[GHOST] §f⚔ Kamu menjadi ghost! Dapatkan §c+1 Poin §funtuk setiap kill!");
    }

    public Set<UUID> getGhostPlayers() {
        return ghostPlayers;
    }

    public void resetForNewRound() {
        eliminatedPlayers.clear();
        ghostPlayers.clear();
    }
}
