package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import java.util.Optional;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class EditorLayoutTest {

    @Test
    void firstSeventeenSlotsAreTheColorsInOrder() {
        for (BoxColor color : BoxColor.values()) {
            assertEquals(Optional.of(new EditorLayout.Button.Color(color)), EditorLayout.buttonAt(color.ordinal()));
        }
    }

    @Test
    void bottomRowHoldsTheActionButtons() {
        assertEquals(Optional.of(EditorLayout.Button.Preview.INSTANCE), EditorLayout.buttonAt(18));
        assertEquals(Optional.of(EditorLayout.Button.UseOffhand.INSTANCE), EditorLayout.buttonAt(20));
        assertEquals(Optional.of(EditorLayout.Button.RemoveIcon.INSTANCE), EditorLayout.buttonAt(21));
        assertEquals(Optional.of(EditorLayout.Button.Rename.INSTANCE), EditorLayout.buttonAt(22));
        assertEquals(Optional.of(EditorLayout.Button.Cancel.INSTANCE), EditorLayout.buttonAt(25));
        assertEquals(Optional.of(EditorLayout.Button.Save.INSTANCE), EditorLayout.buttonAt(26));
    }

    @Test
    void emptyAndOutOfRangeSlotsAreNotButtons() {
        assertTrue(EditorLayout.buttonAt(17).isEmpty());
        assertTrue(EditorLayout.buttonAt(19).isEmpty());
        assertTrue(EditorLayout.buttonAt(27).isEmpty());
        assertTrue(EditorLayout.buttonAt(-999).isEmpty());
    }

    @Test
    void onlyPlainLeftAndRightClicksInTheMenuPressButtons() {
        assertTrue(EditorLayout.isButtonPress(ClickType.LEFT, 26));
        assertTrue(EditorLayout.isButtonPress(ClickType.RIGHT, 0));
    }

    @Test
    void everyOtherClickTypeIsRejected() {
        for (ClickType type : ClickType.values()) {
            if (type == ClickType.LEFT || type == ClickType.RIGHT) {
                continue;
            }
            assertFalse(EditorLayout.isButtonPress(type, 26), type.name());
        }
    }

    @Test
    void clicksInThePlayerInventoryOrOutsideAreRejected() {
        assertFalse(EditorLayout.isButtonPress(ClickType.LEFT, 27));
        assertFalse(EditorLayout.isButtonPress(ClickType.LEFT, -999));
    }
}
