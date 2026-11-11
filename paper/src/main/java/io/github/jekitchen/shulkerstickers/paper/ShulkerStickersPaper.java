package io.github.jekitchen.shulkerstickers.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShulkerStickersPaper extends JavaPlugin {

    private Settings settings;
    private DisplayManager displays;

    @Override
    public void onEnable() {
        settings = new Settings(getDataFolder().toPath().resolve("config.json"), getLogger());
        settings.reload();
        displays = new DisplayManager(this, settings);
        EditorService editor = new EditorService(this, settings, displays);
        var pm = getServer().getPluginManager();
        pm.registerEvents(new GestureListener(editor), this);
        pm.registerEvents(editor, this);
        pm.registerEvents(new StickerTransferListener(this, displays), this);
        pm.registerEvents(new DisplayListener(this, settings, displays), this);
        pm.registerEvents(new HelpListener(settings), this);

        StickerCommands commands = new StickerCommands(this, settings, displays);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(commands.build(), "Shulker Stickers commands", List.of()));

        if (Boolean.getBoolean("shulkerstickers.selftest")) {
            new SelfTest(this, settings, displays).schedule();
        }
        getLogger().info("Shulker Stickers enabled");
    }

    DisplayManager displays() {
        return displays;
    }
}
