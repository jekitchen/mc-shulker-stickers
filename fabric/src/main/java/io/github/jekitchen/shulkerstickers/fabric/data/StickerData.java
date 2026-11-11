package io.github.jekitchen.shulkerstickers.fabric.data;

import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.data.StickerCodec;
import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes sticker data in {@code minecraft:custom_data → PublicBukkitValues}, the same
 * layout Paper uses for plugin data (PRD §6.1). The format itself lives in core
 * ({@link StickerFormat}, {@link StickerCodec}); this class only moves it in and out of NBT.
 * Keys that aren't ours are always left alone.
 */
public final class StickerData {

    /**
     * Set to 1 when the name was saved by a player without {@code shulker_stickers.name.display},
     * so the name is kept but not drawn on the box. Managed separately from the sticker itself.
     */
    public static final String NAME_HIDDEN = StickerFormat.NAME_HIDDEN;

    private StickerData() {
    }

    public static Optional<Sticker> read(BlockEntity blockEntity) {
        return StickerCodec.decode(rawValues(blockEntity));
    }

    public static Optional<Sticker> read(ItemStack stack) {
        return StickerCodec.decode(rawValues(bukkitValues(stack.get(DataComponents.CUSTOM_DATA))));
    }

    /** Our keys (short names) and their values, as found; for decoding and {@code inspect}. */
    public static Map<String, Object> rawValues(BlockEntity blockEntity) {
        return rawValues(bukkitValues(blockEntity.components().get(DataComponents.CUSTOM_DATA)));
    }

    public static boolean isNameHidden(BlockEntity blockEntity) {
        return bukkitValues(blockEntity.components().get(DataComponents.CUSTOM_DATA))
                .getIntOr(StickerFormat.fullKey(NAME_HIDDEN), 0) != 0;
    }

    /** Sets or (with an empty sticker) removes the sticker on a block entity. */
    public static void write(BlockEntity blockEntity, Optional<Sticker> sticker) {
        update(blockEntity, bukkit -> {
            for (String key : StickerFormat.KEYS) {
                if (!key.equals(NAME_HIDDEN)) {
                    bukkit.remove(StickerFormat.fullKey(key));
                }
            }
            sticker.ifPresent(s -> StickerCodec.encode(s).forEach((key, value) -> {
                String full = StickerFormat.fullKey(key);
                if (value instanceof Integer i) {
                    bukkit.putInt(full, i);
                } else {
                    bukkit.putString(full, value.toString());
                }
            }));
        });
    }

    public static void setNameHidden(BlockEntity blockEntity, boolean hidden) {
        update(blockEntity, bukkit -> {
            if (hidden) {
                bukkit.putInt(StickerFormat.fullKey(NAME_HIDDEN), 1);
            } else {
                bukkit.remove(StickerFormat.fullKey(NAME_HIDDEN));
            }
        });
    }

    /** Copies the whole {@code custom_data} component of a block entity onto a dropped item. */
    public static void copyToItem(BlockEntity blockEntity, ItemStack stack) {
        CustomData data = blockEntity.components().get(DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, data);
        }
    }

    private static void update(BlockEntity blockEntity, Consumer<CompoundTag> edit) {
        CustomData current = blockEntity.components().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CustomData updated = current.update(tag -> {
            CompoundTag bukkit = tag.getCompoundOrEmpty(StickerFormat.BUKKIT_VALUES);
            edit.accept(bukkit);
            if (bukkit.isEmpty()) {
                tag.remove(StickerFormat.BUKKIT_VALUES);
            } else {
                tag.put(StickerFormat.BUKKIT_VALUES, bukkit);
            }
        });
        blockEntity.setComponents(DataComponentMap.builder()
                .addAll(blockEntity.components())
                .set(DataComponents.CUSTOM_DATA, updated.isEmpty() ? null : updated)
                .build());
        blockEntity.setChanged();
    }

    private static CompoundTag bukkitValues(@Nullable CustomData data) {
        return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty(StickerFormat.BUKKIT_VALUES);
    }

    private static Map<String, Object> rawValues(CompoundTag bukkit) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : StickerFormat.KEYS) {
            Tag tag = bukkit.get(StickerFormat.fullKey(key));
            if (tag instanceof StringTag(String s)) {
                values.put(key, s);
            } else if (tag instanceof NumericTag n) {
                values.put(key, n.box());
            } else if (tag != null) {
                values.put(key, tag.toString());
            }
        }
        return values;
    }
}
