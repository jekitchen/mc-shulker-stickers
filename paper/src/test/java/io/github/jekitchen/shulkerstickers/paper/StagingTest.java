package io.github.jekitchen.shulkerstickers.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
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
import org.bukkit.enchantments.Enchantment;
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
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** AC-11 (staging: Cancel and disconnecting change nothing, charge nothing) and AC-3 (the off-hand stack). */
class StagingTest {

    /** Every staged change costs XP, so "nothing charged" is visible. */
    private static final StickerConfig COSTS = ConfigLoader.parse("""
            {"color": {"cost": {"type": "xp_levels", "amount": 2}},
             "icon": {"cost": {"type": "xp_levels", "amount": 3}},
             "name": {"display_enabled": false}}
            """).config();

    /** MockBukkit can't spawn display entities, so no player counts as nearby. */
    private static final DisplayManager.Nearby NOBODY = (anchor, radius, vertical) -> List.of();

    private ServerMock server;
    private PlayerMock player;
    private Block box;
    private EditorService editor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        WorldMock world = server.addSimpleWorld("world");
        for (String node : List.of(Permissions.COLOR, Permissions.ICON, Permissions.NAME, Permissions.NAME_DISPLAY)) {
            server.getPluginManager().addPermission(new Permission(node, PermissionDefault.TRUE));
        }
        player = server.addPlayer();
        player.setSneaking(true);
        player.setGameMode(GameMode.SURVIVAL);
        player.setLevel(10);
        box = world.getBlockAt(0, 70, 0);
        box.setType(Material.SHULKER_BOX);
        ShulkerBox state = (ShulkerBox) box.getState();
        state.getSnapshotInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
        state.customName(Component.text("Ores"));
        state.update(true, false);

        Settings settings = Settings.of(COSTS);
        editor = new EditorService(plugin, settings, new DisplayManager(plugin, settings, NOBODY));
        server.getPluginManager().registerEvents(new GestureListener(editor), plugin);
        server.getPluginManager().registerEvents(editor, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
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

    /** An off-hand stack with a name, lore, enchantment and count that must all survive. */
    private static ItemStack fancyOffhand() {
        ItemStack stack = new ItemStack(Material.COBBLESTONE, 37);
        stack.editMeta(meta -> {
            meta.customName(Component.text("Keep me"));
            meta.lore(List.of(Component.text("lore line")));
            meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        });
        return stack;
    }

    /** Stages a color and an icon (both paid for), so a leak of either would show. */
    private void stageColorAndIcon() {
        player.getInventory().setItemInOffHand(fancyOffhand());
        gesture(player);
        assertTrue(editorOpen(player));
        press(BoxColor.RED.ordinal());
        press(EditorLayout.USE_OFFHAND);
    }

    private void assertBoxUnchanged() {
        assertEquals(Material.SHULKER_BOX, box.getType());
        ShulkerBox state = (ShulkerBox) box.getState();
        assertFalse(StickerStore.hasData(state.getPersistentDataContainer()), "no sticker written");
        assertEquals(Component.text("Ores"), state.customName());
        assertEquals(new ItemStack(Material.DIAMOND, 7), state.getInventory().getItem(0));
    }

    // ---- AC-11 -----------------------------------------------------------------------------

    @Test
    void cancelDiscardsStagedChangesAndChargesNothing() {
        stageColorAndIcon();

        press(EditorLayout.CANCEL);
        server.getScheduler().performTicks(3);

        assertFalse(editorOpen(player));
        assertEquals(0, editor.openSessions());
        assertBoxUnchanged();
        assertEquals(10, player.getLevel(), "nothing charged");
        assertEquals(fancyOffhand(), player.getInventory().getItemInOffHand());
        // The lock is released: another player can edit the box.
        PlayerMock other = server.addPlayer();
        other.setSneaking(true);
        gesture(other);
        assertTrue(editorOpen(other));
    }

    @Test
    void disconnectingWithoutSaveLeavesTheBoxUnchangedAndChargesNothing() {
        stageColorAndIcon();

        player.disconnect();
        server.getScheduler().performTicks(3);

        assertEquals(0, editor.openSessions());
        assertBoxUnchanged();
        assertEquals(10, player.getLevel(), "nothing charged");
        assertEquals(fancyOffhand(), player.getInventory().getItemInOffHand());
        assertTrue(editor.editorOf(box).isEmpty(), "lock released");
        PlayerMock other = server.addPlayer();
        other.setSneaking(true);
        gesture(other);
        assertTrue(editorOpen(other));
    }

    // ---- AC-3 ------------------------------------------------------------------------------

    @Test
    void useOffHandItemAndSaveLeavesTheOffHandStackUnchanged() {
        ItemStack mainHand = new ItemStack(Material.AIR);
        player.getInventory().setItemInOffHand(fancyOffhand());
        gesture(player);

        press(EditorLayout.USE_OFFHAND);
        press(EditorLayout.SAVE);
        server.getScheduler().performTicks(3);

        assertFalse(editorOpen(player));
        ItemStack offhand = player.getInventory().getItemInOffHand();
        assertEquals(fancyOffhand(), offhand, "same item, count, name, lore and enchantment");
        assertEquals(37, offhand.getAmount());
        assertEquals(mainHand.getType(), player.getInventory().getItemInMainHand().getType());
        assertEquals(7, player.getLevel(), "the icon cost only");
        Sticker stored = StickerStore.read(((ShulkerBox) box.getState()).getPersistentDataContainer()).orElseThrow();
        assertEquals(Optional.of("minecraft:cobblestone"), stored.iconId());
        // Only allowed visual properties are kept: the enchantment becomes a glint, name and lore are dropped.
        assertEquals(Optional.of("{\"minecraft:enchantment_glint_override\":true}"), stored.iconVisual());
    }
}
