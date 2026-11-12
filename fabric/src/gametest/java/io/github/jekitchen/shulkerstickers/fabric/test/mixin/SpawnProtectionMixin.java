package io.github.jekitchen.shulkerstickers.fabric.test.mixin;

import io.github.jekitchen.shulkerstickers.fabric.test.TestSpawnProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The GameTest server never protects spawn ({@code MinecraftServer#isUnderSpawnProtection} is
 * always false; only {@code DedicatedServer} overrides it). While a test turns it on, this gives
 * the GameTest server the dedicated server's rule for an area the test chooses.
 */
@Mixin(MinecraftServer.class)
public abstract class SpawnProtectionMixin {

    @Inject(method = "isUnderSpawnProtection", at = @At("HEAD"), cancellable = true)
    private void shulkerStickersTest$spawnProtection(ServerLevel level, BlockPos pos, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (TestSpawnProtection.protects((MinecraftServer) (Object) this, level, pos, player)) {
            cir.setReturnValue(true);
        }
    }
}
