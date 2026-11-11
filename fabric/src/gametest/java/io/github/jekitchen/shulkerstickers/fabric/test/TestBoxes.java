package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.LockCode;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Builds a fully loaded test box: 27 distinct stacks, a name, a lock and a sticker. */
final class TestBoxes {

    static final Sticker STICKER = new Sticker(java.util.Optional.of("minecraft:cobblestone"), java.util.Optional.empty(), Anchor.LID, 2, java.util.Optional.empty());
    static final Component NAME = Component.literal("Ores");

    private TestBoxes() {
    }

    static List<ItemStack> contents() {
        List<ItemStack> items = new ArrayList<>();
        var all = BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).limit(27).toList();
        for (int i = 0; i < 27; i++) {
            items.add(new ItemStack(all.get(i), 1 + (i % 5)));
        }
        return items;
    }

    static LockCode lock(GameTestHelper helper) {
        var items = helper.getLevel().registryAccess().lookupOrThrow(Registries.ITEM);
        return new LockCode(ItemPredicate.Builder.item().of(items, Items.DIAMOND).build());
    }

    /** Places a loaded box at {@code rel} facing {@code facing} and returns its block entity. */
    static ShulkerBoxBlockEntity place(GameTestHelper helper, BlockPos rel, BoxColor color, Direction facing) {
        helper.setBlock(rel, Boxes.block(color).defaultBlockState().setValue(ShulkerBoxBlock.FACING, facing));
        ShulkerBoxBlockEntity box = helper.getBlockEntity(rel, ShulkerBoxBlockEntity.class);

        ItemStack stack = new ItemStack(Boxes.block(color));
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents()));
        stack.set(DataComponents.CUSTOM_NAME, NAME);
        stack.set(DataComponents.LOCK, lock(helper));
        box.applyComponentsFromItemStack(stack);
        StickerData.write(box, java.util.Optional.of(STICKER));
        box.setChanged();
        return box;
    }

    /** Places an empty, unlocked, unnamed box with no sticker. */
    static ShulkerBoxBlockEntity placePlain(GameTestHelper helper, BlockPos rel, BoxColor color, Direction facing) {
        helper.setBlock(rel, Boxes.block(color).defaultBlockState().setValue(ShulkerBoxBlock.FACING, facing));
        return helper.getBlockEntity(rel, ShulkerBoxBlockEntity.class);
    }

    static void assertLoaded(GameTestHelper helper, BlockPos rel, BoxColor color, Direction facing) {
        helper.assertBlockState(rel, s -> s.is(Boxes.block(color)), s -> Component.literal("expected " + color + " but was " + s));
        helper.assertBlockProperty(rel, ShulkerBoxBlock.FACING, facing);
        ShulkerBoxBlockEntity box = helper.getBlockEntity(rel, ShulkerBoxBlockEntity.class);
        List<ItemStack> expected = contents();
        for (int i = 0; i < 27; i++) {
            helper.assertTrue(ItemStack.matches(box.getItem(i), expected.get(i)), "slot " + i + " changed: " + box.getItem(i));
        }
        helper.assertValueEqual(box.getCustomName(), NAME, "name");
        helper.assertValueEqual(box.collectComponents().get(DataComponents.LOCK), lock(helper), "lock");
        helper.assertValueEqual(StickerData.read(box), java.util.Optional.of(STICKER), "sticker");
    }
}
