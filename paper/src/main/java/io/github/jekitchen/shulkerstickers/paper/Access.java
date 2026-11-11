package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lockable;
import org.bukkit.entity.Player;

/**
 * Who may open the editor on a box (PRD §5.1, §9). Claim plugins are honored through the
 * cancelled {@code PlayerInteractEvent} (see {@link GestureListener}); this covers the rest.
 */
final class Access {

    private Access() {}

    /** The translation key of the reason to refuse, or empty if the player may edit. */
    static Optional<String> denial(Player player, Block block, StickerConfig config) {
        if (player.getGameMode() == GameMode.SPECTATOR
                || (player.getGameMode() == GameMode.ADVENTURE && !config.allowAdventureMode())) {
            return Optional.of(TextKeys.DENY_ADVENTURE);
        }
        if (!player.hasPermission(Permissions.COLOR) && !player.hasPermission(Permissions.ICON)
                && !player.hasPermission(Permissions.NAME)) {
            return Optional.of(TextKeys.DENY_PERMISSION);
        }
        // A locked box needs its key in the main hand, which the gesture requires to be empty,
        // so a locked box can't be edited (PRD §9: "the same as opening the box").
        if (block.getState(false) instanceof Lockable lockable && lockable.isLocked()) {
            return Optional.of(TextKeys.DENY_LOCKED);
        }
        if (inSpawnProtection(player, block)) {
            return Optional.of(TextKeys.DENY_PROTECTED);
        }
        return Optional.empty();
    }

    /** Vanilla spawn protection: overworld only, ops exempt, square radius around spawn. */
    static boolean inSpawnProtection(Player player, Block block) {
        int radius = Bukkit.getSpawnRadius();
        if (radius <= 0 || player.isOp() || Bukkit.getOperators().isEmpty()) {
            return false;
        }
        World world = block.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL || !world.equals(Bukkit.getWorlds().getFirst())) {
            return false;
        }
        Location spawn = world.getSpawnLocation();
        int dx = Math.abs(block.getX() - spawn.getBlockX());
        int dz = Math.abs(block.getZ() - spawn.getBlockZ());
        return Math.max(dx, dz) <= radius;
    }
}
