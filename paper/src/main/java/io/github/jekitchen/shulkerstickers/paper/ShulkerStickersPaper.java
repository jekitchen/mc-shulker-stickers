package io.github.jekitchen.shulkerstickers.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShulkerStickersPaper extends JavaPlugin {

    private Settings settings;
    private DisplayManager displays;
    private EditorService editor;

    @Override
    public void onEnable() {
        settings = new Settings(getDataFolder().toPath().resolve("config.json"), getLogger());
        settings.reload();
        displays = new DisplayManager(this, settings);
        editor = new EditorService(this, settings, displays,
                io.github.jekitchen.shulkerstickers.paper.claims.Claims.detect(this));
        var pm = getServer().getPluginManager();
        pm.registerEvents(new GestureListener(editor), this);
        pm.registerEvents(editor, this);
        pm.registerEvents(new StickerTransferListener(this, displays), this);
        DisplayListener displayListener = new DisplayListener(this, settings, displays);
        pm.registerEvents(displayListener, this);
        displayListener.processLoadedChunks();
        pm.registerEvents(new HelpListener(settings), this);
        LootTableGuard lootGuard = new LootTableGuard(this);
        pm.registerEvents(lootGuard, this);
        lootGuard.checkWorldDatapacks();

        StickerCommands commands = new StickerCommands(this, settings, displays, displayListener::processLoadedChunks);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(commands.build(), "Shulker Stickers commands", List.of()));

        if (Boolean.getBoolean("shulkerstickers.selftest")) {
            new SelfTest(this, settings, displays, editor).schedule();
        } else if (Boolean.getBoolean("shulkerstickers.perftest")) {
            new PerfTest(this, displays).schedule();
        }
        getLogger().info("Shulker Stickers enabled");
    }

    /**
     * Without our listeners an open editor would become a plain chest of free shulker boxes,
     * so every editor is closed first. Displays are removed too (they are never saved).
     */
    @Override
    public void onDisable() {
        if (editor != null) {
            editor.shutdown();
        }
        if (displays != null) {
            displays.removeAll();
        }
    }

    DisplayManager displays() {
        return displays;
    }
}
