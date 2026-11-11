package io.github.jekitchen.shulkerstickers.core;

import java.util.Objects;
import java.util.Optional;

/** The parts of a box the editor can change. */
public record BoxState(BoxColor color, Optional<String> iconId, Optional<String> name) {

    public BoxState {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(iconId, "iconId");
        Objects.requireNonNull(name, "name");
    }

    public BoxState withColor(BoxColor newColor) {
        return new BoxState(newColor, iconId, name);
    }

    public BoxState withIcon(Optional<String> newIconId) {
        return new BoxState(color, newIconId, name);
    }

    public BoxState withName(Optional<String> newName) {
        return new BoxState(color, iconId, newName);
    }
}
