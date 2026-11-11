package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.data.StickerCodec;
import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reads and writes sticker data in a persistent data container (block or item), using
 * core's codec so the stored keys and values match the Fabric build exactly.
 */
final class StickerStore {

    private StickerStore() {}

    /** Replaces the sticker keys with {@code sticker}; the name-hidden flag is left alone. */
    static void write(PersistentDataContainer pdc, Sticker sticker) {
        StickerFormat.KEYS.forEach(k -> pdc.remove(PaperKeys.sticker(k)));
        StickerCodec.encode(sticker).forEach((shortKey, value) -> {
            NamespacedKey key = PaperKeys.sticker(shortKey);
            if (value instanceof Integer i) {
                pdc.set(key, PersistentDataType.INTEGER, i);
            } else {
                pdc.set(key, PersistentDataType.STRING, (String) value);
            }
        });
    }

    /** Empty if there is no sticker or it can't be read; unreadable data is left in place. */
    static Optional<Sticker> read(PersistentDataContainerView pdc) {
        return StickerCodec.decode(raw(pdc));
    }

    /** Our sticker values by short key, as stored. */
    static Map<String, Object> raw(PersistentDataContainerView pdc) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String shortKey : StickerFormat.KEYS) {
            NamespacedKey key = PaperKeys.sticker(shortKey);
            if (pdc.has(key, PersistentDataType.INTEGER)) {
                map.put(shortKey, pdc.get(key, PersistentDataType.INTEGER));
            } else if (pdc.has(key, PersistentDataType.STRING)) {
                map.put(shortKey, pdc.get(key, PersistentDataType.STRING));
            }
        }
        return map;
    }

    /** True if any of our box keys is present, readable or not. */
    static boolean hasData(PersistentDataContainerView pdc) {
        return PaperKeys.BOX_KEYS.stream().anyMatch(pdc::has);
    }

    static boolean isNameHidden(PersistentDataContainerView pdc) {
        return pdc.getOrDefault(PaperKeys.NAME_HIDDEN, PersistentDataType.INTEGER, 0) == 1;
    }

    static void setNameHidden(PersistentDataContainer pdc, boolean hidden) {
        if (hidden) {
            pdc.set(PaperKeys.NAME_HIDDEN, PersistentDataType.INTEGER, 1);
        } else {
            pdc.remove(PaperKeys.NAME_HIDDEN);
        }
    }

    static void clear(PersistentDataContainer pdc) {
        PaperKeys.BOX_KEYS.forEach(pdc::remove);
    }

    /** Copies only our keys, so other plugins' data on the target is left alone. */
    static void copy(PersistentDataContainerView from, PersistentDataContainer to) {
        for (NamespacedKey key : PaperKeys.BOX_KEYS) {
            copyKey(from, to, key, PersistentDataType.STRING);
            copyKey(from, to, key, PersistentDataType.INTEGER);
        }
    }

    private static <T> void copyKey(PersistentDataContainerView from, PersistentDataContainer to,
            NamespacedKey key, PersistentDataType<?, T> type) {
        if (from.has(key, type)) {
            to.set(key, type, from.get(key, type));
        }
    }
}
