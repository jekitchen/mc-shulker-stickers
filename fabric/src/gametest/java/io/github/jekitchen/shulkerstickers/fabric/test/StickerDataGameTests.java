package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public class StickerDataGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    @GameTest
    public void stickerIsStoredInPublicBukkitValues(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);

        var tag = box.components().get(DataComponents.CUSTOM_DATA).copyTag();
        var bukkit = tag.getCompoundOrEmpty("PublicBukkitValues");
        helper.assertValueEqual(bukkit.getIntOr("shulker_stickers:v", -1), 1, "format version");
        helper.assertValueEqual(bukkit.getStringOr("shulker_stickers:icon_id", ""), "minecraft:cobblestone", "icon_id");
        helper.assertValueEqual(bukkit.getStringOr("shulker_stickers:anchor", ""), "lid", "anchor");
        helper.assertValueEqual(bukkit.getIntOr("shulker_stickers:rot", -1), 2, "rot");
        helper.succeed();
    }

    @GameTest
    public void writingKeepsOtherPluginsData(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        var withOther = box.components().get(DataComponents.CUSTOM_DATA).update(t -> {
            var bukkit = t.getCompoundOrEmpty("PublicBukkitValues");
            bukkit.putString("otherplugin:key", "keep me");
            t.put("PublicBukkitValues", bukkit);
            t.putString("toplevel", "keep me too");
        });
        box.setComponents(net.minecraft.core.component.DataComponentMap.builder()
                .addAll(box.components()).set(DataComponents.CUSTOM_DATA, withOther).build());

        StickerData.write(box, Optional.empty());

        var tag = box.components().get(DataComponents.CUSTOM_DATA).copyTag();
        helper.assertValueEqual(tag.getStringOr("toplevel", ""), "keep me too", "top-level key");
        var bukkit = tag.getCompoundOrEmpty("PublicBukkitValues");
        helper.assertValueEqual(bukkit.getStringOr("otherplugin:key", ""), "keep me", "other plugin key");
        helper.assertTrue(!bukkit.contains("shulker_stickers:icon_id"), "icon should be removed");
        helper.succeed();
    }

    @GameTest
    public void dropCopiesOnlyOurKeys(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        StickerData.setNameHidden(box, true);
        var withOther = box.components().get(DataComponents.CUSTOM_DATA).update(t -> {
            var bukkit = t.getCompoundOrEmpty("PublicBukkitValues");
            bukkit.putString("otherplugin:key", "placement id");
            t.put("PublicBukkitValues", bukkit);
            t.putString("othermod", "placement id");
        });
        box.setComponents(net.minecraft.core.component.DataComponentMap.builder()
                .addAll(box.components()).set(DataComponents.CUSTOM_DATA, withOther).build());

        helper.getLevel().destroyBlock(helper.absolutePos(BOX), true);

        ItemStack dropped = helper.getEntities(EntityTypes.ITEM, BOX, 2.0).getFirst().getItem();
        var tag = dropped.get(DataComponents.CUSTOM_DATA).copyTag();
        var bukkit = tag.getCompoundOrEmpty("PublicBukkitValues");
        helper.assertValueEqual(StickerData.read(dropped), Optional.of(TestBoxes.STICKER), "sticker on the drop");
        helper.assertValueEqual(bukkit.getIntOr("shulker_stickers:name_hidden", 0), 1, "name_hidden on the drop");
        helper.assertTrue(!bukkit.contains("otherplugin:key"), "another plugin's key is not copied (vanilla behavior)");
        helper.assertTrue(!tag.contains("othermod"), "another mod's data is not copied (vanilla behavior)");
        helper.succeed();
    }

    @GameTest
    public void stickerSurvivesBreakAndPlace(GameTestHelper helper) {
        TestBoxes.place(helper, BOX, BoxColor.RED, Direction.NORTH);

        helper.getLevel().destroyBlock(helper.absolutePos(BOX), true);

        List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, BOX, 2.0);
        helper.assertValueEqual(drops.size(), 1, "dropped item entities");
        ItemStack dropped = drops.getFirst().getItem();
        helper.assertValueEqual(StickerData.read(dropped), Optional.of(TestBoxes.STICKER), "sticker on dropped item");

        // Place it again the way a player would: the item's components go onto the new block entity.
        helper.setBlock(BOX, ((net.minecraft.world.item.BlockItem) dropped.getItem()).getBlock()
                .defaultBlockState().setValue(ShulkerBoxBlock.FACING, Direction.NORTH));
        ShulkerBoxBlockEntity placed = helper.getBlockEntity(BOX, ShulkerBoxBlockEntity.class);
        placed.applyComponentsFromItemStack(dropped);
        TestBoxes.assertLoaded(helper, BOX, BoxColor.RED, Direction.NORTH);
        helper.succeed();
    }
}
