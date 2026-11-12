package io.github.jekitchen.shulkerstickers.paper.claims;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * GriefPrevention: build trust in the claim at the box. Outside claims and for players using
 * {@code /ignoreclaims}, editing is allowed. {@code checkPermission} returns null when allowed and
 * fires GriefPrevention's own permission event, so its add-ons can weigh in.
 */
final class GriefPreventionCheck implements ClaimCheck {

    @Override
    public String plugin() {
        return "GriefPrevention";
    }

    @Override
    public boolean canBuild(Player player, Block block) {
        GriefPrevention gp = GriefPrevention.instance;
        if (gp == null || gp.dataStore.getPlayerData(player.getUniqueId()).ignoreClaims) {
            return true;
        }
        Claim claim = gp.dataStore.getClaimAt(block.getLocation(), false, null);
        return claim == null || claim.checkPermission(player, ClaimPermission.Build, null) == null;
    }

    @Override
    public void probe(Block block) {
        GriefPrevention.instance.dataStore.getClaimAt(block.getLocation(), false, null);
        ClaimPermission.Build.name();
    }
}
