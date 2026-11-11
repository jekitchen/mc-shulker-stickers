package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Placing a stickered box puts its data on the block (drops are covered by the datapack self-test). */
class StickerTransferListenerTest {

    private static final StickerData ICON = new StickerData("minecraft:cobblestone", "{}", Anchor.LID, 1);

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private final StickerTransferListener listener = new StickerTransferListener();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void placingCopiesItemStickerOntoTheBlock() {
        ItemStack stack = new ItemStack(Material.BLUE_SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, ICON));
        Block block = world.getBlockAt(0, 70, 0);
        BlockState replaced = block.getState();
        block.setType(Material.BLUE_SHULKER_BOX);
        Block against = block.getRelative(BlockFace.DOWN);

        listener.onPlace(new BlockPlaceEvent(block, replaced, against, stack, player, true, EquipmentSlot.HAND));

        ShulkerBox placed = (ShulkerBox) block.getState();
        assertEquals(Optional.of(ICON), StickerStore.read(placed.getPersistentDataContainer()));
    }

    @Test
    void placingASideIconPutsItOnTheSideFacingThePlacer() {
        StickerData side = new StickerData("minecraft:cobblestone", "{}", Anchor.SIDE, 0);
        ItemStack stack = new ItemStack(Material.BLUE_SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, side));
        Block block = world.getBlockAt(0, 70, 0);
        BlockState replaced = block.getState();
        block.setType(Material.BLUE_SHULKER_BOX); // facing UP by default
        player.teleport(new org.bukkit.Location(world, 0.5, 70, 6)); // south of the box

        listener.onPlace(new BlockPlaceEvent(block, replaced, block.getRelative(BlockFace.DOWN), stack, player,
                true, EquipmentSlot.HAND));

        ShulkerBox placed = (ShulkerBox) block.getState();
        assertEquals("SOUTH", placed.getPersistentDataContainer().get(StickerKeys.FACE,
                org.bukkit.persistence.PersistentDataType.STRING));
    }
}
