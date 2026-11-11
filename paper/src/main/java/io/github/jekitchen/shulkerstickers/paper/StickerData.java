package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import java.util.Objects;

/** One box's icon, as stored (PRD §6.1). The name and color live in vanilla data. */
record StickerData(String iconId, String iconVisual, Anchor anchor, int rotation) {

    StickerData {
        Objects.requireNonNull(iconId, "iconId");
        Objects.requireNonNull(iconVisual, "iconVisual");
        Objects.requireNonNull(anchor, "anchor");
    }
}
