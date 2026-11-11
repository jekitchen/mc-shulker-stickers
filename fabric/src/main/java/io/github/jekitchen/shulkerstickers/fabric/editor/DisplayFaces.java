package io.github.jekitchen.shulkerstickers.fabric.editor;

import io.github.jekitchen.shulkerstickers.fabric.data.Sticker;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;

/** Which face of a placed box an icon goes on, from the box's facing and the stored anchor. */
public final class DisplayFaces {

    private DisplayFaces() {
    }

    /** The four faces perpendicular to {@code facing}, in a fixed order. */
    static List<Direction> sides(Direction facing) {
        return Arrays.stream(Direction.values()).filter(d -> d.getAxis() != facing.getAxis()).toList();
    }

    /**
     * For a side anchor on a placed box, {@code rot} records which of the four sides was clicked.
     * (The PRD's data model has no field for this yet; see the spike findings.)
     */
    static int sideIndex(Direction facing, Direction clicked) {
        return Math.max(0, sides(facing).indexOf(clicked));
    }

    public static Direction face(Direction facing, Sticker sticker) {
        return switch (sticker.anchor()) {
            case LID -> facing;
            case BASE -> facing.getOpposite();
            case SIDE -> sides(facing).get(Math.floorMod(sticker.rot(), 4));
        };
    }
}
