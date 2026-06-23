package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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

    public static void sendToOps(CommandSender fallback, String message) {
        if (fallback != null && fallback.isOp()) {
            fallback.sendMessage(message);
        } else {
            sendToOps(message);
        }
    }
}
