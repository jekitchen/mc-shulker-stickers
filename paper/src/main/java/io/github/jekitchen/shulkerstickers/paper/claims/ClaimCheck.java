package io.github.jekitchen.shulkerstickers.paper.claims;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** One claim plugin's answer to "may this player build here?" (PRD §7.3, owner decision 3). */
public interface ClaimCheck {

    /** The plugin's name, for logs. */
    String plugin();

    /** Region thread of {@code block}. True if the player may build at the box. */
    boolean canBuild(Player player, Block block);

    /**
     * Self-test only: calls the plugin's API for {@code block} without a player, to prove the
     * hook links against the installed version. Throws if it doesn't.
     */
    default void probe(Block block) {}
}
