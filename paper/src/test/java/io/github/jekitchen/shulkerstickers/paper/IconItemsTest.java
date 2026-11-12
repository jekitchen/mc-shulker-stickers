package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Icon rules applied when "Use off-hand item" is clicked, at Save, and when drawn (PRD §5.3). */
class IconItemsTest {

    private static final StickerConfig BLOCKS_BEDROCK = ConfigLoader.parse("""
            {"icon": {"blocklist": ["bedrock"], "max_data_bytes": 40}}
            """).config();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void ordinaryItemIsAllowed() {
        assertTrue(IconItems.allowed("minecraft:cobblestone", Optional.empty(), BLOCKS_BEDROCK));
    }

    @Test
    void configBlocklistIsHonored() {
        assertFalse(IconItems.allowed("minecraft:bedrock", Optional.empty(), BLOCKS_BEDROCK));
    }

    @Test
    void oversizedIconIsRefused() {
        assertFalse(IconItems.allowed("minecraft:cobblestone", Optional.of("{\"minecraft:dyed_color\":16711680}"),
                BLOCKS_BEDROCK));
    }

    /** Security: Material.matchMaterial also takes "BEDROCK" and "minecraft:BEDROCK"; those must not slip past the blocklist. */
    @Test
    void oddSpellingsOfAnItemIdAreRefused() {
        assertFalse(IconItems.allowed("BEDROCK", Optional.empty(), BLOCKS_BEDROCK));
        assertFalse(IconItems.allowed("minecraft:BEDROCK", Optional.empty(), BLOCKS_BEDROCK));
        assertFalse(IconItems.allowed(" bedrock", Optional.empty(), BLOCKS_BEDROCK));
        assertFalse(IconItems.allowed("COBBLESTONE", Optional.empty(), BLOCKS_BEDROCK), "only the exact form is accepted");
        assertTrue(IconItems.allowed("cobblestone", Optional.empty(), BLOCKS_BEDROCK), "a missing namespace is fine");
    }

    @Test
    void unknownOrNonItemIdsAreRefused() {
        assertFalse(IconItems.allowed("minecraft:not_a_real_item", Optional.empty(), StickerConfig.DEFAULTS));
        assertFalse(IconItems.allowed("minecraft:water", Optional.empty(), StickerConfig.DEFAULTS));
    }

    @Test
    void airIsRefused() {
        assertFalse(IconItems.allowed("minecraft:air", Optional.empty(), StickerConfig.DEFAULTS));
    }
}
