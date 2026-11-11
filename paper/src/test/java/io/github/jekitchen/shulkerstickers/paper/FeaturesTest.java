package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Face;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.Cost;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
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
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** 0.1 features on Paper that MockBukkit can model. */
class FeaturesTest {

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private PlayerMock player;
    private Block box;

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
        box = world.getBlockAt(0, 70, 0);
        box.setType(Material.SHULKER_BOX);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EditorService editorWith(StickerConfig config) {
        Settings settings = Settings.of(config);
        EditorService editor = new EditorService(plugin, settings, new DisplayManager(plugin, settings));
        server.getPluginManager().registerEvents(new GestureListener(editor), plugin);
        server.getPluginManager().registerEvents(editor, plugin);
        return editor;
    }

    private void gesture() {
        server.getPluginManager().callEvent(new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, box, BlockFace.UP, EquipmentSlot.HAND));
    }

    private boolean editorOpen() {
        var top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof EditorHolder;
    }

    private void click(int slot) {
        server.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    @Test
    void adventureModeIsDeniedByDefault() {
        editorWith(StickerConfig.DEFAULTS);
        player.setGameMode(GameMode.ADVENTURE);

        gesture();

        assertFalse(editorOpen());
    }

    @Test
    void adventureModeIsAllowedWhenConfigured() {
        editorWith(ConfigLoader.parse("{\"allow_adventure_mode\": true}").config());
        player.setGameMode(GameMode.ADVENTURE);

        gesture();

        assertTrue(editorOpen());
    }

    @Test
    void playersWithoutAnyEditPermissionAreRefused() {
        editorWith(StickerConfig.DEFAULTS);
        player.addAttachment(plugin, Permissions.COLOR, false);
        player.addAttachment(plugin, Permissions.ICON, false);
        player.addAttachment(plugin, Permissions.NAME, false);

        gesture();

        assertFalse(editorOpen());
    }

    @Test
    void gestureToggleOffLeavesVanillaBehavior() {
        editorWith(StickerConfig.DEFAULTS);
        StickerCommands.toggle(player);

        gesture();

        assertFalse(editorOpen());
        StickerCommands.toggle(player);
        gesture();
        assertTrue(editorOpen());
    }

    @Test
    void saveChargesTheConfiguredXpCost() {
        editorWith(ConfigLoader.parse("{\"color\": {\"cost\": {\"type\": \"xp_levels\", \"amount\": 2}}}").config());
        player.setGameMode(GameMode.SURVIVAL);
        player.setLevel(5);
        gesture();

        click(BoxColor.RED.ordinal());
        click(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.RED_SHULKER_BOX, box.getType());
        assertEquals(3, player.getLevel());
    }

    @Test
    void saveIsRefusedWhenThePlayerCannotPay() {
        editorWith(ConfigLoader.parse("{\"color\": {\"cost\": {\"type\": \"xp_levels\", \"amount\": 2}}}").config());
        player.setGameMode(GameMode.SURVIVAL);
        player.setLevel(1);
        gesture();

        click(BoxColor.RED.ordinal());
        click(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
        assertEquals(1, player.getLevel());
        assertTrue(editorOpen());
    }

    @Test
    void featureSwitchedOffCannotBeStaged() {
        editorWith(ConfigLoader.parse("{\"color\": {\"enabled\": false}}").config());
        gesture();

        click(BoxColor.RED.ordinal());
        click(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertEquals(Material.SHULKER_BOX, box.getType());
    }

    @Test
    void itemCostsAreChargedAndRefunded() {
        List<Cost> costs = List.of(new Cost(Cost.Type.ITEM, 3, "minecraft:paper"));
        player.getInventory().addItem(new ItemStack(Material.PAPER, 5));

        assertTrue(Payments.canPay(player, costs));
        Payments.charge(player, costs);
        assertFalse(player.getInventory().containsAtLeast(new ItemStack(Material.PAPER), 3));
        Payments.refund(player, costs);
        assertTrue(player.getInventory().containsAtLeast(new ItemStack(Material.PAPER), 5));
    }

    @Test
    void creativePlayersPayNothing() {
        player.setGameMode(GameMode.CREATIVE);

        assertEquals(List.of(), Payments.costsFor(player, List.of(new Cost(Cost.Type.XP_LEVELS, 9, null))));
    }

    @Test
    void firstPlacementTipIsShownOnce() {
        HelpListener help = new HelpListener(Settings.of(StickerConfig.DEFAULTS));
        BlockPlaceEvent place = new BlockPlaceEvent(box, box.getState(), box.getRelative(BlockFace.DOWN),
                new ItemStack(Material.SHULKER_BOX), player, true, EquipmentSlot.HAND);

        help.onPlace(place);
        help.onPlace(place);

        assertTrue(player.nextComponentMessage() != null);
        assertNull(player.nextComponentMessage());
    }

    @Test
    void clearIconKeepsTheNameAndWhereItIsDrawn() {
        ShulkerBox state = (ShulkerBox) box.getState();
        state.customName(Component.text("Ores"));
        StickerStore.write(state.getPersistentDataContainer(), new Sticker(Optional.of("minecraft:stone"),
                Optional.empty(), Anchor.SIDE, 0, Optional.of(Face.EAST)));
        state.update(true, false);

        StickerCommands.clearBox((ShulkerBox) box.getState(), StickerCommands.ClearPart.ICON);

        ShulkerBox after = (ShulkerBox) box.getState();
        Sticker kept = StickerStore.read(after.getPersistentDataContainer()).orElseThrow();
        assertTrue(kept.iconId().isEmpty());
        assertEquals(Optional.of(Face.EAST), kept.face());
        assertEquals(Component.text("Ores"), after.customName());
    }

    @Test
    void clearAllRemovesEverything() {
        ShulkerBox state = (ShulkerBox) box.getState();
        state.customName(Component.text("Ores"));
        StickerStore.write(state.getPersistentDataContainer(), StickerStoreTest.ICON);
        StickerStore.setNameHidden(state.getPersistentDataContainer(), true);
        state.update(true, false);

        StickerCommands.clearBox((ShulkerBox) box.getState(), StickerCommands.ClearPart.ALL);

        ShulkerBox after = (ShulkerBox) box.getState();
        assertFalse(StickerStore.hasData(after.getPersistentDataContainer()));
        assertNull(after.customName());
    }
}
