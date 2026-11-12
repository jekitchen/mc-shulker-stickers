package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class StickerStoreTest {

    static final Sticker ICON = new Sticker(
            Optional.of("minecraft:cobblestone"), Optional.empty(), Anchor.LID, 2, Optional.empty());

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack stickered(Sticker sticker) {
        ItemStack stack = new ItemStack(Material.SHULKER_BOX);
        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, sticker));
        return stack;
    }

    @Test
    void writesAndReadsBackOnAnItem() {
        assertEquals(Optional.of(ICON), StickerStore.read(stickered(ICON).getPersistentDataContainer()));
    }

    @Test
    void writesTheSharedKeysWithIntegerAndStringTypes() {
        Sticker full = new Sticker(Optional.of("minecraft:stone"), Optional.of("{\"minecraft:dyed_color\":1}"),
                Anchor.SIDE, 1, Optional.of(Face.EAST));
        var pdc = stickered(full).getPersistentDataContainer();

        assertEquals(1, pdc.get(new NamespacedKey("shulker_stickers", "v"), PersistentDataType.INTEGER));
        assertEquals("minecraft:stone", pdc.get(new NamespacedKey("shulker_stickers", "icon_id"), PersistentDataType.STRING));
        assertEquals("{\"minecraft:dyed_color\":1}",
                pdc.get(new NamespacedKey("shulker_stickers", "icon_visual"), PersistentDataType.STRING));
        assertEquals("side", pdc.get(new NamespacedKey("shulker_stickers", "anchor"), PersistentDataType.STRING));
        assertEquals(1, pdc.get(new NamespacedKey("shulker_stickers", "rot"), PersistentDataType.INTEGER));
        assertEquals("east", pdc.get(new NamespacedKey("shulker_stickers", "face"), PersistentDataType.STRING));
    }

    @Test
    void emptyVisualIsOmittedNotWrittenAsEmptyObject() {
        var pdc = stickered(ICON).getPersistentDataContainer();

        assertFalse(pdc.has(new NamespacedKey("shulker_stickers", "icon_visual")));
    }

    @Test
    void rewritingDropsKeysTheNewStickerDoesNotHave() {
        ItemStack stack = stickered(new Sticker(Optional.of("minecraft:stone"), Optional.of("{}"), Anchor.SIDE, 0,
                Optional.of(Face.EAST)));

        stack.editPersistentDataContainer(pdc -> StickerStore.write(pdc, ICON));

        assertEquals(Optional.of(ICON), StickerStore.read(stack.getPersistentDataContainer()));
    }

    @Test
    void readOfUnstickeredContainerIsEmpty() {
        var pdc = new ItemStack(Material.SHULKER_BOX).getPersistentDataContainer();

        assertTrue(StickerStore.read(pdc).isEmpty());
        assertFalse(StickerStore.hasData(pdc));
    }

    @Test
    void unreadableDataIsReportedButKept() {
        ItemStack stack = stickered(ICON);
        stack.editPersistentDataContainer(pdc -> pdc.set(new NamespacedKey("shulker_stickers", "v"),
                PersistentDataType.INTEGER, 99));

        assertTrue(StickerStore.read(stack.getPersistentDataContainer()).isEmpty());
        assertTrue(StickerStore.hasData(stack.getPersistentDataContainer()));
    }

    @Test
    void nameHiddenFlagRoundTrips() {
        ItemStack stack = new ItemStack(Material.SHULKER_BOX);

        stack.editPersistentDataContainer(pdc -> StickerStore.setNameHidden(pdc, true));
        assertTrue(StickerStore.isNameHidden(stack.getPersistentDataContainer()));
        assertEquals(1, stack.getPersistentDataContainer().get(new NamespacedKey("shulker_stickers", "name_hidden"),
                PersistentDataType.INTEGER));

        stack.editPersistentDataContainer(pdc -> StickerStore.setNameHidden(pdc, false));
        assertFalse(StickerStore.isNameHidden(stack.getPersistentDataContainer()));
    }

    @Test
    void clearRemovesEveryKeyIncludingNameHidden() {
        ItemStack stack = stickered(ICON);
        stack.editPersistentDataContainer(pdc -> StickerStore.setNameHidden(pdc, true));

        stack.editPersistentDataContainer(StickerStore::clear);

        assertTrue(stack.getPersistentDataContainer().isEmpty());
    }

    @Test
    void copyMovesStickerAndLeavesOtherPluginsDataAlone() {
        ItemStack from = stickered(ICON);
        from.editPersistentDataContainer(pdc -> StickerStore.setNameHidden(pdc, true));
        ItemStack to = new ItemStack(Material.SHULKER_BOX);
        NamespacedKey other = new NamespacedKey("other", "x");
        to.editPersistentDataContainer(pdc -> pdc.set(other, PersistentDataType.INTEGER, 7));

        to.editPersistentDataContainer(pdc -> StickerStore.copy(from.getPersistentDataContainer(), pdc));

        assertEquals(Optional.of(ICON), StickerStore.read(to.getPersistentDataContainer()));
        assertTrue(StickerStore.isNameHidden(to.getPersistentDataContainer()));
        assertEquals(7, to.getPersistentDataContainer().get(other, PersistentDataType.INTEGER));
    }

    @Test
    void copyOfEmptySourceIsANoOp() {
        ItemStack from = new ItemStack(Material.SHULKER_BOX);
        ItemStack to = new ItemStack(Material.SHULKER_BOX);

        to.editPersistentDataContainer(pdc -> StickerStore.copy(from.getPersistentDataContainer(), pdc));

        assertFalse(StickerStore.hasData(to.getPersistentDataContainer()));
    }

    @Test
    void rawListsStoredValuesForInspect() {
        var raw = StickerStore.raw(stickered(ICON).getPersistentDataContainer());

        assertEquals(1, raw.get("v"));
        assertEquals("minecraft:cobblestone", raw.get("icon_id"));
        assertEquals("lid", raw.get("anchor"));
    }
}
