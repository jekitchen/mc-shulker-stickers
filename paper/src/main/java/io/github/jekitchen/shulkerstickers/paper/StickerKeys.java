package io.github.jekitchen.shulkerstickers.paper;

import java.util.List;
import org.bukkit.NamespacedKey;

/**
 * Keys inside {@code minecraft:custom_data → PublicBukkitValues} (PRD §6.1).
 * TODO(core): move the key names to core so Fabric writes the same strings.
 */
final class StickerKeys {

    static final String NAMESPACE = "shulker_stickers";
    static final int FORMAT_VERSION = 1;

    static final NamespacedKey VERSION = key("v");
    static final NamespacedKey ICON_ID = key("icon_id");
    static final NamespacedKey ICON_VISUAL = key("icon_visual");
    static final NamespacedKey ANCHOR = key("anchor");
    static final NamespacedKey ROT = key("rot");

    static final List<NamespacedKey> ALL = List.of(VERSION, ICON_ID, ICON_VISUAL, ANCHOR, ROT);

    /**
     * Block-only: the exact face of a {@code side} icon on a placed box. Not copied to
     * items, because it is recomputed on placement. Not in PRD §6.1 yet (PRD gap).
     */
    static final NamespacedKey FACE = key("face");

    /** Marks our display entities, so strays can be found and removed. */
    static final NamespacedKey DISPLAY = key("display");

    private StickerKeys() {}

    private static NamespacedKey key(String name) {
        return new NamespacedKey(NAMESPACE, name);
    }
}
