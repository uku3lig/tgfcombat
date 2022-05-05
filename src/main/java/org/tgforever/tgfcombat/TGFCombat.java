package org.tgforever.tgfcombat;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class TGFCombat extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Enabling TGFCombat...");

        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling TGFCombat...");
    }

    public static void sendMessage(CommandSender target, String message) {
        target.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + message);
    }
}
