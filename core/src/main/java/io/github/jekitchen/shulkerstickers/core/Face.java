package io.github.jekitchen.shulkerstickers.core;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** A block face, independent of any platform's direction type. */
public enum Face {
    DOWN, UP, NORTH, SOUTH, WEST, EAST;

    public Face opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /** Lowercase name, as stored in sticker data. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Face> fromSerializedName(String name) {
        return Arrays.stream(values()).filter(f -> f.serializedName().equals(name)).findFirst();
    }
}
