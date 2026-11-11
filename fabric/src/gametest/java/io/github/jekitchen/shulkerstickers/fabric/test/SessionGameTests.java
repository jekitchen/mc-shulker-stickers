package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorLocks;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;

/** The editor session ends cleanly (PRD §9) and the commands are registered (§8). */
public class SessionGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    @GameTest(maxTicks = 40)
    public void walkingAwayClosesTheEditorAndReleasesTheLock(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.survival(helper);
        BlockPos pos = helper.absolutePos(BOX);
        player.teleportTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        helper.assertTrue(EditorMenu.tryOpen(player, helper.getLevel(), pos, Direction.UP), "editor should open");

        player.teleportTo(pos.getX() + 40.5, pos.getY(), pos.getZ() + 0.5);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(player.containerMenu instanceof InventoryMenu, "editor closed, was " + player.containerMenu);
            helper.assertTrue(!EditorLocks.isHeldBy(helper.getLevel(), pos, player), "lock released");
            helper.succeed();
        });
    }

    @GameTest
    public void helpCommandRuns(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput();

        int result = server.getCommands().getDispatcher().execute("shulkerstickers help", source);

        helper.assertValueEqual(result, 1, "help result");
        helper.succeed();
    }

    @GameTest
    public void adminCommandsNeedAdmin(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var node = dispatcher.getRoot().getChild("shulkerstickers");

        helper.assertTrue(node != null, "root command registered");
        helper.assertTrue(node.getChild("help").canUse(player.createCommandSourceStack()), "help for everyone");
        helper.assertTrue(!node.getChild("clear").canUse(player.createCommandSourceStack()), "clear needs admin");
        helper.assertTrue(node.getChild("reload").canUse(helper.getLevel().getServer().createCommandSourceStack()), "console is admin");
        helper.succeed();
    }
}
