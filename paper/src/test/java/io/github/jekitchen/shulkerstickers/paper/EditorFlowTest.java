package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class EditorFlowTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private Block box;
    private EditorService editor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        // The plugin file declares these on a real server.
        for (String node : java.util.List.of(io.github.jekitchen.shulkerstickers.core.Permissions.COLOR,
                io.github.jekitchen.shulkerstickers.core.Permissions.ICON,
                io.github.jekitchen.shulkerstickers.core.Permissions.NAME,
                io.github.jekitchen.shulkerstickers.core.Permissions.NAME_DISPLAY)) {
            server.getPluginManager().addPermission(
                    new org.bukkit.permissions.Permission(node, org.bukkit.permissions.PermissionDefault.TRUE));
        }
        player = server.addPlayer();
        box = world.getBlockAt(0, 70, 0);
        box.setType(Material.SHULKER_BOX);
        Settings settings = Settings.of(io.github.jekitchen.shulkerstickers.core.config.StickerConfig.DEFAULTS);
        editor = new EditorService(plugin, settings, new DisplayManager(plugin, settings));
        server.getPluginManager().registerEvents(new GestureListener(editor), plugin);
        server.getPluginManager().registerEvents(editor, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerInteractEvent interact(PlayerMock who, EquipmentSlot hand) {
        PlayerInteractEvent event = new PlayerInteractEvent(
                who, Action.RIGHT_CLICK_BLOCK, who.getInventory().getItem(hand), box, BlockFace.UP, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private boolean editorOpen(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof EditorHolder;
    }

    @Test
    void sneakWithEmptyMainHandOpensTheEditorInsteadOfTheBox() {
        player.setSneaking(true);

        PlayerInteractEvent event = interact(player, EquipmentSlot.HAND);

        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertTrue(editorOpen(player));
    }

    /** AC-9: the same items as Fabric's MainHandGestureGameTests. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Material.class, names = {
            "STONE", "SHULKER_BOX", "WATER_BUCKET", "BUCKET", "BREAD", "IRON_PICKAXE", "IRON_AXE"})
    void anItemInTheMainHandIsNeverIntercepted(Material item) {
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(new ItemStack(item));

        PlayerInteractEvent event = interact(player, EquipmentSlot.HAND);

        assertTrue(event.useInteractedBlock() != Event.Result.DENY, "block use left to vanilla");
        assertTrue(event.useItemInHand() != Event.Result.DENY, "item use left to vanilla");
        assertFalse(editorOpen(player));
        assertEquals(item, player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void notSneakingIsVanilla() {
        PlayerInteractEvent event = interact(player, EquipmentSlot.HAND);

        assertTrue(event.useInteractedBlock() != Event.Result.DENY);
        assertFalse(editorOpen(player));
    }

    @Test
    void theOffHandPlacementThatFollowsTheGestureIsBlocked() {
        player.setSneaking(true);
        player.getInventory().setItemInOffHand(new ItemStack(Material.STONE));

        PlayerInteractEvent event = interact(player, EquipmentSlot.OFF_HAND);

        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertEquals(1, player.getInventory().getItemInOffHand().getAmount());
    }

    @Test
    void aProtectionPluginDenialIsHonored() {
        player.setSneaking(true);
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, box, BlockFace.UP, EquipmentSlot.HAND);
        event.setUseInteractedBlock(Event.Result.DENY);

        server.getPluginManager().callEvent(event);

        assertFalse(editorOpen(player));
    }

    @Test
    void shiftClickInTheEditorIsCancelledAndChangesNothing() {
        player.setSneaking(true);
        interact(player, EquipmentSlot.HAND);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        var view = player.getOpenInventory();

        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 27,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(click);

        assertTrue(click.isCancelled());
        assertEquals(5, player.getInventory().getItem(0).getAmount());
    }

    @Test
    void escDiscardsStagedChangesAndReleasesTheLock() {
        player.setSneaking(true);
        interact(player, EquipmentSlot.HAND);
        var view = player.getOpenInventory();
        server.getPluginManager().callEvent(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                io.github.jekitchen.shulkerstickers.core.BoxColor.RED.ordinal(), ClickType.LEFT,
                InventoryAction.PICKUP_ALL));

        player.closeInventory();

        assertEquals(Material.SHULKER_BOX, box.getType());
        assertEquals(0, editor.openSessions());
        PlayerMock other = server.addPlayer();
        other.setSneaking(true);
        interact(other, EquipmentSlot.HAND);
        assertTrue(editorOpen(other));
    }

    @Test
    void aSecondPlayerCannotEditTheSameBox() {
        player.setSneaking(true);
        interact(player, EquipmentSlot.HAND);
        PlayerMock other = server.addPlayer();
        other.setSneaking(true);

        interact(other, EquipmentSlot.HAND);

        assertFalse(editorOpen(other));
    }

    @Test
    void saveAppliesTheStagedColor() {
        player.setSneaking(true);
        interact(player, EquipmentSlot.HAND);
        var view = player.getOpenInventory();
        server.getPluginManager().callEvent(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                io.github.jekitchen.shulkerstickers.core.BoxColor.RED.ordinal(), ClickType.LEFT,
                InventoryAction.PICKUP_ALL));

        server.getPluginManager().callEvent(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                io.github.jekitchen.shulkerstickers.core.editor.EditorLayout.SAVE, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        server.getScheduler().performTicks(3);

        assertEquals(Material.RED_SHULKER_BOX, box.getType());
        assertFalse(editorOpen(player));
        assertInstanceOf(org.bukkit.block.ShulkerBox.class, box.getState());
    }
}
