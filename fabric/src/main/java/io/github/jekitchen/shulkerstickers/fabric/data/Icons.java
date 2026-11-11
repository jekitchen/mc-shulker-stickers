package io.github.jekitchen.shulkerstickers.fabric.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.rules.IconRules;
import io.github.jekitchen.shulkerstickers.core.rules.IconVisuals;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Turns an off-hand item into icon data and back (PRD §5.3). Only the item ID and the visual
 * components are kept; stored data is re-checked every time it is drawn.
 */
public final class Icons {

    /** Datapack tag of items that can't be icons, in addition to {@code icon.blocklist}. */
    public static final TagKey<Item> BLOCKLIST_TAG =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("shulker_stickers", "icon_blocklist"));

    private static final String GLINT_OVERRIDE = "minecraft:enchantment_glint_override";

    private Icons() {
    }

    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * The canonical {@code icon_visual} JSON for a stack. Enchanted items keep their glint as an
     * explicit glint override, since enchantments themselves aren't stored.
     */
    public static Optional<String> visualOf(ItemStack stack, HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonElement encoded = DataComponentPatch.CODEC.encodeStart(ops, stack.getComponentsPatch())
                .result().orElse(new JsonObject());
        JsonObject components = encoded.isJsonObject() ? encoded.getAsJsonObject() : new JsonObject();
        if (stack.isEnchanted() && !components.has(GLINT_OVERRIDE)) {
            components.addProperty(GLINT_OVERRIDE, true);
        }
        return IconVisuals.canonicalize(components);
    }

    /** True if the item is on the config blocklist (IDs or {@code #tags}) or in the blocklist tag. */
    public static boolean isBlocked(ItemStack stack, StickerConfig config) {
        if (stack.is(BLOCKLIST_TAG) || IconRules.isBlocked(idOf(stack), config.iconBlocklist())) {
            return true;
        }
        for (String entry : config.iconBlocklist()) {
            if (entry.startsWith("#")) {
                Identifier tag = Identifier.tryParse(entry.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The stack to draw for a stored icon, or empty if it must not be drawn: unknown item,
     * unreadable visuals, blocklisted, or larger than {@code icon.max_data_bytes}.
     */
    public static Optional<ItemStack> displayStack(Sticker sticker, StickerConfig config, HolderLookup.Provider registries) {
        if (sticker.iconId().isEmpty() || !IconRules.fitsSize(sticker, config.iconMaxDataBytes())) {
            return Optional.empty();
        }
        Identifier id = Identifier.tryParse(sticker.iconId().get());
        Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty() || item.get() == Items.AIR) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(item.get());
        if (sticker.iconVisual().isPresent()) {
            Optional<JsonObject> visual = IconVisuals.parse(sticker.iconVisual().get());
            if (visual.isEmpty()) {
                return Optional.empty();
            }
            RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
            Optional<DataComponentPatch> patch = DataComponentPatch.CODEC.parse(ops, visual.get()).result();
            if (patch.isEmpty()) {
                return Optional.empty();
            }
            try {
                stack.applyComponents(patch.get());
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
        return isBlocked(stack, config) ? Optional.empty() : Optional.of(stack);
    }
}
