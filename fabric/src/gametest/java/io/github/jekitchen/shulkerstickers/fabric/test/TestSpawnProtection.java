package io.github.jekitchen.shulkerstickers.fabric.test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Spawn protection for the GameTest server (see {@code SpawnProtectionMixin}), following
 * {@code DedicatedServer#isUnderSpawnProtection}: same dimension, within {@code radius} blocks
 * (square, X and Z only) of the center, and the player isn't an operator. Turned on only inside a
 * try-with-resources block within a single tick, so no other test sees it.
 */
public final class TestSpawnProtection implements AutoCloseable {

    private record Area(ResourceKey<Level> dimension, BlockPos center, int radius) {}

    private static volatile @Nullable Area area;

    private TestSpawnProtection() {
    }

    /** Protects {@code radius} blocks around {@code center} until closed. */
    public static TestSpawnProtection around(ServerLevel level, BlockPos center, int radius) {
        area = new Area(level.dimension(), center.immutable(), radius);
        return new TestSpawnProtection();
    }

    public static boolean protects(MinecraftServer server, ServerLevel level, BlockPos pos, Player player) {
        Area current = area;
        if (current == null || level.dimension() != current.dimension()) {
            return false;
        }
        if (server.getPlayerList().isOp(player.nameAndId())) {
            return false;
        }
        int dx = Math.abs(pos.getX() - current.center().getX());
        int dz = Math.abs(pos.getZ() - current.center().getZ());
        return Math.max(dx, dz) <= current.radius();
    }

    @Override
    public void close() {
        area = null;
    }
}
