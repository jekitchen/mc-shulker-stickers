package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public class EditorMenuGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    private static ServerPlayer playerWithStuff(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 10));
        player.getInventory().setItem(5, new ItemStack(Items.DIAMOND, 3));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 7));
        // The test box is locked; its key is a diamond.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        return player;
    }

    private static List<ItemStack> snapshot(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            items.add(player.getInventory().getItem(i).copy());
        }
        return items;
    }

    private static void assertSame(GameTestHelper helper, List<ItemStack> before, ServerPlayer player, String what) {
        List<ItemStack> after = snapshot(player);
        for (int i = 0; i < before.size(); i++) {
            helper.assertTrue(ItemStack.matches(before.get(i), after.get(i)),
                    what + ": inventory slot " + i + " changed from " + before.get(i) + " to " + after.get(i));
        }
    }

    private static EditorMenu open(GameTestHelper helper, ServerPlayer player) {
        return EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), Direction.UP);
    }

    /** AC-12: no click type can move an item into or out of the editor. */
    @GameTest
    public void everyNonButtonClickIsRejected(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = playerWithStuff(helper);
        EditorMenu menu = open(helper, player);
        List<ItemStack> before = snapshot(player);
        List<ItemStack> menuBefore = menu.getItems().stream().map(ItemStack::copy).toList();

        int[] slots = {0, 17, 18, 19, 23, 24, 27, 30, 54, 62, -999};
        for (ContainerInput input : ContainerInput.values()) {
            for (int slot : slots) {
                for (int button : new int[] {0, 1, 2, 5, 8, 40}) {
                    menu.clicked(slot, button, input, player);
                    helper.assertTrue(menu.getCarried().isEmpty(), input + " slot " + slot + " button " + button + " left an item on the cursor");
                }
            }
        }

        assertSame(helper, before, player, "after clicks");
        for (int i = 0; i < 27; i++) {
            ItemStack now = menu.getSlot(i).getItem();
            helper.assertTrue(ItemStack.matches(now, menuBefore.get(i)) || menu.isButton(i),
                    "editor slot " + i + " changed: " + now);
        }
        helper.succeed();
    }

    @GameTest
    public void colorButtonStagesWithoutTouchingTheBox(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = playerWithStuff(helper);
        EditorMenu menu = open(helper, player);

        menu.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, player);

        helper.assertValueEqual(menu.session().staged().color(), BoxColor.RED, "staged color");
        TestBoxes.assertLoaded(helper, BOX, BoxColor.UNDYED, Direction.UP);
        helper.succeed();
    }

    /** AC-3 and AC-11: Save applies color and icon together; the off-hand stack is unchanged. */
    @GameTest
    public void saveAppliesColorAndOffHandIcon(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = playerWithStuff(helper);
        EditorMenu menu = open(helper, player);
        List<ItemStack> before = snapshot(player);

        menu.clicked(BoxColor.PURPLE.ordinal(), 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.USE_OFFHAND, 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);

        helper.assertBlockState(BOX, s -> s.is(io.github.jekitchen.shulkerstickers.fabric.Boxes.block(BoxColor.PURPLE)),
                s -> net.minecraft.network.chat.Component.literal("expected purple, got " + s));
        ShulkerBoxBlockEntity box = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertValueEqual(StickerData.read(box).flatMap(Sticker::iconId), Optional.of("minecraft:cobblestone"), "icon");
        helper.assertValueEqual(StickerData.read(box).map(Sticker::anchor), Optional.of(Anchor.LID), "anchor");
        assertSame(helper, before, player, "after save");
        helper.succeed();
    }

    /** AC-11: Cancel leaves the box exactly as it was. */
    @GameTest
    public void cancelDiscardsStagedChanges(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = playerWithStuff(helper);
        EditorMenu menu = open(helper, player);

        menu.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.REMOVE_ICON, 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.CANCEL, 0, ContainerInput.PICKUP, player);

        TestBoxes.assertLoaded(helper, BOX, BoxColor.UNDYED, Direction.UP);
        helper.succeed();
    }

    @GameTest
    public void removeIconKeepsTheNamesFace(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = playerWithStuff(helper);
        EditorMenu menu = open(helper, player);

        menu.clicked(EditorLayout.REMOVE_ICON, 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);

        ShulkerBoxBlockEntity box = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        // The box still has a name, so the sticker keeps its face for the name but has no icon.
        helper.assertValueEqual(StickerData.read(box).flatMap(Sticker::iconId), Optional.empty(), "icon");
        helper.assertValueEqual(StickerData.read(box).map(Sticker::anchor), Optional.of(Anchor.LID), "anchor kept for the name");
        helper.succeed();
    }
}
