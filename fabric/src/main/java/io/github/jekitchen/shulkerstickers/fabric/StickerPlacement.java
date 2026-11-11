package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.Faces;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs after a shulker box item is placed as a block (by a player or a dispenser): puts a side
 * icon on the right side (PRD §6.1), draws the sticker, and shows first-placement help (§5.6).
 */
public final class StickerPlacement {

    private StickerPlacement() {
    }

    public static void onPlaced(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)) {
            return;
        }
        Optional<Sticker> sticker = StickerData.read(box);
        if (sticker.isPresent() && sticker.get().anchor() == Anchor.SIDE) {
            Face facing = Boxes.face(box.getBlockState().getValue(ShulkerBoxBlock.FACING));
            Face side = player != null ? sideTowardPlayer(pos, facing, player) : sideTowardDispenser(level, pos, facing);
            Sticker s = sticker.get();
            if (!s.face().equals(Optional.of(side))) {
                StickerData.write(box, Optional.of(new Sticker(s.iconId(), s.iconVisual(), s.anchor(), s.rot(), Optional.of(side))));
            }
        }
        StickerDisplays.refresh(level, pos);
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerPrefs.onBoxPlaced(serverPlayer);
        }
    }

    private static Face sideTowardPlayer(BlockPos pos, Face facing, Player player) {
        Vec3 toPlayer = player.getEyePosition().subtract(Vec3.atCenterOf(pos));
        return Faces.sideToward(facing, toPlayer.x, toPlayer.y, toPlayer.z);
    }

    /** The side facing back toward the dispenser that placed the box, else north, else the first side. */
    static Face sideTowardDispenser(ServerLevel level, BlockPos pos, Face facing) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.getBlock() instanceof DispenserBlock
                    && neighbor.getValue(DispenserBlock.FACING) == direction.getOpposite()) {
                return Faces.dispenserSide(facing, Boxes.face(direction));
            }
        }
        return Faces.dispenserSide(facing, facing);
    }
}
