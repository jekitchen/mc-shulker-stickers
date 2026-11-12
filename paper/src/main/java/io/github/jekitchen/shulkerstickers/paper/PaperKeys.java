package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import java.util.List;
import org.bukkit.NamespacedKey;

/** Persistent-data keys. Sticker keys come from core's {@link StickerFormat}. */
final class PaperKeys {

    /** Box key: 1 when the name isn't drawn (see {@link StickerFormat#NAME_HIDDEN}). */
    static final NamespacedKey NAME_HIDDEN = key(StickerFormat.NAME_HIDDEN);

    /** Marks our display entities, so strays can be found and removed. */
    static final NamespacedKey DISPLAY = key("display");

    /** Player keys. */
    static final NamespacedKey HELP_SEEN = key(StickerFormat.PLAYER_HELP_SEEN);
    static final NamespacedKey GESTURE_OFF = key(StickerFormat.PLAYER_GESTURE_OFF);

    /** Every key that belongs to a box (sticker keys plus {@link #NAME_HIDDEN}). */
    static final List<NamespacedKey> BOX_KEYS = StickerFormat.BOX_KEYS.stream().map(PaperKeys::key).toList();

    private PaperKeys() {}

    static NamespacedKey sticker(String shortKey) {
        return key(shortKey);
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(StickerFormat.NAMESPACE, name);
    }
}
