package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import io.github.jekitchen.shulkerstickers.fabric.Access;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.StickerCommands;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Who may edit, names, costs, feature toggles, locks and clearing (PRD §5, §6.3, §8, §9). */
public class EditRulesGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    private static StickerConfig config(String json) {
        return ConfigLoader.parse(json).config();
    }

    private static void click(EditorMenu menu, ServerPlayer player, int slot) {
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
    }

    private static EditorMenu editor(GameTestHelper helper, ServerPlayer player, Direction clicked, StickerConfig config) {
        return EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), clicked, config);
    }

    // ---- Access ----

    @GameTest
    public void adventureIsDeniedUnlessAllowed(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.make(helper, GameType.ADVENTURE, "adventurer");
        BlockPos pos = helper.absolutePos(BOX);

        helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), pos, StickerConfig.DEFAULTS),
                Optional.of(TextKeys.DENY_ADVENTURE), "default");
        helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), pos, config("{\"allow_adventure_mode\":true}")),
                Optional.empty(), "allowed");
        helper.succeed();
    }

    @GameTest
    public void spectatorIsAlwaysDenied(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.make(helper, GameType.SPECTATOR, "ghost");

        helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), helper.absolutePos(BOX), config("{\"allow_adventure_mode\":true}")),
                Optional.of(TextKeys.DENY_ADVENTURE), "spectator");
        helper.succeed();
    }

    @GameTest
    public void lockedBoxNeedsTheKeyInEitherHand(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.survival(helper);
        BlockPos pos = helper.absolutePos(BOX);

        helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), pos, StickerConfig.DEFAULTS),
                Optional.of(TextKeys.DENY_LOCKED), "no key");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND));
        helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), pos, StickerConfig.DEFAULTS),
                Optional.empty(), "key in off-hand");
        helper.succeed();
    }

    @GameTest
    public void saveIsRefusedWithoutAccess(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.survival(helper);
        EditorMenu menu = editor(helper, player, Direction.UP, StickerConfig.DEFAULTS);

        click(menu, player, BoxColor.RED.ordinal());
        click(menu, player, EditorLayout.SAVE);

        TestBoxes.assertLoaded(helper, BOX, BoxColor.UNDYED, Direction.UP);
        helper.succeed();
    }

    // ---- Locks ----

    @GameTest
    public void secondEditorIsToldWhoIsEditing(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer first = TestPlayers.make(helper, GameType.SURVIVAL, "Alex");
        ServerPlayer second = TestPlayers.make(helper, GameType.SURVIVAL, "Sam");
        BlockPos pos = helper.absolutePos(BOX);

        helper.assertValueEqual(EditorLocks.tryAcquire(helper.getLevel(), pos, first), Optional.empty(), "first");
        helper.assertValueEqual(EditorLocks.tryAcquire(helper.getLevel(), pos, second), Optional.of("Alex"), "second");
        helper.assertValueEqual(EditorLocks.tryAcquire(helper.getLevel(), pos, first), Optional.empty(), "first again");
        EditorLocks.release(helper.getLevel(), pos, first);
        helper.assertValueEqual(EditorLocks.tryAcquire(helper.getLevel(), pos, second), Optional.empty(), "after release");
        EditorLocks.releaseAll(second.getUUID());
        helper.succeed();
    }

    @GameTest
    public void openingTheEditorTakesTheLockAndClosingReleasesIt(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.survival(helper);
        BlockPos pos = helper.absolutePos(BOX);

        helper.assertTrue(EditorMenu.tryOpen(player, helper.getLevel(), pos, Direction.UP), "editor should open");
        helper.assertTrue(EditorLocks.isHeldBy(helper.getLevel(), pos, player), "lock taken");
        player.closeContainer();
        helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), pos, player), "lock released");
        helper.succeed();
    }

    // ---- Names ----

    @GameTest
    public void blockedNameRefusesSave(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        EditorMenu menu = editor(helper, player, Direction.UP, config("{\"name\":{\"blocked_words\":[\"bad\"]}}"));

        menu.session().stageName("My bad box");
        click(menu, player, EditorLayout.SAVE);

        helper.assertTrue(helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class).getCustomName() == null, "name should not be set");
        helper.succeed();
    }

    @GameTest
    public void nameOnlySaveRecordsTheClickedFace(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        EditorMenu menu = editor(helper, player, Direction.WEST, StickerConfig.DEFAULTS);

        menu.session().stageName("Tools");
        click(menu, player, EditorLayout.SAVE);

        ShulkerBoxBlockEntity box = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertValueEqual(box.getCustomName(), Component.literal("Tools"), "name");
        Sticker sticker = StickerData.read(box).orElseThrow();
        helper.assertValueEqual(sticker.iconId(), Optional.empty(), "icon");
        helper.assertValueEqual(sticker.anchor(), Anchor.SIDE, "anchor");
        helper.assertValueEqual(sticker.face(), Optional.of(Face.WEST), "face");
        helper.assertTrue(!StickerData.isNameHidden(box), "name shown");
        helper.succeed();
    }

    @GameTest
    public void removingNameAndIconRemovesTheSticker(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        EditorMenu menu = editor(helper, player, Direction.UP, StickerConfig.DEFAULTS);

        click(menu, player, EditorLayout.REMOVE_ICON);
        menu.session().stageName("");
        click(menu, player, EditorLayout.SAVE);

        ShulkerBoxBlockEntity box = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertTrue(box.getCustomName() == null, "name should be removed");
        helper.assertValueEqual(StickerData.read(box), Optional.empty(), "sticker");
        helper.assertValueEqual(StickerData.rawValues(box).size(), 0, "no keys left");
        helper.succeed();
    }

    // ---- Feature toggles and costs ----

    @GameTest
    public void disabledColorButtonDoesNothing(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        EditorMenu menu = editor(helper, player, Direction.UP, config("{\"color\":{\"enabled\":false}}"));

        click(menu, player, BoxColor.RED.ordinal());

        helper.assertValueEqual(menu.session().staged().color(), BoxColor.UNDYED, "staged color");
        helper.succeed();
    }

    @GameTest
    public void xpCostIsChargedOnlyWhenAffordable(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        StickerConfig priced = config("{\"color\":{\"cost\":{\"type\":\"xp_levels\",\"amount\":2}}}");
        ServerPlayer player = TestPlayers.survival(helper);
        player.setExperienceLevels(1);

        EditorMenu poor = editor(helper, player, Direction.UP, priced);
        click(poor, player, BoxColor.RED.ordinal());
        click(poor, player, EditorLayout.SAVE);
        helper.assertBlockState(BOX, s -> s.is(Boxes.block(BoxColor.UNDYED)), s -> Component.literal("should stay undyed"));
        helper.assertValueEqual(player.experienceLevel, 1, "levels after refusal");

        player.setExperienceLevels(5);
        EditorMenu rich = editor(helper, player, Direction.UP, priced);
        click(rich, player, BoxColor.RED.ordinal());
        click(rich, player, EditorLayout.SAVE);
        helper.assertBlockState(BOX, s -> s.is(Boxes.block(BoxColor.RED)), s -> Component.literal("should be red"));
        helper.assertValueEqual(player.experienceLevel, 3, "levels after paying");
        helper.succeed();
    }

    @GameTest
    public void itemCostTakesItemsAndOnlyForWhatChanged(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        StickerConfig priced = config("{\"icon\":{\"cost\":{\"type\":\"item\",\"item\":\"minecraft:paper\",\"amount\":2}},"
                + "\"color\":{\"cost\":{\"type\":\"xp_levels\",\"amount\":30}}}");
        ServerPlayer player = TestPlayers.survival(helper);
        player.getInventory().setItem(3, new ItemStack(Items.PAPER, 5));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE));

        EditorMenu menu = editor(helper, player, Direction.UP, priced);
        click(menu, player, EditorLayout.USE_OFFHAND);
        click(menu, player, EditorLayout.SAVE);

        helper.assertValueEqual(player.getInventory().getItem(3).getCount(), 3, "paper left");
        helper.assertValueEqual(StickerData.read(helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class)).flatMap(Sticker::iconId),
                Optional.of("minecraft:cobblestone"), "icon");
        helper.succeed();
    }

    @GameTest
    public void creativePlayersPayNothing(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setExperienceLevels(0);
        EditorMenu menu = editor(helper, player, Direction.UP, config("{\"color\":{\"cost\":{\"type\":\"xp_levels\",\"amount\":2}}}"));

        click(menu, player, BoxColor.BLUE.ordinal());
        click(menu, player, EditorLayout.SAVE);

        helper.assertBlockState(BOX, s -> s.is(Boxes.block(BoxColor.BLUE)), s -> Component.literal("should be blue"));
        helper.succeed();
    }

    // ---- Admin clear ----

    @GameTest
    public void clearIconKeepsTheName(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);

        StickerCommands.clear(box, StickerCommands.ClearWhat.ICON);

        helper.assertValueEqual(box.getCustomName(), TestBoxes.NAME, "name");
        helper.assertValueEqual(StickerData.read(box).flatMap(Sticker::iconId), Optional.empty(), "icon");
        helper.assertValueEqual(StickerData.read(box).map(Sticker::anchor), Optional.of(Anchor.LID), "face kept for name");
        helper.succeed();
    }

    @GameTest
    public void clearAllRemovesEverything(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);

        StickerCommands.clear(box, StickerCommands.ClearWhat.ALL);

        helper.assertTrue(box.getCustomName() == null, "name should be removed");
        helper.assertValueEqual(StickerData.rawValues(box).size(), 0, "sticker keys");
        helper.assertValueEqual(box.getItem(0).isEmpty(), false, "contents kept");
        helper.succeed();
    }
}
