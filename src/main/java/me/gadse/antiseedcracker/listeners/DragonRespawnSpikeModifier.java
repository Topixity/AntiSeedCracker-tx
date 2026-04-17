package me.gadse.antiseedcracker.listeners;

import me.gadse.antiseedcracker.AntiSeedCracker;
import me.gadse.antiseedcracker.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DragonRespawnSpikeModifier implements Listener {

    private final AntiSeedCracker plugin;
    private final AtomicBoolean taskScheduled = new AtomicBoolean(false);
    private final AtomicReference<SchedulerUtil.TaskHandle> activeTask = new AtomicReference<>();

    private final EntityType crystalType;

    public DragonRespawnSpikeModifier(AntiSeedCracker plugin) {
        this.plugin = plugin;

        EntityType resolved;
        try {
            resolved = EntityType.END_CRYSTAL;
        } catch (NoSuchFieldError ignored) {
            // Support for versions below 1.20.5 where END_CRYSTAL was named ENDER_CRYSTAL
            resolved = EntityType.valueOf("ENDER_CRYSTAL");
        }
        this.crystalType = resolved;
    }

    @EventHandler
    public void onPlayerPlaceRespawnCrystals(EntityPlaceEvent event) {
        World world = event.getEntity().getWorld();
        if (event.getEntityType() != crystalType
                || world.getEnvironment() != World.Environment.THE_END
                || event.getBlock().getType() != Material.BEDROCK
                || isOutsidePortalRadius(event.getBlock().getLocation())
                || getAmountOfEnderCrystalsOnPortal(world) != 3
                || !plugin.getConfig().getStringList("modifiers.end_spikes.worlds")
                        .contains(world.getName())
                || !taskScheduled.compareAndSet(false, true)) {
            return;
        }

        world.getPersistentDataContainer()
                .set(plugin.getModifiedSpike(), PersistentDataType.BOOLEAN, false);

        Location anchor = new Location(world, 0.5, 65, 0.5);
        SchedulerUtil.TaskHandle handle = SchedulerUtil.runAtLocationTimer(plugin, anchor, () -> {
            // If unregister() already flipped the state, don't touch the world.
            if (!taskScheduled.get()) {
                cancelActive();
                return;
            }
            try {
                DragonBattle dragonBattle = world.getEnderDragonBattle();
                if (dragonBattle == null) {
                    // Fall-back, should not be reachable.
                    plugin.modifyEndSpikes(world);
                    finish();
                    return;
                }

                DragonBattle.RespawnPhase phase = dragonBattle.getRespawnPhase();
                if (phase == DragonBattle.RespawnPhase.START
                        || phase == DragonBattle.RespawnPhase.PREPARING_TO_SUMMON_PILLARS
                        || phase == DragonBattle.RespawnPhase.SUMMONING_PILLARS) {
                    return;
                }

                plugin.modifyEndSpikes(world);
                finish();
            } catch (Throwable t) {
                plugin.getLogger().warning("Dragon respawn spike modifier tick failed: " + t);
                finish();
            }
        }, 300L, 20L);
        SchedulerUtil.TaskHandle previous = activeTask.getAndSet(handle);
        if (previous != null) {
            // Shouldn't happen thanks to the CAS on taskScheduled, but be safe
            // if a reload slipped in between and left a dangling handle.
            try {
                previous.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    private void finish() {
        taskScheduled.set(false);
        cancelActive();
    }

    private void cancelActive() {
        SchedulerUtil.TaskHandle handle = activeTask.getAndSet(null);
        if (handle != null) {
            try {
                handle.cancel();
            } catch (Throwable ignored) {
            }
        }
    }

    private int getAmountOfEnderCrystalsOnPortal(World world) {
        Location endLocation = new Location(world, 0, 65, 0);
        return world.getNearbyEntities(
                endLocation, 7, 3, 7,
                entity -> entity instanceof EnderCrystal
                        && entity.getLocation().getBlock()
                                .getRelative(BlockFace.DOWN).getType() == Material.BEDROCK
        ).size();
    }

    private boolean isOutsidePortalRadius(Location location) {
        return location.getX() < -3 || location.getX() > 3
                || location.getZ() < -3 || location.getZ() > 3;
    }

    public void unregister() {
        EntityPlaceEvent.getHandlerList().unregister(this);
        taskScheduled.set(false);
        cancelActive();
    }
}
