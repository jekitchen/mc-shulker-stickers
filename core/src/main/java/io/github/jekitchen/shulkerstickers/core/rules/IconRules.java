package io.github.jekitchen.shulkerstickers.core.rules;

import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/** What an icon may store and show (PRD §5.3). Checked when applied and again when drawn. */
public final class IconRules {

    /** The only item components an icon keeps; everything else is dropped. */
    public static final List<String> VISUAL_COMPONENTS = List.of(
            "minecraft:custom_model_data",
            "minecraft:item_model",
            "minecraft:enchantment_glint_override",
            "minecraft:dyed_color",
            "minecraft:trim",
            "minecraft:profile",
            "minecraft:banner_patterns",
            "minecraft:base_color",
            "minecraft:potion_contents",
            "minecraft:firework_explosion",
            "minecraft:fireworks");

    private IconRules() {}

    public static boolean isVisualComponent(String componentId) {
        return VISUAL_COMPONENTS.contains(withNamespace(componentId));
    }

    /**
     * True if {@code itemId} is listed in the config blocklist. Entries starting with
     * {@code #} are item tags, which only the platform can resolve, so they're skipped here.
     */
    public static boolean isBlocked(String itemId, Collection<String> blocklist) {
        String id = withNamespace(itemId);
        return blocklist.stream().filter(e -> !e.startsWith("#")).map(IconRules::withNamespace).anyMatch(id::equals);
    }

    /** True if the icon's stored data (ID plus visual SNBT, as UTF-8) is at most {@code maxBytes}. */
    public static boolean fitsSize(Sticker sticker, int maxBytes) {
        int bytes = sticker.iconId().map(IconRules::utf8Length).orElse(0)
                + sticker.iconVisual().map(IconRules::utf8Length).orElse(0);
        return bytes <= maxBytes;
    }

    public static String withNamespace(String id) {
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    private static int utf8Length(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
