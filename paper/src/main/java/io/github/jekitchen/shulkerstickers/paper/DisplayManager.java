package io.github.jekitchen.shulkerstickers.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

/**
 * Draws a box's icon and name with real display entities that are never saved
 * (PRD §7.2). Every method that touches the world must run on the thread that owns
 * the box's region; callers schedule through {@link #refreshLater}.
 */
final class DisplayManager {

    static final int MAX_NAME_CHARS = 24;
    private static final float ICON_SCALE = 0.45f;
    private static final float TEXT_SCALE = 0.3f;
    /** View range is a multiplier of 64 blocks: 0.5 ≈ 32 blocks, 0.25 ≈ 16 blocks. */
    private static final float ICON_VIEW_RANGE = 0.5f;
    private static final float TEXT_VIEW_RANGE = 0.25f;

    private final Plugin plugin;
    private final Map<BlockKey, List<Entity>> displays = new ConcurrentHashMap<>();

    DisplayManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Schedules {@link #refresh} on the box's region thread. */
    void refreshLater(Block block) {
        plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> refresh(block));
    }

    void removeLater(Block block) {
        plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> remove(BlockKey.of(block)));
    }

    /** Region thread only. Re-creates the displays from the block's current data. */
    void refresh(Block block) {
        BlockKey key = BlockKey.of(block);
        remove(key);
        if (!Tag.SHULKER_BOXES.isTagged(block.getType()) || !(block.getState() instanceof ShulkerBox box)) {
            return;
        }
        Optional<StickerData> sticker = StickerStore.read(box.getPersistentDataContainer());
        Component name = box.customName();
        if (sticker.isEmpty() && name == null) {
            return;
        }
        BlockFace facing = ((Directional) block.getBlockData()).getFacing();
        String storedFace = box.getPersistentDataContainer().get(StickerKeys.FACE, PersistentDataType.STRING);
        BlockFace face = sticker
                .map(s -> Faces.iconFace(s.anchor(), facing, parseFace(storedFace)))
                .orElse(facing);
        int rotation = sticker.map(StickerData::rotation).orElse(0);

        List<Entity> spawned = new ArrayList<>(2);
        Location at = faceLocation(block, face, rotation);
        Optional<Material> iconMaterial = sticker
                .map(StickerData::iconId)
                .map(Material::matchMaterial)
                .filter(Material::isItem);
        iconMaterial.ifPresent(material -> spawned.add(block.getWorld().spawn(at, ItemDisplay.class, display -> {
            prepare(display);
            display.setItemStack(new ItemStack(material));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setViewRange(ICON_VIEW_RANGE);
            display.setTransformation(transform(name == null ? 0f : 0.08f, ICON_SCALE));
        })));
        if (name != null) {
            String text = shorten(PlainTextComponentSerializer.plainText().serialize(name));
            float offset = iconMaterial.isPresent() ? -0.3f : -0.05f;
            spawned.add(block.getWorld().spawn(at, TextDisplay.class, display -> {
                prepare(display);
                display.text(Component.text(text));
                display.setShadowed(true);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setViewRange(TEXT_VIEW_RANGE);
                display.setTransformation(transform(offset, TEXT_SCALE));
            }));
        }
        displays.put(key, spawned);
    }

    /** Region thread only. */
    void remove(BlockKey key) {
        List<Entity> old = displays.remove(key);
        if (old != null) {
            old.forEach(Entity::remove);
        }
    }

    /** Region thread only. Forgets and removes every display in a chunk. */
    void removeChunk(java.util.UUID world, int chunkX, int chunkZ) {
        displays.keySet().stream()
                .filter(k -> k.world().equals(world) && k.chunkX() == chunkX && k.chunkZ() == chunkZ)
                .toList()
                .forEach(this::remove);
    }

    int count() {
        return displays.values().stream().mapToInt(List::size).sum();
    }

    List<Entity> displaysAt(Block block) {
        return displays.getOrDefault(BlockKey.of(block), List.of());
    }

    private void prepare(Display display) {
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.FIXED);
        display.getPersistentDataContainer().set(StickerKeys.DISPLAY, PersistentDataType.BOOLEAN, true);
    }

    static String shorten(String name) {
        return name.length() <= MAX_NAME_CHARS ? name : name.substring(0, MAX_NAME_CHARS) + "…";
    }

    private static BlockFace parseFace(String name) {
        if (name == null) {
            return null;
        }
        try {
            return BlockFace.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * A point just outside the face (so the display takes its light from the open space in
     * front, PRD §5.5), rotated so the display's front points out of the face. Top and
     * bottom faces also turn by {@code rotation} quarter turns.
     */
    private static Location faceLocation(Block block, BlockFace face, int rotation) {
        Location at = block.getLocation().add(0.5, 0.5, 0.5)
                .add(face.getDirection().multiply(0.51));
        float yaw = switch (face) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> rotation * 90f;
        };
        float pitch = switch (face) {
            case UP -> -90f;
            case DOWN -> 90f;
            default -> 0f;
        };
        at.setYaw(yaw);
        at.setPitch(pitch);
        return at;
    }

    private static Transformation transform(float up, float scale) {
        return new Transformation(
                new Vector3f(0f, up, 0f),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f());
    }
}
