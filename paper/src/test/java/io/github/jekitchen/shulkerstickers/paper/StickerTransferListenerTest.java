package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Placing a stickered box puts its data on the block (drops are covered by the datapack self-test). */
class StickerTransferListenerTest {

    private static final Sticker ICON = new Sticker(
            Optional.of("minecraft:cobblestone"), Optional.empty(), Anchor.LID, 1, Optional.empty());

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private StickerTransferListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        Settings settings = Settings.of(io.github.jekitchen.shulkerstickers.core.config.StickerConfig.DEFAULTS);
        listener = new StickerTransferListener(plugin, new DisplayManager(plugin, settings));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void place(Block block, ItemStack stack) {
        BlockState replaced = block.getState();
        block.setType(stack.getType()); // facing UP by default
        listener.onPlace(new BlockPlaceEvent(block, replaced, block.getRelative(BlockFace.DOWN), stack, player,
                true, EquipmentSlot.HAND));
    }

    @Test
    void placingCopiesItemStickerOntoTheBlock() {
        ItemStack stack = new ItemStack(Material.BLUE_SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, ICON));
        Block block = world.getBlockAt(0, 70, 0);

        place(block, stack);

        ShulkerBox placed = (ShulkerBox) block.getState();
        assertEquals(Optional.of(ICON), StickerStore.read(placed.getPersistentDataContainer()));
    }

    @Test
    void placingCopiesTheNameHiddenFlag() {
        ItemStack stack = new ItemStack(Material.BLUE_SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.setNameHidden(pdc, true));
        Block block = world.getBlockAt(0, 70, 0);

        place(block, stack);

        assertEquals(true, StickerStore.isNameHidden(((ShulkerBox) block.getState()).getPersistentDataContainer()));
    }

    @Test
    void placingASideIconPutsItOnTheSideFacingThePlacer() {
        Sticker side = new Sticker(ICON.iconId(), ICON.iconVisual(), Anchor.SIDE, 0, Optional.of(Face.EAST));
        ItemStack stack = new ItemStack(Material.BLUE_SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, side));
        Block block = world.getBlockAt(0, 70, 0);
        player.teleport(new org.bukkit.Location(world, 0.5, 70, 6)); // south of the box

        place(block, stack);

        ShulkerBox placed = (ShulkerBox) block.getState();
        assertEquals(Optional.of(Face.SOUTH), StickerStore.read(placed.getPersistentDataContainer()).orElseThrow().face());
    }

    @Test
    void dispenserSideFacesBackTowardTheDispenser() {
        // Upright box; the dispenser is to its west.
        assertEquals(BlockFace.WEST, StickerTransferListener.dispenserSide(BlockFace.UP, BlockFace.WEST));
    }

    @Test
    void dispenserSideFallsBackToNorth() {
        // Box facing east (placed by a dispenser shooting east): west isn't a side, north is.
        assertEquals(BlockFace.NORTH, StickerTransferListener.dispenserSide(BlockFace.EAST, BlockFace.WEST));
    }

    @Test
    void dispenserSideFallsBackToTheFirstSideWhenNorthIsNotOne() {
        assertEquals(BlockFace.DOWN, StickerTransferListener.dispenserSide(BlockFace.NORTH, BlockFace.SOUTH));
    }
}
