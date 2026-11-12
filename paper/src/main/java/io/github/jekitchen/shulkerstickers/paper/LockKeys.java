package io.github.jekitchen.shulkerstickers.paper;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lockable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Whether a player holds the key to a locked box (PRD §9, owner decision 2: the key counts in
 * either hand, as on Fabric).
 *
 * <p>Paper's API can tell a box is locked but can't test an item against the lock, which is an
 * item predicate since 1.21.2. Paper 26.x runs on Minecraft's own (unobfuscated) names, so this
 * asks the server's {@code BaseContainerBlockEntity.lockKey.unlocksWith(item)} by reflection. If that ever
 * fails, locked boxes are refused, the safe choice, and a warning is logged once.
 */
final class LockKeys {

    private static volatile Bridge bridge;
    private static volatile boolean warned;

    private record Bridge(Method getBlockEntity, Method asNmsCopy, Class<?> nmsItemStack) {}

    /**
     * Test hook: MockBukkit has no server lock check, so unit tests supply one. Always null on a
     * real server, where the self-test checks the server's own lock check.
     */
    static volatile java.util.function.BiPredicate<BlockState, ItemStack> testLockCheck;

    private LockKeys() {}

    /** True if the box isn't locked, or the player holds its key in either hand. */
    static boolean mayOpen(Player player, BlockState state, Logger logger) {
        if (!(state instanceof Lockable lockable) || !lockable.isLocked()) {
            return true;
        }
        return unlocksWith(state, player.getInventory().getItemInMainHand(), logger)
                || unlocksWith(state, player.getInventory().getItemInOffHand(), logger);
    }

    static boolean unlocksWith(BlockState state, ItemStack item, Logger logger) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        var testCheck = testLockCheck;
        if (testCheck != null) {
            return testCheck.test(state, item);
        }
        try {
            Bridge b = bridge();
            Object blockEntity = b.getBlockEntity().invoke(state);
            Object lockCode = blockEntity.getClass().getField("lockKey").get(blockEntity);
            Object nmsItem = b.asNmsCopy().invoke(null, item);
            Method unlocks = lockCode.getClass().getMethod("unlocksWith", b.nmsItemStack());
            return (Boolean) unlocks.invoke(lockCode, nmsItem);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                if (logger != null) {
                    logger.warning("Can't check lock keys on this server (" + e + "); locked boxes can't be edited.");
                }
            }
            return false;
        }
    }

    private static Bridge bridge() throws ReflectiveOperationException {
        Bridge b = bridge;
        if (b == null) {
            Class<?> state = Class.forName("org.bukkit.craftbukkit.block.CraftBlockEntityState");
            Class<?> craftItem = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Class<?> nmsItem = Class.forName("net.minecraft.world.item.ItemStack");
            b = new Bridge(state.getMethod("getBlockEntity"), craftItem.getMethod("asNMSCopy", ItemStack.class), nmsItem);
            bridge = b;
        }
        return b;
    }
}
