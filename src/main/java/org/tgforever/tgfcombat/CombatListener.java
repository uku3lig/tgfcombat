package org.tgforever.tgfcombat;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;
import java.util.*;

public class CombatListener implements Listener {
    private final double COOLDOWN = 15.0;
    private final HashMap<UUID, Double> lastAttackTimes;
    private final JavaPlugin plugin;
    private final HashMap<Integer, UUID> lastCrystalAttackers;
    private final HashMap<Location, UUID> lastVolatileBlockAttackers;

    CombatListener(JavaPlugin plugin, HashMap<UUID, Double> lastAttackTimes) {
        this.plugin = plugin;
        this.lastAttackTimes = lastAttackTimes;
        lastCrystalAttackers = new HashMap<Integer, UUID>();
        lastVolatileBlockAttackers = new HashMap<Location, UUID>();
    }

    private boolean triggerCombat(Player entity, Player damager) {
        if (!Database.getInstance(plugin).getState(damager.getUniqueId())) {
            TGFCombat.sendMessage(damager, ChatColor.RED + "You have PVP disabled!");
            return false;
        }

        if (!Database.getInstance(plugin).getState(entity.getUniqueId())) {
            TGFCombat.sendMessage(damager, ChatColor.RED + "That player has PVP disabled!");
            return false;
        }

        final boolean alreadyInCombat = lastAttackTimes.containsKey(damager.getUniqueId()) && ((double)Instant.now().toEpochMilli() / 1000.0 - lastAttackTimes.get(damager.getUniqueId())) < COOLDOWN;
        if (!alreadyInCombat) {
            TGFCombat.sendMessage(damager,ChatColor.YELLOW + "You are in combat! " + ChatColor.RED + "Do not log out!");
        }

        lastAttackTimes.put(damager.getUniqueId(), (double)Instant.now().toEpochMilli() / 1000.0);

        Audience audience = (Audience) damager;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                double timeSinceLastAttack = (double)Instant.now().toEpochMilli() / 1000.0 - lastAttackTimes.get(damager.getUniqueId());
                timeSinceLastAttack = Math.min(timeSinceLastAttack, COOLDOWN);

                String combatBar = ChatColor.RED + "|".repeat((int) Math.ceil(COOLDOWN - timeSinceLastAttack)) + ChatColor.GREEN + "|".repeat((int) Math.floor(timeSinceLastAttack));
                String remainingTime = ChatColor.WHITE + Double.toString((double)Math.round((COOLDOWN - timeSinceLastAttack) * 10d) / 10d);
                String combatIndicator = ChatColor.DARK_AQUA + "Combat" + ChatColor.WHITE + "   >>   ";
                audience.sendActionBar(Component.text(combatIndicator + combatBar + "   " + remainingTime + " ".repeat(4 - (remainingTime.length() - 2))));

                if (timeSinceLastAttack < COOLDOWN) {
                    Bukkit.getScheduler().runTaskLater(plugin, this, 1L);
                }
                else {
                    TGFCombat.sendMessage(damager,ChatColor.GREEN + "You are no longer in combat.");
                }
            }
        };

        if (!alreadyInCombat) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }

        return true;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player entity) {
            if (event.getDamager() instanceof Player damager) {
                final boolean shouldEnterCombat = triggerCombat(entity, damager);
                if (!shouldEnterCombat) {
                    event.setCancelled(true);
                }
            } else if (event.getDamager() instanceof EnderCrystal crystal) {
                if (!lastCrystalAttackers.containsKey(crystal.getEntityId())) {
                    return;
                }

                final UUID uuid = lastCrystalAttackers.get(crystal.getEntityId());
                final Player attacker = plugin.getServer().getPlayer(uuid);
                if (attacker == null) return;
                if (attacker.equals(entity)) return;

                final boolean shouldEnterCombat = triggerCombat(entity, attacker);
                if (!shouldEnterCombat) {
                    event.setCancelled(true);
                }
            }
        }
        else if (event.getEntity() instanceof EnderCrystal crystal) {
            if (event.getDamager() instanceof Player damager) {
                lastCrystalAttackers.put(crystal.getEntityId(), damager.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player damager)) {
            return;
        }

        if (event.getHitEntity() instanceof Player entity) {
            final boolean shouldEnterCombat = triggerCombat(entity, damager);
            if (!shouldEnterCombat) {
                event.setCancelled(true);
            }
        }
        else if (event.getHitEntity() instanceof EnderCrystal crystal) {
            lastCrystalAttackers.put(crystal.getEntityId(), damager.getUniqueId());
        }
    }

    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player damagwr)) {
            return;
        }

        final List<PotionEffectType> negativePotionEffects = Arrays.asList(
                PotionEffectType.BAD_OMEN,
                PotionEffectType.BLINDNESS,
                PotionEffectType.CONFUSION,
                PotionEffectType.HARM,
                PotionEffectType.HUNGER,
                PotionEffectType.POISON,
                PotionEffectType.SLOW,
                PotionEffectType.SLOW_DIGGING,
                PotionEffectType.UNLUCK,
                PotionEffectType.WEAKNESS,
                PotionEffectType.WITHER
        );

        final ThrownPotion potion = event.getEntity();
        for (PotionEffect effect : potion.getEffects()) {
            if (negativePotionEffects.contains(effect.getType())) {
                for (LivingEntity entity : event.getAffectedEntities()) {
                    if (!(entity instanceof Player player)) {
                        continue;
                    }

                    final boolean shouldEnterCombat = triggerCombat(player, damagwr);
                    if (!shouldEnterCombat) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        final Player player = event.getPlayer();

        if (block == null) return;

        if (block.getBlockData() instanceof Bed) {
            if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
                return;
            }

            lastVolatileBlockAttackers.put(block.getLocation(), player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!lastAttackTimes.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }

        double currentTime = (double)Instant.now().toEpochMilli() / 1000.0;
        double lastAttackTime = lastAttackTimes.get(event.getPlayer().getUniqueId());
        if (currentTime - lastAttackTime < COOLDOWN) {
            event.getPlayer().setHealth(0);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        final boolean inCombat = lastAttackTimes.containsKey(event.getPlayer().getUniqueId()) && ((double)Instant.now().toEpochMilli() / 1000.0 - lastAttackTimes.get(event.getPlayer().getUniqueId())) < COOLDOWN;
        if (!inCombat) return;

        final String[] blockedCommands = {
                "/home",
                "/h",
                "/spawn",
                "/sp",
                "/enderchest",
                "/ec",
                "/workbench",
                "/wb",
                "/nether"
        };

        for (String blockedCommand : blockedCommands) {
            if (event.getMessage().split(" ")[0].toLowerCase().equals(blockedCommand)) {
                TGFCombat.sendMessage(event.getPlayer(), ChatColor.RED + "You may not use that command while in combat.");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        lastAttackTimes.put(event.getEntity().getUniqueId(), 0.0);
    }
}
