package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import java.util.Optional;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** The Bukkit adapter over core's face rules. */
class FacesTest {

    @Test
    void lidAndBaseFollowFacing() {
        assertEquals(BlockFace.EAST, Faces.iconFace(Anchor.LID, BlockFace.EAST, Optional.empty()));
        assertEquals(BlockFace.WEST, Faces.iconFace(Anchor.BASE, BlockFace.EAST, Optional.empty()));
    }

    @Test
    void sideUsesStoredFace() {
        assertEquals(BlockFace.SOUTH, Faces.iconFace(Anchor.SIDE, BlockFace.UP, Optional.of(Face.SOUTH)));
    }

    @Test
    void sideOfAnUprightBoxFacesThePlacer() {
        assertEquals(BlockFace.EAST, Faces.sideToward(BlockFace.UP, new Vector(3, 1, 0.5)));
        assertEquals(BlockFace.NORTH, Faces.sideToward(BlockFace.UP, new Vector(0.2, 1, -4)));
    }

    @Test
    void sideOfASidewaysBoxCanBeTopOrBottom() {
        assertEquals(BlockFace.UP, Faces.sideToward(BlockFace.NORTH, new Vector(0.1, 5, 0.3)));
    }

    @Test
    void nonCartesianFacesAreNeverSides() {
        assertFalse(Faces.isSide(BlockFace.UP, BlockFace.NORTH_EAST));
    }

    @Test
    void convertsBetweenBukkitAndCoreFaces() {
        for (Face face : Face.values()) {
            assertEquals(face, Faces.toCore(Faces.toBukkit(face)));
        }
    }
}
