package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The rows of the PRD §6.2 persistence matrix not covered elsewhere, each driven by the real vanilla mechanic. */
public class PersistenceGameTests {

    private static final BlockPos BOX = new BlockPos(2, 2, 2);

    /** The one shulker box item entity in the test area. */
    private static ItemStack droppedBox(GameTestHelper helper) {
        List<ItemEntity> boxes = helper.getEntities(EntityTypes.ITEM).stream()
                .filter(e -> e.getItem().getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock)
                .toList();
        helper.assertValueEqual(boxes.size(), 1, "dropped shulker box item entities");
        return boxes.getFirst().getItem();
    }

    /** The sticker, name and contents all came along. */
    private static void assertStickered(GameTestHelper helper, ItemStack stack, String what) {
        helper.assertValueEqual(StickerData.read(stack), Optional.of(TestBoxes.STICKER), what + ": sticker");
        helper.assertValueEqual(stack.get(DataComponents.CUSTOM_NAME), TestBoxes.NAME, what + ": name");
        var contents = stack.get(DataComponents.CONTAINER);
        helper.assertTrue(contents != null, what + ": contents kept");
        List<ItemStack> expected = TestBoxes.contents();
        List<ItemStack> actual = contents.nonEmptyItemCopyStream().toList();
        helper.assertValueEqual(actual.size(), expected.size(), what + ": stacks in contents");
        for (int i = 0; i < expected.size(); i++) {
            helper.assertTrue(ItemStack.matches(actual.get(i), expected.get(i)), what + ": content " + i + " is " + actual.get(i));
        }
    }

