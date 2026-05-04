package me.gadse.antiseedcracker.slime;

import me.gadse.antiseedcracker.AntiSeedCracker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Slime anti-crack module — replaces the vanilla slime chunk formula with a
 * plugin-salted one so seed crackers cannot derive slime chunks from the
 * world seed. Scheduled work uses Folia-compatible APIs (RegionScheduler /
 * EntityScheduler), which Paper 26.1 ships natively.
 */
public final class SlimeAntiCrackModule implements Listener {

    private static final String CONFIG_FILE = "slime.yml";
    private static final String MESSAGES_FILE = "slime-messages.yml";
    private static final String SALT_FILE = "slime-salt.dat";
    private static final long MULT_X = 341873128712L;
    private static final long MULT_Z = 132897987541L;

    private final AntiSeedCracker plugin;
    private final AtomicLong salt = new AtomicLong();
    private volatile SlimeConfig cfg;
    private volatile SlimeMessages msg;
    private volatile boolean enabled;

    public SlimeAntiCrackModule(AntiSeedCracker plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().severe("Could not create plugin data folder for slime module");
        }
        plugin.saveResource(CONFIG_FILE, false);
        plugin.saveResource(MESSAGES_FILE, false);
        reload();

        if (!enabled) {
            plugin.getLogger().info("Slime anti-crack module disabled via " + CONFIG_FILE + " (enabled: false).");
            return;
        }

