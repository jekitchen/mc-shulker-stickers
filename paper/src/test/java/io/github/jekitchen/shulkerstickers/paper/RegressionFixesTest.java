package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Fixes from the regression review: payment up front, session cleanup, per-chunk display index. */
class RegressionFixesTest {

    private static final StickerConfig PAPER_FOR_COLOR = ConfigLoader.parse("""
            {"color": {"cost": {"type": "item", "item": "minecraft:paper", "amount": 2}},
             "name": {"display_enabled": false}}
            """).config();

    private static final StickerConfig XP_FOR_COLOR = ConfigLoader.parse("""
            {"color": {"cost": {"type": "xp_levels", "amount": 2}}, "name": {"display_enabled": false}}
            """).config();

    private static final DisplayManager.Nearby NOBODY = (anchor, radius, vertical) -> List.of();

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private Block box;
    private EditorService editor;
    private DisplayManager displays;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        for (String node : List.of(Permissions.COLOR, Permissions.ICON, Permissions.NAME, Permissions.NAME_DISPLAY)) {
            server.getPluginManager().addPermission(new Permission(node, PermissionDefault.TRUE));
        }
        player = server.addPlayer();
        player.setSneaking(true);
        player.setGameMode(GameMode.SURVIVAL);
        box = world.getBlockAt(0, 70, 0);
        box.setType(Material.SHULKER_BOX);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void editorWith(StickerConfig config) {
        Settings settings = Settings.of(config);
        displays = new DisplayManager(plugin, settings, NOBODY);
        editor = new EditorService(plugin, settings, displays);
        server.getPluginManager().registerEvents(new GestureListener(editor), plugin);
        server.getPluginManager().registerEvents(editor, plugin);
    }

