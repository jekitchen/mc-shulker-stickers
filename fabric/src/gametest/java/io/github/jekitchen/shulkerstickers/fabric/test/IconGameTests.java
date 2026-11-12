package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.config.ConfigLoader;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.fabric.data.Icons;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.editor.EditorMenu;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Icon visual properties, the blocklist and the size cap (PRD §5.3). */
public class IconGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    /** The exact string Paper must also produce for the same item (cross-platform format). */
    static final String RED_HELMET_VISUAL = "{\"minecraft:dyed_color\":16711680}";

    private static StickerConfig config(String json) {
        return ConfigLoader.parse(json).config();
    }

    private static EditorMenu editor(GameTestHelper helper, ServerPlayer player, StickerConfig config) {
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        return EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), Direction.UP, config);
    }

    private static void click(EditorMenu menu, ServerPlayer player, int slot) {
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
    }

    @GameTest
    public void dyedHelmetStoresCanonicalVisual(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(0xFF0000));
        helmet.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.literal("not stored"))));
        player.setItemInHand(InteractionHand.OFF_HAND, helmet);
        EditorMenu menu = editor(helper, player, StickerConfig.DEFAULTS);

        click(menu, player, EditorLayout.USE_OFFHAND);
        click(menu, player, EditorLayout.SAVE);

        Optional<Sticker> sticker = StickerData.read(helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class));
        helper.assertValueEqual(sticker.flatMap(Sticker::iconId), Optional.of("minecraft:leather_helmet"), "icon id");
        helper.assertValueEqual(sticker.flatMap(Sticker::iconVisual), Optional.of(RED_HELMET_VISUAL), "icon visual");
        helper.succeed();
    }

    @GameTest
    public void enchantedItemKeepsGlintOnly(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        sword.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 3);

        Optional<String> visual = Icons.visualOf(sword, helper.getLevel().registryAccess());

        helper.assertValueEqual(visual, Optional.of("{\"minecraft:enchantment_glint_override\":true}"), "visual");
        helper.succeed();
    }

    @GameTest
    public void storedVisualIsDrawnBack(GameTestHelper helper) {
        Sticker sticker = new Sticker(Optional.of("minecraft:leather_helmet"), Optional.of(RED_HELMET_VISUAL), Anchor.LID, 0, Optional.empty());

        Optional<ItemStack> stack = Icons.displayStack(sticker, StickerConfig.DEFAULTS, helper.getLevel().registryAccess());

        helper.assertTrue(stack.isPresent(), "should draw");
        helper.assertValueEqual(stack.get().get(DataComponents.DYED_COLOR), new DyedItemColor(0xFF0000), "dyed color");
        helper.succeed();
    }

    @GameTest
    public void untrustedVisualDataIsRefilteredWhenDrawn(GameTestHelper helper) {
        // A box made with /give could carry any components; only visual ones are applied.
        Sticker sticker = new Sticker(Optional.of("minecraft:stone"),
                Optional.of("{\"minecraft:lore\":[\"x\"],\"minecraft:enchantment_glint_override\":true}"), Anchor.LID, 0, Optional.empty());

        ItemStack stack = Icons.displayStack(sticker, StickerConfig.DEFAULTS, helper.getLevel().registryAccess()).orElseThrow();

        helper.assertTrue(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().isEmpty(), "lore must not be applied");
        helper.assertValueEqual(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE), true, "glint");
        helper.succeed();
    }

    @GameTest
    public void unknownOrBrokenIconIsNotDrawn(GameTestHelper helper) {
        var regs = helper.getLevel().registryAccess();
        Sticker unknown = new Sticker(Optional.of("nomod:thing"), Optional.empty(), Anchor.LID, 0, Optional.empty());
        Sticker broken = new Sticker(Optional.of("minecraft:stone"), Optional.of("{not json"), Anchor.LID, 0, Optional.empty());
        Sticker badValue = new Sticker(Optional.of("minecraft:stone"), Optional.of("{\"minecraft:dyed_color\":\"red\"}"), Anchor.LID, 0, Optional.empty());

        helper.assertTrue(Icons.displayStack(unknown, StickerConfig.DEFAULTS, regs).isEmpty(), "unknown item");
        helper.assertTrue(Icons.displayStack(broken, StickerConfig.DEFAULTS, regs).isEmpty(), "broken json");
        helper.assertTrue(Icons.displayStack(badValue, StickerConfig.DEFAULTS, regs).isEmpty(), "bad component value");
        helper.succeed();
    }

    @GameTest
    public void blocklistedOffHandIsNotStaged(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT));
        EditorMenu menu = editor(helper, player, config("{\"icon\":{\"blocklist\":[\"dirt\"]}}"));

        click(menu, player, EditorLayout.USE_OFFHAND);

        helper.assertValueEqual(menu.session().staged().iconId(), Optional.empty(), "staged icon");
        helper.succeed();
    }

    @GameTest
    public void blocklistTagEntryIsHonored(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)));
        EditorMenu menu = editor(helper, player, config("{\"icon\":{\"blocklist\":[\"#minecraft:wool\"]}}"));

        click(menu, player, EditorLayout.USE_OFFHAND);

        helper.assertValueEqual(menu.session().staged().iconId(), Optional.empty(), "staged icon");
        helper.succeed();
    }

    @GameTest
    public void blocklistIsRecheckedWhenDrawn(GameTestHelper helper) {
        Sticker sticker = new Sticker(Optional.of("minecraft:dirt"), Optional.empty(), Anchor.LID, 0, Optional.empty());

        helper.assertTrue(Icons.displayStack(sticker, config("{\"icon\":{\"blocklist\":[\"minecraft:dirt\"]}}"),
                helper.getLevel().registryAccess()).isEmpty(), "blocked icon drawn");
        helper.succeed();
    }

    /** Security: an item_model can't make an allowed item look like a blocklisted one. */
    @GameTest
    public void itemModelOfABlockedItemIsNotDrawn(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        StickerConfig blocksDirt = config("{\"icon\":{\"blocklist\":[\"minecraft:dirt\"]}}");
        Sticker disguised = new Sticker(Optional.of("minecraft:stone"), Optional.of("{\"minecraft:item_model\":\"minecraft:dirt\"}"),
                Anchor.LID, 0, Optional.empty());
        helper.assertTrue(Icons.displayStack(disguised, blocksDirt, registries).isEmpty(), "stone drawn as blocked dirt");
        Sticker harmless = new Sticker(Optional.of("minecraft:stone"), Optional.of("{\"minecraft:item_model\":\"minecraft:cobblestone\"}"),
                Anchor.LID, 0, Optional.empty());
        helper.assertTrue(Icons.displayStack(harmless, blocksDirt, registries).isPresent(), "an allowed model is still drawn");
        helper.succeed();
    }

    /** Security: "Use off-hand" stores only visual components, however much else the held item carries. */
    @GameTest
    public void onlyVisualComponentsAreCaptured(GameTestHelper helper) {
        ItemStack heavy = new ItemStack(Items.LEATHER_HELMET);
        heavy.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0xFF0000));
        heavy.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                java.util.Collections.nCopies(200, net.minecraft.network.chat.Component.literal("x".repeat(100)))));
        helper.assertValueEqual(Icons.visualOf(heavy, helper.getLevel().registryAccess()),
                Optional.of("{\"minecraft:dyed_color\":16711680}"), "only the dye is kept");
        helper.succeed();
    }

    @GameTest
    public void oversizedIconIsRefused(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE));
        EditorMenu menu = editor(helper, player, config("{\"icon\":{\"max_data_bytes\":10}}"));

        click(menu, player, EditorLayout.USE_OFFHAND);

        helper.assertValueEqual(menu.session().staged().iconId(), Optional.empty(), "staged icon");
        Sticker big = new Sticker(Optional.of("minecraft:cobblestone"), Optional.empty(), Anchor.LID, 0, Optional.empty());
        helper.assertTrue(Icons.displayStack(big, config("{\"icon\":{\"max_data_bytes\":10}}"),
                helper.getLevel().registryAccess()).isEmpty(), "oversized icon drawn");
        helper.succeed();
    }

    @GameTest
    public void sideIconStoresItsExactFace(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE));
        TestBoxes.placePlain(helper, BOX, BoxColor.UNDYED, Direction.UP);
        EditorMenu menu = EditorMenu.forBox(1, player, helper.getLevel(), helper.absolutePos(BOX), Direction.EAST, StickerConfig.DEFAULTS);

        click(menu, player, EditorLayout.USE_OFFHAND);
        click(menu, player, EditorLayout.SAVE);

        Sticker sticker = StickerData.read(helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class)).orElseThrow();
        helper.assertValueEqual(sticker.anchor(), Anchor.SIDE, "anchor");
        helper.assertValueEqual(sticker.face(), Optional.of(io.github.jekitchen.shulkerstickers.core.Face.EAST), "face");
        helper.assertValueEqual(sticker.rot(), 0, "rot is only for top and bottom");
        helper.succeed();
    }
}
