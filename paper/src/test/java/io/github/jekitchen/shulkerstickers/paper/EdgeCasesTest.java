package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.data.StickerFormat;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

/** PRD §9 edge cases on Paper, through the editor as a player uses it. */
class EdgeCasesTest {

    /** Each part costs a different number of levels, so what was charged shows what was applied. */
    private static final String COSTS = """
            "color": {"cost": {"type": "xp_levels", "amount": 2}},
            "icon": {"cost": {"type": "xp_levels", "amount": 3}},
            "name": {"cost": {"type": "xp_levels", "amount": 4}%s}
            """;

    private static final int LEVELS = 20;

    /** MockBukkit can't set up display entities, so no player counts as nearby. */
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
        player.setLevel(LEVELS);
        box = world.getBlockAt(0, 70, 0);
        box.setType(Material.SHULKER_BOX);
        // Within reach of the box (the reach watcher closes the editor otherwise).
        player.setLocation(new Location(world, 0.5, 70, 2.5));
    }

    @AfterEach
    void tearDown() {
        LockKeys.testLockCheck = null;
        MockBukkit.unmock();
    }

    private void editorWith(String extraNameSettings, String rest) {
        StickerConfig config = ConfigLoader.parse("{" + COSTS.formatted(extraNameSettings) + rest + "}").config();
        Settings settings = Settings.of(config);
        displays = new DisplayManager(plugin, settings, NOBODY);
        editor = new EditorService(plugin, settings, displays);
        server.getPluginManager().registerEvents(new GestureListener(editor), plugin);
        server.getPluginManager().registerEvents(editor, plugin);
    }

    private void editorWithCosts() {
        editorWith("", "");
    }

    private void gesture(PlayerMock who) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(
                who, Action.RIGHT_CLICK_BLOCK, null, box, BlockFace.UP, EquipmentSlot.HAND));
    }

    private void press(int slot) {
        server.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    /** The rename screen's result: MockBukkit has no anvil menu, so this calls what its click does. */
    private void rename(String text) {
        editor.finishRename(player, text);
        server.getScheduler().performTicks(1); // the editor comes back on the player's next tick
    }

    private static boolean editorOpen(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof EditorHolder;
    }

    private ShulkerBox state() {
        return (ShulkerBox) box.getState();
    }

    /** The last action-bar message the player got, as plain text. */
    private static String lastActionBar(PlayerMock who) {
        Component last = null;
        for (Component c = who.nextActionBar(); c != null; c = who.nextActionBar()) {
            last = c;
        }
        return last == null ? null : PlainTextComponentSerializer.plainText().serialize(last);
    }

    private static NamespacedKey key(String shortKey) {
        return new NamespacedKey(StickerFormat.NAMESPACE, shortKey);
    }

    // ---- box open at Save (PRD §5.2, §9) ---------------------------------------------------

    @Test
    void aFullEditOfABoxSomeoneHasOpenAppliesIconAndNameButNotTheColor() {
        editorWithCosts();
        PlayerMock viewer = server.addPlayer();
        viewer.openInventory(state().getInventory());
        player.getInventory().setItemInOffHand(new ItemStack(Material.COBBLESTONE, 5));
        gesture(player);
        assertTrue(editorOpen(player));

        press(BoxColor.RED.ordinal());
        press(EditorLayout.USE_OFFHAND);
        rename("Ores");
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType(), "color refused while the box is open");
        assertEquals(Component.text("Ores"), state().customName(), "name applied");
        assertEquals(Optional.of("minecraft:cobblestone"),
                StickerStore.read(state().getPersistentDataContainer()).flatMap(Sticker::iconId), "icon applied");
        assertEquals(LEVELS - 3 - 4, player.getLevel(), "icon and name charged, the color refunded");
        assertEquals(Texts.plain(player, TextKeys.SAVE_BOX_IN_USE), lastActionBar(player));
        assertEquals("Box is in use, so its color wasn't changed", Texts.plain(player, TextKeys.SAVE_BOX_IN_USE));
        assertFalse(editorOpen(player));
        assertTrue(editor.editorOf(box).isEmpty(), "lock released");
        // Displays stay hidden while the viewer has the box open... (keys, not Blocks: MockBukkit
        // hands out one Block per position whose coordinates move when its Location is changed.)
        BlockKey key = BlockKey.of(box);
        assertTrue(displays.trackedInChunk(world.getUID(), 0, 0).contains(key));
        assertTrue(displays.hiddenWhileOpen(key), "displays hidden while the box is open");

        // ...and come back once the last viewer has closed it: the refresh DisplayListener#onClose schedules.
        viewer.closeInventory();
        world.loadChunk(0, 0); // MockBukkit leaves it unloaded; refreshLater forgets boxes in unloaded chunks
        displays.refreshLater(key, 10);
        server.getScheduler().performTicks(11);
        assertFalse(displays.hiddenWhileOpen(key), "displays shown again after the viewer closes the box");
        assertTrue(displays.trackedInChunk(world.getUID(), 0, 0).contains(key), "icon and name still to draw");
    }

    // ---- blocked word (PRD §5.4, §9) -------------------------------------------------------

    @Test
    void aNameWithABlockedWordIsRefusedAtSaveAndNothingIsCharged() {
        editorWith(", \"blocked_words\": [\"grief\"]", "");
        gesture(player);

        rename("Grief stash");
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertNull(state().customName(), "name not set");
        assertFalse(StickerStore.hasData(state().getPersistentDataContainer()), "no sticker written");
        assertEquals(LEVELS, player.getLevel(), "nothing charged");
        assertEquals(Texts.plain(player, TextKeys.SAVE_BLOCKED_WORD), lastActionBar(player));
        assertTrue(editorOpen(player), "the editor stays open to fix the name");
    }

    // ---- out of reach (PRD §5.2, §9) -------------------------------------------------------

    @Test
    void walkingOutOfReachClosesTheEditorChangesNothingAndReleasesTheLock() {
        editorWithCosts();
        player.getInventory().setItemInOffHand(new ItemStack(Material.COBBLESTONE));
        gesture(player);
        press(BoxColor.RED.ordinal());
        press(EditorLayout.USE_OFFHAND);
        server.getScheduler().performTicks(25);
        assertTrue(editorOpen(player), "control: still open while in reach");

        player.setLocation(new Location(world, 40.5, 70, 40.5));
        server.getScheduler().performTicks(25);

        assertFalse(editorOpen(player));
        assertEquals(0, editor.openSessions());
        assertTrue(editor.editorOf(box).isEmpty(), "lock released");
        assertEquals(Material.SHULKER_BOX, box.getType());
        assertFalse(StickerStore.hasData(state().getPersistentDataContainer()));
        assertEquals(LEVELS, player.getLevel(), "nothing charged");
        assertEquals(Texts.plain(player, TextKeys.EDITOR_DISCARDED), lastActionBar(player));
        PlayerMock other = server.addPlayer();
        other.setSneaking(true);
        gesture(other);
        assertTrue(editorOpen(other), "another player can edit the box now");
    }

    // ---- spectator (PRD §5.1, §9) ----------------------------------------------------------

    @Test
    void spectatorsAreRefusedEvenWhenAdventureModeIsAllowed() {
        editorWith("", ", \"allow_adventure_mode\": true");
        player.setGameMode(GameMode.SPECTATOR);

        gesture(player);

        assertFalse(editorOpen(player));
        assertEquals(Optional.of(TextKeys.DENY_ADVENTURE), Access.denial(player, box,
                ConfigLoader.parse("{\"allow_adventure_mode\": true}").config(), plugin.getLogger(),
                io.github.jekitchen.shulkerstickers.paper.claims.Claims.none(server.getPluginManager(), plugin.getLogger())));
    }

    @Test
    void switchingToSpectatorDuringAnEditRefusesSave() {
        editorWith("", ", \"allow_adventure_mode\": true");
        gesture(player);
        press(BoxColor.RED.ordinal());
        player.setGameMode(GameMode.SPECTATOR);

        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
        assertEquals(LEVELS, player.getLevel());
        assertEquals(Texts.plain(player, TextKeys.DENY_ADVENTURE), lastActionBar(player));
    }

    // ---- an icon item that no longer exists (PRD §6.1, §9) ---------------------------------

    private static final Map<String, Object> UNKNOWN_ICON = Map.of(
            StickerFormat.V, 1, StickerFormat.ICON_ID, "nomod:thing", StickerFormat.ANCHOR, "side",
            StickerFormat.ROT, 0, StickerFormat.FACE, "north");

    private void storeRaw(Map<String, Object> raw) {
        ShulkerBox state = state();
        var pdc = state.getPersistentDataContainer();
        raw.forEach((k, v) -> {
            if (v instanceof Integer i) {
                pdc.set(key(k), PersistentDataType.INTEGER, i);
            } else {
                pdc.set(key(k), PersistentDataType.STRING, (String) v);
            }
        });
        state.update(true, false);
    }

    @Test
    void anIconFromAModThatIsGoneDrawsNothingAndSurvivesARenameAndARecolor() {
        editorWithCosts();
        storeRaw(UNKNOWN_ICON);
        Sticker sticker = StickerStore.read(state().getPersistentDataContainer()).orElseThrow();
        assertEquals(Optional.of("nomod:thing"), sticker.iconId(), "a valid v1 sticker");
        assertEquals(Anchor.SIDE, sticker.anchor());
        assertTrue(IconItems.render(sticker, StickerConfig.DEFAULTS).isEmpty(), "nothing drawn");

        gesture(player);
        press(BoxColor.RED.ordinal());
        rename("Ores");
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.RED_SHULKER_BOX, box.getType(), "recolored");
        assertEquals(Component.text("Ores"), state().customName(), "renamed");
        assertEquals(UNKNOWN_ICON, StickerStore.raw(state().getPersistentDataContainer()), "raw data kept as is");
        assertEquals(LEVELS - 2 - 4, player.getLevel());
    }

    // ---- a locked box at the gesture (PRD §9, owner decision 2) ----------------------------

    private static ItemStack named(Material type, String name) {
        ItemStack item = new ItemStack(type);
        item.editMeta(meta -> meta.customName(Component.text(name)));
        return item;
    }

    /** A box locked with a key named "key"; MockBukkit has no server lock check, so this stands in for it. */
    private void lockBox() {
        ShulkerBox state = state();
        state.setLock("key");
        state.update(true, false);
        LockKeys.testLockCheck = (boxState, item) -> item.hasItemMeta() && item.getItemMeta().hasCustomName()
                && "key".equals(PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().customName()));
    }

    @Test
    void theKeyInTheOffHandOpensALockedBoxAndIsTheOnlyIconOffered() {
        editorWithCosts();
        lockBox();
        player.getInventory().setItemInOffHand(named(Material.TRIPWIRE_HOOK, "key"));

        gesture(player);

        assertTrue(editorOpen(player), "the key in the off-hand opens the editor");
        ItemStack offered = player.getOpenInventory().getTopInventory().getItem(EditorLayout.USE_OFFHAND);
        assertEquals(Material.TRIPWIRE_HOOK, offered.getType(), "Use off-hand item offers the key");
        press(EditorLayout.USE_OFFHAND);
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);
        assertEquals(Optional.of("minecraft:tripwire_hook"),
                StickerStore.read(state().getPersistentDataContainer()).flatMap(Sticker::iconId));
        assertEquals("key", state().getLock(), "still locked");
        assertEquals(Material.TRIPWIRE_HOOK, player.getInventory().getItemInOffHand().getType(), "key kept");
    }

    @Test
    void aWrongItemInTheOffHandIsRefusedWithTheLockedMessage() {
        editorWithCosts();
        lockBox();
        player.getInventory().setItemInOffHand(named(Material.TRIPWIRE_HOOK, "not the key"));

        gesture(player);

        assertFalse(editorOpen(player));
        assertEquals(Texts.plain(player, TextKeys.DENY_LOCKED), lastActionBar(player));
        assertTrue(editor.editorOf(box).isEmpty());
    }
}
