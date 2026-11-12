package io.github.jekitchen.shulkerstickers.fabric.display;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Faces;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.rules.NameRules;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.Settings;
import io.github.jekitchen.shulkerstickers.fabric.data.Icons;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import io.github.jekitchen.shulkerstickers.core.display.DisplayLayout;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws icons and names on placed boxes with Polymer virtual entities: packets only, nothing is
 * saved to the world (PRD §7.2). One holder per box, re-created when its block entity loads.
 * While a box is opening or open, its displays are removed and come back when it closes (§5.5).
 */
public final class StickerDisplays {

    /** Just outside the face, so the display takes light from the open space in front of it. */
    /** Vanilla display view_range is a multiplier of 64 blocks. */
    private static final float VIEW_RANGE_UNIT = 64f;

    private static final Map<ServerLevel, Map<BlockPos, ElementHolder>> HOLDERS = new WeakHashMap<>();
    /** Boxes whose displays are hidden because the box is open. */
    private static final Map<ServerLevel, Set<BlockPos>> HIDDEN = new WeakHashMap<>();
    /** Every loaded shulker box, drawn or not, so a reload can redraw all of them. */
    private static final Map<ServerLevel, Set<ShulkerBoxBlockEntity>> LOADED = new WeakHashMap<>();
    /**
     * Boxes to draw at the end of the tick because their chunk was still loading. A box loaded
     * from disk fires its load event while its chunk is being finished; asking the level for that
     * chunk then would wait for itself forever and hang the server thread.
     */
    private static final Map<ServerLevel, Set<ShulkerBoxBlockEntity>> PENDING = new WeakHashMap<>();

    private StickerDisplays() {
    }

