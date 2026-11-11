package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;

/**
 * Swaps a placed shulker box to another color in one step (PRD §6.3). Must run on the
 * thread that owns the block's region.
 *
 * <p>The block-entity snapshot carries everything vanilla and Paper store on the box
 * (items, name, lock, loot table, components, persistent data). Changing the snapshot's
 * type and writing it back with physics off replaces the block without notifying
 * neighbors, then loads the snapshot into the new block entity.
 */
final class BoxRecolorer {

    private BoxRecolorer() {}

    static void recolor(Block block, BoxColor color) {
        if (!(block.getState() instanceof ShulkerBox snapshot)) {
            throw new IllegalStateException("Not a shulker box: " + block);
        }
        Material target = Objects.requireNonNull(Material.matchMaterial(color.itemId()), color.itemId());
        if (block.getType() == target) {
            return;
        }
        BlockFace facing = ((Directional) block.getBlockData()).getFacing();

        Directional newData = (Directional) target.createBlockData();
        newData.setFacing(facing);
        snapshot.setBlockData(newData);
        snapshot.update(true, false);
    }
}
