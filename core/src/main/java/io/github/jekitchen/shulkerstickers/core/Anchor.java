package io.github.jekitchen.shulkerstickers.core;

/**
 * Where the icon sits relative to the box's {@code facing}, so it lands on the same
 * kind of face when the box is placed again (PRD §6.1).
 */
public enum Anchor {
    LID("lid"),
    BASE("base"),
    SIDE("side");

    private final String serializedName;

    Anchor(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Anchor of(Face facing, Face clicked) {
        if (clicked == facing) {
            return LID;
        }
        if (clicked == facing.opposite()) {
            return BASE;
        }
        return SIDE;
    }
}
