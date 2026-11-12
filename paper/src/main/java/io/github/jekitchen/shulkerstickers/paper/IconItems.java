package io.github.jekitchen.shulkerstickers.paper;

import com.google.gson.JsonObject;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import io.github.jekitchen.shulkerstickers.core.rules.IconRules;
import io.github.jekitchen.shulkerstickers.core.rules.IconVisuals;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

/**
 * Turns an off-hand item into stored icon data and back (PRD §5.3).
 *
 * <p>Only the item ID and the visual components survive. Components are read through
 * Minecraft's own codec ({@code UnsafeValues#serializeItemAsJson}), so the stored JSON
 * matches what the Fabric build writes for the same item.
 */
final class IconItems {

    /** The datapack tag shipped in {@code stickers_datapack}. */
    static final NamespacedKey BLOCKLIST_TAG = new NamespacedKey(StickerFormat.NAMESPACE, "icon_blocklist");

    record Captured(String id, Optional<String> visual) {}

    private IconItems() {}

    @SuppressWarnings("deprecation") // UnsafeValues is the only API that exposes component JSON
    static Captured capture(ItemStack stack) {
        String id = stack.getType().getKey().toString();
        try {
            // Only the visual components are serialized: the held item can be anything (a box full
            // of books), and serializing all of it on every click would be costly.
            ItemStack visualOnly;
            try {
                visualOnly = ItemStack.of(stack.getType());
                visualOnly.copyDataFrom(stack, type -> IconRules.isVisualComponent(type.key().asString()));
            } catch (RuntimeException e) {
                visualOnly = stack.asOne(); // test servers (MockBukkit) lack copyDataFrom; real ones have it
            }
            JsonObject json = Bukkit.getUnsafe().serializeItemAsJson(visualOnly);
            JsonObject components = json.has("components") && json.get("components").isJsonObject()
                    ? json.getAsJsonObject("components")
                    : new JsonObject();
            return new Captured(id, IconVisuals.canonicalize(components, !stack.getEnchantments().isEmpty()));
        } catch (RuntimeException e) {
            return new Captured(id, Optional.empty());
        }
    }

    /**
     * The item to draw, rebuilt from stored data that may be hand-edited or from another
     * version. Empty when it isn't allowed or can't be read; the stored data is left alone.
     */
    @SuppressWarnings("deprecation")
    static Optional<ItemStack> render(Sticker sticker, StickerConfig config) {
        if (sticker.iconId().isEmpty()) {
            return Optional.empty();
        }
        String id = sticker.iconId().get();
        if (!allowed(id, sticker.iconVisual(), config)) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(id);
        if (sticker.iconVisual().isEmpty()) {
            return Optional.of(new ItemStack(material));
        }
        Optional<JsonObject> components = sticker.iconVisual().flatMap(IconVisuals::parse);
        if (components.isEmpty()) {
            return Optional.empty(); // visual data we can't read: draw nothing, keep the data (PRD §6.1)
        }
        if (components.get().isEmpty()) {
            return Optional.of(new ItemStack(material));
        }
        // An item_model can make an allowed item look like a blocked one: check the item it names too.
        if (components.get().get("minecraft:item_model") instanceof com.google.gson.JsonPrimitive model && model.isString()) {
            String modelId = IconRules.withNamespace(model.getAsString());
            Material looksLike = Material.matchMaterial(modelId);
            if (IconRules.isBlocked(modelId, config.iconBlocklist())
                    || (looksLike != null && looksLike.isItem() && inBlockedTag(looksLike, config))) {
                return Optional.empty();
            }
        }
        try {
            JsonObject json = new JsonObject();
            json.addProperty("id", IconRules.withNamespace(id));
            json.addProperty("count", 1);
            json.add("components", components.get());
            json.addProperty("DataVersion", Bukkit.getUnsafe().getDataVersion());
            return Optional.of(Bukkit.getUnsafe().deserializeItemFromJson(json));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Checked when "Use off-hand item" is clicked, at Save, and every time an icon is drawn. */
    static boolean allowed(String id, Optional<String> visual, StickerConfig config) {
        Material material = Material.matchMaterial(id);
        if (material == null || !material.isItem() || material.isAir()) {
            return false;
        }
        // Only the exact form ("minecraft:stone", or "stone"): matchMaterial also takes "STONE" and
        // "minecraft:STONE", which a crafted item could use to slip past the blocklist's IDs.
        if (!material.getKey().toString().equals(IconRules.withNamespace(id))) {
            return false;
        }
        if (IconRules.isBlocked(material.getKey().toString(), config.iconBlocklist()) || inBlockedTag(material, config)) {
            return false;
        }
        Sticker sizeCheck = new Sticker(Optional.of(id), visual,
                io.github.jekitchen.shulkerstickers.core.Anchor.LID, 0, Optional.empty());
        return IconRules.fitsSize(sizeCheck, config.iconMaxDataBytes());
    }

    private static boolean inBlockedTag(Material material, StickerConfig config) {
        if (isTagged(BLOCKLIST_TAG, material)) {
            return true;
        }
        for (String entry : config.iconBlocklist()) {
            if (entry.startsWith("#")) {
                NamespacedKey key = NamespacedKey.fromString(IconRules.withNamespace(entry.substring(1)));
                if (key != null && isTagged(key, material)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTagged(NamespacedKey key, Material material) {
        try {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
            return tag != null && tag.isTagged(material);
        } catch (RuntimeException e) {
            return false; // test servers may not model datapack tags
        }
    }
}
