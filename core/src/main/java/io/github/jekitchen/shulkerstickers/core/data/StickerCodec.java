package io.github.jekitchen.shulkerstickers.core.data;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Converts a {@link Sticker} to and from a flat map of short keys to {@code Integer} or
 * {@code String} values. Each platform copies the map into its own tag type.
 *
 * <p>Decoding never throws. Data it can't read (no or newer version, unknown anchor) decodes
 * to empty; callers keep the raw data and draw nothing, so a later version can recover it.
 */
public final class StickerCodec {

    private StickerCodec() {}

    public static Map<String, Object> encode(Sticker sticker) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(StickerFormat.V, StickerFormat.VERSION);
        sticker.iconId().ifPresent(id -> map.put(StickerFormat.ICON_ID, id));
        sticker.iconVisual().ifPresent(visual -> map.put(StickerFormat.ICON_VISUAL, visual));
        map.put(StickerFormat.ANCHOR, sticker.anchor().serializedName());
        map.put(StickerFormat.ROT, sticker.rot());
        sticker.face().ifPresent(face -> map.put(StickerFormat.FACE, face.serializedName()));
        return map;
    }

    public static Optional<Sticker> decode(Map<String, ?> map) {
        Optional<Integer> version = intValue(map.get(StickerFormat.V));
        if (version.isEmpty() || version.get() < 1 || version.get() > StickerFormat.VERSION) {
            return Optional.empty();
        }
        Optional<Anchor> anchor = stringValue(map.get(StickerFormat.ANCHOR))
                .flatMap(name -> Arrays.stream(Anchor.values())
                        .filter(a -> a.serializedName().equals(name))
                        .findFirst());
        if (anchor.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Sticker(
                stringValue(map.get(StickerFormat.ICON_ID)),
                stringValue(map.get(StickerFormat.ICON_VISUAL)),
                anchor.get(),
                intValue(map.get(StickerFormat.ROT)).orElse(0),
                stringValue(map.get(StickerFormat.FACE)).flatMap(Face::fromSerializedName)));
    }

    private static Optional<Integer> intValue(Object value) {
        if (value instanceof Integer || value instanceof Short || value instanceof Byte || value instanceof Long) {
            return Optional.of(((Number) value).intValue());
        }
        return Optional.empty();
    }

    private static Optional<String> stringValue(Object value) {
        return value instanceof String s ? Optional.of(s) : Optional.empty();
    }
}
