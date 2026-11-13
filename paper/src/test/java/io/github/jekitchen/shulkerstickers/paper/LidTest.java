package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import java.util.List;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Paper replays vanilla's lid timing (PRD §5.5, v0.2): 0.1 per tick, starting in the tick the box
 * opens or closes, never for spectators, and held up while another player still has it open.
 * MockBukkit can't run DisplayListener (no {@code Inventory#getHolder(false)}), so the timing goes
 * through DisplayManager here and the self-test drives the listener on a real server, where the
 * poses are checked too.
 */
class LidTest {

    private ServerMock server;
    private WorldMock world;
    private DisplayManager displays;
    private BlockKey key;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        Block box = world.getBlockAt(0, 70, 0);
        box.setType(Material.SHULKER_BOX);
        ShulkerBox state = (ShulkerBox) box.getState();
        StickerStore.write(state.getPersistentDataContainer(),
                new Sticker(Optional.of("minecraft:stone"), Optional.empty(), Anchor.LID, 0, Optional.empty()));
        state.update(true, false);
        displays = new DisplayManager(plugin, Settings.of(StickerConfig.DEFAULTS), (anchor, radius, vertical) -> List.of());
        world.loadChunk(0, 0);
        displays.refresh(box); // tracked (nobody is near, so nothing is drawn)
        key = BlockKey.of(box);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock viewer(GameMode mode) {
        PlayerMock player = server.addPlayer();
        player.setGameMode(mode);
        return player;
    }

    /**
     * A player opens or closes a box between ticks (packet handling). Vanilla starts the lid in the
     * next tick, and the scheduler runs at the start of that tick, before entity changes are sent:
     * so the first step is taken there, not at the event. Stepping at the event as well put the
     * displays a step ahead of the lid; closing, that is inside the lid (seen in game, 13 Nov).
     */
    @Test
    void theLidStepsOncePerTickStartingTheTickAfterTheEvent() {
        displays.opened(key);
        assertEquals(0, displays.lidTenths(key), "nothing moves at the event itself");
        server.getScheduler().performTicks(1);
        assertEquals(1, displays.lidTenths(key), "first step in the tick vanilla starts the lid");
        server.getScheduler().performTicks(9);
        assertEquals(10, displays.lidTenths(key), "fully open after ten ticks");
        displays.closed(key, 10);
        assertEquals(10, displays.lidTenths(key), "nothing moves at the close event itself");
        server.getScheduler().performTicks(1);
        assertEquals(9, displays.lidTenths(key), "first step down in the tick vanilla starts closing");
        server.getScheduler().performTicks(9);
        assertEquals(0, displays.lidTenths(key), "shut after ten ticks");
    }

    @Test
    void reopeningWhileClosingTurnsTheLidAround() {
        displays.opened(key);
        server.getScheduler().performTicks(12);
        displays.closed(key, 10);
        server.getScheduler().performTicks(3);
        int partWay = displays.lidTenths(key);
        assertTrue(partWay > 0 && partWay < 10, "part way down: " + partWay);
        displays.opened(key);
        assertEquals(partWay, displays.lidTenths(key), "turns at the next tick, not at the event");
        server.getScheduler().performTicks(1);
        assertEquals(partWay + 1, displays.lidTenths(key), "goes back up from where it was");
        server.getScheduler().performTicks(12);
        assertEquals(10, displays.lidTenths(key));
    }

    @Test
    void untrackedBoxesGetNoLidState() {
        Block plain = world.getBlockAt(3, 70, 0);
        plain.setType(Material.SHULKER_BOX);
        BlockKey plainKey = BlockKey.of(plain);
        displays.opened(plainKey);
        assertEquals(0, displays.lidTenths(plainKey), "nothing to move on a box without a sticker or name");
    }

    @Test
    void whoKeepsTheLidOpen() {
        PlayerMock closing = viewer(GameMode.SURVIVAL);
        PlayerMock other = viewer(GameMode.SURVIVAL);
        PlayerMock spectator = viewer(GameMode.SPECTATOR);
        assertFalse(DisplayListener.othersKeepItOpen(List.of(closing), closing), "the last viewer leaving");
        assertTrue(DisplayListener.othersKeepItOpen(List.of(closing, other), closing), "another player still has it open");
        assertFalse(DisplayListener.othersKeepItOpen(List.of(closing, spectator), closing), "spectators don't hold the lid open");
        assertTrue(DisplayListener.isSpectator(spectator));
        assertFalse(DisplayListener.isSpectator(other));
    }
}