    public static void register() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof ShulkerBoxBlockEntity box) {
                LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(box);
                show(level, box);
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof ShulkerBoxBlockEntity box) {
                LOADED.getOrDefault(level, Set.of()).remove(box);
                Set<ShulkerBoxBlockEntity> pending = PENDING.get(level);
                if (pending != null) {
                    pending.remove(box);
                }
                remove(level, blockEntity.getBlockPos());
                HIDDEN.getOrDefault(level, new HashSet<>()).remove(blockEntity.getBlockPos());
            }
        });
        ServerTickEvents.END_LEVEL_TICK.register(StickerDisplays::updateOpenBoxes);
    }

    public static void refresh(ServerLevel level, BlockPos pos) {
        if (loadedBox(level, pos) instanceof ShulkerBoxBlockEntity box) {
            show(level, box);
        } else {
            remove(level, pos);
            HIDDEN.getOrDefault(level, new HashSet<>()).remove(pos);
        }
    }

    /** Redraws every loaded box in a level, drawn or not, e.g. after a config reload. */
    public static void refreshAll(ServerLevel level) {
        Set<BlockPos> positions = new HashSet<>(HOLDERS.getOrDefault(level, Map.of()).keySet());
        positions.addAll(HIDDEN.getOrDefault(level, Set.of()));
        for (ShulkerBoxBlockEntity box : List.copyOf(LOADED.getOrDefault(level, Set.of()))) {
            if (!box.isRemoved()) {
                positions.add(box.getBlockPos().immutable());
            }
        }
        positions.forEach(pos -> refresh(level, pos));
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Map<BlockPos, ElementHolder> holders = HOLDERS.get(level);
        ElementHolder holder = holders == null ? null : holders.remove(pos);
        if (holder != null) {
            holder.destroy();
        }
    }

    /** Number of boxes currently drawn in a level; for tests. */
    public static int count(ServerLevel level) {
        return HOLDERS.getOrDefault(level, Map.of()).size();
    }

    public static boolean isShown(ServerLevel level, BlockPos pos) {
        return HOLDERS.getOrDefault(level, Map.of()).containsKey(pos);
    }

    public static boolean isHidden(ServerLevel level, BlockPos pos) {
        return HIDDEN.getOrDefault(level, Set.of()).contains(pos);
    }

    /** The holder's elements, for tests. */
    public static Optional<ElementHolder> holder(ServerLevel level, BlockPos pos) {
        return Optional.ofNullable(HOLDERS.getOrDefault(level, Map.of()).get(pos));
    }

    private static boolean isOpen(ShulkerBoxBlockEntity box) {
        return box.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
    }

    /** Per-tick hook: draws boxes whose chunk has finished loading, hides displays of open boxes and shows them again on close. Public for the perf test. */
    public static void updateOpenBoxes(ServerLevel level) {
        drawPending(level);
        if (!Settings.config().hideWhileOpen()) {
            if (!HIDDEN.getOrDefault(level, Set.of()).isEmpty()) {
                new ArrayList<>(HIDDEN.get(level)).forEach(pos -> refresh(level, pos));
            }
            return;
        }
        Map<BlockPos, ElementHolder> holders = HOLDERS.get(level);
        if (holders != null && !holders.isEmpty()) {
            for (BlockPos pos : new ArrayList<>(holders.keySet())) {
                if (loadedBox(level, pos) instanceof ShulkerBoxBlockEntity box && isOpen(box)) {
                    remove(level, pos);
                    HIDDEN.computeIfAbsent(level, l -> new HashSet<>()).add(pos);
                }
            }
        }
        Set<BlockPos> hidden = HIDDEN.get(level);
        if (hidden != null && !hidden.isEmpty()) {
            for (BlockPos pos : new ArrayList<>(hidden)) {
                if (!(loadedBox(level, pos) instanceof ShulkerBoxBlockEntity box)) {
                    hidden.remove(pos);
                } else if (!isOpen(box)) {
                    hidden.remove(pos);
                    show(level, box);
                }
            }
        }
    }

    /** Draws {@code box} at the end of the tick, once its chunk is fully loaded. Public for tests. */
    public static void drawLater(ServerLevel level, ShulkerBoxBlockEntity box) {
        PENDING.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(box);
    }

    /** Draws the boxes whose chunk was still loading when they loaded; ones still not ready wait another tick. */
    private static void drawPending(ServerLevel level) {
        Set<ShulkerBoxBlockEntity> pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        List<ShulkerBoxBlockEntity> due = List.copyOf(pending);
        pending.clear();
        for (ShulkerBoxBlockEntity box : due) {
            LevelChunk chunk = loadedChunk(level, box.getBlockPos());
            if (box.isRemoved()) {
                continue;
            }
            if (chunk == null) {
                pending.add(box);
            } else if (chunk.getBlockEntity(box.getBlockPos()) == box) {
                show(level, box);
            }
        }
    }

    /**
     * The block entity at {@code pos} if its chunk is fully loaded. Unlike
     * {@code Level#getBlockEntity}, this never loads the chunk or adds a ticket to it: on the
     * server thread that call goes through {@code getChunk(..., true)}, which adds a one-tick
     * ticket on a cache miss. (In a test the chunk still unloaded, but there is no reason for a
     * per-tick pass over drawn boxes to touch chunk tickets at all.)
     */
    private static net.minecraft.world.level.block.entity.@Nullable BlockEntity loadedBox(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    /** The chunk at {@code pos} if it is fully loaded; never loads it or waits for it. */
    private static @Nullable LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static void show(ServerLevel level, ShulkerBoxBlockEntity box) {
        BlockPos pos = box.getBlockPos().immutable();
        remove(level, pos);
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) {
            // The box's chunk is still loading (we're inside its load): draw it at the end of the tick.
            drawLater(level, box);
            return;
        }
        StickerConfig config = Settings.config();
        if (config.hideWhileOpen() && isOpen(box)) {
            HIDDEN.computeIfAbsent(level, l -> new HashSet<>()).add(pos);
            return;
        }
        HIDDEN.getOrDefault(level, new HashSet<>()).remove(pos);

        Optional<Sticker> sticker = StickerData.read(box);
        Optional<String> name = Optional.ofNullable(box.getCustomName()).map(io.github.jekitchen.shulkerstickers.fabric.SafeNames::plain)
                .filter(n -> config.nameDisplayEnabled() && !StickerData.isNameHidden(box));
        Optional<ItemStack> icon = iconToDraw(sticker, config, level.registryAccess());
        if (icon.isEmpty() && name.isEmpty()) {
            return;
        }

        Direction facing = box.getBlockState().getValue(ShulkerBoxBlock.FACING);
        // A name with no stored anchor (e.g. renamed at an anvil) goes on the lid.
        Anchor anchor = sticker.map(Sticker::anchor).orElse(Anchor.LID);
        Direction face = Boxes.direction(Faces.iconFace(anchor, Boxes.face(facing), sticker.flatMap(Sticker::face)));
        int rot = face.getAxis() == Direction.Axis.Y ? sticker.map(Sticker::rot).orElse(0) : 0;
        Quaternionf rotation = faceRotation(face, rot);

        ElementHolder holder = new ElementHolder();
        icon.ifPresent(stack -> {
            ItemDisplayElement element = new ItemDisplayElement(stack);
            element.setItemDisplayContext(ItemDisplayContext.FIXED);
            element.setLeftRotation(rotation);
            element.setScale(new Vector3f(DisplayLayout.ICON_SCALE));
            element.setTranslation(new Vector3f(0, DisplayLayout.iconOffset(name.isPresent()), 0).rotate(rotation));
            element.setViewRange(config.iconViewRange() / VIEW_RANGE_UNIT);
            if (config.iconFullBright()) {
                element.setBrightness(Brightness.FULL_BRIGHT);
            }
            holder.addElement(element);
        });

        name.ifPresent(text -> {
            TextDisplayElement label = new TextDisplayElement(Component.literal(NameRules.displayText(text, config.nameMaxDisplayChars())));
            label.setBillboardMode(Display.BillboardConstraints.FIXED);
            label.setLeftRotation(rotation);
            label.setScale(new Vector3f(DisplayLayout.NAME_SCALE));
            // Under the icon, measured in the face's own "down" direction; centered with no icon.
            label.setTranslation(new Vector3f(0, DisplayLayout.nameOffset(icon.isPresent()), 0).rotate(rotation));
            label.setShadow(true);
            label.setBackground(0);
            label.setViewRange(config.nameViewRange() / VIEW_RANGE_UNIT);
            holder.addElement(label);
        });

        // The display sits just outside the face (for light) but belongs to the box's own chunk,
        // so a box on a chunk edge never loads or depends on the neighbouring chunk.
        Vec3 at = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(DisplayLayout.FACE_OFFSET));
        new ChunkAttachment(holder, chunk, at, false);
        HOLDERS.computeIfAbsent(level, l -> new HashMap<>()).put(pos, holder);
    }

    /** The icon to draw, if any. {@code icon.enabled = false} hides icons already on boxes too; the data is kept (PRD §8). */
    public static Optional<ItemStack> iconToDraw(Optional<Sticker> sticker, StickerConfig config, RegistryAccess registries) {
        if (!config.iconEnabled()) {
            return Optional.empty();
        }
        return sticker.flatMap(s -> Icons.displayStack(s, config, registries));
    }

    /**
     * Rotation that turns a display's front (+Z) to face out of {@code face}, with the image's top
     * (+Y) pointing: world up on the four sides; on the top and bottom faces, the direction the
     * player was facing ({@code rot} = that direction's 2D data value: south 0, west 1, north 2,
     * east 3), so the icon reads upright like an item in a floor item frame. Shared with Paper.
     */
    public static Quaternionf faceRotation(Direction face, int rot) {
        float quarter = (float) (Math.PI / 2);
        Quaternionf q = switch (face) {
            case SOUTH -> new Quaternionf();
            case NORTH -> new Quaternionf().rotationY(2 * quarter);
            case EAST -> new Quaternionf().rotationY(quarter);
            case WEST -> new Quaternionf().rotationY(-quarter);
            // Tip the front up (+Z -> +Y, top -> north), then turn about Y so the top points along rot.
            case UP -> new Quaternionf().rotationY(2 * quarter - rot * quarter).rotateX(-quarter);
            // Tip the front down (+Z -> -Y, top -> south), then turn about Y the same way.
            case DOWN -> new Quaternionf().rotationY(-rot * quarter).rotateX(quarter);
        };
        return q;
    }
}
