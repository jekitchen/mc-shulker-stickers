package io.github.jekitchen.shulkerstickers.fabric.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * Mock players in a chosen game mode. {@code GameTestHelper#makeMockServerPlayerInLevel} always
 * reports creative, which hides costs and game-mode rules.
 */
final class TestPlayers {

    private TestPlayers() {
    }

    /** A player whose outgoing packets can be read back, to check the messages it was sent. */
    record Watched(ServerPlayer player, EmbeddedChannel channel) {
        /** Every system message sent since the last call, as plain text; action-bar ones only if {@code overlay}. */
        List<String> messages(boolean overlay) {
            // During a server tick, packets are written without a flush until the tick ends.
            channel.flushOutbound();
            List<String> texts = new ArrayList<>();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                if (message instanceof ClientboundSystemChatPacket packet && packet.overlay() == overlay) {
                    texts.add(packet.content().getString());
                }
            }
            return texts;
        }

        /** Action-bar messages sent since the last call. */
        List<String> actionBar() {
            return messages(true);
        }
    }

    static ServerPlayer make(GameTestHelper helper, GameType mode, String name) {
        return watched(helper, mode, name).player();
    }

    static Watched watched(GameTestHelper helper, GameType mode, String name) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return mode;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // The test level's default mode is creative. Switch for real too, so abilities such as
        // infinite materials (which vanilla's anvil, cauldron and buckets check) match the mode.
        player.setGameMode(mode);
        channel.outboundMessages().clear();
        return new Watched(player, channel);
    }

    static ServerPlayer survival(GameTestHelper helper) {
        return make(helper, GameType.SURVIVAL, "survivor");
    }
}
