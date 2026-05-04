package me.gadse.antiseedcracker;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import me.gadse.antiseedcracker.commands.AntiSeedCrackerCommand;
import me.gadse.antiseedcracker.listeners.DragonRespawnSpikeModifier;
import me.gadse.antiseedcracker.listeners.EndCityModifier;
import me.gadse.antiseedcracker.packets.ServerLogin;
import me.gadse.antiseedcracker.packets.ServerRespawn;
import me.gadse.antiseedcracker.slime.SlimeAntiCrackModule;
import me.gadse.antiseedcracker.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.PluginCommand;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AntiSeedCracker extends JavaPlugin {

    private static final Location END_SPIKE_ANCHOR = new Location(null, 0.5, 65, 0.5);

    private ProtocolManager protocolManager;
    private NamespacedKey modifiedSpike;

    private DragonRespawnSpikeModifier dragonRespawnSpikeModifier;
    private EndCityModifier endCityModifier;
    private SlimeAntiCrackModule slimeModule;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Config folder can not be written. Check read/write permissions.");
        }
        saveDefaultConfig();

        protocolManager = ProtocolLibrary.getProtocolManager();
        modifiedSpike = new NamespacedKey(this, "modified-spike");
        dragonRespawnSpikeModifier = new DragonRespawnSpikeModifier(this);
        endCityModifier = new EndCityModifier(this);
        slimeModule = new SlimeAntiCrackModule(this);

        PluginCommand command = getCommand("antiseedcracker");
        if (command == null) {
            getLogger().severe("The antiseedcracker command is missing from plugin.yml.");
        } else {
            AntiSeedCrackerCommand executor = new AntiSeedCrackerCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        reload(true);

        getLogger().info("AntiSeedCracker v" + getDescription().getVersion()
                + " enabled (Folia="
                + SchedulerUtil.isFolia() + ").");
    }

    public void reload(boolean isOnEnable) {
        if (!isOnEnable) {
            protocolManager.removePacketListeners(this);
            dragonRespawnSpikeModifier.unregister();
            endCityModifier.unregister();
            slimeModule.disable();
        }

        if (getConfig().getBoolean("randomize_hashed_seed.login", true)) {
            protocolManager.addPacketListener(new ServerLogin(this));
        }

        if (getConfig().getBoolean("randomize_hashed_seed.respawn", true)) {
            protocolManager.addPacketListener(new ServerRespawn(this));
        }

        if (getConfig().getBoolean("modifiers.end_spikes.enabled", false)) {
            List<String> allowedWorlds = getConfig().getStringList("modifiers.end_spikes.worlds");
            for (World world : getServer().getWorlds()) {
                if (!allowedWorlds.contains(world.getName())) {
                    continue;
                }
                if (world.getEnvironment() != World.Environment.THE_END) {
                    getLogger().warning("The world '" + world.getName()
                            + "' is not an end dimension, it will be ignored.");
                    continue;
                }
                scheduleEndSpikeModification(world);
            }
            getServer().getPluginManager().registerEvents(dragonRespawnSpikeModifier, this);
        }

        if (getConfig().getBoolean("modifiers.end_cities.enabled", false)) {
            getServer().getPluginManager().registerEvents(endCityModifier, this);
        }

        slimeModule.enable();
    }

    public SlimeAntiCrackModule getSlimeModule() {
        return slimeModule;
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
        if (dragonRespawnSpikeModifier != null) {
            dragonRespawnSpikeModifier.unregister();
        }
        if (endCityModifier != null) {
            endCityModifier.unregister();
        }
        if (slimeModule != null) {
            slimeModule.disable();
        }
    }

    public long randomizeHashedSeed(long hashedSeed) {
        int length = Long.toString(Math.abs(hashedSeed)).length();
        if (length < 2) {
            length = 2;
        }
        if (length > 18) {
            length = 18;
        }
        long min = (long) Math.pow(10, length - 1);
        long max = (long) (Math.pow(10, length) - 1);
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    // https://minecraft.wiki/w/End_spike
    private static final List<Integer> SPIKE_HEIGHTS =
            List.of(76, 79, 82, 85, 88, 91, 94, 97, 100, 103);

    /**
     * Dispatches the end spike modification onto the region thread that owns
     * the portal column (0, 65, 0) of the given end world. Safe on both Paper
     * and Folia — on Paper this runs on the next main-thread tick, on Folia it
     * runs on the owning region thread for that chunk.
     */
    public void scheduleEndSpikeModification(World world) {
        Location anchor = new Location(world, END_SPIKE_ANCHOR.getX(),
                END_SPIKE_ANCHOR.getY(), END_SPIKE_ANCHOR.getZ());
        SchedulerUtil.runAtLocation(this, anchor, () -> modifyEndSpikes(world));
    }

    public void modifyEndSpikes(World world) {
        if (world.getEnvironment() != World.Environment.THE_END
                || world.getPersistentDataContainer()
                        .getOrDefault(modifiedSpike, PersistentDataType.BOOLEAN, false)) {
            return;
        }

        Map<Integer, Block> bedrockBlocksByHeight = getBedrockBlocksByHeight(world);
        if (bedrockBlocksByHeight.isEmpty()) {
            getLogger().warning("No bedrock end spikes found in world '"
                    + world.getName() + "', skipping modification.");
            return;
        }

        String mode = getConfig().getString("modifiers.end_spikes.mode", "move");
        if ("swap".equalsIgnoreCase(mode)) {
            swapEndSpikes(world, bedrockBlocksByHeight);
        } else {
            moveEndSpike(world, bedrockBlocksByHeight);
        }
    }

    private void swapEndSpikes(World world, Map<Integer, Block> bedrockBlocksByHeight) {
        List<Integer> availableHeights = SPIKE_HEIGHTS.stream()
                .filter(bedrockBlocksByHeight::containsKey)
                .toList();
        if (availableHeights.size() < 2) {
            getLogger().warning("Not enough end spikes to swap in world '"
                    + world.getName() + "'.");
            return;
        }

        int randomSpikeIndex = ThreadLocalRandom.current().nextInt(availableHeights.size());
        int nextSpikeIndex = (randomSpikeIndex + 1) % availableHeights.size();
        Block spikeOne = bedrockBlocksByHeight.get(availableHeights.get(randomSpikeIndex));
        Block spikeTwo = bedrockBlocksByHeight.get(availableHeights.get(nextSpikeIndex));

        spikeOne.setType(Material.OBSIDIAN);
        world.getBlockAt(spikeOne.getX(), spikeTwo.getY(), spikeOne.getZ())
                .setType(Material.BEDROCK);

        spikeTwo.setType(Material.OBSIDIAN);
        world.getBlockAt(spikeTwo.getX(), spikeOne.getY(), spikeTwo.getZ())
                .setType(Material.BEDROCK);

        world.getPersistentDataContainer().set(modifiedSpike, PersistentDataType.BOOLEAN, true);
    }

    private void moveEndSpike(World world, Map<Integer, Block> bedrockBlocksByHeight) {
        List<Integer> availableHeights = bedrockBlocksByHeight.keySet().stream().toList();
        if (availableHeights.isEmpty()) {
            return;
        }
        int randomSpikeIndex = ThreadLocalRandom.current().nextInt(availableHeights.size());
        Block endSpike = bedrockBlocksByHeight.get(availableHeights.get(randomSpikeIndex));

        endSpike.setType(Material.OBSIDIAN);
        endSpike.getRelative(BlockFace.DOWN).setType(Material.BEDROCK);

        world.getPersistentDataContainer().set(modifiedSpike, PersistentDataType.BOOLEAN, true);
    }

    public Map<Integer, Block> getBedrockBlocksByHeight(World world) {
        Map<Integer, Block> bedrockBlocksByHeight = new HashMap<>(10);

        for (int i = 0; i < 10; i++) {
            // Source: net.minecraft.world.level.levelgen.feature.SpikeFeature.SpikeCacheLoader
            double x = 42.0 * Math.cos(2.0 * (-Math.PI + 0.3141592653589793 * i));
            double z = 42.0 * Math.sin(2.0 * (-Math.PI + 0.3141592653589793 * i));

            Block block = world.getHighestBlockAt((int) Math.floor(x), (int) Math.floor(z));
            int guard = 0;
            while (block.getType() != Material.BEDROCK && block.getY() > world.getMinHeight()) {
                block = block.getRelative(BlockFace.DOWN);
                if (++guard > 320) {
                    break;
                }
            }
            if (block.getType() == Material.BEDROCK) {
                bedrockBlocksByHeight.put(block.getY(), block);
            }
        }

        return bedrockBlocksByHeight;
    }

    public NamespacedKey getModifiedSpike() {
        return modifiedSpike;
    }
}
