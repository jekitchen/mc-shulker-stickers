package io.github.jekitchen.shulkerstickers.paper.claims;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.land.LandWorld;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Lands: the block-place and block-break role flags at the box, asked of the Lands world so the
 * wilderness rules apply too. Worlds Lands doesn't manage are allowed.
 */
final class LandsCheck implements ClaimCheck {

    private final LandsIntegration lands;

    LandsCheck(Plugin plugin) {
        this.lands = LandsIntegration.of(plugin);
    }

    @Override
    public String plugin() {
        return "Lands";
    }

    @Override
    public boolean canBuild(Player player, Block block) {
        LandWorld world = lands.getWorld(block.getWorld());
        if (world == null) {
            return true;
        }
        return world.hasRoleFlag(player, block.getLocation(), Flags.BLOCK_PLACE, block.getType(), false)
                && world.hasRoleFlag(player, block.getLocation(), Flags.BLOCK_BREAK, block.getType(), false);
    }
}
