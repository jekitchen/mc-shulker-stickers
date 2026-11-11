package io.github.jekitchen.shulkerstickers.core.data;

import java.util.List;

/**
 * Key names inside {@code minecraft:custom_data → PublicBukkitValues} (PRD §6.1). Both
 * platforms write exactly these, so items move between Fabric and Paper intact.
 */
public final class StickerFormat {

    public static final String NAMESPACE = "shulker_stickers";
    /** Name of the compound Paper keeps plugin data in. */
    public static final String BUKKIT_VALUES = "PublicBukkitValues";
    /** Current format version; we migrate older data ourselves. */
    public static final int VERSION = 1;

    public static final String V = "v";
    public static final String ICON_ID = "icon_id";
    public static final String ICON_VISUAL = "icon_visual";
    public static final String ANCHOR = "anchor";
    /** Rotation (0–3) of an icon on the top or bottom face. */
    public static final String ROT = "rot";
    /** Exact face of the icon on a placed box; recomputed when the box is placed again. */
    public static final String FACE = "face";

    public static final List<String> KEYS = List.of(V, ICON_ID, ICON_VISUAL, ANCHOR, ROT, FACE);

    private StickerFormat() {}

    public static String fullKey(String shortKey) {
        return NAMESPACE + ":" + shortKey;
    }
}
