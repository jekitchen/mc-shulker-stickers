package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;

/** Persistent-data keys. Sticker keys come from core's {@link StickerFormat}. */
final class PaperKeys {

    /**
     * Box key: 1 when the box's name was set by a player without
     * {@code shulker_stickers.name.display}, so it isn't drawn. TODO(core): move the key name to StickerFormat.
     */
    static final NamespacedKey NAME_HIDDEN = key("name_hidden");

    /** Marks our display entities, so strays can be found and removed. */
    static final NamespacedKey DISPLAY = key("display");

    /** Player keys. */
    static final NamespacedKey HELP_SEEN = key("help_seen");
    static final NamespacedKey GESTURE_OFF = key("gesture_off");

    /** Every key that belongs to a box (sticker keys plus {@link #NAME_HIDDEN}). */
    static final List<NamespacedKey> BOX_KEYS;

    static {
        List<NamespacedKey> keys = new ArrayList<>();
        StickerFormat.KEYS.forEach(k -> keys.add(sticker(k)));
        keys.add(NAME_HIDDEN);
        BOX_KEYS = List.copyOf(keys);
    }

    private PaperKeys() {}

    static NamespacedKey sticker(String shortKey) {
        return key(shortKey);
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(StickerFormat.NAMESPACE, name);
    }
}
