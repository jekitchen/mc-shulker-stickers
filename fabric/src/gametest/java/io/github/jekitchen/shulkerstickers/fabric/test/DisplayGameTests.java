package io.github.jekitchen.shulkerstickers.fabric.test;

import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.display.DisplayLayout;
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

    /**
     * A box that loads with its chunk is drawn at the end of the tick, not during the load (asking
     * for a chunk that is still loading hung the server; scripts/fabric-smoke.sh restarts a real
     * server to check that). Here: a deferred box is drawn by the next level tick.
     */
    @GameTest
    public void boxDeferredWhileItsChunkLoadsIsDrawnNextTick(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerLevel level = helper.getLevel();
        StickerDisplays.remove(level, box.getBlockPos());
        StickerDisplays.drawLater(level, box);
        helper.assertFalse(StickerDisplays.isShown(level, box.getBlockPos()), "not drawn yet");
        // Drawn at the end of a level tick; where that falls relative to this test's ticks varies.
        helper.succeedWhen(() -> {
            List<VirtualElement> elements = elements(helper, BOX);
            helper.assertValueEqual(count(elements, ItemDisplayElement.class), 1L, "icons");
            helper.assertValueEqual(count(elements, TextDisplayElement.class), 1L, "names");
        });
    }

    /**
     * A deferred box that is gone by its turn is never drawn. It is queued after it was broken,
     * so the unload cleanup can't take it off the queue first: the end-of-tick pass itself must
     * skip it.
     */
    @GameTest
    public void deferredBoxBrokenBeforeItsTurnIsNotDrawn(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerLevel level = helper.getLevel();
        StickerDisplays.remove(level, box.getBlockPos());
        helper.setBlock(BOX, Blocks.AIR);
        StickerDisplays.drawLater(level, box);
        // Several ticks, so the end-of-tick pass has certainly run.
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(StickerDisplays.isShown(level, box.getBlockPos()), "nothing drawn for a broken box");
            helper.succeed();
        });
    }

    /**
     * PRD §6.2 "/clone, structure blocks, WorldEdit": a copy keeps its sticker and is drawn right
     * away. (Copies create the box first and load its data after, so the load event alone would
     * see an empty box.)
     */
    @GameTest
    public void clonedBoxKeepsItsStickerAndIsDrawn(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(BOX);
        BlockPos to = helper.absolutePos(BOX.east(2));
        String command = "clone %d %d %d %d %d %d %d %d %d".formatted(
                from.getX(), from.getY(), from.getZ(), from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        try {
            level.getServer().getCommands().getDispatcher().execute(command,
                    level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
        helper.assertTrue(level.getBlockEntity(to) instanceof ShulkerBoxBlockEntity copy
                && StickerData.read(copy).equals(Optional.of(TestBoxes.STICKER)), "the copy keeps the sticker");
        // Drawn at the end of a level tick; where that falls relative to this test's ticks varies.
        helper.succeedWhen(() -> helper.assertValueEqual(
                count(elements(helper, BOX.east(2)), ItemDisplayElement.class), 1L, "icons on the copy"));
    }

    /**
     * Security: a crafted name nesting translations (9 levels, each fallback repeating its argument
     * ten times) would expand to 10^9 characters through Component#getString and hang the server
     * whenever the box is drawn. Drawing it must be quick and the label short.
     */
    @GameTest
    public void aNameBombIsDrawnQuicklyAndShort(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        Component bomb = Component.literal("x");
        for (int level = 0; level < 9; level++) {
            bomb = Component.translatableWithFallback("nope." + level, "%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s", bomb);
        }
        // The bomb is real: three levels already expand to a thousand characters the vanilla way.
        Component small = Component.literal("x");
        for (int level = 0; level < 3; level++) {
            small = Component.translatableWithFallback("nope." + level, "%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s", small);
        }
        helper.assertValueEqual(small.getString().length(), 1000, "vanilla expansion of three levels");

        box.setComponents(net.minecraft.core.component.DataComponentMap.builder().addAll(box.components())
                .set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, bomb).build());
        long start = System.nanoTime();
        StickerDisplays.refresh(helper.getLevel(), helper.absolutePos(BOX));
        long millis = (System.nanoTime() - start) / 1_000_000;
        helper.assertTrue(millis < 500, "drawn in " + millis + " ms");
        TextDisplayElement label = (TextDisplayElement) elements(helper, BOX).stream()
                .filter(TextDisplayElement.class::isInstance).findFirst().orElseThrow();
        helper.assertTrue(label.getText().getString().length() <= 64, "label kept short: " + label.getText().getString().length());
        helper.succeed();
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
    public void sideDisplaysHideWhileTheBoxIsOpen(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        StickerData.write(box, Optional.of(new Sticker(Optional.of("minecraft:cobblestone"), Optional.empty(),
                Anchor.SIDE, 0, Optional.of(Face.SOUTH))));
        StickerDisplays.refresh(helper.getLevel(), pos);
        helper.assertFalse(StickerDisplays.isOnLid(helper.getLevel(), pos), "a side icon isn't on the lid");
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

    /**
     * PRD §5.5 (v0.2): the icon and name on the lid face move with the lid. Fully open, the lid
     * has risen 0.5 blocks along the facing and turned -270 degrees about it (vanilla 26.3), and
     * the displays follow; after closing they are back where they started.
     */
    @GameTest(maxTicks = 200)
    public void lidDisplaysMoveWithTheLid(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BOX);
        StickerDisplays.refresh(level, pos);
        helper.assertTrue(StickerDisplays.isOnLid(level, pos), "a lid icon is on the lid");
        // Looked up again at each check: other GameTests (a config reload) may redraw every box in
        // the shared level, which replaces the elements (and poses the new ones at the lid's progress).
        java.util.function.Supplier<ItemDisplayElement> iconNow = () -> (ItemDisplayElement) elements(helper, BOX).stream()
                .filter(ItemDisplayElement.class::isInstance).findFirst().orElseThrow();
        org.joml.Vector3f restTranslation = new org.joml.Vector3f(iconNow.get().getTranslation());
        org.joml.Quaternionf restRotation = new org.joml.Quaternionf(iconNow.get().getLeftRotation());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND)); // TestBoxes locks with a diamond
        player.teleportTo(pos.getX() - 1.5, pos.getY(), pos.getZ() + 0.5);
        player.openMenu(box);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(box.getProgress(1.0f) == 1.0f, "the box is fully open (" + box.getProgress(1.0f)
                    + ", " + box.getAnimationStatus() + ", menu " + player.containerMenu.getClass().getSimpleName()
                    + ", player removed " + player.isRemoved() + ")");
            helper.assertTrue(StickerDisplays.isShown(level, pos) && !StickerDisplays.isHidden(level, pos),
                    "lid displays stay drawn while the box is open");
            org.joml.Vector3f expectedTranslation = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-270))
                    .transform(new org.joml.Vector3f(0, (float) DisplayLayout.FACE_OFFSET, 0).add(restTranslation))
                    .sub(0, (float) DisplayLayout.FACE_OFFSET, 0).add(0, 0.5f, 0);
            ItemDisplayElement icon = iconNow.get();
            helper.assertTrue(expectedTranslation.distance(new org.joml.Vector3f(icon.getTranslation())) < 1e-4f,
                    "open: the icon rose and turned with the lid (" + icon.getTranslation() + ", expected " + expectedTranslation + ")");
            org.joml.Quaternionf expectedRotation = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-270)).mul(restRotation);
            helper.assertTrue(expectedRotation.equals(new org.joml.Quaternionf(icon.getLeftRotation()), 1e-4f),
                    "open: the icon turned -270 degrees about the facing");
            // The lid stopped moving a few ticks ago: the new pose must have gone out to players by
            // now. (A pose only set on the server, never sent, left real clients' icons in place.)
            helper.assertFalse(icon.isDirty(), "open: the icon's new pose was sent to players");
            player.closeContainer();
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(box.getProgress(1.0f) == 0.0f, "the box is closed again");
                ItemDisplayElement closed = iconNow.get();
                helper.assertTrue(restTranslation.distance(new org.joml.Vector3f(closed.getTranslation())) < 1e-4f
                        && restRotation.equals(new org.joml.Quaternionf(closed.getLeftRotation()), 1e-4f),
                        "closed: the icon is back at rest (" + closed.getTranslation() + ")");
                helper.assertFalse(closed.isDirty(), "closed: the icon's rest pose was sent to players");
                helper.succeed();
            });
        });
    }

    /**
     * The lid's first step reaches players in the same tick as vanilla's "lid starts moving" block
     * event, so the client moves both together from the first frame. Before, the pose was sent a
     * tick later (aimed one step ahead): for that first tick the lid moved while the icon sat still,
     * which the owner saw in game as the start being slightly out of step, opening and closing.
     */
    @GameTest(maxTicks = 60)
    public void lidDisplaysFirstStepIsSentWithTheLid(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BOX);
        StickerDisplays.refresh(level, pos);
        ItemDisplayElement icon = (ItemDisplayElement) elements(helper, BOX).stream()
                .filter(ItemDisplayElement.class::isInstance).findFirst().orElseThrow();
        org.joml.Vector3f rest = new org.joml.Vector3f(icon.getTranslation());
        org.joml.Quaternionf restRotation = new org.joml.Quaternionf(icon.getLeftRotation());
        org.joml.Vector3f up = new org.joml.Vector3f(0, 1, 0);
        org.joml.Vector3f stand = new org.joml.Vector3f(0, (float) DisplayLayout.FACE_OFFSET, 0);

        // One server tick of a box being opened, in vanilla's order: the block event, the box's own
        // tick (the lid moves to 0.1), then the end-of-tick hook.
        box.triggerEvent(1, 1);
        ShulkerBoxBlockEntity.tick(level, pos, box.getBlockState(), box);
        StickerDisplays.updateOpenBoxes(level);
        org.joml.Vector3f want = io.github.jekitchen.shulkerstickers.core.display.LidMotion
                .at(box.getProgress(1.0f), up, stand, rest, restRotation).translation();
        helper.assertTrue(want.distance(new org.joml.Vector3f(icon.getTranslation())) < 1e-4f,
                "opening: posed where the lid is now (" + icon.getTranslation() + ", lid at " + box.getProgress(1.0f) + ", expected " + want + ")");
        helper.assertFalse(icon.isDirty(), "opening: the first step was sent in the same tick");

        // Shut it again the same way: the first closing step goes out at once too.
        helper.runAfterDelay(15, () -> {
            box.triggerEvent(1, 0);
            ShulkerBoxBlockEntity.tick(level, pos, box.getBlockState(), box);
            StickerDisplays.updateOpenBoxes(level);
            // Looked up again: a config reload in another GameTest may have redrawn every box.
            ItemDisplayElement icon2 = (ItemDisplayElement) elements(helper, BOX).stream()
                    .filter(ItemDisplayElement.class::isInstance).findFirst().orElseThrow();
            org.joml.Vector3f closing = io.github.jekitchen.shulkerstickers.core.display.LidMotion
                    .at(box.getProgress(1.0f), up, stand, rest, restRotation).translation();
            helper.assertTrue(closing.distance(new org.joml.Vector3f(icon2.getTranslation())) < 1e-4f,
                    "closing: posed where the lid is now (" + icon2.getTranslation() + ", lid at " + box.getProgress(1.0f) + ")");
            helper.assertFalse(icon2.isDirty(), "closing: the first step was sent in the same tick");
            helper.runAfterDelay(15, helper::succeed);
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
