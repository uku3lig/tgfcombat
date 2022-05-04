package org.tgforever.tgfcombat;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class TGFCombat extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Enabling TGFCombat...");

        saveDefaultConfig();
        CombatListener listener = new CombatListener(this);

        getServer().getPluginManager().registerEvents(listener, this);
        Objects.requireNonNull(this.getCommand("pvp")).setExecutor(new PVPToggleCommand(this, listener));
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling TGFCombat...");
    }

    public static void sendMessage(CommandSender target, String message) {
        target.sendMessage(ChatColor.GOLD + "[" + ChatColor.DARK_RED + "TGFCombat" + ChatColor.GOLD + "] " + message);
    }
}