    private void gesture(PlayerMock who) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(
                who, Action.RIGHT_CLICK_BLOCK, null, box, BlockFace.UP, EquipmentSlot.HAND));
    }

    private void press(int slot) {
        server.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private static boolean editorOpen(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof EditorHolder;
    }

    private int paper() {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.PAPER) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    private void setLocked(boolean locked) {
        ShulkerBox state = (ShulkerBox) box.getState();
        state.setLock(locked ? "key" : null);
        state.update(true, false);
    }

    // ---- 1: payment is taken when Save is pressed ------------------------------------

    @Test
    void costItemsAreTakenWhenSaveIsPressed() {
        editorWith(PAPER_FOR_COLOR);
        player.getInventory().setItem(9, new ItemStack(Material.PAPER, 2));
        gesture(player);

        press(BoxColor.RED.ordinal());
        press(EditorLayout.SAVE);

        assertEquals(0, paper(), "taken before the apply is scheduled");
    }

    @Test
    void escAndDroppingTheCostItemsBeforeTheApplyIsNotAFreeEdit() {
        editorWith(PAPER_FOR_COLOR);
        player.getInventory().setItem(9, new ItemStack(Material.PAPER, 2));
        gesture(player);

        press(BoxColor.RED.ordinal());
        press(EditorLayout.SAVE);
        player.closeInventory();
        player.getInventory().clear();
        server.getScheduler().performTicks(3);

        assertEquals(Material.RED_SHULKER_BOX, box.getType());
        assertEquals(0, paper());
    }

    @Test
    void quittingBeforeTheApplyStillPaysAndApplies() {
        editorWith(XP_FOR_COLOR);
        player.setLevel(5);
        gesture(player);

        press(BoxColor.RED.ordinal());
        press(EditorLayout.SAVE);
        assertEquals(3, player.getLevel());
        player.disconnect();
        server.getScheduler().performTicks(3);

        assertEquals(Material.RED_SHULKER_BOX, box.getType());
        assertEquals(3, player.getLevel());
    }

    @Test
    void aRefusedSaveRefundsEverything() {
        editorWith(PAPER_FOR_COLOR);
        player.getInventory().setItem(9, new ItemStack(Material.PAPER, 2));
        gesture(player);

        press(BoxColor.RED.ordinal());
        press(EditorLayout.SAVE);
        box.setType(Material.AIR);
        server.getScheduler().performTicks(3);

        assertEquals(2, paper());
    }

    @Test
    void aColorRefusedBecauseTheBoxIsOpenRefundsTheItems() {
        editorWith(PAPER_FOR_COLOR);
        player.getInventory().setItem(9, new ItemStack(Material.PAPER, 2));
        PlayerMock viewer = server.addPlayer();
        viewer.openInventory(((ShulkerBox) box.getState()).getInventory());
        gesture(player);

        press(BoxColor.RED.ordinal());
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
        assertEquals(2, paper());
    }

    @Test
    void doubleSaveTakesTheItemsOnce() {
        editorWith(PAPER_FOR_COLOR);
        player.getInventory().setItem(9, new ItemStack(Material.PAPER, 4));
        gesture(player);

        press(BoxColor.RED.ordinal());
        press(EditorLayout.SAVE);
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.RED_SHULKER_BOX, box.getType());
        assertEquals(2, paper());
    }

    @Test
    void aLockedRefusalRefundsTheCost() {
        editorWith(PAPER_FOR_COLOR);
        player.getInventory().setItem(9, new ItemStack(Material.PAPER, 2));
        gesture(player);
        press(BoxColor.RED.ordinal());
        setLocked(true);

        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
        assertEquals(2, paper());
    }

    @Test
    void refundsOwedToAPlayerWhoLeftArePaidWhenTheyJoin() {
        editorWith(PAPER_FOR_COLOR);
        editor.oweRefund(player.getUniqueId(), PAPER_FOR_COLOR.colorCost() == null ? List.of()
                : List.of(PAPER_FOR_COLOR.colorCost()));

        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(player, Component.empty()));
        server.getScheduler().performTicks(2);

        assertEquals(2, paper());
    }

    // ---- 2: a refusal after Esc ends the session -------------------------------------

    @Test
    void aLockedRefusalAfterEscReleasesTheBox() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);
        press(BoxColor.RED.ordinal());
        setLocked(true);

        press(EditorLayout.SAVE);
        player.closeInventory();
        server.getScheduler().performTicks(3);
        setLocked(false);

        PlayerMock other = server.addPlayer();
        other.setSneaking(true);
        gesture(other);
        server.getScheduler().performTicks(2);
        assertTrue(editorOpen(other), "the first player's lock was released");
    }

    // ---- 3: a successful save only closes our own editor -----------------------------

    @Test
    void aSuccessfulSaveDoesNotCloseAMenuOpenedMeanwhile() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);
        press(BoxColor.RED.ordinal());

        press(EditorLayout.SAVE);
        player.closeInventory();
        Inventory chest = server.createInventory(null, 9, Component.text("Chest"));
        player.openInventory(chest);
        server.getScheduler().performTicks(3);

        assertEquals(Material.RED_SHULKER_BOX, box.getType());
        assertSame(chest, player.getOpenInventory().getTopInventory());
    }

    // ---- 4 and 5: per-chunk display index --------------------------------------------

    private Block decorated(int x, int z) {
        Block block = world.getBlockAt(x, 70, z);
        block.setType(Material.SHULKER_BOX);
        ShulkerBox state = (ShulkerBox) block.getState();
        StickerStore.write(state.getPersistentDataContainer(), StickerStoreTest.ICON);
        state.update(true, false);
        return block;
    }

    @Test
    void theChunkIndexFollowsTrackAndForget() {
        editorWith(StickerConfig.DEFAULTS);
        Block a = decorated(1, 1);
        Block b = decorated(2, 2);
        Block c = decorated(17, 1);
        displays.refresh(a);
        displays.refresh(b);
        displays.refresh(c);

        assertEquals(Set.of(BlockKey.of(a), BlockKey.of(b)), displays.trackedInChunk(world.getUID(), 0, 0));
        assertEquals(Set.of(BlockKey.of(c)), displays.trackedInChunk(world.getUID(), 1, 0));

        displays.forget(BlockKey.of(a));
        assertEquals(Set.of(BlockKey.of(b)), displays.trackedInChunk(world.getUID(), 0, 0));

        // A box that lost its data stops being tracked.
        ShulkerBox state = (ShulkerBox) b.getState();
        StickerStore.clear(state.getPersistentDataContainer());
        state.update(true, false);
        displays.refresh(b);
        assertEquals(Set.of(), displays.trackedInChunk(world.getUID(), 0, 0));
    }

    @Test
    void removingAChunkClearsItsIndex() {
        editorWith(StickerConfig.DEFAULTS);
        Block a = decorated(1, 1);
        displays.refresh(a);

        displays.removeChunk(world.getUID(), 0, 0);

        assertEquals(Set.of(), displays.trackedInChunk(world.getUID(), 0, 0));
        assertFalse(displays.isTracked(a));
    }

    @Test
    void aCheckOnAnUnloadedChunkForgetsItsBoxes() {
        editorWith(StickerConfig.DEFAULTS);
        Block a = decorated(1, 1);
        displays.refresh(a);
        world.unloadChunk(0, 0);

        displays.checkNow(world.getUID(), 0, 0);

        assertFalse(displays.isTracked(a));
        assertEquals(Set.of(), displays.trackedInChunk(world.getUID(), 0, 0));
    }

    @Test
    void aLateRefreshDoesNotTrackABoxInAnUnloadedChunk() {
        editorWith(StickerConfig.DEFAULTS);
        Block a = decorated(1, 1);
        world.unloadChunk(0, 0);

        displays.refreshLater(BlockKey.of(a), 1);
        server.getScheduler().performTicks(2);

        assertFalse(displays.isTracked(a));
    }
}
