package io.github.jekitchen.shulkerstickers.core;

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
}
