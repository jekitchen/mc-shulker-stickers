package io.github.jekitchen.shulkerstickers.core.config;

import java.util.Objects;

/** What a change costs at Save (PRD §8). {@code itemId} is set only for {@link Type#ITEM}. */
public record Cost(Type type, int amount, String itemId) {

    public static final Cost NONE = new Cost(Type.NONE, 0, null);

    public enum Type { NONE, XP_LEVELS, ITEM }

    public Cost {
        Objects.requireNonNull(type, "type");
    }

    public boolean isFree() {
        return type == Type.NONE;
    }
}
