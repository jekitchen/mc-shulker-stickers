package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.fabric.Recolor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;

public class RecolorGameTests {

    private static final BlockPos BOX = new BlockPos(2, 1, 2);

    /** AC-1: every variant keeps all 27 slots, the name, the lock, the sticker and facing. */
    @GameTest
    public void recolorThroughAllVariantsKeepsEverything(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.EAST);

        // Every dyed variant in turn, then back to undyed.
        BoxColor[] all = BoxColor.values();
        for (int i = 1; i <= all.length; i++) {
            BoxColor color = all[i % all.length];
            Recolor.Result result = Recolor.swap(helper.getLevel(), helper.absolutePos(BOX), color);
            helper.assertValueEqual(result, Recolor.Result.OK, "recolor to " + color);
            TestBoxes.assertLoaded(helper, BOX, color, Direction.EAST);
        }
        helper.assertValueEqual(helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM).size(), 0, "dropped items");
        helper.succeed();
    }

    /** AC-16: an observer watching the box does not fire when the box is recolored. */
    @GameTest(maxTicks = 40)
    public void recolorDoesNotPulseObserver(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos observer = BOX.east();
        // The observer's face looks west, at the box.
        helper.setBlock(observer, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST));

        helper.runAfterDelay(5, () -> {
            Recolor.swap(helper.getLevel(), helper.absolutePos(BOX), BoxColor.BLUE);
            for (int t = 1; t <= 6; t++) {
                helper.runAfterDelay(t, () -> helper.assertBlockProperty(observer, ObserverBlock.POWERED, false));
            }
            helper.runAfterDelay(8, helper::succeed);
        });
    }

    /** AC-16: a comparator reading the box gives the same signal after the recolor. */
    @GameTest(maxTicks = 40)
    public void comparatorReadsSameSignalAfterRecolor(GameTestHelper helper) {
        BlockPos comparator = BOX.south();
        // A comparator facing north takes its input from the block to its north (the box).
        // Placed first, so filling the box updates it. The empty test structure has no floor.
        helper.setBlock(comparator.below(), Blocks.STONE);
        helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.NORTH));
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);

        helper.runAfterDelay(5, () -> {
            int before = helper.getBlockEntity(comparator, ComparatorBlockEntity.class).getOutputSignal();
            helper.assertTrue(before > 0, "comparator should read a full box, got " + before);
            Recolor.swap(helper.getLevel(), helper.absolutePos(BOX), BoxColor.GREEN);
            helper.runAfterDelay(5, () -> {
                int after = helper.getBlockEntity(comparator, ComparatorBlockEntity.class).getOutputSignal();
                helper.assertValueEqual(after, before, "comparator signal");
                helper.succeed();
            });
        });
    }

    /** A box that someone has open is not recolored (PRD §5.2 locking, §9). */
    @GameTest
    public void recolorRefusedWhileBoxIsOpen(GameTestHelper helper) {
        var box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        var player = helper.makeMockServerPlayerInLevel();
        // The test box is locked; the key is a diamond.
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        player.openMenu(box);
        helper.assertTrue(player.containerMenu instanceof net.minecraft.world.inventory.ShulkerBoxMenu, "box menu should be open");

        Recolor.Result result = Recolor.swap(helper.getLevel(), helper.absolutePos(BOX), BoxColor.RED);

        helper.assertValueEqual(result, Recolor.Result.IN_USE, "result");
        TestBoxes.assertLoaded(helper, BOX, BoxColor.UNDYED, Direction.UP);
        helper.succeed();
    }
}
