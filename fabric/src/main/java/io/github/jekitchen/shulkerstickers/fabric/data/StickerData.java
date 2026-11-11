package io.github.jekitchen.shulkerstickers.fabric.data;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes sticker data in {@code minecraft:custom_data → PublicBukkitValues}, the same
 * layout Paper uses for plugin data (PRD §6.1). Other keys are always left alone.
 *
 * <p>TODO(after spike): the key names and the version belong in core, shared with Paper.
 */
public final class StickerData {

    public static final String BUKKIT_VALUES = "PublicBukkitValues";
    public static final String KEY_VERSION = "shulker_stickers:v";
    public static final String KEY_ICON_ID = "shulker_stickers:icon_id";
    public static final String KEY_ICON_VISUAL = "shulker_stickers:icon_visual";
    public static final String KEY_ANCHOR = "shulker_stickers:anchor";
    public static final String KEY_ROT = "shulker_stickers:rot";
    public static final int FORMAT_VERSION = 1;

    private StickerData() {
    }

    public static Optional<Sticker> read(BlockEntity blockEntity) {
        return read(blockEntity.components().get(DataComponents.CUSTOM_DATA));
    }

    public static Optional<Sticker> read(ItemStack stack) {
        return read(stack.get(DataComponents.CUSTOM_DATA));
    }

    private static Optional<Sticker> read(@Nullable CustomData data) {
        if (data == null) {
            return Optional.empty();
        }
        CompoundTag bukkit = data.copyTag().getCompoundOrEmpty(BUKKIT_VALUES);
        Optional<String> iconId = bukkit.getString(KEY_ICON_ID);
        if (iconId.isEmpty() || bukkit.getIntOr(KEY_VERSION, 0) != FORMAT_VERSION) {
            return Optional.empty();
        }
        String anchorName = bukkit.getStringOr(KEY_ANCHOR, Anchor.LID.serializedName());
        Anchor anchor = Arrays.stream(Anchor.values())
                .filter(a -> a.serializedName().equals(anchorName))
                .findFirst()
                .orElse(Anchor.LID);
        return Optional.of(new Sticker(
                iconId.get(),
                bukkit.getStringOr(KEY_ICON_VISUAL, ""),
                anchor,
                Math.floorMod(bukkit.getIntOr(KEY_ROT, 0), 4)));
    }

    /** Sets or (with an empty sticker) removes the sticker on a block entity. */
    public static void write(BlockEntity blockEntity, Optional<Sticker> sticker) {
        CustomData current = blockEntity.components().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CustomData updated = apply(current, sticker);
        blockEntity.setComponents(DataComponentMap.builder()
                .addAll(blockEntity.components())
                .set(DataComponents.CUSTOM_DATA, updated.isEmpty() ? null : updated)
                .build());
        blockEntity.setChanged();
    }

    /** Copies the whole {@code custom_data} component of a block entity onto a dropped item. */
    public static void copyToItem(BlockEntity blockEntity, ItemStack stack) {
        CustomData data = blockEntity.components().get(DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, data);
        }
    }

    private static CustomData apply(CustomData data, Optional<Sticker> sticker) {
        return data.update(tag -> {
            CompoundTag bukkit = tag.getCompoundOrEmpty(BUKKIT_VALUES);
            for (String key : new String[] {KEY_VERSION, KEY_ICON_ID, KEY_ICON_VISUAL, KEY_ANCHOR, KEY_ROT}) {
                bukkit.remove(key);
            }
            sticker.ifPresent(s -> {
                bukkit.putInt(KEY_VERSION, FORMAT_VERSION);
                bukkit.putString(KEY_ICON_ID, s.iconId());
                if (!s.iconVisual().isEmpty()) {
                    bukkit.putString(KEY_ICON_VISUAL, s.iconVisual());
                }
                bukkit.putString(KEY_ANCHOR, s.anchor().serializedName());
                bukkit.putInt(KEY_ROT, s.rot());
            });
            if (bukkit.isEmpty()) {
                tag.remove(BUKKIT_VALUES);
            } else {
                tag.put(BUKKIT_VALUES, bukkit);
            }
        });
    }
}
