package io.github.jekitchen.shulkerstickers.paper.claims;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** WorldGuard: the region "build" test, unless the player has region bypass. */
final class WorldGuardCheck implements ClaimCheck {

    @Override
    public String plugin() {
        return "WorldGuard";
    }

    @Override
    public boolean canBuild(Player player, Block block) {
        LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
        var platform = WorldGuard.getInstance().getPlatform();
        if (platform.getSessionManager().hasBypass(local, BukkitAdapter.adapt(block.getWorld()))) {
            return true;
        }
        return platform.getRegionContainer().createQuery()
                .testBuild(BukkitAdapter.adapt(block.getLocation().add(0.5, 0.5, 0.5)), local);
    }

    @Override
    public void probe(Block block) {
        WorldGuardPlugin.inst();
        WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));
    }
}
