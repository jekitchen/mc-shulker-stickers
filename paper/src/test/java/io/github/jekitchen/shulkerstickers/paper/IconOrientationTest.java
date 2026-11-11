package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jekitchen.shulkerstickers.core.Face;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The orientation convention both platforms follow. A display with no rotation shows its
 * image facing +Z (south) with the image's top toward +Y. After our rotation:
 * <ul>
 *   <li>the image faces out of the face it is on;</li>
 *   <li>on a side face the image's top points up;</li>
 *   <li>on the top and bottom faces the image's top points the way the player was facing
 *       when the icon was applied ({@code rot} 0 = south, 1 = west, 2 = north, 3 = east),
 *       like an item in a floor item frame read by that player.</li>
 * </ul>
 */
class IconOrientationTest {

    private static final Vector3f SOUTH = new Vector3f(0, 0, 1);
    private static final Vector3f WEST = new Vector3f(-1, 0, 0);
    private static final Vector3f NORTH = new Vector3f(0, 0, -1);
    private static final Vector3f EAST = new Vector3f(1, 0, 0);
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final Vector3f DOWN = new Vector3f(0, -1, 0);
    private static final Vector3f[] FACING_BY_ROT = {SOUTH, WEST, NORTH, EAST};

    private static void assertVec(Vector3f expected, Vector3f actual, String what) {
        assertEquals(expected.x, actual.x, 1e-5, what + " x");
        assertEquals(expected.y, actual.y, 1e-5, what + " y");
        assertEquals(expected.z, actual.z, 1e-5, what + " z");
    }

    private static Vector3f imageNormal(Quaternionf q) {
        return q.transform(new Vector3f(0, 0, 1));
    }

    private static Vector3f imageUp(Quaternionf q) {
        return q.transform(new Vector3f(0, 1, 0));
    }

    @Test
    void sideFacesFaceOutwardUpright() {
        Vector3f[] normals = {NORTH, SOUTH, WEST, EAST};
        Face[] faces = {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST};
        for (int i = 0; i < faces.length; i++) {
            for (int rot = 0; rot < 4; rot++) { // rot never turns a side icon
                Quaternionf q = IconOrientation.rotation(faces[i], rot);
                assertVec(normals[i], imageNormal(q), faces[i] + " normal");
                assertVec(UP, imageUp(q), faces[i] + " up");
            }
        }
    }

    @Test
    void topFaceFacesUpWithTheImageTopTowardThePlayersFacing() {
        for (int rot = 0; rot < 4; rot++) {
            Quaternionf q = IconOrientation.rotation(Face.UP, rot);
            assertVec(UP, imageNormal(q), "top normal rot " + rot);
            assertVec(FACING_BY_ROT[rot], imageUp(q), "top up rot " + rot);
        }
    }

    @Test
    void bottomFaceFacesDownWithTheSameImageTopDirection() {
        for (int rot = 0; rot < 4; rot++) {
            Quaternionf q = IconOrientation.rotation(Face.DOWN, rot);
            assertVec(DOWN, imageNormal(q), "bottom normal rot " + rot);
            assertVec(FACING_BY_ROT[rot], imageUp(q), "bottom up rot " + rot);
        }
    }

    @Test
    void rotationQuadrantFollowsMinecraftYaw() {
        assertEquals(0, IconOrientation.quadrant(0f));     // south
        assertEquals(1, IconOrientation.quadrant(90f));    // west
        assertEquals(2, IconOrientation.quadrant(180f));   // north
        assertEquals(2, IconOrientation.quadrant(-180f));
        assertEquals(3, IconOrientation.quadrant(-90f));   // east
        assertEquals(3, IconOrientation.quadrant(265f));
        assertEquals(0, IconOrientation.quadrant(-44f));
    }
}
