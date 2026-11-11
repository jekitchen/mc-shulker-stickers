package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class FacesTest {

    @Test
    void lidAndBaseFollowFacing() {
        assertEquals(BlockFace.EAST, Faces.iconFace(Anchor.LID, BlockFace.EAST, null));
        assertEquals(BlockFace.WEST, Faces.iconFace(Anchor.BASE, BlockFace.EAST, null));
    }

    @Test
    void sideUsesStoredFaceWhenItIsASide() {
        assertEquals(BlockFace.SOUTH, Faces.iconFace(Anchor.SIDE, BlockFace.UP, BlockFace.SOUTH));
    }

    @Test
    void sideIgnoresAStoredFaceThatIsNotASide() {
        assertEquals(BlockFace.NORTH, Faces.iconFace(Anchor.SIDE, BlockFace.UP, BlockFace.UP));
        assertEquals(BlockFace.NORTH, Faces.iconFace(Anchor.SIDE, BlockFace.UP, null));
    }

    @Test
    void sideOfAnUprightBoxFacesThePlacer() {
        // Player stands to the east of an upright box.
        assertEquals(BlockFace.EAST, Faces.sideToward(BlockFace.UP, new Vector(3, 1, 0.5)));
        assertEquals(BlockFace.NORTH, Faces.sideToward(BlockFace.UP, new Vector(0.2, 1, -4)));
    }

    @Test
    void sideOfASidewaysBoxCanBeTopOrBottom() {
        // Box lid points north; the four sides are up, down, east and west.
        assertEquals(BlockFace.UP, Faces.sideToward(BlockFace.NORTH, new Vector(0.1, 5, 0.3)));
    }

    @Test
    void convertsBetweenBukkitAndCoreFaces() {
        for (var face : io.github.jekitchen.shulkerstickers.core.Face.values()) {
            assertEquals(face, Faces.toCore(Faces.toBukkit(face)));
        }
    }
}
