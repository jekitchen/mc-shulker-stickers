package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import io.github.jekitchen.shulkerstickers.fabric.editor.RenameMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.GameType;

/**
 * The editor reopens a tick after the rename screen closes. Whatever happens in that tick must
 * not leak or steal the box's editor lock (regression review, minor finding).
 */
public class ReopenLockGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    private static ServerPlayer playerNextTo(GameTestHelper helper, BlockPos absolute, String name) {
        ServerPlayer player = TestPlayers.make(helper, GameType.SURVIVAL, name);
        player.teleportTo(absolute.getX() + 2.5, absolute.getY(), absolute.getZ() + 0.5);
        return player;
    }

    private static void click(ServerPlayer player, int slot) {
        player.containerMenu.clicked(slot, 0, ContainerInput.PICKUP, player);
    }

    /** Opens the editor, opens the rename screen and confirms a name; the reopen is now one tick away. */
    private static BlockPos confirmRename(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(BOX);
        helper.assertTrue(EditorMenu.tryOpen(player, helper.getLevel(), pos, Direction.UP), "editor opens");
        click(player, EditorLayout.RENAME);
        RenameMenu rename = (RenameMenu) player.containerMenu;
        rename.setItemName("Ores");
        click(player, AnvilMenu.RESULT_SLOT);
        return pos;
    }

    @GameTest(maxTicks = 40)
    public void sameBoxReopenedBeforeTheReopenTickKeepsItsLock(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer editor = playerNextTo(helper, helper.absolutePos(BOX), "editor");
        BlockPos pos = confirmRename(helper, editor);

        // A modified client re-sends the gesture before the scheduled reopen runs.
        helper.assertTrue(EditorMenu.tryOpen(editor, helper.getLevel(), pos, Direction.UP), "same player reopens");
        EditorMenu newer = (EditorMenu) editor.containerMenu;

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(editor.containerMenu == newer, "the newer editor stays open, was " + editor.containerMenu);
            helper.assertTrue(EditorLocks.isHeldBy(helper.getLevel(), pos, editor), "lock still held by the editor");
            ServerPlayer other = playerNextTo(helper, pos, "other");
            helper.assertTrue(!EditorMenu.tryOpen(other, helper.getLevel(), pos, Direction.UP), "second player refused");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void anotherMenuOpenedBeforeTheReopenTickIsNotStolen(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer editor = playerNextTo(helper, helper.absolutePos(BOX), "editor");
        BlockPos pos = confirmRename(helper, editor);

        editor.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory),
                Component.literal("chest")));
        var chest = editor.containerMenu;

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(editor.containerMenu == chest, "chest still open, was " + editor.containerMenu);
            helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), pos, editor), "lock released");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void disconnectBeforeTheReopenTickReleasesTheLock(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer editor = playerNextTo(helper, helper.absolutePos(BOX), "editor");
        BlockPos pos = confirmRename(helper, editor);

        editor.connection.onDisconnect(new DisconnectionDetails(Component.literal("bye")));

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), pos, editor), "lock released");
            helper.assertTrue(!(editor.containerMenu instanceof EditorMenu), "no editor reopened");
            helper.succeed();
        });
    }
}
