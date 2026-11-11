package io.github.jekitchen.shulkerstickers.core;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Which face of a placed box an icon goes on (PRD §5.3, §6.1). */
public final class Faces {

    private Faces() {}

    /** The four faces perpendicular to {@code facing}, in {@link Face} declaration order. */
    public static List<Face> sides(Face facing) {
        return Arrays.stream(Face.values()).filter(f -> isSide(facing, f)).toList();
    }

    public static boolean isSide(Face facing, Face face) {
        return face != null && face != facing && face != facing.opposite();
    }

    /**
     * The face the icon is drawn on. A side anchor uses the stored face if it is one of the
     * four sides, otherwise the first side.
     */
    public static Face iconFace(Anchor anchor, Face facing, Optional<Face> storedFace) {
        return switch (anchor) {
            case LID -> facing;
            case BASE -> facing.opposite();
            case SIDE -> storedFace.filter(f -> isSide(facing, f)).orElse(sides(facing).getFirst());
        };
    }

    /** The side of a box with this {@code facing} that points most toward the vector (dx, dy, dz). */
    public static Face sideToward(Face facing, double dx, double dy, double dz) {
        Face best = null;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Face side : sides(facing)) {
            int[] n = side.normal();
            double dot = n[0] * dx + n[1] * dy + n[2] * dz;
            if (dot > bestDot) {
                bestDot = dot;
                best = side;
            }
        }
        return best;
    }
}
