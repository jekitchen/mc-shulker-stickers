package io.github.jekitchen.shulkerstickers.paper.claims;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** One claim plugin's answer to "may this player build here?" (PRD §7.3, owner decision 3). */
public interface ClaimCheck {

    /** The plugin's name, for logs. */
    String plugin();

    /** Region thread of {@code block}. True if the player may build at the box. */
    boolean canBuild(Player player, Block block);
}
