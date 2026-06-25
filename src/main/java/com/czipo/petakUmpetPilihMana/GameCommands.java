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
            if (!gm.isStarting()) {
                if (gm.isWaiting()) {
                    sender.sendMessage("§cGunakan /nextround untuk melanjutkan tournament!");
                } else if (gm.isPlaying()) {
                    sender.sendMessage("§cGame sedang berjalan!");
                }
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
                sender.sendMessage("§cSemua peserta sudah pernah jadi Hunter! Gunakan /endgame lalu /start.");
                return true;
            }

            prepareParticipantsForRound();
            sender.sendMessage("§e[GACHA] §fMemilih hunter...");
            runGachaThenStart(available);
        }

        else if (label.equalsIgnoreCase("nextround")) {
            if (!gm.isWaiting()) {
                if (gm.isPlaying()) {
                    sender.sendMessage("§cGame masih berjalan!");
                } else {
                    sender.sendMessage("§c/nextround hanya bisa setelah ronde selesai!");
                }
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

        else if (label.equalsIgnoreCase("setquestion")) {
            if (args.length < 1) {
                sender.sendMessage("§cGunakan: /setquestion <nomor>");
                return true;
            }
            try {
                int qId = Integer.parseInt(args[0]);

                if (gm.isPlaying()) {
                    // Saat permainan: override pertanyaan berikutnya
                    boolean success = plugin.getPilihManaManager().setNextQuestion(qId);
                    if (success) {
                        sender.sendMessage("§a[SET] §fPertanyaan berikutnya akan menggunakan Q" + qId + ".");
                        ModMessages.sendToOps("§eMod mengatur pertanyaan berikutnya: §fQ" + qId);
                    } else {
                        sender.sendMessage("§cPertanyaan nomor " + qId + " tidak ditemukan (pilih 1-44).");
                    }
                } else {
                    // Di luar permainan: tampilkan langsung ke semua participant
                    plugin.getPilihManaManager().triggerStandaloneQuestion(qId, sender);
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cNomor pertanyaan harus berupa angka 1-44.");
            }
        }

        else if (label.equalsIgnoreCase("settimer")) {
            if (gm.isPlaying()) {
                sender.sendMessage("§cTidak bisa mengatur timer saat permainan berlangsung!");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§cGunakan: /settimer <menit>");
                return true;
            }
            try {
                int minutes = Integer.parseInt(args[0]);
                if (minutes < 1 || minutes > 60) {
                    sender.sendMessage("§cTimer harus antara 1-60 menit.");
                    return true;
                }
                gm.setGameTimerMinutes(minutes);
                sender.sendMessage("§a[TIMER] §fDurasi permainan diatur ke §e" + minutes + " menit§f. (" + minutes + " pertanyaan WYR)");
                ModMessages.sendToOps("§eDurasi permainan diubah ke §f" + minutes + " menit §eoleh " + sender.getName());
            } catch (NumberFormatException e) {
                sender.sendMessage("§cMasukkan angka yang valid!");
            }
        }

        return true;
    }

    private void prepareParticipantsForRound() {
        plugin.getGameListener().cleanupParticipants();
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
        SilentCommands.run("gamerule locator_bar false");

        Player hunter = gm.getHunter();
        final int hideMax = 60;
        final int gameSeconds = gm.getGameTimerMinutes() * 60;

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

                    bossBars.startSharedTimer(gm.getOnlineParticipants(), "Waktu Bermain", gameSeconds);

                    GameLoopTask task = new GameLoopTask(plugin, gameSeconds);
                    gm.setGameLoopTask(task.runTaskTimer(plugin, 0L, 20L));
                }
            }
        };
        gm.setHidePhaseTask(hideRunnable.runTaskTimer(plugin, 0L, 20L));
    }
}
