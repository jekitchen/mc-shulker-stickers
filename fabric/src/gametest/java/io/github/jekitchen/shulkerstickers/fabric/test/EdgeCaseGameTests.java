package io.github.jekitchen.shulkerstickers.fabric.test;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import io.github.jekitchen.shulkerstickers.fabric.test.TestPlayers.Watched;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** PRD §9 edge cases not covered elsewhere, each driven end to end (gesture, editor, Save, displays). */
public class EdgeCaseGameTests {

    private static final BlockPos BOX = new BlockPos(2, 2, 2);

    // English texts from core/src/main/resources/shulker_stickers/lang/en_us.json.
    private static final String BOX_IN_USE = "Box is in use, so its color wasn't changed";
    private static final String BOX_GONE = "The box was moved or broken; nothing was changed";
    private static final String DISCARDED = "Changes discarded";
    private static final String LOCKED = "This box is locked";
    private static final String TOGGLED_OFF = "Sticker editor gesture turned off for you";

    /** Color 1 level, icon 2 levels, name 4 levels: what was charged shows which parts were. */
    private static final StickerConfig PRICED = ConfigLoader.parse("""
            {"color": {"cost": {"type": "xp_levels", "amount": 1}},
             "icon":  {"cost": {"type": "xp_levels", "amount": 2}},
             "name":  {"cost": {"type": "xp_levels", "amount": 4}}}
            """).config();

    private static int players;

    private static Watched player(GameTestHelper helper, BlockPos rel) {
        Watched watched = TestPlayers.watched(helper, GameType.SURVIVAL, "edge" + players++);
        BlockPos pos = helper.absolutePos(rel);
        watched.player().teleportTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        return watched;
    }

    private static void click(ServerPlayer player, int slot) {
        player.containerMenu.clicked(slot, 0, ContainerInput.PICKUP, player);
    }

