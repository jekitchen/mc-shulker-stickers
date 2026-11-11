package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import java.util.Arrays;
import java.util.Optional;
import org.bukkit.persistence.PersistentDataContainer;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;

/** Reads and writes sticker data in a persistent data container (block or item). */
final class StickerStore {

    private StickerStore() {}

    static void write(PersistentDataContainer pdc, StickerData data) {
        pdc.set(StickerKeys.VERSION, PersistentDataType.INTEGER, StickerKeys.FORMAT_VERSION);
        pdc.set(StickerKeys.ICON_ID, PersistentDataType.STRING, data.iconId());
        pdc.set(StickerKeys.ICON_VISUAL, PersistentDataType.STRING, data.iconVisual());
        pdc.set(StickerKeys.ANCHOR, PersistentDataType.STRING, data.anchor().serializedName());
        pdc.set(StickerKeys.ROT, PersistentDataType.INTEGER, data.rotation());
    }

    /** Empty if there is no icon or it can't be read; the raw data is left in place either way. */
    static Optional<StickerData> read(PersistentDataContainerView pdc) {
        String iconId = pdc.get(StickerKeys.ICON_ID, PersistentDataType.STRING);
        if (iconId == null) {
            return Optional.empty();
        }
        String visual = pdc.getOrDefault(StickerKeys.ICON_VISUAL, PersistentDataType.STRING, "{}");
        String anchorName = pdc.getOrDefault(StickerKeys.ANCHOR, PersistentDataType.STRING, "lid");
        Optional<Anchor> anchor = Arrays.stream(Anchor.values())
                .filter(a -> a.serializedName().equals(anchorName))
                .findFirst();
        int rotation = pdc.getOrDefault(StickerKeys.ROT, PersistentDataType.INTEGER, 0);
        return anchor.map(a -> new StickerData(iconId, visual, a, Math.floorMod(rotation, 4)));
    }

    static void clear(PersistentDataContainer pdc) {
        StickerKeys.ALL.forEach(pdc::remove);
    }

    /** Copies only our keys, so other plugins' data on the target is left alone. */
    static void copy(PersistentDataContainerView from, PersistentDataContainer to) {
        for (var key : StickerKeys.ALL) {
            copyKey(from, to, key, PersistentDataType.STRING);
            copyKey(from, to, key, PersistentDataType.INTEGER);
        }
    }

    private static <T> void copyKey(
            PersistentDataContainerView from, PersistentDataContainer to,
            org.bukkit.NamespacedKey key, PersistentDataType<?, T> type) {
        if (from.has(key, type)) {
            to.set(key, type, from.get(key, type));
        }
    }
}
