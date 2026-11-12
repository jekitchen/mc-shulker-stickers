package io.github.jekitchen.shulkerstickers.paper.claims;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The opt-in test event ({@code protection.simulate_place_event}): "would this player be allowed
 * to place this box here?" Nothing is placed. Our own listeners ignore it; other plugins can't
 * tell it from a real placement, which is why it is off by default (owner decision 3).
 */
public final class ClaimTestPlaceEvent extends BlockPlaceEvent {

    public ClaimTestPlaceEvent(Block box, Player player) {
        super(box, box.getState(), box, new ItemStack(box.getType()), player, true, EquipmentSlot.HAND);
    }
}
