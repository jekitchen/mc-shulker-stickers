package io.github.jekitchen.shulkerstickers.fabric.test;

import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.Cost;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import io.github.jekitchen.shulkerstickers.fabric.editor.BoxEdits;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import io.github.jekitchen.shulkerstickers.fabric.editor.Payments;
import io.github.jekitchen.shulkerstickers.fabric.editor.RenameMenu;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Regression tests for the Fabric code-review findings (data safety, rename screen, displays, Save). */
public class ReviewFixGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    private static void click(ServerPlayer player, int slot) {
        player.containerMenu.clicked(slot, 0, ContainerInput.PICKUP, player);
    }

    private static ServerPlayer playerNextTo(GameTestHelper helper, BlockPos absolute) {
        ServerPlayer player = TestPlayers.survival(helper);
        player.teleportTo(absolute.getX() + 2.5, absolute.getY(), absolute.getZ() + 0.5);
        return player;
    }

    // ---- 1. Unreadable sticker data is never erased ----

    @GameTest
    public void futureDataSurvivesAColorOnlySaveOnANamedBox(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BoxEdits.setName(box, Component.literal("Ores"));
        CompoundTag bukkit = new CompoundTag();
        bukkit.putInt("shulker_stickers:v", 99);
        bukkit.putString("shulker_stickers:icon_id", "minecraft:stone");
        bukkit.putString("shulker_stickers:anchor", "corner");
        bukkit.putString("shulker_stickers:icon_visual", "{\"future\":1}");
        CompoundTag root = new CompoundTag();
        root.put("PublicBukkitValues", bukkit);
        box.setComponents(DataComponentMap.builder().addAll(box.components())
                .set(DataComponents.CUSTOM_DATA, CustomData.of(root)).build());
        Map<String, Object> before = StickerData.rawValues(box);
        ServerPlayer player = TestPlayers.survival(helper);
        EditorMenu menu = EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), Direction.NORTH, StickerConfig.DEFAULTS);

        menu.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);

        ShulkerBoxBlockEntity after = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertTrue(!after.getBlockState().is(Blocks.SHULKER_BOX), "recolored");
        helper.assertValueEqual(StickerData.rawValues(after), before, "raw sticker keys");
        helper.succeed();
    }

    // ---- 2. Rename screen exits ----

    @GameTest(maxTicks = 40)
    public void escOnTheRenameScreenReturnsToAWorkingEditor(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer player = playerNextTo(helper, pos);
        helper.assertTrue(EditorMenu.tryOpen(player, helper.getLevel(), pos, Direction.UP), "editor opens");
        click(player, EditorLayout.RENAME);
        helper.assertTrue(player.containerMenu instanceof RenameMenu, "rename screen open");

        player.doCloseContainer(); // what the server does when the client presses Esc

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(player.containerMenu instanceof EditorMenu, "back in the editor, was " + player.containerMenu);
            click(player, BoxColor.RED.ordinal());
            helper.assertValueEqual(((EditorMenu) player.containerMenu).session().staged().color(), BoxColor.RED, "editor still works");
            helper.assertTrue(EditorLocks.isHeldBy(helper.getLevel(), pos, player), "lock still held");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void confirmingARenameStagesTheName(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer player = playerNextTo(helper, pos);
        EditorMenu.tryOpen(player, helper.getLevel(), pos, Direction.UP);
        click(player, EditorLayout.RENAME);
        RenameMenu rename = (RenameMenu) player.containerMenu;

        rename.setItemName("Ores");
        click(player, AnvilMenu.RESULT_SLOT);

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(player.containerMenu instanceof EditorMenu, "back in the editor, was " + player.containerMenu);
            helper.assertValueEqual(((EditorMenu) player.containerMenu).session().staged().name(), Optional.of("Ores"), "staged name");
            helper.assertValueEqual(player.getInventory().countItem(Items.PAPER), 0, "no paper given");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void disconnectingDuringRenameReleasesTheLockAndGivesNoItem(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer player = playerNextTo(helper, pos);
        EditorMenu.tryOpen(player, helper.getLevel(), pos, Direction.UP);
        click(player, EditorLayout.RENAME);

        player.connection.onDisconnect(new DisconnectionDetails(Component.literal("bye")));

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), pos, player), "lock released");
            helper.assertValueEqual(player.getInventory().countItem(Items.PAPER), 0, "no paper given");
            helper.succeed();
        });
    }

    // ---- 3. The rename text goes through vanilla's filter ----

    @GameTest
    public void renameTextIsFilteredAndThePreviewIsCurrent(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper);
        AtomicReference<String> confirmed = new AtomicReference<>();
        RenameMenu.open(player, "", confirmed::set, () -> { });
        RenameMenu rename = (RenameMenu) player.containerMenu;

        rename.setItemName("b§rad\u0007");

        helper.assertValueEqual(rename.getSlot(AnvilMenu.RESULT_SLOT).getItem().get(DataComponents.CUSTOM_NAME),
                Component.literal("brad"), "preview shows the filtered text");
        click(player, AnvilMenu.RESULT_SLOT);
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(confirmed.get(), "brad", "confirmed name");
            helper.succeed();
        });
    }

    // ---- 4. Displays belong to the box's own chunk ----

    @GameTest
    public void displayAtAChunkEdgeAttachesToTheBoxChunk(GameTestHelper helper) {
        BlockPos rel = null;
        for (int x = 0; x < 16 && rel == null; x++) {
            BlockPos candidate = new BlockPos(x, 1, 1);
            if ((helper.absolutePos(candidate).getX() & 15) == 15) {
                rel = candidate;
            }
        }
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, rel, BoxColor.UNDYED, Direction.UP);
        StickerData.write(box, Optional.of(new Sticker(Optional.of("minecraft:cobblestone"), Optional.empty(),
                Anchor.SIDE, 0, Optional.of(Face.EAST))));
        BlockPos pos = helper.absolutePos(rel);

        StickerDisplays.refresh(helper.getLevel(), pos);

        var attachment = StickerDisplays.holder(helper.getLevel(), pos).orElseThrow().getAttachment();
        helper.assertTrue(attachment instanceof ChunkAttachment, "chunk attachment");
        helper.assertValueEqual(((ChunkAttachment) attachment).getChunk().getPos(), ChunkPos.containing(pos), "owning chunk");
        helper.assertTrue(attachment.getPos().x > pos.getX() + 1, "display still sits outside the east face");
        StickerDisplays.remove(helper.getLevel(), pos);
        helper.succeed();
    }

    // ---- 5. Reload redraws boxes that weren't drawn ----

    @GameTest
    public void refreshAllDrawsBoxesThatWereNotDrawnBefore(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        BoxEdits.setName(box, Component.literal("Ores")); // no refresh: like a name turned on by a reload
        helper.assertTrue(!StickerDisplays.isShown(helper.getLevel(), pos), "not drawn yet");

        StickerDisplays.refreshAll(helper.getLevel());

        helper.assertTrue(StickerDisplays.isShown(helper.getLevel(), pos), "drawn after refreshAll");
        StickerDisplays.remove(helper.getLevel(), pos);
        helper.succeed();
    }

    // ---- 6. Save only touches the box the editor opened on ----

    @GameTest
    public void saveIsRefusedWhenTheBoxWasReplacedBySameColoredBox(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        TestPlayers.Watched watched = TestPlayers.watched(helper, GameType.SURVIVAL, "survivor");
        ServerPlayer player = watched.player();
        player.teleportTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        player.giveExperienceLevels(5);
        StickerConfig priced = ConfigLoader.parse("{\"color\":{\"cost\":{\"type\":\"xp_levels\",\"amount\":1}}}").config();
        EditorLocks.tryAcquire(helper.getLevel(), pos, player);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> EditorMenu.forBox(id, player, helper.getLevel(), pos, Direction.UP, priced),
                Component.literal("Edit")));
        helper.assertTrue(player.containerMenu instanceof EditorMenu, "editor open");
        click(player, BoxColor.RED.ordinal());

        helper.setBlock(BOX, Blocks.AIR);
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        watched.actionBar();
        click(player, EditorLayout.SAVE);

        helper.assertBlockPresent(Blocks.SHULKER_BOX, BOX);
        helper.assertValueEqual(player.experienceLevel, 5, "nothing charged");
        List<String> messages = watched.actionBar();
        helper.assertTrue(messages.contains("The box was moved or broken; nothing was changed"), "box-gone message, got " + messages);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "editor closed, was " + player.containerMenu);
        helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), pos, player), "editor lock released");
        helper.succeed();
    }

    // ---- Minor 7: the off-hand block isn't placed by the gesture ----

    @GameTest
    public void theOffHandBlockIsNotPlacedByTheGesture(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer player = playerNextTo(helper, pos);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);

        // A vanilla client sends the main-hand use, then (because its main hand did nothing) the off-hand use.
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getOffhandItem(), InteractionHand.OFF_HAND, hit);

        helper.assertBlockNotPresent(Blocks.STONE, BOX.above());
        helper.assertValueEqual(player.getOffhandItem().getCount(), 1, "off-hand unchanged");
        helper.succeed();
    }

    // ---- Minor 10: all editing off means vanilla behavior ----

    @GameTest
    public void editorIsOffWhenEveryFeatureIsOff(GameTestHelper helper) {
        helper.assertTrue(EditorMenu.anyEditingEnabled(StickerConfig.DEFAULTS), "defaults");
        helper.assertTrue(!EditorMenu.anyEditingEnabled(ConfigLoader.parse(
                "{\"color\":{\"enabled\":false},\"icon\":{\"enabled\":false},\"name\":{\"edit_enabled\":false}}").config()), "all off");
        helper.succeed();
    }

    // ---- Minor 11: item costs take only plain items from the main inventory ----

    @GameTest
    public void itemCostsTakeOnlyPlainItemsFromTheMainInventory(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper);
        ItemStack named = new ItemStack(Items.PAPER);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Deed"));
        player.getInventory().setItem(0, named);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.PAPER));
        List<Cost> cost = List.of(new Cost(Cost.Type.ITEM, 1, "minecraft:paper"));

        helper.assertTrue(!Payments.canPay(player, cost), "named and off-hand paper don't count");

        player.getInventory().setItem(5, new ItemStack(Items.PAPER));
        helper.assertTrue(Payments.canPay(player, cost), "plain paper counts");
        Payments.charge(player, cost);
        helper.assertTrue(player.getInventory().getItem(5).isEmpty(), "plain paper taken");
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 1, "named paper kept");
        helper.assertValueEqual(player.getOffhandItem().getCount(), 1, "off-hand kept");
        helper.succeed();
    }
}
