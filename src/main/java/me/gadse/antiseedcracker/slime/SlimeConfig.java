package me.gadse.antiseedcracker.slime;

import org.bukkit.configuration.file.FileConfiguration;

public final class SlimeConfig {

    public final boolean enabled;

    public final int chunkDivisor;
    public final int radiusChunks;
    public final long tickPeriodTicks;
    public final double perChunkTickChance;
    public final int maxPerChunk;
    public final int minY;
    public final int maxY;
    public final int maxLightLevel;
    public final int placementAttempts;

    public final boolean swampEnabled;
    public final int swampYMin;
    public final int swampYMax;
    public final int mangroveYMin;
    public final int mangroveYMax;

    public final boolean cleanupEnabled;

    public final int findRadiusChunks;

    private SlimeConfig(FileConfiguration c) {
        this.enabled = c.getBoolean("enabled", true);

        this.chunkDivisor = Math.max(2, c.getInt("spawn.chunk-divisor", 10));
        this.radiusChunks = Math.max(1, c.getInt("spawn.radius-chunks", 8));
        this.tickPeriodTicks = Math.max(20L, c.getLong("spawn.tick-period-ticks", 100L));
        this.perChunkTickChance = Math.min(1.0, Math.max(0.0, c.getDouble("spawn.per-chunk-tick-chance", 0.08)));
        this.maxPerChunk = Math.max(1, c.getInt("spawn.max-per-chunk", 4));
        this.minY = c.getInt("spawn.min-y", -40);
        this.maxY = c.getInt("spawn.max-y", 40);
        this.maxLightLevel = Math.max(0, Math.min(15, c.getInt("spawn.max-light-level", 7)));
        this.placementAttempts = Math.max(1, c.getInt("spawn.placement-attempts", 5));

        this.swampEnabled = c.getBoolean("swamp.enabled", true);
        this.swampYMin = c.getInt("swamp.swamp-y-min", 51);
        this.swampYMax = c.getInt("swamp.swamp-y-max", 69);
        this.mangroveYMin = c.getInt("swamp.mangrove-y-min", 50);
        this.mangroveYMax = c.getInt("swamp.mangrove-y-max", 70);

        this.cleanupEnabled = c.getBoolean("cleanup.enabled", true);

        this.findRadiusChunks = Math.max(1, c.getInt("find-radius-chunks", 16));
    }

    public static SlimeConfig load(FileConfiguration cfg) {
        return new SlimeConfig(cfg);
    }
}
