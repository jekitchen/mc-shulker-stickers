package io.github.jekitchen.shulkerstickers.core.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout.Button;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EditorLayoutTest {

    @Test
    void firstSeventeenSlotsAreColorsInOrder() {
        for (BoxColor color : BoxColor.values()) {
            assertEquals(Optional.of(new Button.Color(color)), EditorLayout.buttonAt(color.ordinal()));
        }
    }

    @Test
    void rowThreeButtons() {
        assertEquals(Optional.of(Button.PREVIEW), EditorLayout.buttonAt(18));
        assertEquals(Optional.of(Button.USE_OFFHAND), EditorLayout.buttonAt(20));
        assertEquals(Optional.of(Button.REMOVE_ICON), EditorLayout.buttonAt(21));
        assertEquals(Optional.of(Button.RENAME), EditorLayout.buttonAt(22));
        assertEquals(Optional.of(Button.CANCEL), EditorLayout.buttonAt(25));
        assertEquals(Optional.of(Button.SAVE), EditorLayout.buttonAt(26));
    }

    @Test
    void fillerAndOutOfRangeSlotsHaveNoButton() {
        for (int slot : new int[] {17, 19, 23, 24, 27, -1, -999}) {
            assertTrue(EditorLayout.buttonAt(slot).isEmpty(), "slot " + slot);
        }
    }

    @Test
    void slotOfIsTheInverseOfButtonAt() {
        for (int slot = 0; slot < EditorLayout.SIZE; slot++) {
            final int s = slot;
            EditorLayout.buttonAt(slot).ifPresent(b -> assertEquals(s, EditorLayout.slotOf(b)));
        }
    }

    @Test
    void menuSlotsAreTheTopTwentySeven() {
        assertTrue(EditorLayout.isMenuSlot(0));
        assertTrue(EditorLayout.isMenuSlot(26));
        assertFalse(EditorLayout.isMenuSlot(27));
        assertFalse(EditorLayout.isMenuSlot(-999));
    }
}
