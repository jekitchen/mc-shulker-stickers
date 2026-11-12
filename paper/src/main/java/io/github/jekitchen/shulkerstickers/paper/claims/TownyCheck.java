package io.github.jekitchen.shulkerstickers.paper.claims;

import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Towny: the cached build and destroy permissions for the box's location and type. */
final class TownyCheck implements ClaimCheck {

    @Override
    public String plugin() {
        return "Towny";
    }

    @Override
    public boolean canBuild(Player player, Block block) {
        return PlayerCacheUtil.getCachePermission(player, block.getLocation(), block.getType(),
                TownyPermission.ActionType.BUILD)
                && PlayerCacheUtil.getCachePermission(player, block.getLocation(), block.getType(),
                TownyPermission.ActionType.DESTROY);
    }
}
