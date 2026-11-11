package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShulkerStickersFabric implements ModInitializer {

    public static final String MOD_ID = "shulker_stickers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        registerGesture();
        registerDropCopy();
        StickerDisplays.register();
        LOGGER.info("Shulker Stickers loaded");
    }

    /** Sneak + right-click a placed box with an empty main hand opens the editor (PRD §5.1). */
    private static void registerGesture() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND
                    || !player.isSecondaryUseActive()
                    || !player.getMainHandItem().isEmpty()
                    || player.isSpectator()
                    || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof ShulkerBoxBlock)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                // Spike: no permission, claim, lock or adventure-mode checks yet.
                EditorMenu.open(serverPlayer, serverLevel, hit.getBlockPos(), hit.getDirection());
            }
            return InteractionResult.SUCCESS;
        });
    }

    /**
     * The vanilla shulker loot table copies only custom_name, container, lock and container_loot,
     * so custom_data (where stickers live) would be lost on break. Copy it onto the dropped box.
     */
    private static void registerDropCopy() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            if (!(context.getOptional(LootContextParams.BLOCK_ENTITY) instanceof ShulkerBoxBlockEntity box)) {
                return;
            }
            for (ItemStack drop : drops) {
                if (drop.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock) {
                    StickerData.copyToItem(box, drop);
                }
            }
        });
    }
}
