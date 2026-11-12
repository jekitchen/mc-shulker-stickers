package io.github.jekitchen.shulkerstickers.core.data;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import java.util.Objects;
import java.util.Optional;

/**
 * A box's sticker as stored. {@code iconVisual} holds the allowed visual properties as SNBT
 * (PRD §5.3). A sticker may have no icon: it then only records where the name is drawn.
 */
public record Sticker(Optional<String> iconId, Optional<String> iconVisual, Anchor anchor, int rot, Optional<Face> face) {

    public Sticker {
        Objects.requireNonNull(iconId, "iconId");
        Objects.requireNonNull(iconVisual, "iconVisual");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(face, "face");
        rot = Math.floorMod(rot, 4);
    }

    public boolean hasIcon() {
        return iconId.isPresent();
    }
}
