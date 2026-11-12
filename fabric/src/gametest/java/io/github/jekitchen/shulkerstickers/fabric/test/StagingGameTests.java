package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * AC-11: with a color, an icon and a name all staged, and every change priced, leaving the editor
 * any way other than Save (Cancel, Esc, disconnecting, walking out of reach) leaves the box
 * exactly as it was and charges nothing.
 */
public class StagingGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);
    private static final int LEVELS = 20;
    private static final int PAPER = 10;

    private static final StickerConfig PRICED = ConfigLoader.parse("{"
            + "\"color\":{\"cost\":{\"type\":\"xp_levels\",\"amount\":2}},"
            + "\"icon\":{\"cost\":{\"type\":\"item\",\"item\":\"minecraft:paper\",\"amount\":2}},"
            + "\"name\":{\"cost\":{\"type\":\"xp_levels\",\"amount\":1}}}").config();

    /**
     * Opens the editor on a loaded box (as the gesture does: takes the lock, then opens the menu)
     * and stages a new color, the off-hand item as the icon, and a new name.
     */
    private static ServerPlayer openAndStageEverything(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BlockPos pos = helper.absolutePos(BOX);
        ServerPlayer player = TestPlayers.survival(helper);
        player.teleportTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        player.setExperienceLevels(LEVELS);
        player.getInventory().setItem(3, new ItemStack(Items.PAPER, PAPER));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND)); // the box's key
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLOWSTONE, 3));

        helper.assertValueEqual(EditorLocks.tryAcquire(helper.getLevel(), pos, player), Optional.empty(), "lock");
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> EditorMenu.forBox(id, player, helper.getLevel(), pos, Direction.UP, PRICED),
                Component.literal("Edit")));
        EditorMenu menu = (EditorMenu) player.containerMenu;

        menu.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.USE_OFFHAND, 0, ContainerInput.PICKUP, player);
        menu.session().stageName("Renamed");

        var staged = menu.session().staged();
        helper.assertValueEqual(staged.color(), BoxColor.RED, "staged color");
        helper.assertValueEqual(staged.iconId(), Optional.of("minecraft:glowstone"), "staged icon");
        helper.assertValueEqual(staged.name(), Optional.of("Renamed"), "staged name");
        return player;
    }

    private static void assertNothingChangedOrCharged(GameTestHelper helper, ServerPlayer player) {
        TestBoxes.assertLoaded(helper, BOX, BoxColor.UNDYED, Direction.UP);
        helper.assertValueEqual(player.experienceLevel, LEVELS, "levels");
        helper.assertValueEqual(player.getInventory().countItem(Items.PAPER), PAPER, "paper");
        helper.assertValueEqual(player.getOffhandItem().getCount(), 3, "off-hand");
        helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), helper.absolutePos(BOX), player), "lock released");
    }

    private static void leaveThenCheck(GameTestHelper helper, Consumer<ServerPlayer> leave, boolean expectInventoryMenu) {
        ServerPlayer player = openAndStageEverything(helper);
        leave.accept(player);
        helper.runAfterDelay(3, () -> {
            if (expectInventoryMenu) {
                helper.assertTrue(player.containerMenu instanceof InventoryMenu, "editor closed, was " + player.containerMenu);
            }
            assertNothingChangedOrCharged(helper, player);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void cancelWithEverythingStagedChangesAndChargesNothing(GameTestHelper helper) {
        leaveThenCheck(helper, p -> p.containerMenu.clicked(EditorLayout.CANCEL, 0, ContainerInput.PICKUP, p), true);
    }

    @GameTest(maxTicks = 40)
    public void escWithEverythingStagedChangesAndChargesNothing(GameTestHelper helper) {
        // What the server does when the client presses Esc.
        leaveThenCheck(helper, ServerPlayer::doCloseContainer, true);
    }

    @GameTest(maxTicks = 40)
    public void disconnectWithEverythingStagedChangesAndChargesNothing(GameTestHelper helper) {
        leaveThenCheck(helper, p -> p.connection.onDisconnect(new DisconnectionDetails(Component.literal("bye"))), false);
    }

    @GameTest(maxTicks = 40)
    public void walkingOutOfReachWithEverythingStagedChangesAndChargesNothing(GameTestHelper helper) {
        leaveThenCheck(helper, p -> p.teleportTo(p.getX() + 40, p.getY(), p.getZ()), true);
    }

    /** The other half of AC-11: Save applies all three staged changes together, and charges for each. */
    @GameTest
    public void saveAppliesEverythingStagedAndChargesForEach(GameTestHelper helper) {
        ServerPlayer player = openAndStageEverything(helper);

        player.containerMenu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);

        helper.assertBlockState(BOX, s -> s.is(Boxes.block(BoxColor.RED)), s -> Component.literal("expected red, got " + s));
        ShulkerBoxBlockEntity box = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertValueEqual(box.getCustomName(), Component.literal("Renamed"), "name");
        helper.assertValueEqual(StickerData.read(box).flatMap(Sticker::iconId), Optional.of("minecraft:glowstone"), "icon");
        helper.assertValueEqual(player.experienceLevel, LEVELS - 3, "levels: 2 for color + 1 for name");
        helper.assertValueEqual(player.getInventory().countItem(Items.PAPER), PAPER - 2, "paper: 2 for the icon");
        helper.assertValueEqual(player.getOffhandItem().getCount(), 3, "off-hand unchanged");
        helper.succeed();
    }
}
