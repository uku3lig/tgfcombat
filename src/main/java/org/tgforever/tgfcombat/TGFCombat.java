package org.tgforever.tgfcombat;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;

public class TGFCombat extends JavaPlugin {
    private HashMap<UUID, Boolean> pvpEnabled;
    private HashMap<UUID, Double> lastAttackTimes;

    @Override
    public void onEnable() {
        getLogger().info("onEnable has been run");
        pvpEnabled = new HashMap<UUID, Boolean>();
        lastAttackTimes = new HashMap<UUID, Double>();

        try {
            final FileReader pvpPreferencesFile = new FileReader("pvpPreferences.txt");
            final BufferedReader br = new BufferedReader(pvpPreferencesFile);
            String line;

            while (true) {
                try {
                    line = br.readLine();
                }
                catch (IOException e) {
                    break;
                }

                if (line == null) break;

                String[] split = line.split(":");
                pvpEnabled.put(UUID.fromString(split[0]), Boolean.valueOf(split[1]));
            }

        }
        catch (FileNotFoundException e) {
            new File("pvpPreferences.txt");
        }


        getServer().getPluginManager().registerEvents(new CombatListener(this, pvpEnabled, lastAttackTimes), this);
        this.getCommand("pvp").setExecutor(new CommandPVPToggle(pvpEnabled, lastAttackTimes));
    }

    @Override
    public void onDisable() {
        getLogger().info("onDisable has been run");
    }
}
