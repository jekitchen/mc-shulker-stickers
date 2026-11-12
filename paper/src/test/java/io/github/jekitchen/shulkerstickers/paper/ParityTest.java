package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.BoxState;
import io.github.jekitchen.shulkerstickers.core.Changes;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Paper behaves like the Fabric build where the parity review found differences. */
class ParityTest {

    /** MockBukkit can't spawn display entities, so no player counts as nearby in these tests. */
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

    private void gesture(BlockFace face) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, box, face, EquipmentSlot.HAND));
    }

    private void press(int slot) {
        server.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private boolean editorOpen() {
        var top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof EditorHolder;
    }

    private void store(Sticker sticker, String name) {
        ShulkerBox state = (ShulkerBox) box.getState();
        StickerStore.write(state.getPersistentDataContainer(), sticker);
        if (name != null) {
            state.customName(Component.text(name));
        }
        state.update(true, false);
    }

    private ShulkerBox state() {
        return (ShulkerBox) box.getState();
    }

    private EditorService.SaveResult apply(BoxState original, BoxState staged, Changes changes, BlockFace clicked, int rot) {
        return editor.apply(box, new EditorService.SaveRequest(original, staged, changes, clicked, rot, false,
                new AtomicBoolean()));
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    // ---- #3: removing the icon keeps where the name is drawn -----------------------------

    @Test
    void removingTheIconOfANamedBoxKeepsTheNamePlacement() {
        editorWith(StickerConfig.DEFAULTS);
        store(new Sticker(Optional.of("minecraft:stone"), Optional.empty(), Anchor.SIDE, 0, Optional.of(Face.EAST)), "Ores");
        BoxState original = new BoxState(BoxColor.UNDYED, Optional.of("minecraft:stone"), Optional.of("Ores"));

        apply(original, original.withIcon(Optional.empty(), Optional.empty()), new Changes(false, true, false),
                BlockFace.UP, 2);

        Sticker after = StickerStore.read(state().getPersistentDataContainer()).orElseThrow();
        assertEquals(Optional.empty(), after.iconId());
        assertEquals(Anchor.SIDE, after.anchor());
        assertEquals(Optional.of(Face.EAST), after.face());
        assertEquals(0, after.rot());
    }

    // ---- #5: removing the name of a name-only box leaves no orphan keys ------------------

    @Test
    void removingTheNameOfANameOnlyBoxRemovesItsStickerKeys() {
        editorWith(StickerConfig.DEFAULTS);
        store(new Sticker(Optional.empty(), Optional.empty(), Anchor.LID, 0, Optional.empty()), "Ores");
        BoxState original = new BoxState(BoxColor.UNDYED, Optional.empty(), Optional.of("Ores"));

        apply(original, original.withName(Optional.empty()), new Changes(false, false, true), BlockFace.UP, 0);

        assertTrue(StickerStore.raw(state().getPersistentDataContainer()).isEmpty());
        assertEquals(null, state().customName());
    }

    @Test
    void clearNameOnANameOnlyBoxRemovesItsStickerKeys() {
        store(new Sticker(Optional.empty(), Optional.empty(), Anchor.LID, 0, Optional.empty()), "Ores");

        StickerCommands.clearBox(state(), StickerCommands.ClearPart.NAME);

        assertTrue(StickerStore.raw(state().getPersistentDataContainer()).isEmpty());
    }

    @Test
    void clearNameKeepsAnIconSticker() {
        store(new Sticker(Optional.of("minecraft:stone"), Optional.empty(), Anchor.LID, 0, Optional.empty()), "Ores");

        StickerCommands.clearBox(state(), StickerCommands.ClearPart.NAME);

        assertTrue(StickerStore.read(state().getPersistentDataContainer()).orElseThrow().hasIcon());
    }

    // ---- #6: rot only for icons on the top or bottom face --------------------------------

    @Test
    void rotationIsZeroForIconsOnASide() {
        editorWith(StickerConfig.DEFAULTS);
        BoxState original = new BoxState(BoxColor.UNDYED, Optional.empty(), Optional.empty());

        apply(original, original.withIcon(Optional.of("minecraft:stone"), Optional.empty()),
                new Changes(false, true, false), BlockFace.NORTH, 2);

        assertEquals(0, StickerStore.read(state().getPersistentDataContainer()).orElseThrow().rot());
    }

    @Test
    void rotationIsKeptForIconsOnTheTop() {
        editorWith(StickerConfig.DEFAULTS);
        BoxState original = new BoxState(BoxColor.UNDYED, Optional.empty(), Optional.empty());

        apply(original, original.withIcon(Optional.of("minecraft:stone"), Optional.empty()),
                new Changes(false, true, false), BlockFace.UP, 2);

        assertEquals(2, StickerStore.read(state().getPersistentDataContainer()).orElseThrow().rot());
    }

    @Test
    void iconRotationIsTakenWhenUseOffHandIsClicked() {
        editorWith(StickerConfig.DEFAULTS);
        player.setRotation(0f, 0f); // facing south at open
        gesture(BlockFace.UP);
        player.getInventory().setItemInOffHand(new ItemStack(Material.COBBLESTONE));

        player.setRotation(180f, 0f); // facing north when the icon is taken
        press(EditorLayout.USE_OFFHAND);
        player.setRotation(90f, 0f); // turning again before Save changes nothing
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        Sticker after = StickerStore.read(state().getPersistentDataContainer()).orElseThrow();
        assertEquals(Optional.of("minecraft:cobblestone"), after.iconId());
        assertEquals(2, after.rot());
    }

    // ---- #8: protection denial says why --------------------------------------------------

    @Test
    void aProtectionPluginDenialShowsTheProtectedMessage() {
        editorWith(StickerConfig.DEFAULTS);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            void deny(PlayerInteractEvent event) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }, plugin);

        gesture(BlockFace.UP);

        assertFalse(editorOpen());
        assertEquals(Texts.plain(player, TextKeys.DENY_PROTECTED), plain(player.nextActionBar()));
    }

    @Test
    void saveRechecksGameMode() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(BlockFace.UP);
        press(BoxColor.RED.ordinal());
        player.setGameMode(GameMode.ADVENTURE);

        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
    }

    // ---- #13: locked at Save keeps the editor open ---------------------------------------

    @Test
    void aBoxLockedDuringTheEditRefusesSaveAndKeepsTheEditorOpen() {
        editorWith(StickerConfig.DEFAULTS);
        gesture(BlockFace.UP);
        press(BoxColor.RED.ordinal());
        ShulkerBox state = state();
        state.setLock("key");
        state.update(true, false);

        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
        assertTrue(editorOpen(), "the editor stays open");
        // The refusal is reported; drain to the last action-bar message.
        Component last = null;
        for (Component c = player.nextActionBar(); c != null; c = player.nextActionBar()) {
            last = c;
        }
        assertEquals(Texts.plain(player, TextKeys.DENY_LOCKED), plain(last));
        // Unlocked again, the same session can still save.
        ShulkerBox unlocked = state();
        unlocked.setLock(null);
        unlocked.update(true, false);
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);
        assertEquals(Material.RED_SHULKER_BOX, box.getType());
    }

    // ---- #12: unparseable visuals draw nothing -------------------------------------------

    @Test
    void unparseableIconVisualsDrawNothing() {
        Sticker sticker = new Sticker(Optional.of("minecraft:stone"), Optional.of("{not json"), Anchor.LID, 0,
                Optional.empty());

        assertTrue(IconItems.render(sticker, StickerConfig.DEFAULTS).isEmpty());
    }

    // ---- #15: a config file that can't be read keeps the current settings ---------------

    @Test
    void unreadableConfigOnReloadKeepsTheCurrentSettings(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"allow_adventure_mode\": true}");
        Settings settings = new Settings(file, Logger.getLogger("test"));
        settings.reload();
        Files.delete(file);
        Files.createDirectory(file); // reading a directory fails with an IOException

        List<String> warnings = settings.reload();

        assertTrue(settings.config().allowAdventureMode());
        assertEquals(1, warnings.size());
    }

    // ---- #9: reload knows every box with something to draw -------------------------------

    @Test
    void boxesWithStickerDataOrANameAreTrackedEvenWhenNothingIsDrawn() {
        editorWith(ConfigLoader.parse("{\"icon\": {\"enabled\": false}, \"name\": {\"display_enabled\": false}}").config());
        store(new Sticker(Optional.of("minecraft:stone"), Optional.empty(), Anchor.LID, 0, Optional.empty()), "Ores");
        Block plain = world.getBlockAt(5, 70, 0);
        plain.setType(Material.SHULKER_BOX);

        displays.refresh(box);
        displays.refresh(plain);

        assertTrue(displays.isTracked(box));
        assertFalse(displays.isTracked(plain));
        assertEquals(0, displays.count());
    }
}
