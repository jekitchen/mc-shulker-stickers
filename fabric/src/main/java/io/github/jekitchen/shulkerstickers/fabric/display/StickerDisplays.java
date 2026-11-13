package io.github.jekitchen.shulkerstickers.fabric.display;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import io.github.jekitchen.shulkerstickers.core.display.LidMotion;
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
    /** Boxes drawn on their lid face: their displays move with the lid (PRD §5.5, v0.2). */
    private static final Map<ServerLevel, Map<BlockPos, Lid>> LIDS = new WeakHashMap<>();

    /** A display's pose with the box closed. */
    private record Rest(DisplayElement element, Vector3f translation, Quaternionf rotation) {}

    /** The displays on a box's lid face, and the lid progress they were last drawn at. */
    private static final class Lid {
        final Vector3f facing;
        final Vector3f standOffset;
        final List<Rest> rests;
        float shown;

        Lid(Vector3f facing, Vector3f standOffset, List<Rest> rests) {
            this.facing = facing;
            this.standOffset = standOffset;
            this.rests = rests;
        }

        /** Moves every display to the lid's pose at {@code progress}, over one tick if {@code smooth}. */
        void moveTo(float progress, boolean smooth) {
            for (Rest rest : rests) {
                LidMotion.Pose pose = LidMotion.at(progress, facing, standOffset, rest.translation(), rest.rotation());
                rest.element().setTranslation(pose.translation());
                rest.element().setLeftRotation(pose.leftRotation());
                rest.element().setInterpolationDuration(smooth ? 1 : 0);
                rest.element().startInterpolation();
            }
            shown = progress;
        }
    }

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
        Map<BlockPos, Lid> lids = LIDS.get(level);
        if (lids != null) {
            lids.remove(pos);
        }
    }

    /** Whether the box's displays are on its lid face (and so move with the lid); for tests. */
    public static boolean isOnLid(ServerLevel level, BlockPos pos) {
        return LIDS.getOrDefault(level, Map.of()).containsKey(pos);
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

    /**
     * Per-tick hook, after vanilla has moved the lids this tick: draws boxes whose chunk has
     * finished loading, moves lid-face displays with the lid, hides side displays of open boxes
     * and shows them again on close. Public for the perf test.
     */
    public static void updateOpenBoxes(ServerLevel level) {
        drawPending(level);
        boolean hideWhileOpen = Settings.config().hideWhileOpen();
        if (!hideWhileOpen && !HIDDEN.getOrDefault(level, Set.of()).isEmpty()) {
            new ArrayList<>(HIDDEN.get(level)).forEach(pos -> refresh(level, pos));
        }
        // One pass, one lookup per drawn box: lid-face displays follow the lid, side ones of an
        // open box are hidden (collected first, since hiding changes the map being walked).
        Map<BlockPos, ElementHolder> holders = HOLDERS.get(level);
        Map<BlockPos, Lid> lids = LIDS.getOrDefault(level, Map.of());
        List<BlockPos> toHide = null;
        if (holders != null && !holders.isEmpty()) {
            for (BlockPos pos : holders.keySet()) {
                Lid lid = lids.get(pos);
                if (lid == null && !hideWhileOpen) {
                    continue;
                }
                if (!(loadedBox(level, pos) instanceof ShulkerBoxBlockEntity box)) {
                    continue;
                }
                if (lid != null) {
                    // The pose set now reaches players with Polymer's update next tick, a tick after
                    // the lid's own block event, so aim one step ahead while the lid is moving.
                    float progress = switch (box.getAnimationStatus()) {
                        case OPENING -> Math.min(1f, box.getProgress(1.0f) + LidMotion.STEP);
                        case CLOSING -> Math.max(0f, box.getProgress(1.0f) - LidMotion.STEP);
                        default -> box.getProgress(1.0f);
                    };
                    if (progress != lid.shown) {
                        lid.moveTo(progress, true);
                    }
                } else if (isOpen(box)) {
                    if (toHide == null) {
                        toHide = new ArrayList<>();
                    }
                    toHide.add(pos);
                }
            }
        }
        if (toHide != null) {
            for (BlockPos pos : toHide) {
                remove(level, pos);
                HIDDEN.computeIfAbsent(level, l -> new HashSet<>()).add(pos);
            }
        }
        if (!hideWhileOpen) {
            return;
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
        // The chunk's own map: unlike getBlockEntity, it never creates a pending block entity (which
        // would fire the load event while the per-tick pass walks the drawn boxes).
        return chunk == null ? null : chunk.getBlockEntities().get(pos);
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
        Optional<Sticker> sticker = StickerData.read(box);
        Optional<String> name = Optional.ofNullable(box.getCustomName()).map(io.github.jekitchen.shulkerstickers.fabric.SafeNames::plain)
                .filter(n -> config.nameDisplayEnabled() && !StickerData.isNameHidden(box));
        Optional<ItemStack> icon = iconToDraw(sticker, config, level.registryAccess());

        Direction facing = box.getBlockState().getValue(ShulkerBoxBlock.FACING);
        // A name with no stored anchor (e.g. renamed at an anvil) goes on the lid.
        Anchor anchor = sticker.map(Sticker::anchor).orElse(Anchor.LID);
        Direction face = Boxes.direction(Faces.iconFace(anchor, Boxes.face(facing), sticker.flatMap(Sticker::face)));
        // Displays on the lid face move with the lid; the others are hidden while the box is open.
        boolean onLid = face == facing;
        if (!onLid && config.hideWhileOpen() && isOpen(box)) {
            HIDDEN.computeIfAbsent(level, l -> new HashSet<>()).add(pos);
            return;
        }
        HIDDEN.getOrDefault(level, new HashSet<>()).remove(pos);
        if (icon.isEmpty() && name.isEmpty()) {
            return;
        }
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
        // Lid displays tick with the chunk: that tick is what sends a moved pose to players (at the
        // end of the chunk's tick, so a pose set at the end of tick N goes out in tick N+1).
        // Displays that never move don't tick, so a wall of side-icon boxes costs nothing per tick.
        Vec3 at = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(DisplayLayout.FACE_OFFSET));
        new ChunkAttachment(holder, chunk, at, onLid);
        HOLDERS.computeIfAbsent(level, l -> new HashMap<>()).put(pos, holder);

        if (onLid) {
            List<Rest> rests = new ArrayList<>();
            for (var element : holder.getElements()) {
                if (element instanceof DisplayElement display) {
                    rests.add(new Rest(display, new Vector3f(display.getTranslation()), new Quaternionf(display.getLeftRotation())));
                }
            }
            Vector3f facingVec = new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            Lid lid = new Lid(facingVec, new Vector3f(facingVec).mul((float) DisplayLayout.FACE_OFFSET), rests);
            float progress = box.getProgress(1.0f);
            if (progress > 0) {
                lid.moveTo(progress, false); // drawn while the box is (partly) open
            }
            LIDS.computeIfAbsent(level, l -> new HashMap<>()).put(pos, lid);
        }
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
