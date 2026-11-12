package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.Optional;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Paper keeps a placed box's persistent data on the block entity, separate from the
 * item's {@code custom_data}. Drops are handled by the built-in datapack (see
 * {@link ShulkerStickersBootstrap}); this copies our keys from the item to the block when
 * a player or a dispenser places a box, and picks the side for a {@code side} icon (PRD §6.1).
 */
final class StickerTransferListener implements Listener {

    private final Plugin plugin;
    private final DisplayManager displays;

    StickerTransferListener(Plugin plugin, DisplayManager displays) {
        this.plugin = plugin;
        this.displays = displays;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        if (event instanceof io.github.jekitchen.shulkerstickers.paper.claims.ClaimTestPlaceEvent) {
            return; // a claim test, not a placement
        }
        ItemStack stack = event.getItemInHand();
        Block block = event.getBlockPlaced();
        if (!Tag.SHULKER_BOXES.isTagged(stack.getType())) {
            return;
        }
        // A side icon goes on the side facing the player who placed the box.
        Vector toPlayer = event.getPlayer().getEyeLocation().toVector()
                .subtract(block.getLocation().add(0.5, 0.5, 0.5).toVector());
        transfer(stack.getPersistentDataContainer(), block,
                facing -> Faces.sideToward(facing, toPlayer));
    }

    /**
     * Dispensers place boxes without a place event. The dispense event fires before the box
     * is placed, so the copy runs on the box's region one tick later.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDispense(BlockDispenseEvent event) {
        ItemStack stack = event.getItem();
        if (!Tag.SHULKER_BOXES.isTagged(stack.getType())
                || !StickerStore.hasData(stack.getPersistentDataContainer())
                || !(event.getBlock().getBlockData() instanceof Directional dispenser)) {
            return;
        }
        BlockFace out = dispenser.getFacing();
        Block target = event.getBlock().getRelative(out);
        // Only a box this dispense places can get the sticker. If something already fills the
        // space, the dispense can't place, and a box already there must not be labelled.
        if (!target.isEmpty() && !target.isReplaceable()) {
            return;
        }
        ItemStack copy = stack.clone();
        plugin.getServer().getRegionScheduler().runDelayed(plugin, target.getLocation(), t -> {
            if (target.getState(false) instanceof ShulkerBox box
                    && target.getType() == stack.getType()
                    && !StickerStore.hasData(box.getPersistentDataContainer())) {
                // The side facing back toward the dispenser, or north if that isn't a side.
                transfer(copy.getPersistentDataContainer(), target,
                        facing -> dispenserSide(facing, out.getOppositeFace()));
                displays.refresh(target);
            }
        }, 1);
    }

    static BlockFace dispenserSide(BlockFace boxFacing, BlockFace towardDispenser) {
        return Faces.toBukkit(io.github.jekitchen.shulkerstickers.core.Faces.dispenserSide(
                Faces.toCore(boxFacing), Faces.toCore(towardDispenser)));
    }

    /** Region thread. Copies item data to the block and records the side face. */
    static void transfer(PersistentDataContainerView from, Block block,
            java.util.function.Function<BlockFace, BlockFace> sidePicker) {
        if (!StickerStore.hasData(from) || !(block.getState() instanceof ShulkerBox box)) {
            return;
        }
        StickerStore.copy(from, box.getPersistentDataContainer());
        Optional<Sticker> sticker = StickerStore.read(from);
        if (sticker.isPresent() && sticker.get().anchor() == Anchor.SIDE) {
            BlockFace facing = ((Directional) block.getBlockData()).getFacing();
            Face side = Faces.toCore(sidePicker.apply(facing));
            Sticker s = sticker.get();
            StickerStore.write(box.getPersistentDataContainer(),
                    new Sticker(s.iconId(), s.iconVisual(), s.anchor(), s.rot(), Optional.of(side)));
        }
        box.update(true, false);
        // The item's copy in custom_data would go stale after the first edit; persistent data is
        // the only copy on Paper (owner decision 4).
        WorldMigration.migrate(block, java.util.logging.Logger.getLogger("ShulkerStickers"));
    }
}
