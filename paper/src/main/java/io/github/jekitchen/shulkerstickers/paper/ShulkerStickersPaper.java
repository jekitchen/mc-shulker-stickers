package io.github.jekitchen.shulkerstickers.paper;

import org.bukkit.plugin.java.JavaPlugin;

public final class ShulkerStickersPaper extends JavaPlugin {

    private DisplayManager displays;

    @Override
    public void onEnable() {
        displays = new DisplayManager(this);
        EditorService editor = new EditorService(this, displays);
        var pm = getServer().getPluginManager();
        pm.registerEvents(new GestureListener(editor), this);
        pm.registerEvents(editor, this);
        pm.registerEvents(new StickerTransferListener(), this);
        pm.registerEvents(new DisplayListener(this, displays), this);

        if (Boolean.getBoolean("shulkerstickers.selftest")) {
            new SelfTest(this, displays).schedule();
        }
        getLogger().info("Shulker Stickers enabled");
    }

    DisplayManager displays() {
        return displays;
    }
}
