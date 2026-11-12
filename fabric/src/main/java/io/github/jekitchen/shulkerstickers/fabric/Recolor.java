package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/** Swaps a placed box to another color, keeping everything else (PRD §6.3). */
public final class Recolor {

    public enum Result { OK, UNCHANGED, NOT_A_BOX, IN_USE }

    /**
     * Update clients only. Skips neighbor updates (1), neighbor shape updates (16, so observers
     * don't fire), the old block entity's removal side effects (256) and the new block's onPlace (512).
     */
    private static final int SWAP_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SKIP_ON_PLACE;

    private Recolor() {
    }

    public static Result swap(ServerLevel level, BlockPos pos, BoxColor color) {
        BlockState oldState = level.getBlockState(pos);
        if (!(oldState.getBlock() instanceof ShulkerBoxBlock)
                || !(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity oldBox)) {
            return Result.NOT_A_BOX;
        }
        if (Boxes.color(oldState.getBlock()).orElse(null) == color) {
            return Result.UNCHANGED;
        }
        if (Boxes.isInUse(level, oldBox)) {
            return Result.IN_USE;
        }

        // Items, name, lock, loot table, components (incl. custom_data) all live in this tag.
        CompoundTag saved = oldBox.saveWithoutMetadata(level.registryAccess());

        BlockState newState = Boxes.block(color).defaultBlockState()
                .setValue(ShulkerBoxBlock.FACING, oldState.getValue(ShulkerBoxBlock.FACING));
        // Clear the old box first so its contents can't be seen twice, then place the new one.
        oldBox.clearContent();
        level.setBlock(pos, newState, SWAP_FLAGS);

        if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity newBox)) {
            throw new IllegalStateException("No shulker box block entity after recolor at " + pos);
        }
        // Our own write: the caller redraws the box, so the load hook mustn't queue another redraw.
        io.github.jekitchen.shulkerstickers.fabric.data.StickerData.asOwnWrite(() -> newBox.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved)));
        newBox.setChanged();
        level.updateNeighbourForOutputSignal(pos, newState.getBlock());
        return Result.OK;
    }
}
