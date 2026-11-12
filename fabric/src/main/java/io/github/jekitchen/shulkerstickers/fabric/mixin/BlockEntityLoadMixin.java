package io.github.jekitchen.shulkerstickers.fabric.mixin;

import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a shulker box's data is loaded:
 * <ul>
 *   <li>World moved from Paper (owner decision 4): Paper keeps a placed box's sticker in the block
 *       entity's root {@code PublicBukkitValues}, which vanilla ignores and drops on the next save.
 *       When a box loads with our keys there, they replace ours in {@code custom_data}.</li>
 *   <li>A box already in a level got new data ({@code /clone}, {@code /setblock}, structure
 *       blocks, WorldEdit): these create the box first and load its data after, so the load event
 *       saw an empty box. Draw it again at the end of the tick. (Boxes loading with their chunk
 *       have no level yet here and are drawn by the load event.) {@code /clone} sets the
 *       components separately, so {@code setComponents} is hooked too.</li>
 * </ul>
 */
@Mixin(BlockEntity.class)
abstract class BlockEntityLoadMixin {

    @Inject(method = "loadWithComponents", at = @At("TAIL"))
    private void shulkerStickers$afterLoad(ValueInput input, CallbackInfo ci) {
        if ((Object) this instanceof ShulkerBoxBlockEntity box) {
            input.read(StickerFormat.BUKKIT_VALUES, CompoundTag.CODEC)
                    .ifPresent(paperValues -> StickerData.adoptPaperValues(box, paperValues));
            if (!StickerData.isOwnWrite()) {
                redrawIfPlaced(box);
            }
        }
    }

    /** {@code /clone} copies a box's components (where the sticker lives) separately from its other data. */
    @Inject(method = "setComponents", at = @At("TAIL"))
    private void shulkerStickers$afterSetComponents(DataComponentMap components, CallbackInfo ci) {
        if ((Object) this instanceof ShulkerBoxBlockEntity box && !StickerData.isOwnWrite()) {
            redrawIfPlaced(box);
        }
    }

    private static void redrawIfPlaced(ShulkerBoxBlockEntity box) {
        if (box.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            StickerDisplays.drawLater(level, box);
        }
    }
}
