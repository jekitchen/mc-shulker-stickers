package io.github.jekitchen.shulkerstickers.core;

import java.util.Objects;
import java.util.Optional;

/**
 * The parts of a box the editor can change. {@code iconVisual} is the icon's allowed
 * visual properties as SNBT (PRD §5.3); it is empty when there is no icon.
 */
public record BoxState(BoxColor color, Optional<String> iconId, Optional<String> iconVisual, Optional<String> name) {

    public BoxState {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(iconId, "iconId");
        Objects.requireNonNull(iconVisual, "iconVisual");
        Objects.requireNonNull(name, "name");
        if (iconId.isEmpty()) {
            iconVisual = Optional.empty();
        }
    }

    /** A state whose icon has no visual properties. */
    public BoxState(BoxColor color, Optional<String> iconId, Optional<String> name) {
        this(color, iconId, Optional.empty(), name);
    }

    public BoxState withColor(BoxColor newColor) {
        return new BoxState(newColor, iconId, iconVisual, name);
    }

    public BoxState withIcon(Optional<String> newIconId, Optional<String> newIconVisual) {
        return new BoxState(color, newIconId, newIconVisual, name);
    }

    public BoxState withName(Optional<String> newName) {
        return new BoxState(color, iconId, iconVisual, newName);
    }
}
