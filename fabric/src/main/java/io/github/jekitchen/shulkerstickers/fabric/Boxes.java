package io.github.jekitchen.shulkerstickers.fabric;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Face;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Maps core's platform-free types to Minecraft's. */
public final class Boxes {

    private Boxes() {
    }

    public static Block block(BoxColor color) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(color.itemId()));
    }

    public static Optional<BoxColor> color(Block block) {
        if (!(block instanceof ShulkerBoxBlock)) {
            return Optional.empty();
        }
        return BoxColor.fromItemId(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    /**
     * True if any player has this box's menu open, or its lid is still moving. The animation
     * status alone lags a tick behind opening, so open menus are checked directly.
     */
    public static boolean isInUse(ServerLevel level, ShulkerBoxBlockEntity box) {
        if (box.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
            return true;
        }
        for (ServerPlayer player : level.players()) {
            if (player.containerMenu instanceof ShulkerBoxMenu menu
                    && !menu.slots.isEmpty()
                    && menu.getSlot(0).container == box) {
                return true;
            }
        }
        return false;
    }

    public static Face face(Direction direction) {
        return switch (direction) {
            case DOWN -> Face.DOWN;
            case UP -> Face.UP;
            case NORTH -> Face.NORTH;
            case SOUTH -> Face.SOUTH;
            case WEST -> Face.WEST;
            case EAST -> Face.EAST;
        };
    }
}
