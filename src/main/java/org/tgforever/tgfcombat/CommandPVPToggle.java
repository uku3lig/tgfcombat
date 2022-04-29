package org.tgforever.tgfcombat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

public class CommandPVPToggle implements CommandExecutor {
    private final HashMap<UUID, Boolean> pvpEnabled;
    private final HashMap<UUID, Double> lastAttackTimes;

    public CommandPVPToggle(HashMap<UUID, Boolean> pvpEnabled, HashMap<UUID, Double> lastAttackTimes) {
        super();
        this.pvpEnabled = pvpEnabled;
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

        final boolean currentValue = pvpEnabled.getOrDefault(player.getUniqueId(), false);
        pvpEnabled.put(player.getUniqueId(), !currentValue);

        if (!currentValue) {
            player.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + ChatColor.GREEN + "PVP enabled!");
        }
        else {
            player.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + ChatColor.GREEN + "PVP disabled!");
        }

        try {
            FileWriter dictFile = new FileWriter("pvpPreferences.txt");
            for (UUID uuid : pvpEnabled.keySet()) {
                dictFile.write(uuid.toString() + ":" + Boolean.toString(pvpEnabled.get(uuid)) + "\n");
            }

            dictFile.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }
}