    private static Optional<ItemStack> firstNonEmpty(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                return Optional.of(container.getItem(i));
            }
        }
        return Optional.empty();
    }

    // ---- Piston and explosion: the box drops as an item, data kept ----

    @GameTest(maxTicks = 60)
    public void boxDestroyedByAPistonDropsWithItsSticker(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.LIME, Direction.UP);
        BlockPos piston = BOX.west();
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));

        helper.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Boxes.block(BoxColor.LIME), BOX);
            ItemStack dropped = droppedBox(helper);
            helper.assertTrue(dropped.is(Boxes.item(BoxColor.LIME)), "color kept, was " + dropped);
            assertStickered(helper, dropped, "piston drop");
        });
    }

    @GameTest(maxTicks = 40)
    public void boxDestroyedByAnExplosionDropsWithItsSticker(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.ORANGE, Direction.UP);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(BOX));

        // TNT explosions have no drop decay by default, so the drop is certain.
        helper.getLevel().explode(null, center.x, center.y, center.z, 2.0f, Level.ExplosionInteraction.TNT);

        helper.assertBlockNotPresent(Boxes.block(BoxColor.ORANGE), BOX);
        ItemStack dropped = droppedBox(helper);
        helper.assertTrue(dropped.is(Boxes.item(BoxColor.ORANGE)), "color kept, was " + dropped);
        assertStickered(helper, dropped, "explosion drop");
        helper.succeed();
    }

    // ---- Hopper and dropper move the item with its components ----

    @GameTest(maxTicks = 60)
    public void hopperMovesAStickeredBoxWithItsSticker(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos hopperRel = chestRel.above();
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(hopperRel, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.getBlockEntity(hopperRel, HopperBlockEntity.class).setItem(0, TestBoxes.stickeredItem(BoxColor.BLUE));
        ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);

        helper.succeedWhen(() -> {
            ItemStack moved = firstNonEmpty(chest).orElseThrow(() -> helper.assertionException(Component.literal("not in the chest yet")));
            helper.assertTrue(moved.is(Boxes.item(BoxColor.BLUE)), "color kept, was " + moved);
            assertStickered(helper, moved, "hopper");
        });
    }

    @GameTest(maxTicks = 60)
    public void dropperPassesAStickeredBoxIntoAContainerWithItsSticker(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 1, 1);
        BlockPos dropperRel = chestRel.above();
        helper.setBlock(chestRel, Blocks.CHEST);
        helper.setBlock(dropperRel, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.DOWN));
        helper.getBlockEntity(dropperRel, DispenserBlockEntity.class).setItem(0, TestBoxes.stickeredItem(BoxColor.CYAN));
        ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);

        helper.setBlock(dropperRel.east(), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            ItemStack moved = firstNonEmpty(chest).orElseThrow(() -> helper.assertionException(Component.literal("not in the chest yet")));
            helper.assertTrue(moved.is(Boxes.item(BoxColor.CYAN)), "color kept, was " + moved);
            assertStickered(helper, moved, "dropper into chest");
        });
    }

    @GameTest(maxTicks = 60)
    public void dropperThrowsAStickeredBoxOutWithItsSticker(GameTestHelper helper) {
        BlockPos dropperRel = new BlockPos(1, 2, 1);
        helper.setBlock(dropperRel, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.getBlockEntity(dropperRel, DispenserBlockEntity.class).setItem(0, TestBoxes.stickeredItem(BoxColor.PINK));

        helper.setBlock(dropperRel.west(), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            ItemStack dropped = droppedBox(helper);
            helper.assertTrue(dropped.is(Boxes.item(BoxColor.PINK)), "color kept, was " + dropped);
            assertStickered(helper, dropped, "dropper into the world");
        });
    }

    // ---- Anvil rename, dye recipe, cauldron wash ----

    @GameTest
    public void anvilRenameKeepsTheSticker(GameTestHelper helper) {
        BlockPos anvil = new BlockPos(1, 1, 1);
        helper.setBlock(anvil, Blocks.ANVIL);
        ServerPlayer player = TestPlayers.survival(helper);
        player.setExperienceLevels(10);
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(anvil)));

        menu.getSlot(AnvilMenu.INPUT_SLOT).set(TestBoxes.stickeredItem(BoxColor.RED));
        menu.setItemName("Renamed");
        menu.clicked(AnvilMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);

        ItemStack taken = menu.getCarried();
        helper.assertTrue(taken.is(Boxes.item(BoxColor.RED)), "took the renamed box, carried " + taken);
        helper.assertValueEqual(taken.get(DataComponents.CUSTOM_NAME), Component.literal("Renamed"), "new name");
        helper.assertValueEqual(StickerData.read(taken), Optional.of(TestBoxes.STICKER), "sticker");
        helper.assertTrue(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty(), "input used up");
        helper.assertTrue(player.experienceLevel < 10, "the rename cost levels, so the real take path ran");
        menu.setCarried(ItemStack.EMPTY);
        helper.succeed();
    }

    @GameTest
    public void dyeRecipeKeepsTheSticker(GameTestHelper helper) {
        ItemStack box = TestBoxes.stickeredItem(BoxColor.UNDYED);
        CraftingInput input = CraftingInput.of(2, 1, List.of(box, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:yellow_dye")))));

        var recipe = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> helper.assertionException(Component.literal("no crafting recipe matched box + dye")));
        ItemStack dyed = recipe.value().assemble(input);

        helper.assertTrue(dyed.is(Boxes.item(BoxColor.YELLOW)), "dyed yellow, was " + dyed);
        assertStickered(helper, dyed, "dye recipe");
        helper.succeed();
    }

    @GameTest
    public void cauldronWashKeepsTheSticker(GameTestHelper helper) {
        BlockPos cauldronRel = new BlockPos(1, 1, 1);
        helper.setBlock(cauldronRel, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        BlockPos cauldron = helper.absolutePos(cauldronRel);
        ServerPlayer player = TestPlayers.survival(helper);
        player.teleportTo(cauldron.getX() + 2.5, cauldron.getY(), cauldron.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, TestBoxes.stickeredItem(BoxColor.PURPLE));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cauldron).add(0, 0.5, 0), Direction.UP, cauldron, false);

        InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);

        helper.assertTrue(result.consumesAction(), "the cauldron washed the box, result " + result);
        ItemStack washed = player.getMainHandItem();
        helper.assertTrue(washed.is(Items.SHULKER_BOX), "washed to undyed, was " + washed);
        assertStickered(helper, washed, "cauldron wash");
        helper.assertBlockProperty(cauldronRel, LayeredCauldronBlock.LEVEL, 2);
        helper.succeed();
    }
}
