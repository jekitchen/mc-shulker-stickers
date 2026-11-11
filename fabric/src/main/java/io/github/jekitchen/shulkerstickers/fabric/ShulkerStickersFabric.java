package io.github.jekitchen.shulkerstickers.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShulkerStickersFabric implements ModInitializer {

    public static final String MOD_ID = "shulker_stickers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Shulker Stickers loaded");
    }
}
