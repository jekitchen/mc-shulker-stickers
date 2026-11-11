package io.github.jekitchen.shulkerstickers.paper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Registers the built-in datapack. It overrides the 17 shulker box loot tables so a
 * dropped box keeps our keys from the block's persistent data, on every drop path
 * (players, pistons, explosions, {@code /setblock destroy}).
 */
public final class ShulkerStickersBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        if (Boolean.getBoolean("shulkerstickers.selftest.nodatapack")) {
            return; // spike control run: proves the datapack is what carries the data
        }
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
            try {
                var uri = Objects.requireNonNull(getClass().getResource("/stickers_datapack")).toURI();
                event.registrar().discoverPack(uri, "loot");
            } catch (URISyntaxException | IOException e) {
                context.getLogger().error("Could not register the Shulker Stickers datapack", e);
            }
        }));
    }
}
