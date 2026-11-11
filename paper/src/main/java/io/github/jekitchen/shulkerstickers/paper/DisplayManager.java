package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.rules.NameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
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
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Draws a box's icon and name with real display entities that are never saved
 * (PRD §7.2). Every method that touches the world must run on the thread that owns
 * the box's region; callers schedule through {@link #refreshLater}.
 */
final class DisplayManager {

    private static final float ICON_SCALE = 0.45f;
    private static final float TEXT_SCALE = 0.3f;

    private final Plugin plugin;
    private final Settings settings;
    private final Map<BlockKey, List<Entity>> displays = new ConcurrentHashMap<>();

    DisplayManager(Plugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** Schedules {@link #refresh} on the box's region thread. */
    void refreshLater(Block block) {
        plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> refresh(block));
    }

    void refreshLater(BlockKey key, long delayTicks) {
        Location location = key.location();
        if (location == null) {
            return;
        }
        plugin.getServer().getRegionScheduler().runDelayed(plugin, location, t -> refresh(location.getBlock()),
                Math.max(1, delayTicks));
    }

    void removeLater(Block block) {
        plugin.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> remove(BlockKey.of(block)));
    }

    /** Re-creates every display this manager knows about (after a config reload). */
    void refreshAllLater() {
        displays.keySet().forEach(key -> refreshLater(key, 1));
    }

    /** Region thread only. Re-creates the displays from the block's current data. */
    void refresh(Block block) {
        BlockKey key = BlockKey.of(block);
        remove(key);
        if (!Tag.SHULKER_BOXES.isTagged(block.getType()) || !(block.getState(false) instanceof ShulkerBox box)) {
            return;
        }
        StickerConfig config = settings.config();
        if (config.hideWhileOpen() && !box.getInventory().getViewers().isEmpty()) {
            return; // shown again when the last viewer closes the box
        }
        Optional<Sticker> sticker = StickerStore.read(box.getPersistentDataContainer());
        Optional<ItemStack> icon = config.iconEnabled()
                ? sticker.flatMap(s -> IconItems.render(s, config))
                : Optional.empty();
        Optional<String> name = Optional.ofNullable(box.customName())
                .filter(n -> config.nameDisplayEnabled())
                .filter(n -> !StickerStore.isNameHidden(box.getPersistentDataContainer()))
                .map(n -> PlainTextComponentSerializer.plainText().serialize(n))
                .filter(n -> !n.isBlank());
        if (icon.isEmpty() && name.isEmpty()) {
            return;
        }
        BlockFace facing = ((Directional) block.getBlockData()).getFacing();
        // A name without a stored anchor (e.g. from a real anvil) goes on the lid.
        Anchor anchor = sticker.map(Sticker::anchor).orElse(Anchor.LID);
        BlockFace face = Faces.iconFace(anchor, facing, sticker.flatMap(Sticker::face));
        int rotation = sticker.map(Sticker::rot).orElse(0);

        List<Entity> spawned = new ArrayList<>(2);
        Location at = faceLocation(block, face, rotation);
        icon.ifPresent(stack -> spawned.add(block.getWorld().spawn(at, ItemDisplay.class, display -> {
            prepare(display, config);
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setViewRange(viewRange(config.iconViewRange()));
            display.setTransformation(transform(name.isEmpty() ? 0f : 0.08f, ICON_SCALE));
        })));
        name.ifPresent(n -> {
            String text = NameRules.displayText(n, config.nameMaxDisplayChars());
            float offset = icon.isPresent() ? -0.3f : -0.05f;
            spawned.add(block.getWorld().spawn(at, TextDisplay.class, display -> {
                prepare(display, config);
                display.text(Component.text(text));
                display.setShadowed(true);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setViewRange(viewRange(config.nameViewRange()));
                display.setTransformation(transform(offset, TEXT_SCALE));
            }));
        });
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
    void removeChunk(UUID world, int chunkX, int chunkZ) {
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

    private void prepare(Display display, StickerConfig config) {
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.FIXED);
        if (config.iconFullBright()) {
            display.setBrightness(new Display.Brightness(15, 15));
        }
        display.getPersistentDataContainer().set(PaperKeys.DISPLAY, PersistentDataType.BOOLEAN, true);
    }

    /** Display view range is a multiplier of 64 blocks. */
    static float viewRange(int blocks) {
        return blocks / 64f;
    }

    /**
     * A point just outside the face (so the display takes its light from the open space in
     * front, PRD §5.5), rotated so the display's front points out of the face. Top and
     * bottom faces also turn by {@code rotation} quarter turns.
     */
    private static Location faceLocation(Block block, BlockFace face, int rotation) {
        Location at = block.getLocation().add(0.5, 0.5, 0.5).add(face.getDirection().multiply(0.51));
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
        return new Transformation(new Vector3f(0f, up, 0f), new AxisAngle4f(),
                new Vector3f(scale, scale, scale), new AxisAngle4f());
    }
}
