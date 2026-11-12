package io.github.jekitchen.shulkerstickers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnchorTest {

    @Test
    void clickedFaceEqualToFacingIsLid() {
        assertEquals(Anchor.LID, Anchor.of(Face.UP, Face.UP));
        assertEquals(Anchor.LID, Anchor.of(Face.NORTH, Face.NORTH));
    }

    @Test
    void clickedFaceOppositeFacingIsBase() {
        assertEquals(Anchor.BASE, Anchor.of(Face.UP, Face.DOWN));
        assertEquals(Anchor.BASE, Anchor.of(Face.EAST, Face.WEST));
    }

    @Test
    void anyOtherFaceIsSide() {
        assertEquals(Anchor.SIDE, Anchor.of(Face.UP, Face.SOUTH));
        assertEquals(Anchor.SIDE, Anchor.of(Face.NORTH, Face.DOWN));
    }

    @Test
    void serializedNamesMatchDataFormat() {
        assertEquals("lid", Anchor.LID.serializedName());
        assertEquals("base", Anchor.BASE.serializedName());
        assertEquals("side", Anchor.SIDE.serializedName());
    }
}
