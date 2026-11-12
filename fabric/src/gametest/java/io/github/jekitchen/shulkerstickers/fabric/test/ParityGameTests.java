package io.github.jekitchen.shulkerstickers.fabric.test;

import com.mojang.authlib.GameProfile;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.PlayerPrefs;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import io.github.jekitchen.shulkerstickers.fabric.editor.BoxEdits;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fabric fixes from the cross-platform parity review (behavior must match Paper). */
public class ParityGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    /** A survival player whose outgoing packets can be read back. */
    private record Watched(ServerPlayer player, EmbeddedChannel channel) {
        List<SoundEvent> sounds() {
            List<SoundEvent> sounds = new ArrayList<>();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                if (message instanceof ClientboundSoundPacket packet) {
                    sounds.add(packet.getSound().value());
                }
            }
            return sounds;
        }
    }

    private static Watched watched(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "listener"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        BlockPos pos = helper.absolutePos(BOX);
        player.teleportTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        channel.outboundMessages().clear();
        return new Watched(player, channel);
    }

    // ---- #4: a color-only or no-op Save leaves an anvil name on the lid ----

    @GameTest
    public void colorOnlySaveOnAnAnvilNamedBoxWritesNoStickerKeys(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BoxEdits.setName(box, Component.literal("Ores"));
        ServerPlayer player = TestPlayers.survival(helper);
        EditorMenu menu = EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), Direction.NORTH, StickerConfig.DEFAULTS);

        menu.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, player);
        menu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);

        ShulkerBoxBlockEntity after = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertTrue(StickerData.rawValues(after).isEmpty(), "no sticker keys, was " + StickerData.rawValues(after));
        helper.succeed();
    }

    @GameTest
    public void noOpSaveOnAnAnvilNamedBoxWritesNoStickerKeys(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        BoxEdits.setName(box, Component.literal("Ores"));
        ServerPlayer player = TestPlayers.survival(helper);
        EditorMenu menu = EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), Direction.NORTH, StickerConfig.DEFAULTS);

        menu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, player);

        helper.assertTrue(StickerData.rawValues(box).isEmpty(), "no sticker keys, was " + StickerData.rawValues(box));
        helper.succeed();
    }

    // ---- #17: feedback sounds and hand swing ----

    @GameTest
    public void openingTheEditorPlaysTheShulkerOpenSound(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        Watched watched = watched(helper);

        EditorMenu.tryOpen(watched.player(), helper.getLevel(), helper.absolutePos(BOX), Direction.UP);

        helper.assertTrue(watched.sounds().contains(SoundEvents.SHULKER_BOX_OPEN), "open sound sent");
        helper.succeed();
    }

    @GameTest
    public void savingPlaysTheShulkerCloseSound(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        Watched watched = watched(helper);
        EditorMenu.tryOpen(watched.player(), helper.getLevel(), helper.absolutePos(BOX), Direction.UP);
        watched.sounds();

        watched.player().containerMenu.clicked(BoxColor.RED.ordinal(), 0, ContainerInput.PICKUP, watched.player());
        watched.player().containerMenu.clicked(EditorLayout.SAVE, 0, ContainerInput.PICKUP, watched.player());

        helper.assertTrue(watched.sounds().contains(SoundEvents.SHULKER_BOX_CLOSE), "close sound sent");
        helper.succeed();
    }

    @GameTest
    public void cancelPlaysTheShulkerCloseSound(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        Watched watched = watched(helper);
        EditorMenu.tryOpen(watched.player(), helper.getLevel(), helper.absolutePos(BOX), Direction.UP);
        watched.sounds();

        watched.player().containerMenu.clicked(EditorLayout.CANCEL, 0, ContainerInput.PICKUP, watched.player());

        helper.assertTrue(watched.sounds().contains(SoundEvents.SHULKER_BOX_CLOSE), "close sound sent");
        helper.succeed();
    }

    @GameTest
    public void theGestureSwingsTheHandForThePlayerToo(GameTestHelper helper) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        Watched watched = watched(helper);
        watched.player().setShiftKeyDown(true);
        BlockPos pos = helper.absolutePos(BOX);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        InteractionResult result = UseBlockCallback.EVENT.invoker()
                .interact(watched.player(), helper.getLevel(), InteractionHand.MAIN_HAND, hit);

        // A vanilla client doesn't predict this swing, so the server must send it back to the player too.
        helper.assertTrue(result instanceof InteractionResult.Success success
                && success.swingSource() == InteractionResult.SwingSource.SERVER_ONLY, "server-sent swing, was " + result);
        helper.succeed();
    }

    // ---- #18: first-run help is only marked as seen when it's actually shown ----

    @GameTest
    public void helpIsNotMarkedSeenWhileTheMessageIsTurnedOff(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper);
        StickerConfig off = ConfigLoader.parse("{\"help\": {\"first_place_message\": false}}").config();

        PlayerPrefs.onBoxPlaced(player, off);
        helper.assertTrue(!PlayerPrefs.get(player).helpSeen(), "still unseen while off");

        PlayerPrefs.onBoxPlaced(player, StickerConfig.DEFAULTS);
        helper.assertTrue(PlayerPrefs.get(player).helpSeen(), "seen once it's shown");
        helper.succeed();
    }

    // ---- Icon orientation on every face ----

    private static final float EPS = 1e-4f;

    private static void assertVector(GameTestHelper helper, Vector3f actual, Direction expected, String what) {
        Vector3f want = new Vector3f(expected.getStepX(), expected.getStepY(), expected.getStepZ());
        helper.assertTrue(actual.distance(want) < EPS, what + ": expected " + expected + " " + want + ", got " + actual);
    }

    @GameTest
    public void iconFacesOutwardAndReadsUprightOnEveryFace(GameTestHelper helper) {
        for (Direction face : Direction.values()) {
            for (int rot = 0; rot < 4; rot++) {
                Quaternionf q = StickerDisplays.faceRotation(face, rot);
                Vector3f normal = q.transform(new Vector3f(0, 0, 1));
                Vector3f up = q.transform(new Vector3f(0, 1, 0));
                String what = face + " rot " + rot;
                assertVector(helper, normal, face, what + " front");
                if (face.getAxis() == Direction.Axis.Y) {
                    // rot is the player's facing (2D data value); the image's top points that way.
                    assertVector(helper, up, Direction.from2DDataValue(rot), what + " image top");
                } else {
                    assertVector(helper, up, Direction.UP, what + " image top");
                }
            }
        }
        helper.succeed();
    }
}
