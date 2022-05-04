package org.tgforever.tgfcombat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public record PVPToggleCommand(JavaPlugin plugin, CombatListener listener) implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player player) {
            if (listener.isInCombat(player)) {
                TGFCombat.sendMessage(player, ChatColor.RED + "You cannot toggle PVP while in combat!");
            } else {
                final boolean newState = !Database.getInstance(plugin).getState(player.getUniqueId());
                Database.getInstance(plugin).edit(player.getUniqueId(), newState);

                TGFCombat.sendMessage(player, ChatColor.GREEN + "PVP " + (newState ? "enabled" : "disabled") + "!");
            }
        } else TGFCombat.sendMessage(sender, "YOU CAN'T TOGGLE YOUR PVP YOU CLOG SHITTER");
        return true;
    }
}
