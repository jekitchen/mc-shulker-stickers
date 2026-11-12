package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Face;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How an icon or name is turned on each face (the convention both platforms follow).
 * A display with no rotation shows its image facing +Z with the image's top toward +Y.
 * We turn it so the image faces out of its face; on a side the image's top points up;
 * on the top and bottom the image's top points the way the player faced when the icon was
 * applied ({@code rot} 0 = south, 1 = west, 2 = north, 3 = east).
 */
final class IconOrientation {

    private IconOrientation() {}

    /** Yaw quadrant as stored in {@code rot}: Minecraft yaw 0 = south, 90 = west, 180 = north, 270 = east. */
    static int quadrant(float yaw) {
        return Math.floorMod(Math.round(yaw / 90f), 4);
    }

    /** The left rotation for a display on {@code face}; {@code rot} only matters on the top and bottom. */
    static Quaternionf rotation(Face face, int rot) {
        Vector3f normal = vector(face.normal());
        Vector3f up = switch (face) {
            case UP, DOWN -> facing(rot);
            default -> new Vector3f(0, 1, 0);
        };
        Vector3f right = new Vector3f(up).cross(normal); // image +X = up × normal for a right-handed frame
        Matrix3f basis = new Matrix3f(right, up, normal);
        return new Quaternionf().setFromNormalized(basis).normalize();
    }

    private static Vector3f facing(int rot) {
        return switch (Math.floorMod(rot, 4)) {
            case 0 -> new Vector3f(0, 0, 1);
            case 1 -> new Vector3f(-1, 0, 0);
            case 2 -> new Vector3f(0, 0, -1);
            default -> new Vector3f(1, 0, 0);
        };
    }

    private static Vector3f vector(int[] n) {
        return new Vector3f(n[0], n[1], n[2]);
    }
}
