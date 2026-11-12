package io.github.jekitchen.shulkerstickers.fabric.editor;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import java.util.Optional;
import net.minecraft.core.Direction;

/** Where a sticker sits on a placed box: anchor, top/bottom rotation and (for sides) the exact face. */
public record Placement(Anchor anchor, int rot, Optional<Face> face) {

    public static Placement of(Sticker sticker) {
        return new Placement(sticker.anchor(), sticker.rot(), sticker.face());
    }

    /**
     * The placement for a sticker applied by clicking {@code clicked} on a box facing
     * {@code facing}. On the top and bottom faces the icon turns to match {@code playerFacing}.
     */
    public static Placement clicked(Direction facing, Direction clicked, Direction playerFacing) {
        Anchor anchor = Anchor.of(Boxes.face(facing), Boxes.face(clicked));
        int rot = clicked.getAxis() == Direction.Axis.Y ? playerFacing.get2DDataValue() : 0;
        Optional<Face> face = anchor == Anchor.SIDE ? Optional.of(Boxes.face(clicked)) : Optional.empty();
        return new Placement(anchor, rot, face);
    }

    public Sticker sticker(Optional<String> iconId, Optional<String> iconVisual) {
        return new Sticker(iconId, iconVisual, anchor, rot, face);
    }
}
