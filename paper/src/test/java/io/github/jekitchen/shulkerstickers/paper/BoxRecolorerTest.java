package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** AC-1: a recolor keeps all 27 slots, the name, the lock, the icon and facing. */
class BoxRecolorerTest {

    private static final io.github.jekitchen.shulkerstickers.core.data.Sticker ICON = new io.github.jekitchen.shulkerstickers.core.data.Sticker(Optional.of("minecraft:cobblestone"), Optional.empty(), Anchor.SIDE, 0, Optional.of(io.github.jekitchen.shulkerstickers.core.Face.NORTH));

    private WorldMock world;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        world = new WorldMock(Material.STONE, 64);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block decoratedBox() {
        Block block = world.getBlockAt(0, 70, 0);
        block.setType(Material.SHULKER_BOX);
        Directional data = (Directional) block.getBlockData();
        data.setFacing(BlockFace.EAST);
        block.setBlockData(data);

        ShulkerBox box = (ShulkerBox) block.getState();
        for (int slot = 0; slot < 27; slot++) {
            box.getSnapshotInventory().setItem(slot, new ItemStack(Material.DIAMOND, slot + 1));
        }
        box.customName(Component.text("Ores"));
        box.setLock("key");
        StickerStore.write(box.getPersistentDataContainer(), ICON);
        // MockBukkit snapshots don't capture block data (real Paper does), so set it here too.
        box.setBlockData(data);
        box.update(true, false);
        return block;
    }

    @Test
    void recolorToEveryVariantKeepsContentsNameLockIconAndFacing() {
        Block block = decoratedBox();

        for (BoxColor color : BoxColor.values()) {
            BoxRecolorer.recolor(block, color);

            assertEquals(Material.matchMaterial(color.itemId()), block.getType(), color.name());
            assertEquals(BlockFace.EAST, ((Directional) block.getBlockData()).getFacing(), color.name());
            ShulkerBox box = (ShulkerBox) block.getState();
            for (int slot = 0; slot < 27; slot++) {
                ItemStack item = box.getInventory().getItem(slot);
                assertEquals(Material.DIAMOND, item.getType(), color + " slot " + slot);
                assertEquals(slot + 1, item.getAmount(), color + " slot " + slot);
            }
            assertEquals(Component.text("Ores"), box.customName(), color.name());
            assertEquals("key", box.getLock(), color.name());
            assertEquals(Optional.of(ICON), StickerStore.read(box.getPersistentDataContainer()), color.name());
        }
    }

    @Test
    void recolorDropsNoItemEntities() {
        Block block = decoratedBox();

        BoxRecolorer.recolor(block, BoxColor.RED);

        assertTrue(world.getEntities().isEmpty());
    }
}
