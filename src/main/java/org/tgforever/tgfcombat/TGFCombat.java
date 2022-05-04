package org.tgforever.tgfcombat;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;

public class TGFCombat extends JavaPlugin {
    private HashMap<UUID, Double> lastAttackTimes;

    @Override
    public void onEnable() {
        getLogger().info("onEnable has been run");
        lastAttackTimes = new HashMap<UUID, Double>();

        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        this.getCommand("pvp").setExecutor(new CommandPVPToggle(this, lastAttackTimes));
    }

    @Override
    public void onDisable() {
        getLogger().info("onDisable has been run");
    }

    public static void sendMessage(CommandSender target, String message) {
        target.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + message);
    }
}
