package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.core.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission checks (PRD §8). Uses the Fabric Permissions API when it is installed (so LuckPerms
 * and similar work); otherwise everyone has the player nodes and admin needs op level 2.
 */
public final class Perms {

    private static final boolean API = FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0");

    private Perms() {
    }

    public static boolean has(ServerPlayer player, String node) {
        if (API) {
            return Api.check(player, node, Permissions.ADMIN.equals(node));
        }
        return vanillaHas(player.permissions(), node);
    }

    public static boolean has(CommandSourceStack source, String node) {
        if (API) {
            return Api.check(source, node, Permissions.ADMIN.equals(node));
        }
        return vanillaHas(source.permissions(), node);
    }

    /** The rule without a permissions mod: everyone has the player nodes; admin needs op level 2. */
    public static boolean vanillaHas(PermissionSet permissions, String node) {
        return !Permissions.ADMIN.equals(node)
                || permissions.hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    /** Kept separate so the optional API's classes load only when it is installed. */
    private static final class Api {
        static boolean check(ServerPlayer player, String node, boolean admin) {
            return admin
                    ? me.lucko.fabric.api.permissions.v0.Permissions.check(player, node, PermissionLevel.GAMEMASTERS)
                    : me.lucko.fabric.api.permissions.v0.Permissions.check(player, node, true);
        }

        static boolean check(CommandSourceStack source, String node, boolean admin) {
            return admin
                    ? me.lucko.fabric.api.permissions.v0.Permissions.check(source, node, PermissionLevel.GAMEMASTERS)
                    : me.lucko.fabric.api.permissions.v0.Permissions.check(source, node, true);
        }
    }
}
