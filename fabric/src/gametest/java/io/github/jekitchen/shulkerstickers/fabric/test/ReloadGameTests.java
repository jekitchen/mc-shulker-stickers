package io.github.jekitchen.shulkerstickers.fabric.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.Settings;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * AC-7: a changed config file takes effect after {@code /shulkerstickers reload}, run through the
 * real command dispatcher. The whole test runs inside one tick and puts the original file back
 * before it returns, so no other test ever sees the changed settings.
 */
public class ReloadGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    private static int reload(MinecraftServer server) {
        try {
            return server.getCommands().getDispatcher().execute("shulkerstickers reload",
                    server.createCommandSourceStack().withSuppressedOutput());
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** An editor using the live settings, the same ones the gesture uses. */
    private static EditorMenu liveEditor(GameTestHelper helper, ServerPlayer player) {
        return EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), Direction.UP);
    }

    @GameTest
    public void reloadAppliesAChangedConfigFile(GameTestHelper helper) throws IOException {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.survival(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Path file = Settings.file();
        byte[] original = Files.exists(file) ? Files.readAllBytes(file) : null;
        helper.assertTrue(Settings.config().colorEnabled(), "color editing starts on");

        try {
            Files.writeString(file, "{\"color\":{\"enabled\":false},\"name\":{\"blocked_words\":[\"bad\"]}}", StandardCharsets.UTF_8);
            helper.assertValueEqual(reload(server), 1, "reload result (1 = no warnings)");

            helper.assertTrue(!Settings.config().colorEnabled(), "color.enabled read from the file");
            EditorMenu noColor = liveEditor(helper, player);
            noColor.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, player);
            helper.assertValueEqual(noColor.session().staged().color(), BoxColor.UNDYED, "color button does nothing after reload");

            EditorMenu blocked = liveEditor(helper, player);
            blocked.session().stageName("A bad name");
            blocked.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);
            helper.assertTrue(helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class).getCustomName() == null,
                    "name.blocked_words read from the file: the blocked name is refused");
        } finally {
            try {
                if (original != null) {
                    Files.write(file, original);
                } else {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            reload(server);
        }

        helper.assertTrue(Settings.config().colorEnabled(), "original settings back after the second reload");
        EditorMenu restored = liveEditor(helper, player);
        restored.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, player);
        helper.assertValueEqual(restored.session().staged().color(), BoxColor.RED, "color button works again");
        helper.succeed();
    }
}
