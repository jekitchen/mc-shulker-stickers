package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import io.github.jekitchen.shulkerstickers.fabric.Access;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AC-10, spawn protection: a non-op player can't edit a box inside the spawn-protection radius.
 * The GameTest server has no spawn protection of its own; {@link TestSpawnProtection} gives it
 * the dedicated server's rule around the test box for the length of one tick.
 */
public class SpawnProtectionGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    /** A non-op survival player next to the box, holding its key in the off-hand. */
    private static ServerPlayer playerWithKey(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper);
        BlockPos pos = helper.absolutePos(BOX);
        player.teleportTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND));
        helper.assertTrue(!helper.getLevel().getServer().getPlayerList().isOp(player.nameAndId()), "player is not an operator");
        return player;
    }

    @GameTest
    public void editorIsRefusedInsideSpawnProtection(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = playerWithKey(helper);
        BlockPos pos = helper.absolutePos(BOX);
        helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), pos, StickerConfig.DEFAULTS), Optional.empty(),
                "allowed outside spawn protection");

        try (var protection = TestSpawnProtection.around(helper.getLevel(), pos, 16)) {
            helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), pos, StickerConfig.DEFAULTS),
                    Optional.of(TextKeys.DENY_PROTECTED), "deny reason");
            helper.assertTrue(!EditorMenu.tryOpen(player, helper.getLevel(), pos, Direction.UP), "editor refused");

            // The gesture itself: sneak + right-click with an empty main hand.
            player.setShiftKeyDown(true);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
            UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
        }

        helper.assertTrue(!(player.containerMenu instanceof EditorMenu), "no editor open, was " + player.containerMenu);
        helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), pos, player), "no lock taken");
        TestBoxes.assertLoaded(helper, BOX, BoxColor.UNDYED, Direction.UP);
        helper.succeed();
    }

    @GameTest
    public void saveIsRefusedOnceTheBoxIsInsideSpawnProtection(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = playerWithKey(helper);
        BlockPos pos = helper.absolutePos(BOX);
        EditorMenu menu = EditorMenu.forBox(1, player, helper.getLevel(), pos, Direction.UP, StickerConfig.DEFAULTS);
        menu.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, player);
        menu.session().stageName("Renamed");

        try (var protection = TestSpawnProtection.around(helper.getLevel(), pos, 16)) {
            menu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);
        }

        TestBoxes.assertLoaded(helper, BOX, BoxColor.UNDYED, Direction.UP);
        helper.succeed();
    }

    /** Only boxes inside the radius are refused, so the tests above aren't passing on a blanket deny. */
    @GameTest
    public void boxOutsideTheRadiusIsNotProtected(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = playerWithKey(helper);
        BlockPos pos = helper.absolutePos(BOX);

        try (var protection = TestSpawnProtection.around(helper.getLevel(), pos.east(5), 2)) {
            helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), pos, StickerConfig.DEFAULTS), Optional.empty(),
                    "allowed outside the radius");
        }
        helper.succeed();
    }
}
