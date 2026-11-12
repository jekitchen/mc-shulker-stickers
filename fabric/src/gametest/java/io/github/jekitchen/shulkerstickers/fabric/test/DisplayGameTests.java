package io.github.jekitchen.shulkerstickers.fabric.test;

import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.PlayerPrefs;
import io.github.jekitchen.shulkerstickers.fabric.StickerPlacement;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import io.github.jekitchen.shulkerstickers.fabric.editor.BoxEdits;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Drawing, hiding while open, placement and first-run help (PRD §5.4–§5.6, §6.1). */
public class DisplayGameTests {

    private static final BlockPos BOX = new BlockPos(2, 1, 2);

    private static List<VirtualElement> elements(GameTestHelper helper, BlockPos rel) {
        ServerLevel level = helper.getLevel();
        return StickerDisplays.holder(level, helper.absolutePos(rel)).map(h -> h.getElements()).orElse(List.of());
    }

    private static long count(List<VirtualElement> elements, Class<?> type) {
        return elements.stream().filter(type::isInstance).count();
    }

    @GameTest
    public void iconAndNameAreDrawn(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        StickerDisplays.refresh(helper.getLevel(), helper.absolutePos(BOX));

        List<VirtualElement> elements = elements(helper, BOX);
        helper.assertValueEqual(count(elements, ItemDisplayElement.class), 1L, "icons");
        helper.assertValueEqual(count(elements, TextDisplayElement.class), 1L, "names");
        helper.succeed();
    }

    @GameTest
    public void iconsOffHidesExistingIcons(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        var registries = helper.getLevel().registryAccess();
        StickerConfig off = ConfigLoader.parse("{\"icon\":{\"enabled\":false}}").config();
        helper.assertTrue(StickerDisplays.iconToDraw(StickerData.read(box), StickerConfig.DEFAULTS, registries).isPresent(),
                "icon drawn when enabled");
        helper.assertTrue(StickerDisplays.iconToDraw(StickerData.read(box), off, registries).isEmpty(),
                "icon hidden when icons are off");
        helper.assertValueEqual(StickerData.read(box).flatMap(Sticker::iconId), Optional.of("minecraft:cobblestone"), "data kept");
        helper.succeed();
    }

    @GameTest
    public void nameOnlyBoxDrawsItsName(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BoxEdits.setName(box, Component.literal("Wood"));
        StickerDisplays.refresh(helper.getLevel(), helper.absolutePos(BOX));

        List<VirtualElement> elements = elements(helper, BOX);
        helper.assertValueEqual(count(elements, ItemDisplayElement.class), 0L, "icons");
        helper.assertValueEqual(count(elements, TextDisplayElement.class), 1L, "names");
        helper.succeed();
    }

    @GameTest
    public void hiddenNameIsNotDrawn(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        StickerData.setNameHidden(box, true);
        StickerDisplays.refresh(helper.getLevel(), helper.absolutePos(BOX));

        List<VirtualElement> elements = elements(helper, BOX);
        helper.assertValueEqual(count(elements, TextDisplayElement.class), 0L, "names");
        helper.assertValueEqual(count(elements, ItemDisplayElement.class), 1L, "icons");
        helper.assertValueEqual(StickerData.read(box).flatMap(Sticker::iconId), Optional.of("minecraft:cobblestone"),
                "writing the flag keeps the sticker");
        helper.succeed();
    }

    @GameTest
    public void writingAStickerKeepsTheHiddenNameFlag(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        StickerData.setNameHidden(box, true);

        StickerData.write(box, Optional.of(TestBoxes.STICKER));

        helper.assertTrue(StickerData.isNameHidden(box), "flag kept");
        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void displaysHideWhileTheBoxIsOpen(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        StickerDisplays.refresh(helper.getLevel(), pos);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));

        BlockPos near = pos.east(2);
        player.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);

        player.openMenu(box);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(StickerDisplays.isHidden(helper.getLevel(), pos), "hidden while open (lid " + box.getAnimationStatus() + ", shown " + StickerDisplays.isShown(helper.getLevel(), pos) + ", menu " + player.containerMenu.getClass().getSimpleName() + ")");
            helper.assertTrue(!StickerDisplays.isShown(helper.getLevel(), pos), "not shown while open");
            player.closeContainer();
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(StickerDisplays.isShown(helper.getLevel(), pos), "shown again after closing");
                helper.succeed();
            });
        });
    }

    @GameTest
    public void dispensedBoxPutsSideIconTowardTheDispenser(GameTestHelper helper) {
        // Dispenser west of the box position, facing east; the box is placed facing up.
        BlockPos dispenser = BOX.west();
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        ItemStack stack = new ItemStack(Boxes.block(BoxColor.CYAN));
        ShulkerBoxBlockEntity source = TestBoxes.placePlain(helper, BOX.above(2), BoxColor.CYAN, Direction.UP);
        StickerData.write(source, Optional.of(new Sticker(Optional.of("minecraft:cobblestone"), Optional.empty(),
                Anchor.SIDE, 0, Optional.of(Face.SOUTH))));
        StickerData.copyToItem(source, stack);

        ((BlockItem) stack.getItem()).place(new DirectionalPlaceContext(helper.getLevel(), helper.absolutePos(BOX),
                Direction.EAST, stack, Direction.UP));

        ShulkerBoxBlockEntity placed = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        Sticker sticker = StickerData.read(placed).orElseThrow();
        helper.assertValueEqual(sticker.face(), Optional.of(Face.WEST), "side toward the dispenser");
        helper.assertTrue(StickerDisplays.isShown(helper.getLevel(), helper.absolutePos(BOX)), "drawn after placement");
        helper.succeed();
    }

    @GameTest
    public void playerPlacementPutsSideIconTowardThePlayerAndShowsHelpOnce(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        StickerData.write(box, Optional.of(new Sticker(Optional.of("minecraft:cobblestone"), Optional.empty(),
                Anchor.SIDE, 0, Optional.of(Face.WEST))));
        ServerPlayer player = TestPlayers.survival(helper);
        BlockPos east = helper.absolutePos(BOX).east(3);
        player.teleportTo(east.getX() + 0.5, east.getY(), east.getZ() + 0.5);
        helper.assertTrue(!PlayerPrefs.get(player).helpSeen(), "help not seen yet");

        StickerPlacement.onPlaced(helper.getLevel(), helper.absolutePos(BOX), player);

        helper.assertValueEqual(StickerData.read(box).flatMap(Sticker::face), Optional.of(Face.EAST), "side toward player");
        helper.assertTrue(PlayerPrefs.get(player).helpSeen(), "help marked as seen");
        helper.succeed();
    }

    @GameTest
    public void toggleFlipsTheGesture(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper);

        helper.assertTrue(PlayerPrefs.toggleGesture(player), "first toggle turns it off");
        helper.assertTrue(PlayerPrefs.gestureOff(player), "off");
        helper.assertTrue(!PlayerPrefs.toggleGesture(player), "second toggle turns it on");
        helper.succeed();
    }
}
