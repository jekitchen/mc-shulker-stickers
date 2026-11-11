package io.github.jekitchen.shulkerstickers.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IconVisualsTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void keepsOnlyVisualComponentsSortedAndCompact() {
        JsonObject components = json("""
                {"minecraft:trim": {"material": "minecraft:gold", "pattern": "minecraft:coast"},
                 "minecraft:container": [{"slot": 0}],
                 "minecraft:dyed_color": 16711680,
                 "minecraft:lore": ["secret"]}
                """);

        assertEquals(
                Optional.of("{\"minecraft:dyed_color\":16711680,\"minecraft:trim\":{\"material\":\"minecraft:gold\",\"pattern\":\"minecraft:coast\"}}"),
                IconVisuals.canonicalize(components));
    }

    @Test
    void enchantedItemsGetAGlintOverride() {
        // Enchantments themselves aren't stored; only the shine is kept.
        assertEquals(Optional.of("{\"minecraft:enchantment_glint_override\":true}"),
                IconVisuals.canonicalize(json("{\"minecraft:enchantments\":{\"minecraft:sharpness\":5}}"), true));
    }

    @Test
    void anExplicitGlintOverrideWins() {
        assertEquals(Optional.of("{\"minecraft:enchantment_glint_override\":false}"),
                IconVisuals.canonicalize(json("{\"minecraft:enchantment_glint_override\":false}"), true));
    }

    @Test
    void notEnchantedAddsNothing() {
        assertEquals(Optional.empty(), IconVisuals.canonicalize(json("{}"), false));
    }

    @Test
    void unnamespacedKeysAreNamespaced() {
        assertEquals(Optional.of("{\"minecraft:dyed_color\":1}"), IconVisuals.canonicalize(json("{\"dyed_color\":1}")));
    }

    @Test
    void removedComponentMarkersAreDropped() {
        // Component patches mark removed components with a leading "!".
        assertEquals(Optional.empty(), IconVisuals.canonicalize(json("{\"!minecraft:dyed_color\":{}}")));
    }

    @Test
    void nothingVisualGivesEmpty() {
        assertEquals(Optional.empty(), IconVisuals.canonicalize(json("{\"minecraft:lore\":[\"x\"]}")));
    }

    @Test
    void nestedObjectKeysAreSortedToo() {
        String out = IconVisuals.canonicalize(json("{\"minecraft:trim\":{\"pattern\":\"p\",\"material\":\"m\"}}")).orElseThrow();

        assertEquals("{\"minecraft:trim\":{\"material\":\"m\",\"pattern\":\"p\"}}", out);
    }

    @Test
    void parseReadsBackAnObjectAndRejectsJunk() {
        assertTrue(IconVisuals.parse("{\"minecraft:dyed_color\":1}").isPresent());
        assertTrue(IconVisuals.parse("not json").isEmpty());
        assertTrue(IconVisuals.parse("[1,2]").isEmpty());
    }

    @Test
    void parseRefiltersUntrustedData() {
        JsonObject parsed = IconVisuals.parse("{\"minecraft:dyed_color\":1,\"minecraft:container\":[]}").orElseThrow();

        assertEquals(1, parsed.size());
        assertTrue(parsed.has("minecraft:dyed_color"));
    }
}
