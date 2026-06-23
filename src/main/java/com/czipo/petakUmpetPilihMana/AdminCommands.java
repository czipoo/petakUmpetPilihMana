package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public class AdminCommands implements CommandExecutor {
    private final PetakUmpetPilihMana plugin;

    public AdminCommands(PetakUmpetPilihMana plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            return true;
        }

        GameManager gm = plugin.getGameManager();

        if (label.equalsIgnoreCase("regis")) {
            if (gm.isGameRunning()) {
                sender.sendMessage("§cTidak bisa melakukan registrasi saat game berlangsung!");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§cGunakan: /regis <nama>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                gm.regis(target);
                sender.sendMessage("§a" + target.getName() + " berhasil terdaftar!");
            } else {
                sender.sendMessage("§cPlayer tidak online.");
            }
        }

        else if (label.equalsIgnoreCase("regisall")) {
            if (gm.isGameRunning()) {
                sender.sendMessage("§cTidak bisa registrasi saat game berlangsung!");
                return true;
            }
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!gm.isParticipant(p)) {
                    gm.regis(p);
                    count++;
                }
            }
            sender.sendMessage("§a" + count + " player berhasil terdaftar! Total: §f" + gm.getParticipants().size());
        }

        else if (label.equalsIgnoreCase("unregis")) {
            if (gm.isGameRunning()) {
                sender.sendMessage("§cTidak bisa unregis saat game berlangsung!");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§cGunakan: /unregis <nama>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                gm.unregis(target);
                sender.sendMessage("§e" + target.getName() + " telah dihapus dari daftar.");
            } else {
                sender.sendMessage("§cPlayer tidak online.");
            }
        }

        else if (label.equalsIgnoreCase("listplayer")) {
            sender.sendMessage("§6§lDAFTAR PESERTA PETAK UMPET:");
            for (Player p : gm.getParticipants()) {
                sender.sendMessage("§7- §f" + p.getName());
            }
        }

        else if (label.equalsIgnoreCase("endgame")) {
            if (gm.isGameRunning()) {
                sender.sendMessage("§cGame masih berjalan! Tunggu ronde selesai.");
                return true;
            }
            if (!gm.isAwaitingNextRound()) {
                sender.sendMessage("§c/endgame hanya bisa setelah ronde selesai dan sebelum /nextround!");
                return true;
            }

            gm.endTournament();
            plugin.getGameListener().resetForNewRound();
            plugin.getGameListener().clearParticipantInventories();
            plugin.getPilihManaManager().endWyrPhase();
            plugin.getPilihManaManager().resetParticipantEffects();
            plugin.getTimerBossBarManager().removeAll();

            Bukkit.broadcastMessage("§c§lGAME TELAH BERAKHIR!");
            showLeaderboard();
            ModMessages.sendToOps("§eGunakan §a/start §euntuk memulai tournament baru.");
        }

        else if (label.equalsIgnoreCase("listscore")) {
            showLeaderboard();
        }

        else if (label.equalsIgnoreCase("resetgame")) {
            gm.cancelAllTasks();
            gm.resetCurrentRoundHunter();
            plugin.getGameListener().resetForNewRound();
            plugin.getGameListener().clearParticipantInventories();
            plugin.getPilihManaManager().endWyrPhase();
            plugin.getPilihManaManager().resetParticipantEffects();
            plugin.getTimerBossBarManager().removeAll();

            sender.sendMessage("§a§lRESET RONDE! §fHunter ronde ini dihapus dari riwayat. Skor tetap.");
            Bukkit.broadcastMessage("§c§lRONDE DIRESET!");
            ModMessages.sendToOps("§eGunakan §a/start §eatau §a/nextround §euntuk melanjutkan.");
        }

        return true;
    }

    private void showLeaderboard() {
        GameManager gm = plugin.getGameManager();
        Bukkit.broadcastMessage("§8==============================");
        Bukkit.broadcastMessage("§6§lLEADERBOARD PETAK UMPET");
        Bukkit.broadcastMessage("§8==============================");

        gm.getScores().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    int score = entry.getValue();
                    Bukkit.broadcastMessage("§e" + name + ": §f" + score + " Poin");
                });

        Bukkit.broadcastMessage("§8==============================");
    }
}
