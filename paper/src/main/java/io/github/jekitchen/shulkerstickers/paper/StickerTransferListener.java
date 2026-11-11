package io.github.jekitchen.shulkerstickers.paper;

import org.bukkit.Tag;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Paper keeps a placed box's persistent data on the block entity, and vanilla's loot
 * table doesn't copy it to the dropped item. Copy our keys across in both directions so
 * the item carries the same {@code custom_data} that the Fabric build writes.
 */
final class StickerTransferListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDrop(BlockDropItemEvent event) {
        if (!(event.getBlockState() instanceof TileState state)
                || !Tag.SHULKER_BOXES.isTagged(state.getType())
                || StickerStore.read(state.getPersistentDataContainer()).isEmpty()) {
            return;
        }
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            if (Tag.SHULKER_BOXES.isTagged(stack.getType())) {
                stack.editPersistentDataContainer(pdc -> StickerStore.copy(state.getPersistentDataContainer(), pdc));
                drop.setItemStack(stack);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        ItemStack stack = event.getItemInHand();
        if (!Tag.SHULKER_BOXES.isTagged(stack.getType())
                || StickerStore.read(stack.getPersistentDataContainer()).isEmpty()
                || !(event.getBlockPlaced().getState() instanceof TileState state)) {
            return;
        }
        StickerStore.copy(stack.getPersistentDataContainer(), state.getPersistentDataContainer());
        state.update(true, false);
    }
}
