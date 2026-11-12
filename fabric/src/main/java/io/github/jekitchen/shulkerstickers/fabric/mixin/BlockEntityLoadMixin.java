package io.github.jekitchen.shulkerstickers.fabric.mixin;

import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World moved from Paper (owner decision 4): Paper keeps a placed box's sticker in the block
 * entity's root {@code PublicBukkitValues}, which vanilla ignores and drops on the next save.
 * When a shulker box loads with our keys there, they replace ours in {@code custom_data}.
 */
@Mixin(BlockEntity.class)
abstract class BlockEntityLoadMixin {

    @Inject(method = "loadWithComponents", at = @At("TAIL"))
    private void shulkerStickers$migrateFromPaper(ValueInput input, CallbackInfo ci) {
        if ((Object) this instanceof ShulkerBoxBlockEntity box) {
            input.read(StickerFormat.BUKKIT_VALUES, CompoundTag.CODEC)
                    .ifPresent(paperValues -> StickerData.adoptPaperValues(box, paperValues));
        }
    }
}
