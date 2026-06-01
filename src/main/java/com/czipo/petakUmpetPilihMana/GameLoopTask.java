package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class GameLoopTask extends BukkitRunnable {
    private final PetakUmpetPilihMana plugin;
    private int totalSeconds = 300; // 5 Menit

    public GameLoopTask(PetakUmpetPilihMana plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameRunning() || totalSeconds <= 0) {
            this.cancel();
            Bukkit.broadcastMessage("§6§lWAKTU HABIS! Game Selesai.");
            return;
        }

        // Tampilkan Timer di Action Bar setiap detik
        String timeFormatted = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
        Bukkit.getOnlinePlayers().forEach(p ->
                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent("§6§lWAKTU BERMAIN: §e" + timeFormatted)));

        // Trigger Pilih Mana setiap menit (5:00, 4:00, 3:00, 2:00, 1:00)
        if (totalSeconds % 60 == 0 && totalSeconds > 0) {
            triggerPilihMana();
        }

        totalSeconds--;
    }

    private void triggerPilihMana() {
        plugin.getPilihManaManager().triggerPilihMana();
    }
}
