package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import io.github.jekitchen.shulkerstickers.paper.claims.ClaimCheck;
import io.github.jekitchen.shulkerstickers.paper.claims.ClaimTestPlaceEvent;
import io.github.jekitchen.shulkerstickers.paper.claims.Claims;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Owner decision 3: supported claim plugins are asked directly; other plugins only through the
 * opt-in test place event. The real plugins aren't available here, so stand-in checks are used.
 */
class ClaimsTest {

    private static final StickerConfig SIMULATE = ConfigLoader.parse(
            "{\"protection\":{\"simulate_place_event\":true}}").config();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Block box;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        var world = server.addSimpleWorld("world");
        for (String node : List.of(Permissions.COLOR, Permissions.ICON, Permissions.NAME)) {
            server.getPluginManager().addPermission(new Permission(node, PermissionDefault.TRUE));
        }
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        box = world.getBlockAt(0, 70, 0);
        box.setType(Material.SHULKER_BOX);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private record Fake(String plugin, boolean allow) implements ClaimCheck {
        @Override
        public boolean canBuild(Player p, Block b) {
            return allow;
        }
    }

    private Claims claims(ClaimCheck... checks) {
        return new Claims(List.of(checks), server.getPluginManager(), plugin.getLogger());
    }

    private Optional<String> denial(Claims claims, StickerConfig config) {
        return Access.denial(player, box, config, plugin.getLogger(), claims);
    }

    @Test
    void everyInstalledPluginMustAllowBuilding() {
        assertEquals(Optional.empty(), denial(claims(new Fake("GriefPrevention", true), new Fake("WorldGuard", true)),
                StickerConfig.DEFAULTS));
        assertEquals(Optional.of(TextKeys.DENY_PROTECTED),
                denial(claims(new Fake("GriefPrevention", true), new Fake("WorldGuard", false)), StickerConfig.DEFAULTS));
    }

    @Test
    void aCheckThatThrowsRefuses() {
        ClaimCheck broken = new ClaimCheck() {
            @Override
            public String plugin() {
                return "Lands";
            }

            @Override
            public boolean canBuild(Player p, Block b) {
                throw new IllegalStateException("API changed");
            }
        };
        assertEquals(Optional.of(TextKeys.DENY_PROTECTED), denial(claims(broken), StickerConfig.DEFAULTS));
    }

    @Test
    void testEventIsOffByDefault() {
        AtomicInteger seen = new AtomicInteger();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            void on(BlockPlaceEvent event) {
                seen.incrementAndGet();
                event.setCancelled(true);
            }
        }, plugin);
        assertEquals(Optional.empty(), denial(claims(), StickerConfig.DEFAULTS));
        assertEquals(0, seen.get(), "no event is fired unless the admin turns it on");
    }

    @Test
    void testEventLetsOtherPluginsRefuse() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            void on(BlockPlaceEvent event) {
                event.setCancelled(true); // an unsupported claim plugin
            }
        }, plugin);
        assertEquals(Optional.of(TextKeys.DENY_PROTECTED), denial(claims(), SIMULATE));
    }

    @Test
    void testEventAllowedWhenNobodyObjects() {
        assertEquals(Optional.empty(), denial(claims(), SIMULATE));
        assertEquals(Material.SHULKER_BOX, box.getType(), "nothing was placed or changed");
    }

    @Test
    void ourListenersIgnoreTheTestEvent() {
        Settings settings = Settings.of(StickerConfig.DEFAULTS);
        server.getPluginManager().registerEvents(new HelpListener(settings), plugin);
        server.getPluginManager().callEvent(new ClaimTestPlaceEvent(box, player));
        assertFalse(player.getPersistentDataContainer().has(PaperKeys.HELP_SEEN), "no first-placement tip");
        assertTrue(player.nextMessage() == null, "no chat message");
    }
}
