package io.github.jekitchen.shulkerstickers.paper.claims;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.bentobox.api.flags.FlagListener;
import world.bentobox.bentobox.lists.Flags;

/**
 * BentoBox (BSkyBlock, AcidIsland, AOneBlock and its other game modes): the island's
 * {@code PLACE_BLOCKS} and {@code BREAK_BLOCKS} protection flags at the box.
 *
 * <p>Each flag is asked through BentoBox's own listener for it ({@link FlagListener#checkIsland}),
 * silently and with an event nobody else sees. Island ranks, world defaults outside islands,
 * deleted islands, ops and BentoBox's bypass permissions all work the way they do for placing and
 * breaking blocks. Worlds that aren't BentoBox worlds are allowed.
 */
final class BentoBoxCheck implements ClaimCheck {

    @Override
    public String plugin() {
        return "BentoBox";
    }

    @Override
    public boolean canBuild(Player player, Block block) {
        Location at = block.getLocation();
        if (!BentoBox.getInstance().getIWM().inWorld(at)) {
            return true;
        }
        Event question = new ClaimTestPlaceEvent(block, player);
        return allowed(Flags.PLACE_BLOCKS, question, player, at)
                && allowed(Flags.BREAK_BLOCKS, question, player, at);
    }

    private static boolean allowed(Flag flag, Event question, Player player, Location at) {
        return listener(flag).checkIsland(question, player, at, flag, true);
    }

    /** BentoBox's listener for {@code flag}. Throws if there isn't one, so the check refuses. */
    private static FlagListener listener(Flag flag) {
        if (flag.getListener().orElse(null) instanceof FlagListener listener) {
            return listener;
        }
        throw new IllegalStateException("BentoBox has no listener for " + flag.getID());
    }

    @Override
    public void probe(Block block) {
        BentoBox.getInstance().getIWM().inWorld(block.getLocation());
        BentoBox.getInstance().getIslands().getProtectedIslandAt(block.getLocation());
        listener(Flags.PLACE_BLOCKS);
        listener(Flags.BREAK_BLOCKS);
    }
}
