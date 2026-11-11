package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.LockCode;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * Who may edit a box (PRD §5.1, §6.3 step 1, §9). Checked when the editor opens and again at Save.
 * Editing needs build permission, not just container access.
 */
public final class Access {

    private static final boolean PROTECTION_API = FabricLoader.getInstance().isModLoaded("common-protection-api");

    private Access() {
    }

    /** The translation key of the reason the player can't edit this box, or empty if they can. */
    public static Optional<String> denyReason(ServerPlayer player, ServerLevel level, BlockPos pos, StickerConfig config) {
        GameType mode = player.gameMode();
        if (mode == GameType.SPECTATOR || (mode == GameType.ADVENTURE && !config.allowAdventureMode())) {
            return Optional.of(TextKeys.DENY_ADVENTURE);
        }
        if (!level.mayInteract(player, pos) || (PROTECTION_API && !Protection.canEdit(player, level, pos))) {
            return Optional.of(TextKeys.DENY_PROTECTED);
        }
        if (level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box && !holdsKey(player, box)) {
            return Optional.of(TextKeys.DENY_LOCKED);
        }
        if (!Perms.has(player, Permissions.COLOR) && !Perms.has(player, Permissions.ICON) && !Perms.has(player, Permissions.NAME)) {
            return Optional.of(TextKeys.DENY_PERMISSION);
        }
        return Optional.empty();
    }

    /**
     * A box with a vanilla lock needs its key. The gesture needs an empty main hand, so the key
     * counts in either hand (PRD gap: the PRD says "the same as opening the box", which checks only
     * the main hand and would make locked boxes impossible to edit).
     */
    public static boolean holdsKey(ServerPlayer player, ShulkerBoxBlockEntity box) {
        LockCode lock = box.collectComponents().getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
        return lock.equals(LockCode.NO_LOCK)
                || lock.unlocksWith(player.getMainHandItem())
                || lock.unlocksWith(player.getOffhandItem());
    }

    /** Kept separate so Common Protection API's classes load only when it is installed. */
    private static final class Protection {
        static boolean canEdit(ServerPlayer player, ServerLevel level, BlockPos pos) {
            return eu.pb4.common.protection.api.CommonProtection.canPlaceBlock(level, pos, player.nameAndId(), player)
                    && eu.pb4.common.protection.api.CommonProtection.canBreakBlock(level, pos, player.nameAndId(), player);
        }
    }
}
