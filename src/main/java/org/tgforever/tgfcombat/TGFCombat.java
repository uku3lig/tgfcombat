package org.tgforever.tgfcombat;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class TGFCombat extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Enabling TGFCombat...");
        saveDefaultConfig();

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) getServer().getPluginManager().registerEvents(new CombatListener(this, provider.getProvider()), this);
        else Bukkit.getLogger().severe("ERROR: Could not find LuckPerms. Make sure you have it installed.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling TGFCombat...");
    }

    public static void sendMessage(CommandSender target, String message) {
        target.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + message);
    }
}
