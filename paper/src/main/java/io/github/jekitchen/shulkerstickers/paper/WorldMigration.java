package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Worlds moved between platforms (owner decision 4). On Paper a placed box's sticker lives in its
 * persistent data; Fabric keeps it in the block entity's {@code custom_data} component, which the
 * Paper API can't see. Paper 26.x runs on Minecraft's own names, so this reads and edits that
 * component by reflection:
 *
 * <ul>
 *   <li>A box with our keys in {@code custom_data} and none in its persistent data (saved by
 *       Fabric) has them moved into its persistent data.
 *   <li>Our keys are then removed from {@code custom_data}. On Paper that copy is only what the
 *       box was placed from and goes stale after an edit, and leaving it would show stale stickers
 *       if the world later moves to Fabric.
 * </ul>
 *
 * If reflection fails, nothing changes and a warning is logged once.
 */
final class WorldMigration {

    private static volatile Bridge bridge;
    private static volatile boolean warned;

    private record Bridge(Method getBlockEntity, Method components, Method setComponents, Method get,
            Object customDataType, Method builder, Method addAll, Method set, Method build,
            Method copyTag, Method customDataOf, Method getCompoundOrEmpty, Method getString, Method getInt,
            Method remove, Method put, Method isEmpty) {}

    private WorldMigration() {}

