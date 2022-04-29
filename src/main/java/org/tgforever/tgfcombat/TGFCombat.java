package org.tgforever.tgfcombat;

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

        getServer().getPluginManager().registerEvents(new CombatListener(this, lastAttackTimes), this);
        this.getCommand("pvp").setExecutor(new CommandPVPToggle(lastAttackTimes));
    }

    @Override
    public void onDisable() {
        getLogger().info("onDisable has been run");
    }
}
