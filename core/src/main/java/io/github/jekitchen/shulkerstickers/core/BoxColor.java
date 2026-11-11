package io.github.jekitchen.shulkerstickers.core;

import java.util.Arrays;
import java.util.Optional;

/** The 17 shulker box variants: undyed, then the 16 dye colors in creative-tab order. */
public enum BoxColor {
    UNDYED(null),
    WHITE("white"),
    LIGHT_GRAY("light_gray"),
    GRAY("gray"),
    BLACK("black"),
    BROWN("brown"),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    LIME("lime"),
    GREEN("green"),
    CYAN("cyan"),
    LIGHT_BLUE("light_blue"),
    BLUE("blue"),
    PURPLE("purple"),
    MAGENTA("magenta"),
    PINK("pink");

    private final String dyeName;
    private final String itemId;

    BoxColor(String dyeName) {
        this.dyeName = dyeName;
        this.itemId = dyeName == null ? "minecraft:shulker_box" : "minecraft:" + dyeName + "_shulker_box";
    }

    /** Vanilla dye name, or {@code null} for the undyed box. */
    public String dyeName() {
        return dyeName;
    }

    /** Vanilla block and item ID, e.g. {@code minecraft:red_shulker_box}. */
    public String itemId() {
        return itemId;
    }

    public static Optional<BoxColor> fromItemId(String itemId) {
        return Arrays.stream(values()).filter(c -> c.itemId.equals(itemId)).findFirst();
    }
}
