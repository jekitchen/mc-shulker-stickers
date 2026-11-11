package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import io.github.jekitchen.shulkerstickers.fabric.Access;
import io.github.jekitchen.shulkerstickers.fabric.Perms;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * The optional integrations running for real: Fabric Permissions API (mod id
 * {@code fabric-permissions-api-v0}) and Common Protection API ({@code common-protection-api}),
 * both loaded only in the GameTest server.
 */
public class IntegrationGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    private static EditorMenu editor(GameTestHelper helper, ServerPlayer player) {
        return EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), Direction.UP, StickerConfig.DEFAULTS);
    }

    private static void click(EditorMenu menu, ServerPlayer player, int slot) {
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
    }

    private static boolean canUseClear(GameTestHelper helper, CommandSourceStack source) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        return root.getChild("shulkerstickers").getChild("clear").canUse(source);
    }

    @GameTest
    public void bothLibrariesAreLoaded(GameTestHelper helper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0"), "permissions api");
        helper.assertTrue(FabricLoader.getInstance().isModLoaded("common-protection-api"), "protection api");
        helper.succeed();
    }

    // ---- Fabric Permissions API ----

    @GameTest
    public void deniedIconPermissionGreysUseOffHand(GameTestHelper helper) {
        FakeIntegrations.register();
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "perm_noicon");
        FakeIntegrations.set(player, Permissions.ICON, TriState.FALSE);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE));
        EditorMenu menu = editor(helper, player);

        ItemStack button = menu.getSlot(EditorLayout.USE_OFFHAND).getItem();
        boolean greyed = button.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream()
                .map(Component::getString).anyMatch(s -> s.equals("Turned off on this server"));
        helper.assertTrue(greyed, "Use off-hand item should be greyed");
        click(menu, player, EditorLayout.USE_OFFHAND);
        helper.assertValueEqual(menu.session().staged().iconId(), Optional.empty(), "staged icon");
        helper.succeed();
    }

    @GameTest
    public void iconPermissionRevokedBeforeSaveRefusesTheIcon(GameTestHelper helper) {
        FakeIntegrations.register();
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "perm_revoked");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE));
        EditorMenu menu = editor(helper, player);

        click(menu, player, EditorLayout.USE_OFFHAND);
        helper.assertValueEqual(menu.session().staged().iconId(), Optional.of("minecraft:cobblestone"), "staged");
        FakeIntegrations.set(player, Permissions.ICON, TriState.FALSE);
        click(menu, player, EditorLayout.SAVE);

        ShulkerBoxBlockEntity box = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertValueEqual(StickerData.read(box).flatMap(s -> s.iconId()), Optional.empty(), "icon must not be saved");
        helper.succeed();
    }

    @GameTest
    public void deniedNameDisplaySavesTheNameHidden(GameTestHelper helper) {
        FakeIntegrations.register();
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "perm_nodisplay");
        FakeIntegrations.set(player, Permissions.NAME_DISPLAY, TriState.FALSE);
        EditorMenu menu = editor(helper, player);

        menu.session().stageName("Secret");
        click(menu, player, EditorLayout.SAVE);

        ShulkerBoxBlockEntity box = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        helper.assertValueEqual(box.getCustomName(), Component.literal("Secret"), "name saved");
        helper.assertTrue(StickerData.isNameHidden(box), "name_hidden should be set");
        helper.succeed();
    }

    @GameTest
    public void adminGrantedByThePermissionsModWithoutOp(GameTestHelper helper) {
        FakeIntegrations.register();
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "perm_admin_yes");
        CommandSourceStack source = player.createCommandSourceStack();

        helper.assertTrue(!canUseClear(helper, source), "not op, no grant: refused");
        FakeIntegrations.set(player, Permissions.ADMIN, TriState.TRUE);
        helper.assertTrue(canUseClear(helper, source), "granted: allowed without op");
        helper.succeed();
    }

    @GameTest
    public void adminDeniedByThePermissionsModEvenForOps(GameTestHelper helper) {
        FakeIntegrations.register();
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "perm_admin_no");
        CommandSourceStack op = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);

        helper.assertTrue(canUseClear(helper, op), "op with no answer: allowed (default = op level 2)");
        FakeIntegrations.set(player, Permissions.ADMIN, TriState.FALSE);
        helper.assertTrue(!canUseClear(helper, op), "denied: refused even for an op");
        helper.succeed();
    }

    @GameTest
    public void unansweredPlayerNodesDefaultToAllowed(GameTestHelper helper) {
        FakeIntegrations.register();
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "perm_default");

        for (String node : new String[] {Permissions.COLOR, Permissions.ICON, Permissions.NAME, Permissions.NAME_DISPLAY}) {
            helper.assertTrue(Perms.has(player, node), node + " should default to allowed");
        }
        helper.assertTrue(!Perms.has(player, Permissions.ADMIN), "admin should default to op only");
        helper.succeed();
    }

    @GameTest
    public void withoutThePermissionsModEveryoneHasPlayerNodesAndAdminNeedsOp(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "perm_vanilla");

        helper.assertTrue(Perms.vanillaHas(player.permissions(), Permissions.ICON), "player node");
        helper.assertTrue(!Perms.vanillaHas(player.permissions(), Permissions.ADMIN), "admin without op");
        helper.assertTrue(Perms.vanillaHas(LevelBasedPermissionSet.GAMEMASTER, Permissions.ADMIN), "admin with op level 2");
        helper.succeed();
    }

    // ---- Common Protection API ----

    @GameTest
    public void claimedBoxRefusesTheGesture(GameTestHelper helper) {
        FakeIntegrations.register();
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "claim_gesture");
        BlockPos pos = helper.absolutePos(BOX);
        FakeIntegrations.PROTECTED.add(pos);

        helper.assertValueEqual(Access.denyReason(player, helper.getLevel(), pos, StickerConfig.DEFAULTS),
                Optional.of(TextKeys.DENY_PROTECTED), "deny reason");
        helper.assertTrue(!EditorMenu.tryOpen(player, helper.getLevel(), pos, Direction.UP), "editor must not open");
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "no menu open");
        FakeIntegrations.PROTECTED.remove(pos);
        helper.succeed();
    }

    @GameTest
    public void boxClaimedAfterOpeningRefusesSave(GameTestHelper helper) {
        FakeIntegrations.register();
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ServerPlayer player = TestPlayers.make(helper, net.minecraft.world.level.GameType.SURVIVAL, "claim_save");
        BlockPos pos = helper.absolutePos(BOX);
        EditorMenu menu = editor(helper, player);

        click(menu, player, BoxColor.RED.ordinal());
        FakeIntegrations.PROTECTED.add(pos);
        click(menu, player, EditorLayout.SAVE);

        helper.assertBlockState(BOX, s -> s.is(net.minecraft.world.level.block.Blocks.SHULKER_BOX),
                s -> Component.literal("box must stay undyed, was " + s));
        FakeIntegrations.PROTECTED.remove(pos);
        helper.succeed();
    }
}