    /** Opens a real editor (so closing it can be checked) with a chosen config, holding the box's editor lock. */
    private static EditorMenu openEditor(ServerPlayer player, ServerLevel level, BlockPos pos, Direction face, StickerConfig config) {
        EditorLocks.tryAcquire(level, pos, player);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> EditorMenu.forBox(id, player, level, pos, face, config),
                Component.literal("Edit")));
        return (EditorMenu) player.containerMenu;
    }

    /**
     * Sneak + right-click {@code face} of the box the way a vanilla client does it: for each hand
     * in turn, use the item on the block; stop if that did something or failed; otherwise use the
     * item in the air if the hand isn't empty.
     */
    private static InteractionResult gesture(GameTestHelper helper, ServerPlayer player, BlockPos rel, Direction face) {
        BlockPos pos = helper.absolutePos(rel);
        player.setShiftKeyDown(true);
        Vec3 at = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
        BlockHitResult hit = new BlockHitResult(at, face, pos, false);
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
        return last;
    }

    private static List<VirtualElement> elements(GameTestHelper helper, BlockPos rel) {
        return StickerDisplays.holder(helper.getLevel(), helper.absolutePos(rel)).map(ElementHolder::getElements).orElse(List.of());
    }

    private static long count(List<VirtualElement> elements, Class<?> type) {
        return elements.stream().filter(type::isInstance).count();
    }

    private static List<ItemStack> droppedBoxes(GameTestHelper helper) {
        return helper.getEntities(EntityTypes.ITEM).stream()
                .map(ItemEntity::getItem)
                .filter(s -> s.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock)
                .toList();
    }

    private static void assertClosed(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "editor closed, was " + player.containerMenu);
        helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), helper.absolutePos(rel), player), "editor lock released");
    }

    // ---- Someone has the box open at Save: color refused, icon and name applied ----

    @GameTest(maxTicks = 100)
    public void fullSaveWhileSomeoneHasTheBoxOpenAppliesAllButTheColor(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        viewer.teleportTo(pos.getX() - 1.5, pos.getY(), pos.getZ() + 0.5);
        viewer.openMenu(box);
        helper.assertTrue(viewer.containerMenu instanceof ShulkerBoxMenu, "the viewer has the box open");

        Watched editor = player(helper, BOX);
        ServerPlayer player = editor.player();
        player.giveExperienceLevels(10);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));
        EditorMenu menu = openEditor(player, level, pos, Direction.UP, PRICED);
        click(player, BoxColor.RED.ordinal());
        click(player, EditorLayout.USE_OFFHAND);
        menu.session().stageName("Ores");

        helper.startSequence()
                .thenIdle(3) // the viewer's lid is opening
                .thenExecute(() -> {
                    editor.actionBar();
                    helper.assertTrue(player.containerMenu == menu, "editor still open before Save, was " + player.containerMenu);
                    helper.assertTrue(menu.session().changes().color() && menu.session().changes().icon()
                            && menu.session().changes().name(), "color, icon and name staged: " + menu.session().changes());
                    click(player, EditorLayout.SAVE);

                    helper.assertBlockPresent(Blocks.SHULKER_BOX, BOX);
                    ShulkerBoxBlockEntity after = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
                    helper.assertTrue(after == box, "same box (not swapped for another color)");
                    helper.assertValueEqual(StickerData.read(after).flatMap(Sticker::iconId), Optional.of("minecraft:apple"), "icon applied");
                    helper.assertValueEqual(after.getCustomName(), Component.literal("Ores"), "name applied");
                    helper.assertValueEqual(player.experienceLevel, 10 - 2 - 4, "icon and name charged, color not");
                    List<String> messages = editor.actionBar();
                    helper.assertTrue(messages.contains(BOX_IN_USE), "in-use message, got " + messages);
                    assertClosed(helper, player, BOX);
                    helper.assertTrue(viewer.containerMenu instanceof ShulkerBoxMenu, "the viewer still has the box open");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(StickerDisplays.isHidden(level, pos), "displays hidden while the box is open");
                    helper.assertFalse(StickerDisplays.isShown(level, pos), "nothing drawn while the box is open");
                })
                .thenExecute(viewer::closeContainer)
                .thenWaitUntil(() -> {
                    List<VirtualElement> elements = elements(helper, BOX);
                    helper.assertValueEqual(count(elements, ItemDisplayElement.class), 1L, "icon drawn after the viewer closed");
                    helper.assertValueEqual(count(elements, TextDisplayElement.class), 1L, "name drawn after the viewer closed");
                })
                .thenSucceed();
    }

    // ---- Icon item no longer exists: nothing drawn, raw data kept ----

    private static final Sticker GONE_ICON = new Sticker(Optional.of("nomod:thing"), Optional.empty(), Anchor.LID, 3, Optional.empty());

    private static ShulkerBoxBlockEntity boxWithGoneIcon(GameTestHelper helper, BlockPos rel) {
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, rel, BoxColor.UNDYED, Direction.UP);
        StickerData.write(box, Optional.of(GONE_ICON));
        StickerDisplays.refresh(helper.getLevel(), helper.absolutePos(rel));
        return box;
    }

    @GameTest
    public void iconOfAMissingItemIsNotDrawnAndSurvivesARename(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = boxWithGoneIcon(helper, BOX);
        Map<String, Object> before = StickerData.rawValues(box);
        helper.assertValueEqual(before.get(StickerFormat.ICON_ID), "nomod:thing", "raw icon id stored");
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer player = player(helper, BOX).player();

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertFalse(StickerDisplays.isShown(level, pos), "nothing drawn for a missing item");
                    EditorMenu menu = openEditor(player, level, pos, Direction.UP, StickerConfig.DEFAULTS);
                    menu.session().stageName("Ores");
                    click(player, EditorLayout.SAVE);

                    ShulkerBoxBlockEntity after = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
                    helper.assertValueEqual(after.getCustomName(), Component.literal("Ores"), "renamed");
                    helper.assertValueEqual(StickerData.rawValues(after), before, "raw sticker data after a rename");
                    assertClosed(helper, player, BOX);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(count(elements(helper, BOX), TextDisplayElement.class), 1L, "name drawn"))
                .thenExecute(() -> helper.assertValueEqual(count(elements(helper, BOX), ItemDisplayElement.class), 0L, "icon still not drawn"))
                .thenSucceed();
    }

    @GameTest
    public void iconOfAMissingItemSurvivesARecolorBySave(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = boxWithGoneIcon(helper, BOX);
        Map<String, Object> before = StickerData.rawValues(box);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer player = player(helper, BOX).player();

        openEditor(player, level, pos, Direction.UP, StickerConfig.DEFAULTS);
        click(player, BoxColor.PURPLE.ordinal());
        click(player, EditorLayout.SAVE);

        helper.assertBlockPresent(Boxes.block(BoxColor.PURPLE), BOX);
        ShulkerBoxBlockEntity after = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertValueEqual(StickerData.rawValues(after), before, "raw sticker data after a recolor");
        assertClosed(helper, player, BOX);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertFalse(StickerDisplays.isShown(level, pos), "still nothing drawn"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void iconOfAMissingItemSurvivesBreakPickUpAndPlace(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = boxWithGoneIcon(helper, BOX);
        Map<String, Object> before = StickerData.rawValues(box);
        ServerLevel level = helper.getLevel();
        BlockPos placeRel = BOX.west(2); // the player stands east of the box
        helper.setBlock(placeRel.below(), Blocks.STONE);
        ServerPlayer player = player(helper, BOX).player();

        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(BOX)), "box broken");
        helper.assertBlockNotPresent(Blocks.SHULKER_BOX, BOX);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(droppedBoxes(helper).size(), 1, "dropped boxes"))
                .thenWaitUntil(() -> {
                    ItemEntity drop = helper.getEntities(EntityTypes.ITEM).getFirst();
                    drop.playerTouch(player); // refused until the pickup delay is over
                    helper.assertTrue(drop.isRemoved(), "picked up");
                })
                .thenExecute(() -> {
                    int slot = -1;
                    for (int i = 0; i < player.getInventory().getContainerSize() && slot < 0; i++) {
                        slot = player.getInventory().getItem(i).is(Items.SHULKER_BOX) ? i : -1;
                    }
                    ItemStack held = slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot);
                    helper.assertTrue(held.is(Items.SHULKER_BOX), "box in the inventory, slot " + slot);
                    helper.assertValueEqual(StickerData.read(held), Optional.of(GONE_ICON), "sticker on the item");
                    ItemStack picked = held.copy();
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.MAIN_HAND, picked);
                    player.setShiftKeyDown(false);
                    BlockPos floor = helper.absolutePos(placeRel.below());
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
                    InteractionResult result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    helper.assertTrue(result.consumesAction(), "placed, was " + result);

                    helper.assertBlockPresent(Blocks.SHULKER_BOX, placeRel);
                    ShulkerBoxBlockEntity placed = helper.getBlockEntity(placeRel, ShulkerBoxBlockEntity.class);
                    helper.assertValueEqual(StickerData.rawValues(placed), before, "raw sticker data after break, pick up and place");
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertFalse(StickerDisplays.isShown(level, helper.absolutePos(placeRel)), "nothing drawn"))
                .thenSucceed();
    }

    // ---- Box moved by a piston while the editor is open ----

    private static final BlockPos PUSHED = new BlockPos(3, 1, 2);
    private static final BlockPos PISTON = PUSHED.west();
    private static final BlockPos POWER = PISTON.west();

    /**
     * The piston push and the Save happen in the same tick, before the player's tick can close
     * the editor (as when the click arrives right after the push): Save itself must refuse.
     */
    @GameTest
    public void saveIsRefusedWhenAPistonPushedTheBoxAway(GameTestHelper helper) {
        TestBoxes.place(helper, PUSHED, BoxColor.UNDYED, Direction.UP);
        ServerLevel level = helper.getLevel();
        helper.setBlock(PISTON, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        Watched editor = player(helper, PUSHED);
        ServerPlayer player = editor.player();
        player.giveExperienceLevels(5);
        openEditor(player, level, helper.absolutePos(PUSHED), Direction.UP, PRICED);
        click(player, BoxColor.RED.ordinal());

        // Power the piston without telling it, then run its push right now instead of as a block event.
        level.setBlock(helper.absolutePos(POWER), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.getBlockState(PISTON).triggerEvent(level, helper.absolutePos(PISTON), 0, Direction.EAST.get3DDataValue());
        helper.assertBlockNotPresent(Blocks.SHULKER_BOX, PUSHED);
        editor.actionBar();

        click(player, EditorLayout.SAVE);

        List<String> messages = editor.actionBar();
        helper.assertTrue(messages.contains(BOX_GONE), "box-gone message, got " + messages);
        assertClosed(helper, player, PUSHED);
        helper.assertValueEqual(player.experienceLevel, 5, "nothing charged");
        List<ItemStack> drops = droppedBoxes(helper);
        helper.assertValueEqual(drops.size(), 1, "dropped boxes");
        helper.assertTrue(drops.getFirst().is(Items.SHULKER_BOX), "dropped box keeps its color, was " + drops.getFirst());
        helper.assertValueEqual(StickerData.read(drops.getFirst()), Optional.of(TestBoxes.STICKER), "dropped box keeps its sticker");
        helper.succeed();
    }

    /** The usual order: the push happens in a tick, and the editor closes before any Save can arrive. */
    @GameTest(maxTicks = 60)
    public void pistonPushClosesTheEditorWithAMessageAndChargesNothing(GameTestHelper helper) {
        TestBoxes.place(helper, PUSHED, BoxColor.UNDYED, Direction.UP);
        ServerLevel level = helper.getLevel();
        helper.setBlock(PISTON, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        Watched editor = player(helper, PUSHED);
        ServerPlayer player = editor.player();
        player.giveExperienceLevels(5);
        openEditor(player, level, helper.absolutePos(PUSHED), Direction.UP, PRICED);
        click(player, BoxColor.RED.ordinal());
        editor.actionBar();

        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Blocks.SHULKER_BOX, PUSHED);
            assertClosed(helper, player, PUSHED);
            List<String> messages = editor.actionBar();
            helper.assertTrue(messages.contains(DISCARDED), "discarded message, got " + messages);
            helper.assertValueEqual(player.experienceLevel, 5, "nothing charged");
            List<ItemStack> drops = droppedBoxes(helper);
            helper.assertValueEqual(drops.size(), 1, "dropped boxes");
            helper.assertTrue(drops.getFirst().is(Items.SHULKER_BOX), "dropped box keeps its color, was " + drops.getFirst());
            helper.assertValueEqual(StickerData.read(drops.getFirst()), Optional.of(TestBoxes.STICKER), "dropped box keeps its sticker");
        });
    }

    // ---- Locked box at the gesture ----

    @GameTest
    public void lockedBoxOpensWithTheKeyInTheOffHandAndOffersOnlyTheKey(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP); // locked with a diamond
        ServerPlayer player = player(helper, BOX).player();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND));

        gesture(helper, player, BOX, Direction.UP);

        helper.assertTrue(player.containerMenu instanceof EditorMenu, "editor opens, was " + player.containerMenu);
        EditorMenu menu = (EditorMenu) player.containerMenu;
        ItemStack offered = menu.getSlot(EditorLayout.USE_OFFHAND).getItem();
        helper.assertTrue(offered.is(Items.DIAMOND), "the key is what's offered, was " + offered);
        click(player, EditorLayout.USE_OFFHAND);
        helper.assertValueEqual(menu.session().staged().iconId(), Optional.of("minecraft:diamond"), "the key is staged");
        click(player, EditorLayout.SAVE);

        ShulkerBoxBlockEntity box = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertValueEqual(StickerData.read(box).flatMap(Sticker::iconId), Optional.of("minecraft:diamond"), "icon saved");
        helper.assertValueEqual(box.collectComponents().get(DataComponents.LOCK), TestBoxes.lock(helper), "lock kept");
        helper.assertTrue(ItemStack.matches(player.getOffhandItem(), new ItemStack(Items.DIAMOND)), "key kept");
        assertClosed(helper, player, BOX);
        helper.succeed();
    }

    @GameTest
    public void lockedBoxRefusesTheGestureWithTheWrongItem(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP); // locked with a diamond
        Watched watched = player(helper, BOX);
        ServerPlayer player = watched.player();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));

        gesture(helper, player, BOX, Direction.UP);

        helper.assertFalse(player.containerMenu instanceof EditorMenu, "editor refused");
        List<String> messages = watched.actionBar();
        helper.assertTrue(messages.contains(LOCKED), "locked message, got " + messages);
        helper.assertBlockNotPresent(Blocks.STONE, BOX.above());
        helper.assertValueEqual(player.getOffhandItem().getCount(), 1, "off-hand unchanged");
        helper.assertFalse(EditorLocks.isHeldBy(helper.getLevel(), helper.absolutePos(BOX), player), "no editor lock taken");
        TestBoxes.assertLoaded(helper, BOX, BoxColor.UNDYED, Direction.UP);
        helper.succeed();
    }

    // ---- /shulkerstickers toggle restores vanilla ----

    @GameTest
    public void toggledOffTheGesturePlacesTheOffHandBlock(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        Watched watched = player(helper, BOX);
        ServerPlayer player = watched.player();
        helper.getLevel().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "shulkerstickers toggle");
        List<String> chat = watched.messages(false);
        helper.assertTrue(chat.contains(TOGGLED_OFF), "toggle message, got " + chat);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));

        gesture(helper, player, BOX, Direction.UP);

        helper.assertBlockPresent(Blocks.STONE, BOX.above());
        helper.assertTrue(player.getOffhandItem().isEmpty(), "stone used, off-hand " + player.getOffhandItem());
        helper.assertFalse(player.containerMenu instanceof EditorMenu, "editor not opened");
        helper.assertFalse(EditorLocks.isHeldBy(helper.getLevel(), helper.absolutePos(BOX), player), "no editor lock taken");
        helper.succeed();
    }

    // ---- Shield or totem in the off-hand ----

    private static void offHandIsLeftAloneUntilUsed(GameTestHelper helper, Item item) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = player(helper, BOX).player();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));

        gesture(helper, player, BOX, Direction.UP);

        helper.assertTrue(player.containerMenu instanceof EditorMenu, "editor opens, was " + player.containerMenu);
        EditorMenu menu = (EditorMenu) player.containerMenu;
        helper.assertFalse(player.isUsingItem(), "off-hand item not used (shield not raised)");
        helper.assertTrue(ItemStack.matches(player.getOffhandItem(), new ItemStack(item)), "off-hand unchanged: " + player.getOffhandItem());
        helper.assertValueEqual(menu.session().staged().iconId(), Optional.<String>empty(), "nothing staged by the gesture");
        helper.assertFalse(menu.session().isDirty(), "no changes staged");

        click(player, EditorLayout.USE_OFFHAND);

        helper.assertValueEqual(menu.session().staged().iconId(), Optional.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString()),
                "staged when clicked");
        helper.assertTrue(ItemStack.matches(player.getOffhandItem(), new ItemStack(item)), "off-hand still unchanged");
        player.closeContainer();
        helper.succeed();
    }

    @GameTest
    public void shieldInTheOffHandIsNotRaisedOrStagedByTheGesture(GameTestHelper helper) {
        offHandIsLeftAloneUntilUsed(helper, Items.SHIELD);
    }

    @GameTest
    public void totemInTheOffHandIsNotUsedOrStagedByTheGesture(GameTestHelper helper) {
        offHandIsLeftAloneUntilUsed(helper, Items.TOTEM_OF_UNDYING);
    }

    // ---- Box facing down or sideways: the icon goes on the clicked face ----

    private static void iconGoesOnTheClickedFace(GameTestHelper helper, Direction facing, Direction clicked) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, facing);
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer player = player(helper, BOX).player();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));

        gesture(helper, player, BOX, clicked);
        helper.assertTrue(player.containerMenu instanceof EditorMenu, "editor opens, was " + player.containerMenu);
        click(player, EditorLayout.USE_OFFHAND);
        click(player, EditorLayout.SAVE);

        Sticker sticker = StickerData.read(helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class)).orElseThrow();
        helper.assertValueEqual(sticker.anchor(), Anchor.SIDE, "anchor");
        helper.assertValueEqual(sticker.face(), Optional.of(Boxes.face(clicked)), "stored face");
        Vec3 expected = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(clicked.getUnitVec3i()).scale(0.51));
        helper.succeedWhen(() -> {
            ElementHolder holder = StickerDisplays.holder(helper.getLevel(), pos)
                    .orElseThrow(() -> helper.assertionException(Component.literal("not drawn yet")));
            helper.assertValueEqual(count(holder.getElements(), ItemDisplayElement.class), 1L, "icons");
            Vec3 at = holder.getAttachment().getPos();
            helper.assertTrue(at.distanceTo(expected) < 1.0e-6, "display on the " + clicked + " face: at " + at + ", expected " + expected);
        });
    }

    @GameTest
    public void boxFacingDownTakesTheIconOnTheClickedEastFace(GameTestHelper helper) {
        iconGoesOnTheClickedFace(helper, Direction.DOWN, Direction.EAST);
    }

    @GameTest
    public void boxFacingSidewaysTakesTheIconOnTheClickedTopFace(GameTestHelper helper) {
        iconGoesOnTheClickedFace(helper, Direction.SOUTH, Direction.UP);
    }
}
