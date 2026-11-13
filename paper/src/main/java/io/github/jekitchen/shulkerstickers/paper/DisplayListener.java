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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import java.util.List;

/**
 * Keeps display entities in step with the world. Chunk work is re-scheduled on the
 * chunk's region so it is correct on Folia/Canvas whichever thread fires the event.
 */
final class DisplayListener implements Listener {

    /** Roughly how long the lid takes to close, so displays come back once it's shut. */
    private static final long LID_CLOSE_TICKS = 10;

    private final org.bukkit.plugin.Plugin plugin;
    private final Settings settings;
    private final DisplayManager displays;

    DisplayListener(org.bukkit.plugin.Plugin plugin, Settings settings, DisplayManager displays) {
        this.plugin = plugin;
        this.settings = settings;
        this.displays = displays;
    }

    /**
     * The lid rises when a box opens: displays on the lid face move with it, the others are
     * hidden while it's open (PRD §5.5).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onOpen(InventoryOpenEvent event) {
        // Vanilla doesn't move the lid for spectators (ShulkerBoxBlockEntity#startOpen).
        if (event.getInventory().getHolder(false) instanceof ShulkerBox box && !isSpectator(event.getPlayer())) {
            Block block = box.getBlock();
            onBoxThread(block, () -> displays.opened(BlockKey.of(block)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ShulkerBox box) || isSpectator(event.getPlayer())) {
            return;
        }
        // The lid starts down as soon as the last (non-spectator) viewer closes, like vanilla's
        // stopOpen; the closing player may still be listed as a viewer during this event.
        if (!othersKeepItOpen(event.getInventory().getViewers(), event.getPlayer())) {
            Block block = box.getBlock();
            onBoxThread(block, () -> displays.closed(BlockKey.of(block), LID_CLOSE_TICKS));
        }
    }

    /** Whether anyone besides {@code closing} (spectators don't count, as in vanilla) still has the box open. */
    static boolean othersKeepItOpen(java.util.List<org.bukkit.entity.HumanEntity> viewers,
            org.bukkit.entity.HumanEntity closing) {
        return viewers.stream().anyMatch(viewer -> viewer != closing && !isSpectator(viewer));
    }

    static boolean isSpectator(org.bukkit.entity.HumanEntity who) {
        return who != null && who.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }

    /** Runs now if this thread owns the box's region (so the lid keeps step with vanilla's), else there. */
    private void onBoxThread(Block block, Runnable task) {
        if (plugin.getServer().isOwnedByCurrentRegion(block.getLocation())) {
            task.run();
        } else {
            plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), task);
        }
    }

    @EventHandler
    void onChunkLoad(ChunkLoadEvent event) {
        processLater(event.getChunk());
    }

    /**
     * Chunks already loaded when the plugin is enabled (spawn and force-loaded chunks load before
     * plugins do, and stay loaded) never send a load event, so they get the same pass now.
     */
    void processLoadedChunks() {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            org.bukkit.Chunk[] loaded;
            try {
                loaded = world.getLoadedChunks();
            } catch (RuntimeException e) {
                // Paper, Folia and Canvas 26.3 allow this from the enable thread; a fork that
                // doesn't must not stop the plugin from enabling.
                plugin.getLogger().warning("Can't list the loaded chunks of " + world.getName()
                        + " (" + e.getMessage() + "); boxes in them are drawn when their chunks next load");
                continue;
            }
            for (org.bukkit.Chunk chunk : loaded) {
                processLater(chunk);
            }
        }
    }

    /** On the chunk's region thread: moves Fabric-saved stickers into persistent data, then draws every box. */
    private void processLater(org.bukkit.Chunk chunk) {
        plugin.getServer().getRegionScheduler().execute(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> {
            if (!chunk.isLoaded()) {
                return;
            }
            for (var state : chunk.getTileEntities(b -> Tag.SHULKER_BOXES.isTagged(b.getType()), false)) {
                if (state instanceof ShulkerBox) {
                    // A box saved by Fabric: move its sticker into persistent data first (owner decision 4).
                    WorldMigration.migrate(state.getBlock(), plugin.getLogger());
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
        if (!(event instanceof io.github.jekitchen.shulkerstickers.paper.claims.ClaimTestPlaceEvent)
                && Tag.SHULKER_BOXES.isTagged(event.getBlockPlaced().getType())) {
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
            displays.forget(BlockKey.of(block));
        }
    }
}
