package io.github.jekitchen.shulkerstickers.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FacesTest {

    @Test
    void sidesArePerpendicularInDeclarationOrder() {
        assertEquals(List.of(Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST), Faces.sides(Face.UP));
        assertEquals(List.of(Face.DOWN, Face.UP, Face.WEST, Face.EAST), Faces.sides(Face.SOUTH));
    }

    @Test
    void isSideExcludesFacingAxis() {
        assertTrue(Faces.isSide(Face.UP, Face.EAST));
        assertFalse(Faces.isSide(Face.UP, Face.UP));
        assertFalse(Faces.isSide(Face.UP, Face.DOWN));
    }

    @Test
    void lidAndBaseIgnoreStoredFace() {
        assertEquals(Face.NORTH, Faces.iconFace(Anchor.LID, Face.NORTH, Optional.of(Face.UP)));
        assertEquals(Face.SOUTH, Faces.iconFace(Anchor.BASE, Face.NORTH, Optional.of(Face.UP)));
    }

    @Test
    void sideUsesStoredFaceWhenItIsASide() {
        assertEquals(Face.WEST, Faces.iconFace(Anchor.SIDE, Face.UP, Optional.of(Face.WEST)));
    }

    @Test
    void sideFallsBackToFirstSideWhenStoredFaceIsMissingOrInvalid() {
        assertEquals(Face.NORTH, Faces.iconFace(Anchor.SIDE, Face.UP, Optional.empty()));
        assertEquals(Face.NORTH, Faces.iconFace(Anchor.SIDE, Face.UP, Optional.of(Face.DOWN)));
    }

    @Test
    void sideTowardPicksTheSideFacingTheVector() {
        // Box facing up, player standing to the east and a little north.
        assertEquals(Face.EAST, Faces.sideToward(Face.UP, 3, 1, -1));
        // Box facing north, player above: the up side wins.
        assertEquals(Face.UP, Faces.sideToward(Face.NORTH, 0.2, 5, -4));
    }

    @Test
    void dispenserSideFacesBackTowardTheDispenserWhenThatIsASide() {
        assertEquals(Face.WEST, Faces.dispenserSide(Face.UP, Face.WEST));
    }

    @Test
    void dispenserSideFallsBackToNorth() {
        // Box dispensed eastward: the dispenser is behind its base, not a side.
        assertEquals(Face.NORTH, Faces.dispenserSide(Face.EAST, Face.WEST));
    }

    @Test
    void dispenserSideUsesFirstSideWhenNorthIsNotASide() {
        assertEquals(Face.DOWN, Faces.dispenserSide(Face.NORTH, Face.SOUTH));
    }

    @Test
    void normalsPointOutward() {
        assertArrayEquals(new int[] {0, 1, 0}, Face.UP.normal());
        assertArrayEquals(new int[] {0, 0, -1}, Face.NORTH.normal());
        assertArrayEquals(new int[] {1, 0, 0}, Face.EAST.normal());
    }
}
