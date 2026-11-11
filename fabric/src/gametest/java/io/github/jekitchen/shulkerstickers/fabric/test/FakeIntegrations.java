package io.github.jekitchen.shulkerstickers.fabric.test;

import eu.pb4.common.protection.api.CommonProtection;
import eu.pb4.common.protection.api.ProtectionProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Stand-ins for a permissions mod (like LuckPerms) and a claims mod, registered through the real
 * Fabric Permissions API and Common Protection API. Each test uses its own player names and
 * positions, so the shared tables don't interfere.
 */
final class FakeIntegrations {

    /** Player name → permission node → answer. Missing entries answer DEFAULT. */
    static final Map<String, Map<String, TriState>> PERMISSIONS = new ConcurrentHashMap<>();
    /** Absolute positions a fake claim protects from everyone. */
    static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();

    private static boolean registered;

    private FakeIntegrations() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PermissionCheckEvent.EVENT.register((source, node) -> {
            if (source instanceof CommandSourceStack stack && stack.getEntity() instanceof ServerPlayer player) {
                return PERMISSIONS.getOrDefault(player.getScoreboardName(), Map.of()).getOrDefault(node, TriState.DEFAULT);
            }
            return TriState.DEFAULT;
        });
        CommonProtection.register(Identifier.fromNamespaceAndPath("shulker_stickers_test", "fake_claims"), new ProtectionProvider() {
            @Override
            public boolean isProtected(Level level, BlockPos pos) {
                return PROTECTED.contains(pos);
            }

            @Override
            public boolean isAreaProtected(Level level, AABB area) {
                return PROTECTED.stream().anyMatch(p -> area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)));
            }

            @Override
            public boolean canBreakBlock(Level level, BlockPos pos, NameAndId profile, Player player) {
                return !PROTECTED.contains(pos);
            }

            @Override
            public boolean canPlaceBlock(Level level, BlockPos pos, NameAndId profile, Player player) {
                return !PROTECTED.contains(pos);
            }
        });
    }

    static void set(ServerPlayer player, String node, TriState answer) {
        PERMISSIONS.computeIfAbsent(player.getScoreboardName(), k -> new ConcurrentHashMap<>()).put(node, answer);
    }
}
