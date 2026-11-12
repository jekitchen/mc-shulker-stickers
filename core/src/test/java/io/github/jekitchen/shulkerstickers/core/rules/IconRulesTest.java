package io.github.jekitchen.shulkerstickers.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IconRulesTest {

    @Test
    void visualComponentsAreThePrdList() {
        assertEquals(List.of(
                "minecraft:custom_model_data", "minecraft:item_model", "minecraft:enchantment_glint_override",
                "minecraft:dyed_color", "minecraft:trim", "minecraft:profile", "minecraft:banner_patterns",
                "minecraft:base_color", "minecraft:potion_contents", "minecraft:firework_explosion",
                "minecraft:fireworks"), IconRules.VISUAL_COMPONENTS);
    }

    @Test
    void isVisualComponentAcceptsIdsWithOrWithoutNamespace() {
        assertTrue(IconRules.isVisualComponent("minecraft:trim"));
        assertTrue(IconRules.isVisualComponent("trim"));
        assertFalse(IconRules.isVisualComponent("minecraft:container"));
        assertFalse(IconRules.isVisualComponent("minecraft:writable_book_content"));
    }

    @Test
    void blocklistMatchesIdsWithOrWithoutNamespace() {
        assertTrue(IconRules.isBlocked("minecraft:bedrock", List.of("bedrock")));
        assertTrue(IconRules.isBlocked("minecraft:bedrock", List.of("minecraft:bedrock")));
        assertFalse(IconRules.isBlocked("minecraft:stone", List.of("minecraft:bedrock")));
    }

    @Test
    void tagEntriesInTheListAreNotTreatedAsIds() {
        assertFalse(IconRules.isBlocked("minecraft:bedrock", List.of("#minecraft:bedrock")));
    }

    @Test
    void fitsSizeCountsUtf8BytesOfIdAndVisual() {
        Sticker sticker = new Sticker(Optional.of("minecraft:stone"), Optional.of("{a:\"é\"}"), Anchor.LID, 0, Optional.empty());
        int bytes = "minecraft:stone".length() + "{a:\"é\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        assertTrue(IconRules.fitsSize(sticker, bytes));
        assertFalse(IconRules.fitsSize(sticker, bytes - 1));
    }

    @Test
    void noIconAlwaysFits() {
        Sticker sticker = new Sticker(Optional.empty(), Optional.empty(), Anchor.LID, 0, Optional.empty());

        assertTrue(IconRules.fitsSize(sticker, 0));
    }

    @Test
    void namespaceDefaultsToMinecraft() {
        assertEquals("minecraft:stone", IconRules.withNamespace("stone"));
        assertEquals("mymod:thing", IconRules.withNamespace("mymod:thing"));
    }
}
