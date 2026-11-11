package io.github.jekitchen.shulkerstickers.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StickerCodecTest {

    private static final Sticker FULL = new Sticker(
            Optional.of("minecraft:cobblestone"), Optional.of("{}"), Anchor.SIDE, 2, Optional.of(Face.EAST));

    @Test
    void fullKeyAddsNamespace() {
        assertEquals("shulker_stickers:icon_id", StickerFormat.fullKey(StickerFormat.ICON_ID));
    }

    @Test
    void encodeWritesShortKeysAndVersion() {
        Map<String, Object> map = StickerCodec.encode(FULL);

        assertEquals(1, map.get("v"));
        assertEquals("minecraft:cobblestone", map.get("icon_id"));
        assertEquals("{}", map.get("icon_visual"));
        assertEquals("side", map.get("anchor"));
        assertEquals(2, map.get("rot"));
        assertEquals("east", map.get("face"));
    }

    @Test
    void encodeOmitsAbsentOptionals() {
        Sticker nameOnly = new Sticker(Optional.empty(), Optional.empty(), Anchor.LID, 0, Optional.empty());

        Map<String, Object> map = StickerCodec.encode(nameOnly);

        assertFalse(map.containsKey("icon_id"));
        assertFalse(map.containsKey("icon_visual"));
        assertFalse(map.containsKey("face"));
        assertFalse(nameOnly.hasIcon());
    }

    @Test
    void roundTrips() {
        assertEquals(Optional.of(FULL), StickerCodec.decode(StickerCodec.encode(FULL)));
    }

    @Test
    void missingVersionIsUnreadable() {
        Map<String, Object> map = new HashMap<>(StickerCodec.encode(FULL));
        map.remove("v");

        assertTrue(StickerCodec.decode(map).isEmpty());
    }

    @Test
    void newerVersionIsUnreadable() {
        Map<String, Object> map = new HashMap<>(StickerCodec.encode(FULL));
        map.put("v", 2);

        assertTrue(StickerCodec.decode(map).isEmpty());
    }

    @Test
    void unknownAnchorIsUnreadable() {
        Map<String, Object> map = new HashMap<>(StickerCodec.encode(FULL));
        map.put("anchor", "diagonal");

        assertTrue(StickerCodec.decode(map).isEmpty());
    }

    @Test
    void wrongTypedOptionalFieldsAreIgnored() {
        Map<String, Object> map = new HashMap<>(StickerCodec.encode(FULL));
        map.put("icon_visual", 42);
        map.put("rot", "three");
        map.put("face", "sideways");

        Sticker sticker = StickerCodec.decode(map).orElseThrow();

        assertEquals(Optional.empty(), sticker.iconVisual());
        assertEquals(0, sticker.rot());
        assertEquals(Optional.empty(), sticker.face());
        assertEquals(Optional.of("minecraft:cobblestone"), sticker.iconId());
    }

    @Test
    void rotationIsNormalized() {
        Map<String, Object> map = new HashMap<>(StickerCodec.encode(FULL));
        map.put("rot", -1);

        assertEquals(3, StickerCodec.decode(map).orElseThrow().rot());
    }

    @Test
    void anyIntegralNumberIsAcceptedForInts() {
        Map<String, Object> map = new HashMap<>(StickerCodec.encode(FULL));
        map.put("v", (byte) 1);
        map.put("rot", 1L);

        assertEquals(1, StickerCodec.decode(map).orElseThrow().rot());
    }
}
