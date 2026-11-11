package io.github.jekitchen.shulkerstickers.paper;

import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import java.util.List;

/**
 * Keeps display entities in step with the world. Chunk work is re-scheduled on the
 * chunk's region so it is correct on Folia/Canvas whichever thread fires the event.
 */
final class DisplayListener implements Listener {

    private final org.bukkit.plugin.Plugin plugin;
    private final DisplayManager displays;

    DisplayListener(org.bukkit.plugin.Plugin plugin, DisplayManager displays) {
        this.plugin = plugin;
        this.displays = displays;
    }

    @EventHandler
    void onChunkLoad(ChunkLoadEvent event) {
        var chunk = event.getChunk();
        plugin.getServer().getRegionScheduler().execute(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> {
            if (!chunk.isLoaded()) {
                return;
            }
            for (var state : chunk.getTileEntities(b -> Tag.SHULKER_BOXES.isTagged(b.getType()), false)) {
                if (state instanceof ShulkerBox) {
                    displays.refresh(state.getBlock());
                }
            }
        });
    }

    @EventHandler
    void onChunkUnload(ChunkUnloadEvent event) {
        // Our displays are never saved; forget them so they are re-made on the next load.
        var chunk = event.getChunk();
        displays.removeChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        if (Tag.SHULKER_BOXES.isTagged(event.getBlockPlaced().getType())) {
            displays.refreshLater(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        removeIfBox(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDestroy(BlockDestroyEvent event) {
        removeIfBox(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::removeIfBox);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::removeIfBox);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPistonExtend(BlockPistonExtendEvent event) {
        removeAll(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPistonRetract(BlockPistonRetractEvent event) {
        removeAll(event.getBlocks());
    }

    private void removeAll(List<Block> blocks) {
        blocks.forEach(this::removeIfBox);
    }

    private void removeIfBox(Block block) {
        if (Tag.SHULKER_BOXES.isTagged(block.getType())) {
            displays.remove(BlockKey.of(block));
        }
    }
}
