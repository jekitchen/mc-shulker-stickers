package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.BoxState;
import io.github.jekitchen.shulkerstickers.core.Changes;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Save is a one-shot transaction that never loses data or charges for what wasn't applied (review fixes). */
class SaveSafetyTest {

    private static final StickerConfig COLOR_COSTS_2 =
            ConfigLoader.parse("{\"color\": {\"cost\": {\"type\": \"xp_levels\", \"amount\": 2}}}").config();

    /** MockBukkit can't spawn text displays, so name tests turn name display off. */
    private static final StickerConfig NO_NAME_DISPLAY =
            ConfigLoader.parse("{\"name\": {\"display_enabled\": false}}").config();

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private Block box;
    private EditorService editor;

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
        editor = new EditorService(plugin, settings, new DisplayManager(plugin, settings));
        server.getPluginManager().registerEvents(new GestureListener(editor), plugin);
        server.getPluginManager().registerEvents(editor, plugin);
    }

    private void gesture(PlayerMock who) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(
                who, Action.RIGHT_CLICK_BLOCK, null, box, BlockFace.UP, EquipmentSlot.HAND));
    }

    private boolean editorOpen(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof EditorHolder;
    }

    private InventoryClickEvent click(int slot, ClickType type, InventoryAction action) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(),
                slot < 0 ? InventoryType.SlotType.OUTSIDE : InventoryType.SlotType.CONTAINER, slot, type, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void press(int slot) {
        click(slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    // ---- one-shot Save -------------------------------------------------------------------

    @Test
    void doubleClickingSaveChargesOnce() {
        // Removing an icon spawns no display entities, which MockBukkit can't model.
        editorWith(ConfigLoader.parse("{\"icon\": {\"cost\": {\"type\": \"xp_levels\", \"amount\": 2}}}").config());
        ShulkerBox state = (ShulkerBox) box.getState();
        StickerStore.write(state.getPersistentDataContainer(), StickerStoreTest.ICON);
        state.update(true, false);
        player.setLevel(5);
        gesture(player);

        press(EditorLayout.REMOVE_ICON);
        press(EditorLayout.SAVE);
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertFalse(StickerStore.hasData(((ShulkerBox) box.getState()).getPersistentDataContainer()));
        assertEquals(3, player.getLevel());
    }

    @Test
    void pressesAfterSaveDoNotChangeWhatIsApplied() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);

        press(BoxColor.RED.ordinal());
        press(EditorLayout.SAVE);
        press(BoxColor.BLUE.ordinal());
        server.getScheduler().performTicks(3);

        assertEquals(Material.RED_SHULKER_BOX, box.getType());
    }

    // ---- unreadable data is kept (PRD §6.1) ----------------------------------------------

    @Test
    void renamingABoxWithUnreadableStickerDataKeepsTheRawKeys() {
        editorWith(NO_NAME_DISPLAY);
        ShulkerBox state = (ShulkerBox) box.getState();
        var pdc = state.getPersistentDataContainer();
        pdc.set(key(StickerFormat.V), PersistentDataType.INTEGER, 99);
        pdc.set(key(StickerFormat.ICON_ID), PersistentDataType.STRING, "future:thing");
        pdc.set(key(StickerFormat.ANCHOR), PersistentDataType.STRING, "corner");
        state.update(true, false);

        BoxState original = new BoxState(BoxColor.UNDYED, Optional.empty(), Optional.empty());
        EditorService.SaveRequest request = new EditorService.SaveRequest(
                original, original.withName(Optional.of("Ores")), new Changes(false, false, true),
                BlockFace.UP, 0, false, new java.util.concurrent.atomic.AtomicBoolean());
        EditorService.SaveResult result = editor.apply(box, request);

        assertTrue(result.saved());
        ShulkerBox after = (ShulkerBox) box.getState();
        assertEquals(Component.text("Ores"), after.customName());
        assertEquals(99, after.getPersistentDataContainer().get(key(StickerFormat.V), PersistentDataType.INTEGER));
        assertEquals("future:thing", after.getPersistentDataContainer().get(key(StickerFormat.ICON_ID), PersistentDataType.STRING));
        assertEquals("corner", after.getPersistentDataContainer().get(key(StickerFormat.ANCHOR), PersistentDataType.STRING));
    }

    private static NamespacedKey key(String shortKey) {
        return new NamespacedKey(StickerFormat.NAMESPACE, shortKey);
    }

    // ---- pay only for what was applied (PRD §8) ------------------------------------------

    @Test
    void aColorChangeRefusedBecauseTheBoxIsOpenIsNotCharged() {
        editorWith(COLOR_COSTS_2);
        player.setLevel(5);
        PlayerMock viewer = server.addPlayer();
        viewer.openInventory(((ShulkerBox) box.getState()).getInventory());
        gesture(player);

        press(BoxColor.RED.ordinal());
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
        assertEquals(5, player.getLevel());
    }

    @Test
    void applyReportsWhatWasActuallyApplied() {
        editorWith(NO_NAME_DISPLAY);
        PlayerMock viewer = server.addPlayer();
        viewer.openInventory(((ShulkerBox) box.getState()).getInventory());
        BoxState original = new BoxState(BoxColor.UNDYED, Optional.empty(), Optional.empty());
        BoxState staged = original.withColor(BoxColor.RED).withName(Optional.of("Ores"));

        EditorService.SaveResult result = editor.apply(box, new EditorService.SaveRequest(
                original, staged, new Changes(true, false, true), BlockFace.UP, 0, false, new java.util.concurrent.atomic.AtomicBoolean()));

        assertEquals(new Changes(false, false, true), result.applied());
    }

    // ---- the box must be the one the editor opened on (PRD §6.3 step 1, §9) --------------

    @Test
    void breakingAndReplacingTheBoxDuringAnEditRefusesSave() {
        editorWith(COLOR_COSTS_2);
        player.setLevel(5);
        gesture(player);
        press(BoxColor.RED.ordinal());

        PlayerMock breaker = server.addPlayer();
        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockBreakEvent(box, breaker));
        box.setType(Material.AIR);
        box.setType(Material.SHULKER_BOX); // same position, same color, a different box
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
        assertEquals(5, player.getLevel());
        assertFalse(editorOpen(player));
        assertTrue(editor.editorOf(box).isEmpty());
    }

    @Test
    void applyRefusesWhenTheBoxWasReplaced() {
        editorWith(NO_NAME_DISPLAY);
        BoxState original = new BoxState(BoxColor.UNDYED, Optional.empty(), Optional.empty());
        java.util.concurrent.atomic.AtomicBoolean gone = new java.util.concurrent.atomic.AtomicBoolean(true);

        EditorService.SaveResult result = editor.apply(box, new EditorService.SaveRequest(
                original, original.withName(Optional.of("Ores")), new Changes(false, false, true),
                BlockFace.UP, 0, false, gone));

        assertFalse(result.saved());
        assertEquals(null, ((ShulkerBox) box.getState()).customName());
    }

    // ---- feature switches ----------------------------------------------------------------

    @Test
    void withEveryEditFeatureOffTheGestureIsVanilla() {
        editorWith(ConfigLoader.parse("""
                {"color": {"enabled": false}, "icon": {"enabled": false}, "name": {"edit_enabled": false}}
                """).config());
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, box, BlockFace.UP, EquipmentSlot.HAND);

        server.getPluginManager().callEvent(event);

        assertFalse(editorOpen(player));
        assertTrue(event.useInteractedBlock() != org.bukkit.event.Event.Result.DENY);
    }

    // ---- rename text ---------------------------------------------------------------------

    @Test
    void renameTextLosesFormattingCodesAndControlCharacters() {
        assertEquals("cRed Ores", EditorService.cleanRenameText("§cRed\u0007 Ores\u007f"));
        assertEquals("Ünïcode ✓", EditorService.cleanRenameText("Ünïcode ✓"));
    }

    // ---- locks and shutdown --------------------------------------------------------------

    @Test
    void reopeningByTheSamePlayerKeepsTheLock() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);
        gesture(player);
        PlayerMock other = server.addPlayer();
        other.setSneaking(true);

        gesture(other);

        assertFalse(editorOpen(other));
        assertTrue(editorOpen(player));
    }

    @Test
    void shutdownClosesEditorsAndReleasesLocks() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);

        editor.shutdown();

        assertFalse(editorOpen(player));
        assertEquals(0, editor.openSessions());
        assertTrue(editor.editorOf(box).isEmpty());
    }

    // ---- AC-12: every non-button click type is rejected ----------------------------------

    @Test
    void numberKeySwapIsRejected() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));

        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                BoxColor.RED.ordinal(), ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        server.getPluginManager().callEvent(event);

        assertRejected(event);
    }

    @Test
    void offHandSwapIsRejected() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);
        player.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND, 5));

        assertRejected(click(BoxColor.RED.ordinal(), ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP));
    }

    @Test
    void doubleClickCollectIsRejected() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));

        assertRejected(click(27, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR));
    }

    @Test
    void clickOutsideTheWindowIsRejected() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));

        assertRejected(click(-999, ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR));
    }

    @Test
    void dragAcrossTheEditorIsRejected() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(player);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));

        InventoryDragEvent drag = new InventoryDragEvent(player.getOpenInventory(), null,
                new ItemStack(Material.DIAMOND, 5), false,
                Map.of(0, new ItemStack(Material.DIAMOND, 2), 1, new ItemStack(Material.DIAMOND, 3)));
        server.getPluginManager().callEvent(drag);

        assertTrue(drag.isCancelled());
        assertEquals(5, player.getInventory().getItem(0).getAmount());
    }

    private void assertRejected(InventoryClickEvent event) {
        assertTrue(event.isCancelled(), "click must be cancelled");
        assertEquals(5, player.getInventory().getItem(0) == null
                ? player.getInventory().getItemInOffHand().getAmount()
                : player.getInventory().getItem(0).getAmount());
        // Nothing was staged: Save closes without changing the box.
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);
        assertEquals(Material.SHULKER_BOX, box.getType());
    }
}
