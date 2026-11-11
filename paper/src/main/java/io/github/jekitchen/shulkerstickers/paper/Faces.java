package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import java.util.Optional;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/** Bukkit adapter for core's {@link io.github.jekitchen.shulkerstickers.core.Faces}. */
final class Faces {

    private Faces() {}

    static BlockFace iconFace(Anchor anchor, BlockFace facing, Optional<Face> storedFace) {
        return toBukkit(io.github.jekitchen.shulkerstickers.core.Faces.iconFace(anchor, toCore(facing), storedFace));
    }

    /** The side of the box (relative to {@code facing}) that points most toward {@code toward}. */
    static BlockFace sideToward(BlockFace facing, Vector toward) {
        return toBukkit(io.github.jekitchen.shulkerstickers.core.Faces.sideToward(
                toCore(facing), toward.getX(), toward.getY(), toward.getZ()));
    }

    static boolean isSide(BlockFace facing, BlockFace face) {
        return isCoreFace(face) && io.github.jekitchen.shulkerstickers.core.Faces.isSide(toCore(facing), toCore(face));
    }

    static boolean isCoreFace(BlockFace face) {
        return face != null && face.isCartesian();
    }

    static BlockFace toBukkit(Face face) {
        return BlockFace.valueOf(face.name());
    }

    static Face toCore(BlockFace face) {
        return Face.valueOf(face.name());
    }
}
