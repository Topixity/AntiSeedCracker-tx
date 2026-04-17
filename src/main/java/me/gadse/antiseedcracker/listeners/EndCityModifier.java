package me.gadse.antiseedcracker.listeners;

import me.gadse.antiseedcracker.AntiSeedCracker;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;

public class EndCityModifier implements Listener {

    private final AntiSeedCracker plugin;
    private final NamespacedKey cityModified;

    public EndCityModifier(AntiSeedCracker plugin) {
        this.plugin = plugin;
        this.cityModified = new NamespacedKey(plugin, "end-city-modified");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.THE_END
                || !plugin.getConfig().getStringList("modifiers.end_cities.worlds")
                        .contains(world.getName())) {
            return;
        }

        Chunk chunk = event.getChunk();
        Collection<GeneratedStructure> structures;
        try {
            structures = chunk.getStructures(Structure.END_CITY);
        } catch (Throwable t) {
            // Defensive: some server forks / older API versions may throw on
            // non-generated chunks. Skip rather than crash the region thread.
            return;
        }
        if (structures.isEmpty()) {
            return;
        }

        for (GeneratedStructure structure : structures) {
            if (structure.getPersistentDataContainer()
                    .getOrDefault(cityModified, PersistentDataType.BOOLEAN, false)) {
                continue;
            }

            int modifiedBlockCount = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 62; y <= 65; y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() == Material.MAGENTA_STAINED_GLASS) {
                            block.setType(Material.MAGENTA_STAINED_GLASS_PANE, false);
                            modifiedBlockCount++;
                        }
                    }
                }
            }
            if (modifiedBlockCount > 0) {
                structure.getPersistentDataContainer()
                        .set(cityModified, PersistentDataType.BOOLEAN, true);
            }
        }
    }

    public void unregister() {
        ChunkLoadEvent.getHandlerList().unregister(this);
    }
}
