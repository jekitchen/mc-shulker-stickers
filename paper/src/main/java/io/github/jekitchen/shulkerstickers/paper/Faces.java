package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import java.util.List;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/** Face maths for where an icon is drawn. */
final class Faces {

    static final List<BlockFace> ALL = List.of(
            BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);

    private Faces() {}

    /**
     * The face the icon sits on. {@code storedSide} is the block-only face key, used only
     * for side anchors; without a valid one the first side in {@link #ALL} order is used.
     */
    static BlockFace iconFace(Anchor anchor, BlockFace facing, BlockFace storedSide) {
        return switch (anchor) {
            case LID -> facing;
            case BASE -> facing.getOppositeFace();
            case SIDE -> isSide(facing, storedSide)
                    ? storedSide
                    : ALL.stream().filter(f -> isSide(facing, f)).findFirst().orElseThrow();
        };
    }

    /** The side of the box (relative to {@code facing}) that points most toward {@code toPlayer}. */
    static BlockFace sideToward(BlockFace facing, Vector toPlayer) {
        BlockFace best = null;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (BlockFace face : ALL) {
            if (!isSide(facing, face)) {
                continue;
            }
            double dot = face.getDirection().dot(toPlayer);
            if (dot > bestDot) {
                bestDot = dot;
                best = face;
            }
        }
        return best;
    }

    static boolean isSide(BlockFace facing, BlockFace face) {
        return face != null && face != facing && face != facing.getOppositeFace() && ALL.contains(face);
    }

    static BlockFace toBukkit(Face face) {
        return BlockFace.valueOf(face.name());
    }

    static Face toCore(BlockFace face) {
        return Face.valueOf(face.name());
    }
}
