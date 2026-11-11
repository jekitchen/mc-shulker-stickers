package io.github.jekitchen.shulkerstickers.paper;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks the Sticker editor's inventory, so its events can be told apart from real containers. */
final class EditorHolder implements InventoryHolder {

    private final EditorService.Session session;
    private Inventory inventory;

    EditorHolder(EditorService.Session session) {
        this.session = session;
    }

    EditorService.Session session() {
        return session;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
