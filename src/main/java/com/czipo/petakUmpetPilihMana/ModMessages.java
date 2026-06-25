package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ModMessages {
    private ModMessages() {}

    public static void sendToOps(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                player.sendMessage(message);
            }
        }
    }
}
