package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Paper keeps a placed box's persistent data on the block entity, separate from the
 * item's {@code custom_data}. Drops are handled by the built-in datapack (see
 * {@link ShulkerStickersBootstrap}); this copies our keys from the item to the block
 * when a player places a box.
 */
final class StickerTransferListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        ItemStack stack = event.getItemInHand();
        if (!Tag.SHULKER_BOXES.isTagged(stack.getType())
                || StickerStore.read(stack.getPersistentDataContainer()).isEmpty()
                || !(event.getBlockPlaced().getState() instanceof TileState state)) {
            return;
        }
        StickerStore.copy(stack.getPersistentDataContainer(), state.getPersistentDataContainer());
        StickerStore.read(stack.getPersistentDataContainer())
                .filter(s -> s.anchor() == Anchor.SIDE)
                .ifPresent(s -> {
                    // A side icon goes on the side facing the player who placed the box (PRD §6.1).
                    var block = event.getBlockPlaced();
                    BlockFace facing = ((Directional) block.getBlockData()).getFacing();
                    Vector toPlayer = event.getPlayer().getEyeLocation().toVector()
                            .subtract(block.getLocation().add(0.5, 0.5, 0.5).toVector());
                    state.getPersistentDataContainer().set(StickerKeys.FACE, PersistentDataType.STRING,
                            Faces.sideToward(facing, toPlayer).name());
                });
        state.update(true, false);
    }
}
