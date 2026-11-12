package io.github.jekitchen.shulkerstickers.fabric.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
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

    static ServerPlayer make(GameTestHelper helper, GameType mode, String name) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return mode;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // The test level's default mode is creative. Switch for real too, so abilities such as
        // infinite materials (which vanilla's anvil, cauldron and buckets check) match the mode.
        player.setGameMode(mode);
        return player;
    }

    static ServerPlayer survival(GameTestHelper helper) {
        return make(helper, GameType.SURVIVAL, "survivor");
    }
}
