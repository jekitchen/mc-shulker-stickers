package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Security (review finding): every close of a box schedules a redraw, so a player opening and
 * closing a box quickly could queue a redraw (and respawned displays) for every close. Only one
 * delayed refresh per box waits at a time.
 */
class DelayedRefreshTest {

    private ServerMock server;
    private DisplayManager displays;
    private BlockKey key;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("world");
        Block box = world.getBlockAt(0, 70, 0);
        box.setType(Material.SHULKER_BOX);
        world.loadChunk(0, 0);
        displays = new DisplayManager(MockBukkit.createMockPlugin(), Settings.of(StickerConfig.DEFAULTS),
                (anchor, radius, vertical) -> List.of());
        key = BlockKey.of(box);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void oneDelayedRefreshPerBoxAtATime() {
        assertTrue(displays.refreshLater(key, 10), "the first is scheduled");
        for (int i = 0; i < 50; i++) {
            assertFalse(displays.refreshLater(key, 10), "more while one waits are dropped");
        }
        server.getScheduler().performTicks(11);
        assertTrue(displays.refreshLater(key, 10), "after it ran, the next one is scheduled again");
    }
}
