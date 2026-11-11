package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class StickerStoreTest {

    private static final StickerData ICON = new StickerData("minecraft:cobblestone", "{}", Anchor.LID, 2);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void writesAndReadsBackOnAnItem() {
        ItemStack stack = new ItemStack(Material.SHULKER_BOX);

        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, ICON));

        assertEquals(Optional.of(ICON), StickerStore.read(stack.getPersistentDataContainer()));
    }

    @Test
    void writesTheFormatVersion() {
        ItemStack stack = new ItemStack(Material.SHULKER_BOX);

        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, ICON));

        assertEquals(1, stack.getPersistentDataContainer().get(StickerKeys.VERSION,
                org.bukkit.persistence.PersistentDataType.INTEGER));
    }

    @Test
    void readOfUnstickeredContainerIsEmpty() {
        ItemStack stack = new ItemStack(Material.SHULKER_BOX);

        assertTrue(StickerStore.read(stack.getPersistentDataContainer()).isEmpty());
    }

    @Test
    void clearRemovesEveryKey() {
        ItemStack stack = new ItemStack(Material.SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, ICON));

        stack.editPersistentDataContainer(StickerStore::clear);

        assertTrue(stack.getPersistentDataContainer().isEmpty());
    }

    @Test
    void copyMovesStickerAndLeavesOtherPluginsDataAlone() {
        ItemStack from = new ItemStack(Material.SHULKER_BOX);
        from.editPersistentDataContainer(pdc -> StickerStore.write(pdc, ICON));
        ItemStack to = new ItemStack(Material.SHULKER_BOX);
        org.bukkit.NamespacedKey other = new org.bukkit.NamespacedKey("other", "x");
        to.editPersistentDataContainer(pdc -> pdc.set(other, org.bukkit.persistence.PersistentDataType.INTEGER, 7));

        to.editPersistentDataContainer(pdc -> StickerStore.copy(from.getPersistentDataContainer(), pdc));

        assertEquals(Optional.of(ICON), StickerStore.read(to.getPersistentDataContainer()));
        assertEquals(7, to.getPersistentDataContainer().get(other, org.bukkit.persistence.PersistentDataType.INTEGER));
    }

    @Test
    void copyOfEmptySourceIsANoOp() {
        ItemStack from = new ItemStack(Material.SHULKER_BOX);
        ItemStack to = new ItemStack(Material.SHULKER_BOX);

        to.editPersistentDataContainer(pdc -> StickerStore.copy(from.getPersistentDataContainer(), pdc));

        assertFalse(StickerStore.read(to.getPersistentDataContainer()).isPresent());
    }
}
