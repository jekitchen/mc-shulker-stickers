package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;

/** A one-time tip on a player's first shulker box placement (PRD §5.6). */
final class HelpListener implements Listener {

    private final Settings settings;

    HelpListener(Settings settings) {
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        if (event instanceof io.github.jekitchen.shulkerstickers.paper.claims.ClaimTestPlaceEvent) {
            return; // a claim test, not a placement
        }
        Player player = event.getPlayer();
        if (!settings.config().firstPlaceMessage()
                || !Tag.SHULKER_BOXES.isTagged(event.getBlockPlaced().getType())
                || player.getPersistentDataContainer().has(PaperKeys.HELP_SEEN)) {
            return;
        }
        player.getPersistentDataContainer().set(PaperKeys.HELP_SEEN, PersistentDataType.BOOLEAN, true);
        player.sendMessage(Texts.text(player, NamedTextColor.AQUA, TextKeys.HELP_FIRST_PLACE));
    }
}