        boolean generated = loadOrCreateSalt();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) {
            startSpawnLoop(p);
        }
        plugin.getLogger().info("Slime anti-crack module enabled (salt "
                + (generated ? "generated" : "loaded") + ").");
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        // EntityScheduler tasks are cancelled automatically when the plugin disables.
        enabled = false;
    }

    /** Reload config + messages. Does not regenerate salt. */
    public void reload() {
        File cfgFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        File msgFile = new File(plugin.getDataFolder(), MESSAGES_FILE);
        cfg = SlimeConfig.load(YamlConfiguration.loadConfiguration(cfgFile));
        msg = SlimeMessages.load(YamlConfiguration.loadConfiguration(msgFile));
        this.enabled = cfg.enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SlimeMessages messages() {
        return msg;
    }

    private boolean loadOrCreateSalt() {
        File f = new File(plugin.getDataFolder(), SALT_FILE);
        if (f.exists()) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
                salt.set(in.readLong());
                return false;
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to read " + SALT_FILE + ", regenerating: " + e.getMessage());
            }
        }
        return regenerateSalt();
    }

    /** Generate a fresh salt and persist it. Returns true on success. */
    public boolean regenerateSalt() {
        long s = new SecureRandom().nextLong();
        salt.set(s);
        File f = new File(plugin.getDataFolder(), SALT_FILE);
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            out.writeLong(s);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write " + SALT_FILE + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isCustomSlimeChunk(int chunkX, int chunkZ) {
        long key = (chunkX * MULT_X + chunkZ * MULT_Z) ^ salt.get();
        return new Random(key).nextInt(cfg.chunkDivisor) == 0;
    }

    private boolean isVanillaSwampSpawn(Biome biome, int y) {
        SlimeConfig c = cfg;
        if (!c.swampEnabled) return false;
        if (biome == Biome.SWAMP) return y >= c.swampYMin && y <= c.swampYMax;
        if (biome == Biome.MANGROVE_SWAMP) return y >= c.mangroveYMin && y <= c.mangroveYMax;
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.SLIME) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        Location loc = e.getLocation();
        Biome biome = loc.getBlock().getBiome();
        if (isVanillaSwampSpawn(biome, loc.getBlockY())) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        startSpawnLoop(e.getPlayer());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        if (!cfg.cleanupEnabled) return;
        Chunk c = e.getChunk();
        int cx = c.getX();
        int cz = c.getZ();
        if (isCustomSlimeChunk(cx, cz)) return;

        for (Entity entity : e.getEntities()) {
            if (entity.getType() != EntityType.SLIME) continue;
            if (entity.customName() != null) continue;
            if (!entity.getScoreboardTags().isEmpty()) continue;

            Location eloc = entity.getLocation();
            Biome biome = eloc.getBlock().getBiome();
            if (isVanillaSwampSpawn(biome, eloc.getBlockY())) continue;

            entity.remove();
        }
    }

    private void startSpawnLoop(Player player) {
        SlimeConfig c = cfg;
        player.getScheduler().runAtFixedRate(
                plugin,
                task -> runSpawnTick(player),
                () -> { },
                c.tickPeriodTicks,
                c.tickPeriodTicks
        );
    }

    private void runSpawnTick(Player player) {
        if (!player.isOnline()) return;
        SlimeConfig c = cfg;
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        int pcx = loc.getBlockX() >> 4;
        int pcz = loc.getBlockZ() >> 4;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int r = c.radiusChunks;
        double chance = c.perChunkTickChance;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!isCustomSlimeChunk(cx, cz)) continue;
                if (rng.nextDouble() >= chance) continue;
                Bukkit.getRegionScheduler().execute(plugin, world, cx, cz,
                        () -> tryPluginSpawn(world, cx, cz));
            }
        }
    }

    private void tryPluginSpawn(World world, int cx, int cz) {
        if (!world.isChunkLoaded(cx, cz)) return;
        SlimeConfig c = cfg;

        Chunk chunk = world.getChunkAt(cx, cz);
        int slimeCount = 0;
        for (Entity e : chunk.getEntities()) {
            if (e.getType() == EntityType.SLIME) {
                slimeCount++;
                if (slimeCount >= c.maxPerChunk) return;
            }
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int yRange = c.maxY - c.minY;
        if (yRange <= 0) return;
        for (int attempt = 0; attempt < c.placementAttempts; attempt++) {
            int bx = (cx << 4) + rng.nextInt(16);
            int bz = (cz << 4) + rng.nextInt(16);
            int by = c.minY + rng.nextInt(yRange);

            Block feet = world.getBlockAt(bx, by, bz);
            Block head = world.getBlockAt(bx, by + 1, bz);
            Block ground = world.getBlockAt(bx, by - 1, bz);

            if (!feet.getType().isAir()) continue;
            if (!head.getType().isAir()) continue;
            if (!ground.getType().isSolid()) continue;
            if (feet.getLightLevel() > c.maxLightLevel) continue;

            int size = 1 << rng.nextInt(3);
            Location spawnLoc = new Location(world, bx + 0.5, by, bz + 0.5);
            world.spawn(spawnLoc, Slime.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                    s -> s.setSize(size));
            return;
        }
    }

    // -------- command handlers (delegated from /asc slime ...) --------

    public void handleCheck(CommandSender sender, String[] args) {
        // args = [check, x?, z?]
        int cx;
        int cz;
        if (args.length == 1) {
            if (!(sender instanceof Player p)) {
                msg.send(sender, "console-need-coords");
                return;
            }
            Location l = p.getLocation();
            cx = l.getBlockX() >> 4;
            cz = l.getBlockZ() >> 4;
        } else if (args.length == 3) {
            try {
                cx = Integer.parseInt(args[1]);
                cz = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                msg.send(sender, "invalid-coords");
                return;
            }
        } else {
            msg.send(sender, "usage-check");
            return;
        }
        boolean slime = isCustomSlimeChunk(cx, cz);
        msg.send(sender, slime ? "check-slime" : "check-not-slime", "cx", cx, "cz", cz);
    }

    public void handleFind(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "find-player-only");
            return;
        }
        SlimeConfig c = cfg;
        int radius = c.findRadiusChunks;
        Location loc = p.getLocation();
        int pcx = loc.getBlockX() >> 4;
        int pcz = loc.getBlockZ() >> 4;
        List<int[]> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (isCustomSlimeChunk(cx, cz)) {
                    found.add(new int[]{cx, cz, dx * dx + dz * dz});
                }
            }
        }
        found.sort((a, b) -> Integer.compare(a[2], b[2]));
        int limit = Math.min(found.size(), 10);
        if (found.isEmpty()) {
            msg.send(sender, "find-none", "radius", radius);
            return;
        }
        msg.send(sender, "find-header", "count", limit);
        for (int i = 0; i < limit; i++) {
            int[] e = found.get(i);
            int bx = e[0] << 4;
            int bz = e[1] << 4;
            msg.send(sender, "find-entry", "cx", e[0], "cz", e[1], "bx", bx, "bz", bz);
        }
    }

    public void handleShuffleSalt(CommandSender sender, boolean confirmed) {
        if (!confirmed) {
            msg.send(sender, "shuffle-warn");
            return;
        }
        boolean ok = regenerateSalt();
        msg.send(sender, ok ? "shuffle-done" : "shuffle-failed");
    }

    @SuppressWarnings("unused")
    private static int highestSolidY(World world, int x, int z) {
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
    }
}
