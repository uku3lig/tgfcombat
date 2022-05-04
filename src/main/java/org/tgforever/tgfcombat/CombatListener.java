package org.tgforever.tgfcombat;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityPotionEffectEvent.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

// FIXME make attacks NON LETHAL
// FIXME players in creative mode cannot attack
public class CombatListener implements Listener {
    private static final int COOLDOWN = 15; // TODO move to config
    private static final List<PotionEffectType> NEGATIVE_EFFECTS = Arrays.asList(
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

    private final HashMap<UUID, Instant> lastAttack = new HashMap<>();
    private final JavaPlugin plugin;
    private final HashMap<Integer, UUID> crystalAttackers;
    private final HashMap<Location, UUID> lastVolatileBlockAttackers;

    CombatListener(JavaPlugin plugin) {
        this.plugin = plugin;
        crystalAttackers = new HashMap<>();
        lastVolatileBlockAttackers = new HashMap<>();
    }

    private boolean isInCombat(Player player) {
        if (!lastAttack.containsKey(player.getUniqueId())) return false;
        return lastAttack.get(player.getUniqueId()).plusSeconds(COOLDOWN).isAfter(Instant.now());
    }

    private boolean triggerCombat(Player entity, Player damager) {
        // if any in creative, do not trigger combat
        if (entity.getGameMode().equals(GameMode.CREATIVE) || damager.getGameMode().equals(GameMode.CREATIVE)) {
            return false;
        }

        if (!Database.getInstance(plugin).getState(damager.getUniqueId())) {
            TGFCombat.sendMessage(damager, ChatColor.RED + "You have PVP disabled!");
            return false;
        }

        if (!Database.getInstance(plugin).getState(entity.getUniqueId())) {
            TGFCombat.sendMessage(damager, ChatColor.RED + "That player has PVP disabled!");
            return false;
        }

        Runnable task = new Runnable() {
            @Override
            public void run() {
                int sinceLastAttack = (int) Duration.between(Instant.now(), lastAttack.get(damager.getUniqueId())).abs().toSeconds();
                sinceLastAttack = Math.min(sinceLastAttack, COOLDOWN);

                String combatBar = ChatColor.RED + "|".repeat(COOLDOWN - sinceLastAttack) + ChatColor.GREEN + "|".repeat(sinceLastAttack);
                String remainingTime = ChatColor.WHITE + String.valueOf(COOLDOWN - sinceLastAttack) + "s";
                String combatIndicator = ChatColor.DARK_AQUA + "Combat" + ChatColor.WHITE + " » ";
                damager.sendActionBar(Component.text(combatIndicator + combatBar + "   " + remainingTime + " ".repeat(4 - (remainingTime.length() - 2))));

                if (sinceLastAttack < COOLDOWN) Bukkit.getScheduler().runTaskLater(plugin, this, 1L);
                else TGFCombat.sendMessage(damager, ChatColor.GREEN + "You are no longer in combat.");
            }
        };

        if (!isInCombat(damager)) {
            TGFCombat.sendMessage(damager, ChatColor.YELLOW + "You are in combat! " + ChatColor.RED + "Do not log out!");
            Bukkit.getScheduler().runTask(plugin, task);
        }

        if (!isInCombat(entity)) TGFCombat.sendMessage(entity, ChatColor.YELLOW + damager.getName() + " is attacking you!");
        else lastAttack.put(entity.getUniqueId(), Instant.now()); // if both are in combat, reset both timers

        lastAttack.put(damager.getUniqueId(), Instant.now());
        return true;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player entity) {
            if (event.getDamager() instanceof Player damager && !triggerCombat(entity, damager)) {
                event.setCancelled(true);
            } else if (event.getDamager() instanceof EnderCrystal crystal && crystalAttackers.containsKey(crystal.getEntityId())) {
                final Player attacker = plugin.getServer().getPlayer(crystalAttackers.get(crystal.getEntityId()));
                if (attacker == null || attacker.equals(entity)) return;
                if (!triggerCombat(entity, attacker)) event.setCancelled(true);
            } else if (event.getDamager() instanceof ThrownPotion potion && potion.getItem().getType().equals(Material.LINGERING_POTION)
                && potion.getShooter() instanceof Player damager && !triggerCombat(entity, damager)) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof EnderCrystal crystal && event.getDamager() instanceof Player damager) {
            crystalAttackers.put(crystal.getEntityId(), damager.getUniqueId());
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player damager)) return;
        if (event.getEntity() instanceof ThrownPotion) return; // if a potion is thrown, let it be handled by onPotionSplash

        if (event.getHitEntity() instanceof Player entity) {
            if (entity.getUniqueId().equals(damager.getUniqueId())) return; // self shooting shouldn't trigger combat

            final boolean shouldEnterCombat = triggerCombat(entity, damager);
            if (!shouldEnterCombat) {
                event.setCancelled(true);
            }
        } else if (event.getHitEntity() instanceof EnderCrystal crystal) {
            crystalAttackers.put(crystal.getEntityId(), damager.getUniqueId());
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player damager)) return;

        // splashing ONLY yourself shouldn't have an effect
        if (event.getAffectedEntities().stream().filter(Player.class::isInstance).map(Entity::getUniqueId).allMatch(damager.getUniqueId()::equals)) return;

        // if none of the effects are negative, don't do anything
        if (event.getEntity().getEffects().stream().map(PotionEffect::getType).noneMatch(NEGATIVE_EFFECTS::contains)) return;

        boolean shouldCancel = event.getAffectedEntities().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .anyMatch(player -> !triggerCombat(player, damager));

        // sadly there is no way of canceling on a player-per-player basis, so we just have to cancel it for everyone
        if (shouldCancel) event.setCancelled(true);
    }

    @EventHandler
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        if (!(event.getEntity().getSource() instanceof Player damager)) return;

        // splashing yourself shouldn't have an effect
        if (event.getAffectedEntities().stream().filter(Player.class::isInstance).map(Entity::getUniqueId).allMatch(damager.getUniqueId()::equals)) return;

        // if the effect isn't negative, don't do anything
        if (!NEGATIVE_EFFECTS.contains(event.getEntity().getBasePotionData().getType().getEffectType())) return;

        boolean shouldCancel = event.getAffectedEntities().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .anyMatch(player -> !triggerCombat(player, damager));

        // sadly there is no way of canceling on a player-per-player basis, so we just have to cancel it for everyone
        if (shouldCancel) event.setCancelled(true);

    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        final Player player = event.getPlayer();

        if (block == null) return;

        // FIXME respawn anchors
        if (block.getBlockData() instanceof Bed) {
            if (player.getWorld().getEnvironment() == World.Environment.NORMAL) {
                return;
            }

            lastVolatileBlockAttackers.put(block.getLocation(), player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (isInCombat(event.getPlayer())) event.getPlayer().setHealth(0);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isInCombat(event.getPlayer())) return;

        // TODO blocked commands in config file
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
        lastAttack.remove(event.getEntity().getUniqueId());
    }
}
