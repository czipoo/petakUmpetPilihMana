package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

public class GameCommands implements CommandExecutor {
    private final PetakUmpetPilihMana plugin;
    private final Random random = new Random();

    public GameCommands(PetakUmpetPilihMana plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) return true;
        GameManager gm = plugin.getGameManager();

        if (label.equalsIgnoreCase("gacha")) {
            List<Player> available = gm.getParticipants().stream()
                    .filter(p -> !gm.getPastHunters().contains(p.getUniqueId()))
                    .toList();

            if (available.isEmpty()) {
                sender.sendMessage("§cSemua peserta sudah pernah jadi Hunter! Gunakan /resetgame.");
                return true;
            }

            sender.sendMessage("§e[GACHA] §fMemilih hunter...");
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks < 20) {
                        Player randomName = gm.getParticipants().get(random.nextInt(gm.getParticipants().size()));

                        for (Player online : Bukkit.getOnlinePlayers()) {
                            online.sendTitle("§7Memilih Hunter...", "§e" + randomName.getName(), 0, 7, 0);
                            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
                        }
                        ticks++;
                    } else {
                        this.cancel();
                        Player hunter = available.get(random.nextInt(available.size()));
                        gm.setHunter(hunter);

                        for (Player online : Bukkit.getOnlinePlayers()) {
                            online.sendTitle("§c§l" + hunter.getName(), "§fTerpilih menjadi HUNTER!", 10, 40, 10);
                            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        }
                        Bukkit.broadcastMessage("§6§l[GACHA] §e" + hunter.getName() + " §fterpilih menjadi HUNTER!");
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }

        else if (label.equalsIgnoreCase("start")) {
            if (gm.isGameRunning()) {
                sender.sendMessage("§cGame sudah berjalan!");
                return true;
            }
            if (gm.getHunter() == null) {
                sender.sendMessage("§cGacha hunter dulu dengan /gacha!");
                return true;
            }
            if (!gm.getHunter().isOnline()) {
                sender.sendMessage("§cHunter offline! Tidak bisa start.");
                return true;
            }
            sender.sendMessage("§a§l[START] §fGame dimulai!");
            startHidePhase();
        }

        else if (label.equalsIgnoreCase("nextround")) {
            if (gm.isGameRunning()) {
                sender.sendMessage("§cGame masih berjalan! Gunakan /endgame untuk mengakhiri.");
                return true;
            }
            gm.nextRound();
            plugin.getPilihManaManager().resetAllActiveEffects();
            sender.sendMessage("§a§l[NEXT ROUND] §fStatus direset untuk ronde baru. Score tetap tersimpan.");
            Bukkit.broadcastMessage("§6§lRonde baru dimulai! Tunggu perintah admin.");
        }

        else if (label.equalsIgnoreCase("question")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cCommand ini hanya bisa dijalankan oleh Player!");
                return true;
            }
            if (args.length < 1) {
                p.sendMessage("§cGunakan: /question <nomor>");
                return true;
            }
            try {
                int qId = Integer.parseInt(args[0]);
                plugin.getPilihManaManager().triggerTestQuestion(p, qId);
            } catch (NumberFormatException e) {
                p.sendMessage("§cNomor pertanyaan harus berupa angka 1-55.");
            }
        }

        return true;
    }

    private void startHidePhase() {
        GameManager gm = plugin.getGameManager();
        gm.setGameRunning(true);
        gm.resetDeadCount();

        plugin.getGameListener().resetForNewRound();
        plugin.getPilihManaManager().resetAllActiveEffects();

        Player hunter = gm.getHunter();

        new BukkitRunnable() {
            int count = 60;
            @Override
            public void run() {
                if (count > 0) {
                    if (hunter != null && hunter.isOnline()) {
                        hunter.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 0, false, false));
                        hunter.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 10, false, false));
                        hunter.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                new net.md_5.bungee.api.chat.TextComponent("§c§lKAMU SEDANG DI-FREEZE! §f" + count + "s"));
                    }

                    if (count <= 5) {
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            p.sendTitle("§c" + count, "§fSiapkan diri!", 0, 21, 0);
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                        });
                    }

                    Bukkit.getOnlinePlayers().forEach(p -> {
                        if (!p.equals(hunter)) {
                            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                    new net.md_5.bungee.api.chat.TextComponent("§eWaktu Ngumpet: §f" + count + "s"));
                        }
                    });

                    count--;
                } else {
                    this.cancel();
                    if (hunter != null) {
                        hunter.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                        hunter.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
                        plugin.getGameListener().giveHunterGear(hunter);
                    }
                    Bukkit.broadcastMessage("§c§lHUNTER DILEPASKAN!");
                    new GameLoopTask(plugin).runTaskTimer(plugin, 0L, 20L);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
