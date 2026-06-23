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

    public GameCommands(PetakUmpetPilihMana plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            return true;
        }
        GameManager gm = plugin.getGameManager();

        if (label.equalsIgnoreCase("start")) {
            if (gm.isGameRunning()) {
                sender.sendMessage("§cGame sudah berjalan!");
                return true;
            }
            if (gm.isAwaitingNextRound()) {
                sender.sendMessage("§cRonde sebelumnya belum dilanjutkan! Gunakan /nextround atau /endgame.");
                return true;
            }
            if (gm.getParticipants().size() < 2) {
                sender.sendMessage("§cMinimum 2 peserta untuk bermain (1 hunter, 1 hider)!");
                return true;
            }

            List<Player> available = gm.getParticipants().stream()
                    .filter(p -> !gm.getPastHunters().contains(p.getUniqueId()))
                    .toList();

            if (available.isEmpty()) {
                sender.sendMessage("§cSemua peserta sudah pernah jadi Hunter! Gunakan /endgame lalu /start, atau /resetgame.");
                return true;
            }

            prepareParticipantsForRound();
            sender.sendMessage("§e[GACHA] §fMemilih hunter...");
            runGachaThenStart(available);
        }

        else if (label.equalsIgnoreCase("nextround")) {
            if (gm.isGameRunning()) {
                sender.sendMessage("§cGame masih berjalan!");
                return true;
            }
            if (!gm.isAwaitingNextRound()) {
                sender.sendMessage("§c/nextround hanya bisa setelah ronde selesai!");
                return true;
            }
            if (gm.getParticipants().size() < 2) {
                sender.sendMessage("§cMinimum 2 peserta untuk bermain!");
                return true;
            }

            List<Player> available = gm.getParticipants().stream()
                    .filter(p -> !gm.getPastHunters().contains(p.getUniqueId()))
                    .toList();

            if (available.isEmpty()) {
                sender.sendMessage("§cSemua peserta sudah pernah jadi Hunter! Gunakan /endgame lalu /start.");
                return true;
            }

            gm.nextRound();
            plugin.getGameListener().resetForNewRound();
            prepareParticipantsForRound();

            sender.sendMessage("§e[GACHA] §fMemilih hunter untuk ronde baru...");
            runGachaThenStart(available);
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
                p.sendMessage("§cNomor pertanyaan harus berupa angka 1-25.");
            }
        }

        return true;
    }

    private void prepareParticipantsForRound() {
        plugin.getGameListener().clearParticipantInventories();
        plugin.getPilihManaManager().resetParticipantEffects();
        plugin.getTimerBossBarManager().removeAll();
    }

    private void runGachaThenStart(List<Player> available) {
        GameManager gm = plugin.getGameManager();

        BukkitRunnable gachaRunnable = new BukkitRunnable() {
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
                    gm.setGachaTask(null);

                    Player hunter = available.get(random.nextInt(available.size()));
                    gm.setHunter(hunter);

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.sendTitle("§c§l" + hunter.getName(), "§fTerpilih menjadi HUNTER!", 10, 40, 10);
                        online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    }
                    Bukkit.broadcastMessage("§6§l[GACHA] §e" + hunter.getName() + " §fterpilih menjadi HUNTER!");

                    startHidePhase();
                }
            }
        };
        gm.setGachaTask(gachaRunnable.runTaskTimer(plugin, 0L, 2L));
    }

    private void startHidePhase() {
        GameManager gm = plugin.getGameManager();
        TimerBossBarManager bossBars = plugin.getTimerBossBarManager();

        gm.setGameRunning(true);
        gm.setHidePhaseActive(true);
        gm.resetDeadCount();
        plugin.getGameListener().resetForNewRound();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule locator_bar false");

        Player hunter = gm.getHunter();
        final int hideMax = 60;

        BukkitRunnable hideRunnable = new BukkitRunnable() {
            int count = hideMax;

            @Override
            public void run() {
                if (!gm.isGameRunning() || !gm.isHidePhaseActive()) {
                    this.cancel();
                    gm.setHidePhaseTask(null);
                    bossBars.removeAll();
                    return;
                }

                if (count > 0) {
                    if (hunter != null && hunter.isOnline()) {
                        hunter.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 0, false, false));
                        hunter.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 10, false, false));
                        bossBars.setPlayerTimer(hunter, "KAMU SEDANG DI-FREEZE!", count, hideMax);
                    }

                    for (Player p : gm.getOnlineParticipants()) {
                        if (hunter != null && p.equals(hunter)) {
                            continue;
                        }
                        bossBars.setPlayerTimer(p, "Waktu Ngumpet", count, hideMax);
                    }

                    if (count <= 5) {
                        Bukkit.getOnlinePlayers().forEach(p -> {
                            p.sendTitle("§c" + count, "§fSiapkan diri!", 0, 21, 0);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
                        });
                    }

                    count--;
                } else {
                    this.cancel();
                    gm.setHidePhaseTask(null);
                    gm.setHidePhaseActive(false);
                    bossBars.removeAll();

                    if (hunter != null && hunter.isOnline()) {
                        hunter.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                        hunter.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
                        plugin.getGameListener().giveHunterGear(hunter);
                    }
                    Bukkit.broadcastMessage("§c§lHUNTER DILEPASKAN!");

                    plugin.getTimerBossBarManager().startSharedTimer(
                            gm.getOnlineParticipants(), "Waktu Bermain", 300);

                    GameLoopTask task = new GameLoopTask(plugin);
                    gm.setGameLoopTask(task.runTaskTimer(plugin, 0L, 20L));
                }
            }
        };
        gm.setHidePhaseTask(hideRunnable.runTaskTimer(plugin, 0L, 20L));
    }
}
