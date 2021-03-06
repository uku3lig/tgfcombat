package org.tgforever.tgfcombat;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class CombatListener implements Listener {
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
    private static final Node INSTANT_TP_NODE = Node.builder("essentials.teleport.timer.bypass").build();

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final Map<UUID, Instant> lastAttack = new HashMap<>();
    private final Map<Integer, UUID> crystalAttackers = new HashMap<>();
    private final Map<Integer, UUID> tntMinecartAttackers = new HashMap<>();

    private final Set<UUID> kickedPlayers = new HashSet<>();

    public CombatListener(JavaPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getServer().getOnlinePlayers().stream()
                .filter(this::isInCombat)
                .filter(p -> isPvpProtected(p.getLocation()))
                .forEach(p -> {
                    p.setHealth(Math.max(0, p.getHealth() - 5));
                    p.damage(0.01); // this is done to play the damage animation
                    // you should use betterhurtcam btw :D
                    lastAttack.put(p.getUniqueId(), Instant.now());
                }), 0, 20); // 20 ticks = 1 second
    }

    public boolean isInCombat(Player player) {
        if (player.isDead()) return false;
        if (!lastAttack.containsKey(player.getUniqueId())) return false;
        return lastAttack.get(player.getUniqueId()).plusSeconds(plugin.getConfig().getInt("combat-tag-length")).isAfter(Instant.now());
    }

    private boolean triggerCombat(Player entity, Player damager) {
        if (isPvpProtected(entity.getLocation()) || isPvpProtected(damager.getLocation())) return false;
        if (damager.getGameMode().equals(GameMode.CREATIVE)) return false;

        int cooldown = plugin.getConfig().getInt("combat-tag-length");

        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (damager.isDead()) return;

                lastAttack.computeIfAbsent(damager.getUniqueId(), u -> Instant.now());
                int sinceLastAttack = (int) Duration.between(Instant.now(), lastAttack.get(damager.getUniqueId())).abs().toSeconds();
                sinceLastAttack = Math.min(sinceLastAttack, cooldown);

                String combatBar = ChatColor.RED + "|".repeat(cooldown - sinceLastAttack) + ChatColor.GREEN + "|".repeat(sinceLastAttack);
                String remainingTime = ChatColor.WHITE + String.valueOf(cooldown - sinceLastAttack) + "s";
                String combatIndicator = ChatColor.DARK_AQUA + "Combat" + ChatColor.WHITE + " ?? ";
                damager.sendActionBar(Component.text(combatIndicator + combatBar + "   " + remainingTime + " ".repeat(4 - (remainingTime.length() - 2))));

                if (sinceLastAttack < cooldown) Bukkit.getScheduler().runTaskLater(plugin, this, 1L);
                else {
                    luckPerms.getUserManager().modifyUser(entity.getUniqueId(), u -> u.data().remove(INSTANT_TP_NODE));
                    TGFCombat.sendMessage(damager, ChatColor.GREEN + "You are no longer in combat.");
                }
            }
        };

        if (!isInCombat(damager)) {
            TGFCombat.sendMessage(damager, ChatColor.YELLOW + "You are in combat! " + ChatColor.RED + "Do not log out!");
            Bukkit.getScheduler().runTask(plugin, task);
        }

        if (!isInCombat(entity)) {
            luckPerms.getUserManager().modifyUser(entity.getUniqueId(), u -> u.data().add(INSTANT_TP_NODE));
            TGFCombat.sendMessage(entity, ChatColor.YELLOW + damager.getName() + " is attacking you!");
        } else lastAttack.put(entity.getUniqueId(), Instant.now()); // if both are in combat, reset both timers

        lastAttack.put(damager.getUniqueId(), Instant.now());
        return isInCombat(entity);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player entity) {
            if (event.getDamager() instanceof Player damager && !event.getCause().equals(EntityDamageEvent.DamageCause.THORNS) && !triggerCombat(entity, damager)) {
                event.setDamage(event.getDamage() / 2.5);
            } else if (event.getDamager() instanceof EnderCrystal crystal && crystalAttackers.containsKey(crystal.getEntityId())) {
                final Player attacker = plugin.getServer().getPlayer(crystalAttackers.get(crystal.getEntityId()));
                if (attacker == null || attacker.getUniqueId().equals(entity.getUniqueId())) return;
                if (!triggerCombat(entity, attacker)) {
                    // listen. this works.
                    event.setDamage(event.getDamage() - Math.min(115, event.getFinalDamage() * 4));
                }
            } else if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player damager
                    && !damager.getUniqueId().equals(entity.getUniqueId()) && !triggerCombat(entity, damager)) {
                event.setDamage(event.getDamage() / 3);
                // disable potion effects
                arrow.setBasePotionData(new PotionData(PotionType.UNCRAFTABLE));
            } else if (event.getDamager() instanceof TNTPrimed tnt && tnt.getSource() instanceof Player damager
                    && !triggerCombat(entity, damager)) {
                event.setDamage(event.getDamage() / 4);
            } else if (event.getDamager() instanceof ExplosiveMinecart minecart && tntMinecartAttackers.containsKey(minecart.getEntityId())) {
                final Player damager = plugin.getServer().getPlayer(tntMinecartAttackers.get(minecart.getEntityId()));
                if (damager == null || damager.getUniqueId().equals(entity.getUniqueId())) return;
                if (!triggerCombat(entity, damager)) event.setDamage(event.getDamage() / 6);
            }
        } else if (event.getEntity() instanceof EnderCrystal crystal && event.getDamager() instanceof Player damager) {
            crystalAttackers.put(crystal.getEntityId(), damager.getUniqueId());
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player damager)) return;

        // if a potion is thrown, let it be handled by onPotionSplash
        if (event.getEntity() instanceof ThrownPotion) return;

        if (event.getHitEntity() instanceof Player entity) {
            if (entity.getUniqueId().equals(damager.getUniqueId())) return; // self shooting shouldn't trigger combat
            triggerCombat(entity, damager);
        } else if (event.getHitEntity() instanceof EnderCrystal crystal) {
            crystalAttackers.put(crystal.getEntityId(), damager.getUniqueId());
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player damager)) return;

        // splashing ONLY yourself shouldn't have an effect
        if (event.getAffectedEntities().stream().filter(Player.class::isInstance).map(Entity::getUniqueId).allMatch(damager.getUniqueId()::equals))
            return;

        // if none of the effects are negative, don't do anything
        if (event.getEntity().getEffects().stream().map(PotionEffect::getType).noneMatch(NEGATIVE_EFFECTS::contains))
            return;

        event.getAffectedEntities().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .filter(player -> !triggerCombat(player, damager))
                .forEach(player -> event.setIntensity(player, event.getIntensity(player) / 3));
    }

    @EventHandler
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        if (!(event.getEntity().getSource() instanceof Player damager)) return;

        // splashing yourself shouldn't have an effect
        if (event.getAffectedEntities().stream().filter(Player.class::isInstance).map(Entity::getUniqueId).allMatch(damager.getUniqueId()::equals))
            return;

        // if the effect isn't negative, don't do anything
        if (!NEGATIVE_EFFECTS.contains(event.getEntity().getBasePotionData().getType().getEffectType())) return;

        event.getAffectedEntities().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .forEach(player -> triggerCombat(player, damager));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        final Player damager = event.getPlayer();

        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        if (block == null) return;

        Set<Player> affectedPlayers = block.getWorld().getPlayers().stream()
                .filter(p -> isInExplosionRange(p, block) && !p.getUniqueId().equals(damager.getUniqueId()))
                .collect(Collectors.toSet());

        if (!affectedPlayers.stream().allMatch(this::isInCombat)) {
            TGFCombat.sendMessage(damager, ChatColor.YELLOW + "You cannot blow up this block as someone is in explosion range.");
            event.setCancelled(true);
        } else {
            affectedPlayers.forEach(p -> triggerCombat(p, damager));
        }
    }

    @EventHandler
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!event.getEntityType().equals(EntityType.MINECART_TNT)) return;
        if (event.getPlayer() == null) return;
        tntMinecartAttackers.put(event.getEntity().getEntityId(), event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        luckPerms.getUserManager().modifyUser(event.getPlayer().getUniqueId(), u -> u.data().remove(INSTANT_TP_NODE));
        if (kickedPlayers.contains(event.getPlayer().getUniqueId())) {
            kickedPlayers.remove(event.getPlayer().getUniqueId());
        } else if (isInCombat(event.getPlayer())) {
            event.getPlayer().setHealth(0);
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        kickedPlayers.add(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isInCombat(event.getPlayer())) return;

        String command = event.getMessage().split(" ")[0].toLowerCase();
        if (plugin.getConfig().getStringList("blacklisted-commands").contains(command)) {
            TGFCombat.sendMessage(event.getPlayer(), ChatColor.RED + "You may not use that command while in combat.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        lastAttack.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isInCombat(event.getPlayer())) return;
        if (!isPvpProtected(event.getTo()) || isPvpProtected(event.getFrom())) return;

        Player player = event.getPlayer();
        if (player.isInsideVehicle() && !player.leaveVehicle()) {
            event.setCancelled(true);
            return;
        }

        Runnable task = () -> {
            Vector from = event.getFrom().toVector();
            Vector to = event.getTo().toVector();

            Vector velocity = from.subtract(to)
                    .normalize()
                    .multiply(1.5)
                    .setY(0);

            player.setVelocity(velocity);
        };

        Bukkit.getScheduler().runTaskLater(plugin, task, 1L);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!isInCombat(event.getPlayer())) return;
        if (!isPvpProtected(event.getTo()) || isPvpProtected(event.getFrom())) return;

        event.setCancelled(true);
    }

    private boolean isPvpProtected(Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(location.getWorld()));
        if (manager == null) return false;

        return manager.getRegions().values().stream()
                .filter(r -> Objects.equals(r.getFlag(Flags.PVP), StateFlag.State.DENY))
                .anyMatch(r -> r.contains(BukkitAdapter.asBlockVector(location)));
    }

    private boolean isInExplosionRange(Player player, Block block) {
        BlockData data = block.getBlockData();
        Environment env = block.getWorld().getEnvironment();

        if (!(data instanceof Bed || data instanceof RespawnAnchor)) return false;
        if (data instanceof Bed && block.getWorld().getEnvironment().equals(Environment.NORMAL)) return false;
        if (data instanceof RespawnAnchor anchor && (env.equals(Environment.NETHER) || anchor.getCharges() == 0))
            return false;

        double distance = Math.sqrt(block.getLocation().distanceSquared(player.getLocation()));
        // beds and anchors have an explosion power of 5
        // so entities will not take damage if the distance is more than 2*power = 2*5 = 10 blocks
        // this was found inspecting the minecraft code, Explosion#collectBlocksAndDamageEntities
        // this is like 99% accurate, but the error margin is a fraction of a block, nothing is going to change at these distances
        return distance <= 10;
    }
}