    /** Region thread. Returns true if the box's persistent data was filled from Fabric's data. */
    static boolean migrate(Block block, Logger logger) {
        if (!(block.getState(false) instanceof ShulkerBox live)) {
            return false;
        }
        try {
            Bridge b = bridge();
            Object blockEntity = b.getBlockEntity().invoke(live);
            Object components = b.components().invoke(blockEntity);
            Object customData = b.get().invoke(components, b.customDataType());
            if (customData == null) {
                return false;
            }
            Object tag = b.copyTag().invoke(customData);
            Object bukkit = b.getCompoundOrEmpty().invoke(tag, StickerFormat.BUKKIT_VALUES);
            Map<String, Object> ours = new LinkedHashMap<>();
            for (String key : StickerFormat.BOX_KEYS) {
                String full = StickerFormat.fullKey(key);
                Optional<?> s = (Optional<?>) b.getString().invoke(bukkit, full);
                if (s.isPresent()) {
                    ours.put(key, s.get());
                } else {
                    ((Optional<?>) b.getInt().invoke(bukkit, full)).ifPresent(value -> ours.put(key, value));
                }
            }
            if (ours.isEmpty()) {
                return false;
            }

            boolean adopted = false;
            ShulkerBox snapshot = (ShulkerBox) block.getState();
            if (!StickerStore.hasData(snapshot.getPersistentDataContainer())) {
                write(snapshot.getPersistentDataContainer(), ours);
                snapshot.update(true, false);
                adopted = true;
            }

            // Strip our keys from custom_data on the live block entity (after any update above).
            Object liveEntity = b.getBlockEntity().invoke(block.getState(false));
            Object liveComponents = b.components().invoke(liveEntity);
            Object liveData = b.get().invoke(liveComponents, b.customDataType());
            if (liveData != null) {
                Object liveTag = b.copyTag().invoke(liveData);
                Object liveBukkit = b.getCompoundOrEmpty().invoke(liveTag, StickerFormat.BUKKIT_VALUES);
                for (String key : StickerFormat.BOX_KEYS) {
                    b.remove().invoke(liveBukkit, StickerFormat.fullKey(key));
                }
                if ((Boolean) b.isEmpty().invoke(liveBukkit)) {
                    b.remove().invoke(liveTag, StickerFormat.BUKKIT_VALUES);
                } else {
                    b.put().invoke(liveTag, StickerFormat.BUKKIT_VALUES, liveBukkit);
                }
                Object newData = (Boolean) b.isEmpty().invoke(liveTag) ? null : b.customDataOf().invoke(null, liveTag);
                Object builder = b.builder().invoke(null);
                b.addAll().invoke(builder, liveComponents);
                b.set().invoke(builder, b.customDataType(), newData);
                b.setComponents().invoke(liveEntity, b.build().invoke(builder));
            }
            return adopted;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                logger.warning("Can't read Fabric-saved sticker data on this server (" + e
                        + "); boxes placed in a Fabric world may look plain until broken and placed again.");
            }
            return false;
        }
    }

    /** Self-test only: stores {@code values} in the box's custom_data the way Fabric does. */
    static void putFabricStyle(Block block, Map<String, Object> values) throws ReflectiveOperationException {
        Bridge b = bridge();
        Class<?> compound = Class.forName("net.minecraft.nbt.CompoundTag");
        Object bukkit = compound.getConstructor().newInstance();
        for (var e : values.entrySet()) {
            String full = StickerFormat.fullKey(e.getKey());
            if (e.getValue() instanceof Integer i) {
                compound.getMethod("putInt", String.class, int.class).invoke(bukkit, full, i);
            } else {
                compound.getMethod("putString", String.class, String.class).invoke(bukkit, full, e.getValue().toString());
            }
        }
        Object tag = compound.getConstructor().newInstance();
        b.put().invoke(tag, StickerFormat.BUKKIT_VALUES, bukkit);
        Object blockEntity = b.getBlockEntity().invoke(block.getState(false));
        Object builder = b.builder().invoke(null);
        b.addAll().invoke(builder, b.components().invoke(blockEntity));
        b.set().invoke(builder, b.customDataType(), b.customDataOf().invoke(null, tag));
        b.setComponents().invoke(blockEntity, b.build().invoke(builder));
    }

    private static void write(PersistentDataContainer pdc, Map<String, Object> ours) {
        ours.forEach((key, value) -> {
            NamespacedKey namespaced = PaperKeys.sticker(key);
            if (value instanceof Integer i) {
                pdc.set(namespaced, PersistentDataType.INTEGER, i);
            } else {
                pdc.set(namespaced, PersistentDataType.STRING, value.toString());
            }
        });
    }

    private static Bridge bridge() throws ReflectiveOperationException {
        Bridge b = bridge;
        if (b == null) {
            Class<?> state = Class.forName("org.bukkit.craftbukkit.block.CraftBlockEntityState");
            Class<?> blockEntity = Class.forName("net.minecraft.world.level.block.entity.BlockEntity");
            Class<?> map = Class.forName("net.minecraft.core.component.DataComponentMap");
            Class<?> getter = Class.forName("net.minecraft.core.component.DataComponentGetter");
            Class<?> builder = Class.forName("net.minecraft.core.component.DataComponentMap$Builder");
            Class<?> type = Class.forName("net.minecraft.core.component.DataComponentType");
            Class<?> components = Class.forName("net.minecraft.core.component.DataComponents");
            Class<?> customData = Class.forName("net.minecraft.world.item.component.CustomData");
            Class<?> compound = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> tag = Class.forName("net.minecraft.nbt.Tag");
            Field customDataField = components.getField("CUSTOM_DATA");
            b = new Bridge(
                    state.getMethod("getBlockEntity"),
                    blockEntity.getMethod("components"),
                    blockEntity.getMethod("setComponents", map),
                    getter.getMethod("get", type),
                    customDataField.get(null),
                    map.getMethod("builder"),
                    builder.getMethod("addAll", map),
                    builder.getMethod("set", type, Object.class),
                    builder.getMethod("build"),
                    customData.getMethod("copyTag"),
                    customData.getMethod("of", compound),
                    compound.getMethod("getCompoundOrEmpty", String.class),
                    compound.getMethod("getString", String.class),
                    compound.getMethod("getInt", String.class),
                    compound.getMethod("remove", String.class),
                    compound.getMethod("put", String.class, tag),
                    compound.getMethod("isEmpty"));
            bridge = b;
        }
        return b;
    }
}
