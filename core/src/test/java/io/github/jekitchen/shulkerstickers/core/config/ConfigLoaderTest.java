package io.github.jekitchen.shulkerstickers.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @Test
    void defaultsMatchThePrd() {
        StickerConfig d = StickerConfig.DEFAULTS;

        assertTrue(d.colorEnabled() && d.iconEnabled() && d.nameEditEnabled() && d.nameDisplayEnabled());
        assertEquals(Cost.NONE, d.colorCost());
        assertEquals(Cost.NONE, d.iconCost());
        assertEquals(Cost.NONE, d.nameCost());
        assertEquals(List.of(), d.iconBlocklist());
        assertEquals(2048, d.iconMaxDataBytes());
        assertEquals(32, d.iconViewRange());
        assertEquals(10, d.nameViewRange());
        assertFalse(d.simulatePlaceEvent());
        assertFalse(d.iconFullBright());
        assertEquals(24, d.nameMaxDisplayChars());
        assertEquals(List.of(), d.nameBlockedWords());
        assertTrue(d.hideWhileOpen());
        assertTrue(d.firstPlaceMessage());
        assertFalse(d.allowAdventureMode());
    }

    @Test
    void emptyObjectGivesDefaultsWithoutWarnings() {
        ConfigLoader.Result result = ConfigLoader.parse("{}");

        assertEquals(StickerConfig.DEFAULTS, result.config());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void readsNestedKeys() {
        String json = """
                {
                  "color": {"enabled": false, "cost": {"type": "xp_levels", "amount": 2}},
                  "icon": {"blocklist": ["minecraft:bedrock"], "max_data_bytes": 512, "view_range": 20,
                           "full_bright": true, "cost": {"type": "item", "item": "minecraft:paper", "amount": 1}},
                  "name": {"edit_enabled": false, "display_enabled": false, "view_range": 8,
                           "max_display_chars": 10, "blocked_words": ["bad", "/b.d/"]},
                  "display": {"hide_while_open": false},
                  "help": {"first_place_message": false},
                  "allow_adventure_mode": true
                }
                """;

        ConfigLoader.Result result = ConfigLoader.parse(json);
        StickerConfig c = result.config();

        assertEquals(List.of(), result.warnings());
        assertFalse(c.colorEnabled());
        assertEquals(new Cost(Cost.Type.XP_LEVELS, 2, null), c.colorCost());
        assertEquals(new Cost(Cost.Type.ITEM, 1, "minecraft:paper"), c.iconCost());
        assertEquals(List.of("minecraft:bedrock"), c.iconBlocklist());
        assertEquals(512, c.iconMaxDataBytes());
        assertEquals(20, c.iconViewRange());
        assertTrue(c.iconFullBright());
        assertFalse(c.nameEditEnabled());
        assertFalse(c.nameDisplayEnabled());
        assertEquals(8, c.nameViewRange());
        assertEquals(10, c.nameMaxDisplayChars());
        assertEquals(List.of("bad", "/b.d/"), c.nameBlockedWords());
        assertFalse(c.hideWhileOpen());
        assertFalse(c.firstPlaceMessage());
        assertTrue(c.allowAdventureMode());
    }

    @Test
    void wrongTypesKeepDefaultsAndWarn() {
        ConfigLoader.Result result = ConfigLoader.parse("""
                {"color": {"enabled": "yes"}, "icon": {"max_data_bytes": "big", "blocklist": "stone"}}
                """);

        assertEquals(StickerConfig.DEFAULTS, result.config());
        assertEquals(3, result.warnings().size());
        assertTrue(result.warnings().getFirst().contains("color.enabled"), result.warnings().toString());
    }

    @Test
    void negativeNumbersAreRejected() {
        ConfigLoader.Result result = ConfigLoader.parse("{\"icon\": {\"view_range\": -5}}");

        assertEquals(32, result.config().iconViewRange());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void badCostTypeFallsBackToNone() {
        ConfigLoader.Result result = ConfigLoader.parse("{\"name\": {\"cost\": {\"type\": \"diamonds\"}}}");

        assertEquals(Cost.NONE, result.config().nameCost());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void itemCostWithoutItemFallsBackToNone() {
        ConfigLoader.Result result = ConfigLoader.parse("{\"name\": {\"cost\": {\"type\": \"item\", \"amount\": 3}}}");

        assertEquals(Cost.NONE, result.config().nameCost());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void brokenJsonGivesDefaultsAndOneWarning() {
        ConfigLoader.Result result = ConfigLoader.parse("{ not json");

        assertEquals(StickerConfig.DEFAULTS, result.config());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void unknownKeysWarn() {
        ConfigLoader.Result result = ConfigLoader.parse("{\"colour\": {}}");

        assertEquals(List.of("Unknown setting 'colour' ignored"), result.warnings());
    }

    @Test
    void toJsonRoundTrips() {
        StickerConfig custom = ConfigLoader.parse("""
                {"color": {"cost": {"type": "xp_levels", "amount": 1}}, "name": {"blocked_words": ["x"]}}
                """).config();

        assertEquals(custom, ConfigLoader.parse(ConfigLoader.toJson(custom)).config());
    }

    @Test
    void loadOrCreateWritesDefaultsWhenMissing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub/shulker_stickers.json");

        ConfigLoader.Result result = ConfigLoader.loadOrCreate(file);

        assertEquals(StickerConfig.DEFAULTS, result.config());
        assertTrue(Files.exists(file));
        assertEquals(StickerConfig.DEFAULTS, ConfigLoader.parse(Files.readString(file)).config());
    }

    @Test
    void loadOrCreateReadsExistingFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("shulker_stickers.json");
        Files.writeString(file, "{\"allow_adventure_mode\": true}");

        assertTrue(ConfigLoader.loadOrCreate(file).config().allowAdventureMode());
        assertEquals("{\"allow_adventure_mode\": true}", Files.readString(file));
    }
}
