package io.github.jekitchen.shulkerstickers.fabric.mixin;

import io.github.jekitchen.shulkerstickers.fabric.StickerPlacement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric API has no "block placed from an item" event, so hook the end of {@code BlockItem#place}. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void shulkerStickers$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()
                && ((BlockItem) (Object) this).getBlock() instanceof ShulkerBoxBlock
                && context.getLevel() instanceof ServerLevel level) {
            StickerPlacement.onPlaced(level, context.getClickedPos(), context.getPlayer());
        }
    }
}
