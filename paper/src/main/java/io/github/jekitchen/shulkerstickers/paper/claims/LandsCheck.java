package io.github.jekitchen.shulkerstickers.paper.claims;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.land.Area;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Lands: the area's block-place role flag. Unclaimed land is left to Lands' own wilderness rules. */
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
        Area area = lands.getArea(block.getLocation());
        return area == null || area.hasRoleFlag(player, Flags.BLOCK_PLACE, block.getType(), false);
    }
}
