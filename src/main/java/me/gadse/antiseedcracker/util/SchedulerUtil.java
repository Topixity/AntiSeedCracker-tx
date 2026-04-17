package me.gadse.antiseedcracker.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public final class SchedulerUtil {

    private static final boolean FOLIA;

    static {
        boolean detected;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            detected = true;
        } catch (ClassNotFoundException e) {
            detected = false;
        }
        FOLIA = detected;
    }

    private SchedulerUtil() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    public static TaskHandle runAtLocationTimer(Plugin plugin, Location location, Runnable task,
                                                long initialDelayTicks, long periodTicks) {
        long delay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            var scheduled = Bukkit.getRegionScheduler().runAtFixedRate(
                    plugin, location, scheduledTask -> task.run(), delay, period);
            return scheduled::cancel;
        }
        var bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return bukkitTask::cancel;
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }
}
