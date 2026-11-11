package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
        ServerLifecycleEvents.SERVER_STARTING.register(server -> Settings.load());
        registerGesture();
        registerDropCopy();
        StickerDisplays.register();
        PlayerPrefs.register();
        StickerCommands.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EditorLocks.releaseAll(handler.getPlayer().getUUID()));
        LOGGER.info("Shulker Stickers loaded");
    }

    /** Sneak + right-click a placed box with an empty main hand opens the editor (PRD §5.1). */
    private static void registerGesture() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!player.isSecondaryUseActive()
                    || !player.getMainHandItem().isEmpty()
                    || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof ShulkerBoxBlock)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer && PlayerPrefs.gestureOff(serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (player.isSpectator() || !EditorMenu.anyEditingEnabled(Settings.config())) {
                return InteractionResult.PASS;
            }
            if (hand == InteractionHand.OFF_HAND) {
                // A vanilla client follows the gesture's main-hand click with an off-hand click and
                // predicts placing the off-hand block. Refuse it, and resend the inventory so the
                // client's off-hand count is put right (PRD §5.1).
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.inventoryMenu.sendAllDataToRemote();
                }
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                EditorMenu.tryOpen(serverPlayer, serverLevel, hit.getBlockPos(), hit.getDirection());
            }
            // Consumed even when refused, so the off-hand block isn't placed instead (PRD §5.1).
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
