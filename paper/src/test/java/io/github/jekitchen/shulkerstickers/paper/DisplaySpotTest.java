package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/**
 * Where a display stands when the spot in front of a face is past the box's chunk edge (see
 * {@code DisplayManager#spawnSpot}). The self-test covers the loaded/unloaded decision on real servers.
 */
class DisplaySpotTest {

    private static final double EPS = 1.0E-9;

    /** The spot 0.51 blocks out from the face of the box at x, z in direction dx, dz. */
    private static Location inFront(int x, int z, double dx, double dz) {
        return new Location(null, x + 0.5 + dx * 0.51, 64.5, z + 0.5 + dz * 0.51);
    }

    @Test
    void aSpotInsideTheChunkIsKept() {
        Location wanted = inFront(5, 5, 1, 0);
        Location at = DisplayManager.insideOwnChunk(wanted, 5, 5);
        assertEquals(wanted.getX(), at.getX(), EPS);
        assertEquals(wanted.getZ(), at.getZ(), EPS);
    }

    @Test
    void eastOfTheLastColumnIsPulledBackInside() {
        Location at = DisplayManager.insideOwnChunk(inFront(15, 3, 1, 0), 15, 3);
        assertEquals(16 - 1.0E-3, at.getX(), EPS);
        assertEquals(3.5, at.getZ(), EPS);
        assertEquals(64.5, at.getY(), EPS);
    }

    @Test
    void negativeCoordinatesUseTheRightChunk() {
        // x = -1 is the last column of chunk -1 (x -16..-1); east of it is chunk 0.
        assertEquals(-1.0E-3, DisplayManager.insideOwnChunk(inFront(-1, -20, 1, 0), -1, -20).getX(), EPS);
        // x = -16 is the first column of chunk -1; west of it is chunk -2.
        assertEquals(-16 + 1.0E-3, DisplayManager.insideOwnChunk(inFront(-16, -20, -1, 0), -16, -20).getX(), EPS);
        // z = -17 is the last column of chunk -2 (z -32..-17); south of it is chunk -1.
        assertEquals(-16 - 1.0E-3, DisplayManager.insideOwnChunk(inFront(4, -17, 0, 1), 4, -17).getZ(), EPS);
        // z = -32 is the first column of chunk -2; north of it is chunk -3.
        assertEquals(-32 + 1.0E-3, DisplayManager.insideOwnChunk(inFront(4, -32, 0, -1), 4, -32).getZ(), EPS);
    }
}
