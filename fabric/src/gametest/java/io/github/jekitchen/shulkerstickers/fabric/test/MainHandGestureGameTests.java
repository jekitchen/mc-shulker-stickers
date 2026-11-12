package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.PlayerPrefs;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AC-9: sneak + right-click on a box with an item in the main hand behaves exactly as vanilla.
 * Each test does the same click twice, once with the gesture on and once with it turned off by
 * {@code /shulkerstickers toggle} (which leaves only vanilla behavior), and compares the results.
 */
public class MainHandGestureGameTests {

    private static final BlockPos WITH_GESTURE = new BlockPos(1, 1, 1);
    private static final BlockPos VANILLA = new BlockPos(1, 1, 5);

    /** What a click changed: the block in front of the clicked face, the held item, and what's open. */
    private record Outcome(BlockState inFront, ItemStack mainHand, boolean usingItem, Class<?> menu, String result) {
        boolean sameAs(Outcome other) {
            return inFront.equals(other.inFront) && ItemStack.matches(mainHand, other.mainHand)
                    && usingItem == other.usingItem && menu == other.menu && result.equals(other.result);
        }
    }

    private static int players;

    /**
     * Sneak + right-click the box's top face the way a vanilla client does it: for each hand in
     * turn, use the item on the block; stop if that did something or failed; otherwise use the
     * item in the air if the hand isn't empty.
     */
    private static Outcome click(GameTestHelper helper, BlockPos rel, Item item, boolean gestureOff) {
        TestBoxes.placePlain(helper, rel, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(rel);
        ServerPlayer player = TestPlayers.make(helper, GameType.SURVIVAL, "clicker" + players++);
        player.teleportTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        Vec3 target = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        player.getFoodData().setFoodLevel(5); // hungry, so food can be eaten
        player.setShiftKeyDown(true);
        if (gestureOff) {
            PlayerPrefs.toggleGesture(player);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        BlockHitResult hit = new BlockHitResult(target, Direction.UP, pos, false);

        InteractionResult last = InteractionResult.PASS;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            last = player.gameMode.useItemOn(player, helper.getLevel(), held, hand, hit);
            if (last.consumesAction() || last instanceof InteractionResult.Fail) {
                break;
            }
            if (!held.isEmpty()) {
                last = player.gameMode.useItem(player, helper.getLevel(), held, hand);
                if (last.consumesAction()) {
                    break;
                }
            }
        }

        helper.assertTrue(!(player.containerMenu instanceof EditorMenu), item + ": the editor must not open");
        helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), pos, player), item + ": no editor lock taken");
        return new Outcome(helper.getBlockState(rel.above()), player.getMainHandItem().copy(), player.isUsingItem(),
                player.containerMenu.getClass(), describe(last));
    }

    private static String describe(InteractionResult result) {
        return result instanceof InteractionResult.Success s ? "Success/" + s.swingSource() : result.getClass().getSimpleName();
    }

    /** Does the click with the gesture on and off, asserts they match, and returns the result with it on. */
    private static Outcome sameAsVanilla(GameTestHelper helper, Item item) {
        Outcome withGesture = click(helper, WITH_GESTURE, item, false);
        Outcome vanilla = click(helper, VANILLA, item, true);
        helper.assertTrue(withGesture.sameAs(vanilla), item + ": with the gesture " + withGesture + " but vanilla " + vanilla);
        return withGesture;
    }

    @GameTest
    public void placeableBlockIsPlacedAsInVanilla(GameTestHelper helper) {
        Outcome outcome = sameAsVanilla(helper, Items.STONE);
        helper.assertTrue(outcome.inFront().is(Blocks.STONE), "stone placed on the box, was " + outcome.inFront());
        helper.succeed();
    }

    @GameTest
    public void anotherShulkerBoxIsPlacedAsInVanilla(GameTestHelper helper) {
        Outcome outcome = sameAsVanilla(helper, Boxes.item(BoxColor.RED));
        helper.assertTrue(outcome.inFront().is(Boxes.block(BoxColor.RED)), "box placed on the box, was " + outcome.inFront());
        helper.succeed();
    }

    @GameTest
    public void waterBucketEmptiesAsInVanilla(GameTestHelper helper) {
        Outcome outcome = sameAsVanilla(helper, Items.WATER_BUCKET);
        helper.assertTrue(outcome.mainHand().is(Items.BUCKET), "bucket emptied, holding " + outcome.mainHand());
        helper.assertTrue(outcome.inFront().is(Blocks.WATER), "water placed on the box, was " + outcome.inFront());
        helper.succeed();
    }

    @GameTest
    public void emptyBucketDoesWhatVanillaDoes(GameTestHelper helper) {
        Outcome outcome = sameAsVanilla(helper, Items.BUCKET);
        helper.assertTrue(outcome.mainHand().is(Items.BUCKET), "still an empty bucket, holding " + outcome.mainHand());
        helper.succeed();
    }

    @GameTest
    public void foodIsEatenAsInVanilla(GameTestHelper helper) {
        Outcome outcome = sameAsVanilla(helper, Items.BREAD);
        helper.assertTrue(outcome.usingItem(), "started eating");
        helper.succeed();
    }

    @GameTest
    public void toolDoesWhatVanillaDoes(GameTestHelper helper) {
        Outcome outcome = sameAsVanilla(helper, Items.DIAMOND_PICKAXE);
        helper.assertTrue(outcome.inFront().isAir(), "nothing placed, was " + outcome.inFront());
        helper.succeed();
    }

    @GameTest
    public void axeDoesWhatVanillaDoes(GameTestHelper helper) {
        sameAsVanilla(helper, Items.IRON_AXE);
        helper.succeed();
    }
}
