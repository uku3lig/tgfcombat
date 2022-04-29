package org.tgforever.tgfcombat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

public class CommandPVPToggle implements CommandExecutor {
    private final JavaPlugin plugin;
    private final HashMap<UUID, Double> lastAttackTimes;

    public CommandPVPToggle(JavaPlugin plugin, HashMap<UUID, Double> lastAttackTimes) {
        super();
        this.plugin = plugin;
        this.lastAttackTimes = lastAttackTimes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }

        Player player = (Player) sender;
        final boolean alreadyInCombat = lastAttackTimes.containsKey(player.getUniqueId()) && ((double) Instant.now().toEpochMilli() / 1000.0 - lastAttackTimes.get(player.getUniqueId())) < 15.0;
        if (alreadyInCombat) {
            player.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + ChatColor.RED + "You cannot toggle PVP while in combat!");
            return true;
        }

        final boolean currentValue = Database.getInstance(plugin).getStates().getOrDefault(player.getUniqueId(), false);
        Database.getInstance(plugin).edit(player.getUniqueId(), !currentValue);

        if (!currentValue) {
            player.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + ChatColor.GREEN + "PVP enabled!");
        }
        else {
            player.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + ChatColor.GREEN + "PVP disabled!");
        }

        return true;
    }
}
