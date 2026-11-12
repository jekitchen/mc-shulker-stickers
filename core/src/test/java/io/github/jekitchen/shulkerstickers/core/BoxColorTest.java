package io.github.jekitchen.shulkerstickers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoxColorTest {

    @Test
    void hasSeventeenVariantsUndyedFirstThenCreativeTabOrder() {
        assertEquals(17, BoxColor.values().length);
        assertEquals(BoxColor.UNDYED, BoxColor.values()[0]);
        assertEquals(
                "white,light_gray,gray,black,brown,red,orange,yellow,lime,green,cyan,light_blue,blue,purple,magenta,pink",
                String.join(",", Arrays.stream(BoxColor.values()).skip(1).map(BoxColor::dyeName).toList()));
    }

    @Test
    void itemIdsMatchVanilla() {
        assertEquals("minecraft:shulker_box", BoxColor.UNDYED.itemId());
        assertEquals("minecraft:light_blue_shulker_box", BoxColor.LIGHT_BLUE.itemId());
    }

    @Test
    void lookupByItemIdRoundTripsEveryVariant() {
        for (BoxColor color : BoxColor.values()) {
            assertEquals(Optional.of(color), BoxColor.fromItemId(color.itemId()));
        }
    }

    @Test
    void lookupOfNonShulkerIdIsEmpty() {
        assertTrue(BoxColor.fromItemId("minecraft:chest").isEmpty());
    }
}
