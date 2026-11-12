package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * AC-2 (release gate): saving a recolor while a hopper moves items out of or into the box
 * produces no duplicated or lost items. Every tick the recolor goes through the real editor
 * Save, so every hopper transfer lands on a tick that also recolors the box.
 */
public class HopperRecolorGameTests {

    private static final int TICKS = 60;

    /** Counts every item in the given containers plus any item entities in the test area. */
    private static Map<Item, Integer> count(GameTestHelper helper, Container... containers) {
        Map<Item, Integer> counts = new HashMap<>();
        for (Container container : containers) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }
        for (ItemEntity entity : helper.getEntities(EntityTypes.ITEM)) {
            counts.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
        }
        return counts;
    }

    private static Map<Item, Integer> count(List<ItemStack> stacks) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** Recolors the box through the editor's Save, as a player would. */
    private static void saveRecolor(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        BoxColor current = Boxes.color(helper.getBlockState(rel).getBlock()).orElseThrow();
        BoxColor next = BoxColor.values()[(current.ordinal() + 1) % BoxColor.values().length];
        EditorMenu menu = EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(rel), Direction.UP, StickerConfig.DEFAULTS);
        menu.clicked(next.ordinal(), 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);
        helper.assertBlockState(rel, s -> s.is(Boxes.block(next)), s -> Component.literal("Save should recolor to " + next + ", was " + s));
    }

    private static ServerPlayer keyHolder(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper);
        // The test box is locked; its key is a diamond.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        return player;
    }

    /** A hopper under the box pulls from it (and pushes into a chest) while the box is recolored every tick. */
    @GameTest(maxTicks = TICKS + 20)
    public void recolorWhileAHopperPullsFromTheBoxNeitherDuplicatesNorLoses(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos hopperRel = chestRel.above();
        BlockPos boxRel = hopperRel.above();
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(hopperRel, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        TestBoxes.place(helper, boxRel, BoxColor.UNDYED, Direction.UP);
        Map<Item, Integer> expected = count(TestBoxes.contents());
        ServerPlayer player = keyHolder(helper);
        HopperBlockEntity hopper = helper.getBlockEntity(hopperRel, HopperBlockEntity.class);
        ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);

        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ >= TICKS) {
                return;
            }
            // The hopper also pulls right before and right after the swap, in the same tick.
            HopperBlockEntity.suckInItems(helper.getLevel(), hopper);
            saveRecolor(helper, player, boxRel);
            HopperBlockEntity.suckInItems(helper.getLevel(), hopper);
        });
        helper.runAfterDelay(TICKS + 5, () -> {
            ShulkerBoxBlockEntity box = helper.getBlockEntity(boxRel, ShulkerBoxBlockEntity.class);
            Map<Item, Integer> actual = count(helper, box, hopper, chest);
            helper.assertValueEqual(actual, expected, "items in box + hopper + chest + dropped");
            helper.assertTrue(!chest.isEmpty(), "the hopper should have moved items on into the chest");
            helper.assertTrue(!box.isEmpty(), "the box should still hold items");
            helper.succeed();
        });
    }

    /** A hopper above the box feeds into it while the box is recolored every tick. */
    @GameTest(maxTicks = TICKS + 20)
    public void recolorWhileAHopperFeedsTheBoxNeitherDuplicatesNorLoses(GameTestHelper helper) {
        BlockPos boxRel = new BlockPos(1, 1, 1);
        BlockPos hopperRel = boxRel.above();
        ShulkerBoxBlockEntity placed = TestBoxes.place(helper, boxRel, BoxColor.UNDYED, Direction.UP);
        // Make room: empty the last ten slots so the hopper has somewhere to put things.
        for (int i = 17; i < 27; i++) {
            placed.setItem(i, ItemStack.EMPTY);
        }
        List<ItemStack> boxStart = TestBoxes.contents().subList(0, 17);
        helper.setBlock(hopperRel, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = helper.getBlockEntity(hopperRel, HopperBlockEntity.class);
        // One item per slot: a hopper moves one item every 8 ticks, so all five go in within TICKS.
        List<ItemStack> hopperStart = List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.EMERALD),
                new ItemStack(Items.REDSTONE), new ItemStack(Items.LAPIS_LAZULI), new ItemStack(Items.QUARTZ));
        for (int i = 0; i < hopperStart.size(); i++) {
            hopper.setItem(i, hopperStart.get(i).copy());
        }
        Map<Item, Integer> expected = count(boxStart);
        count(hopperStart).forEach((item, n) -> expected.merge(item, n, Integer::sum));
        ServerPlayer player = keyHolder(helper);

        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ < TICKS) {
                saveRecolor(helper, player, boxRel);
            }
        });
        helper.runAfterDelay(TICKS + 5, () -> {
            ShulkerBoxBlockEntity box = helper.getBlockEntity(boxRel, ShulkerBoxBlockEntity.class);
            Map<Item, Integer> actual = count(helper, box, hopper);
            helper.assertValueEqual(actual, expected, "items in box + hopper + dropped");
            helper.assertTrue(hopper.isEmpty(), "the hopper should have emptied into the box, still holds items");
            helper.succeed();
        });
    }
}
