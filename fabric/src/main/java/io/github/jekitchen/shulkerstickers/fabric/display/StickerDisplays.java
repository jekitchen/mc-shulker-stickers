package io.github.jekitchen.shulkerstickers.fabric.display;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import io.github.jekitchen.shulkerstickers.fabric.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.editor.DisplayFaces;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws icons and names on placed boxes with Polymer virtual entities: packets only, nothing is
 * saved to the world (PRD §7.2). One holder per box, re-created when its block entity loads.
 *
 * <p>Spike limits: displays are not hidden while the box is open, the icon is drawn from the item
 * ID only (no visual properties), and the view ranges are fixed.
 */
public final class StickerDisplays {

    /** Just outside the face, so the display takes light from the open space in front of it. */
    private static final double FACE_OFFSET = 0.51;
    private static final float ICON_SCALE = 0.5f;
    private static final float NAME_SCALE = 0.3f;
    private static final int NAME_MAX_CHARS = 24;
    /** Vanilla view_range is a multiplier of 64 blocks: 0.5 = 32 blocks, 0.25 = 16 blocks. */
    private static final float ICON_VIEW_RANGE = 0.5f;
    private static final float NAME_VIEW_RANGE = 0.25f;

    private static final Map<ServerLevel, Map<BlockPos, ElementHolder>> HOLDERS = new WeakHashMap<>();

    private StickerDisplays() {
    }

    public static void register() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof ShulkerBoxBlockEntity box) {
                show(level, box);
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof ShulkerBoxBlockEntity) {
                remove(level, blockEntity.getBlockPos());
            }
        });
    }

    public static void refresh(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box) {
            show(level, box);
        } else {
            remove(level, pos);
        }
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Map<BlockPos, ElementHolder> holders = HOLDERS.get(level);
        ElementHolder holder = holders == null ? null : holders.remove(pos);
        if (holder != null) {
            holder.destroy();
        }
    }

    /** Number of boxes currently drawn in a level; for tests and /shulkerstickers inspect. */
    public static int count(ServerLevel level) {
        return HOLDERS.getOrDefault(level, Map.of()).size();
    }

    private static void show(ServerLevel level, ShulkerBoxBlockEntity box) {
        BlockPos pos = box.getBlockPos().immutable();
        remove(level, pos);

        Optional<Sticker> sticker = StickerData.read(box);
        Optional<String> name = Optional.ofNullable(box.getCustomName()).map(Component::getString);
        if (sticker.isEmpty()) {
            // MVP: a name is only drawn on the face that carries the icon. A name-only box keeps the
            // vanilla behavior (name shown in the GUI title) until the PRD decides which face to use.
            return;
        }

        Direction facing = box.getBlockState().getValue(ShulkerBoxBlock.FACING);
        Direction face = DisplayFaces.face(facing, sticker.get());
        int rot = face.getAxis() == Direction.Axis.Y ? sticker.get().rot() : 0;
        Quaternionf rotation = faceRotation(face, rot);

        ElementHolder holder = new ElementHolder();
        ItemDisplayElement icon = new ItemDisplayElement(iconStack(sticker.get().iconId()));
        icon.setItemDisplayContext(ItemDisplayContext.FIXED);
        icon.setLeftRotation(rotation);
        icon.setScale(new Vector3f(ICON_SCALE));
        icon.setViewRange(ICON_VIEW_RANGE);
        holder.addElement(icon);

        name.ifPresent(text -> {
            TextDisplayElement label = new TextDisplayElement(Component.literal(shorten(text)));
            label.setBillboardMode(Display.BillboardConstraints.FIXED);
            label.setLeftRotation(rotation);
            label.setScale(new Vector3f(NAME_SCALE));
            // Under the icon, measured in the face's own "down" direction.
            label.setTranslation(new Vector3f(0, -0.42f, 0).rotate(rotation));
            label.setShadow(true);
            label.setBackground(0);
            label.setViewRange(NAME_VIEW_RANGE);
            holder.addElement(label);
        });

        Vec3 at = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(FACE_OFFSET));
        ChunkAttachment.of(holder, level, at);
        HOLDERS.computeIfAbsent(level, l -> new HashMap<>()).put(pos, holder);
    }

    private static ItemStack iconStack(String iconId) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(iconId)).orElse(Items.AIR);
        return new ItemStack(item);
    }

    private static String shorten(String name) {
        return name.length() <= NAME_MAX_CHARS ? name : name.substring(0, NAME_MAX_CHARS - 1) + "…";
    }

    /**
     * Rotation that turns a display's front (+Z) to face out of {@code face}. On the top and bottom
     * faces, {@code rot} turns the icon in quarter turns to match where the player was facing.
     */
    static Quaternionf faceRotation(Direction face, int rot) {
        float quarter = (float) (Math.PI / 2);
        Quaternionf q = switch (face) {
            case SOUTH -> new Quaternionf();
            case NORTH -> new Quaternionf().rotationY(2 * quarter);
            case EAST -> new Quaternionf().rotationY(quarter);
            case WEST -> new Quaternionf().rotationY(-quarter);
            case UP -> new Quaternionf().rotationX(-quarter);
            case DOWN -> new Quaternionf().rotationX(quarter);
        };
        return q.rotateZ(rot * quarter);
    }
}
