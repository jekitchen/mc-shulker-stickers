package io.github.jekitchen.shulkerstickers.paper;

import org.bukkit.GameMode;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/**
 * Sneak + right-click a placed shulker box with an empty main hand opens the editor
 * (PRD §5.1). Runs at HIGH so protection plugins (which deny at LOW/NORMAL) go first;
 * a denied interaction is how claim plugins refuse the editor.
 */
final class GestureListener implements Listener {

    private final EditorService editor;

    GestureListener(EditorService editor) {
        this.editor = editor;
    }

    static boolean isGesture(Player player, Action action, Block block) {
        return action == Action.RIGHT_CLICK_BLOCK
                && block != null
                && Tag.SHULKER_BOXES.isTagged(block.getType())
                && player.isSneaking()
                && player.getInventory().getItemInMainHand().isEmpty()
                && player.getGameMode() != GameMode.SPECTATOR
                && !gestureOff(player);
    }

    /** {@code /shulkerstickers toggle} turns the gesture off for one player. */
    static boolean gestureOff(Player player) {
        return player.getPersistentDataContainer().getOrDefault(PaperKeys.GESTURE_OFF, PersistentDataType.BOOLEAN, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onInteract(PlayerInteractEvent event) {
        if (!isGesture(event.getPlayer(), event.getAction(), event.getClickedBlock())) {
            return;
        }
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return; // a protection plugin said no
        }
        // The client sends a second, off-hand packet when the main hand does nothing.
        // Cancelling it stops the off-hand block being placed against the box (PRD §5.1).
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        editor.open(event.getPlayer(), event.getClickedBlock(), event.getBlockFace());
    }
}
