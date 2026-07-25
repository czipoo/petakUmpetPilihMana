package com.czipo.petakUmpetPilihMana;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

public final class SilentCommands {
    private SilentCommands() {}

    public static void run(String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        Boolean previous = world != null ? world.getGameRuleValue(GameRule.SEND_COMMAND_FEEDBACK) : null;

        try {
            if (world != null) {
                world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } finally {
            if (world != null && previous != null) {
                world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, previous);
            }
        }
    }
}
