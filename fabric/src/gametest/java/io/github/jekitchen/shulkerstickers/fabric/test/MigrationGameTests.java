package io.github.jekitchen.shulkerstickers.fabric.test;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Owner decision 4: a box saved by Paper keeps its stickers when the world moves to Fabric. */
public class MigrationGameTests {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    /** Saves the box, lets {@code edit} turn the NBT into what Paper would have written, and loads it again. */
    private static ShulkerBoxBlockEntity reload(GameTestHelper helper, ShulkerBoxBlockEntity box,
            java.util.function.Consumer<CompoundTag> edit) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = box.saveWithFullMetadata(registries);
        edit.accept(saved);
        return (ShulkerBoxBlockEntity) BlockEntity.loadStatic(box.getBlockPos(), box.getBlockState(), saved, registries);
    }

    @GameTest
    public void paperRootValuesReplaceTheStaleItemCopy(GameTestHelper helper) {
        // custom_data holds the cobblestone sticker (the item it was placed from); Paper's live data says diamond.
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ShulkerBoxBlockEntity loaded = reload(helper, box, tag -> {
            CompoundTag paper = new CompoundTag();
            paper.putInt("shulker_stickers:v", 1);
            paper.putString("shulker_stickers:icon_id", "minecraft:diamond");
            paper.putString("shulker_stickers:anchor", "side");
            paper.putInt("shulker_stickers:rot", 0);
            paper.putString("shulker_stickers:face", "east");
            paper.putInt("shulker_stickers:name_hidden", 1);
            paper.putString("otherplugin:key", "theirs");
            tag.put("PublicBukkitValues", paper);
        });

        Optional<Sticker> sticker = StickerData.read(loaded);
        helper.assertValueEqual(sticker.flatMap(Sticker::iconId), Optional.of("minecraft:diamond"), "Paper's icon wins");
        helper.assertValueEqual(sticker.map(s -> s.anchor().name()), Optional.of("SIDE"), "anchor");
        helper.assertTrue(StickerData.isNameHidden(loaded), "name_hidden carried over");
        helper.succeed();
    }

    @GameTest
    public void paperRootWithoutOurKeysClearsTheStaleCopy(GameTestHelper helper) {
        // On Paper the sticker was removed; only the stale item copy is left in custom_data.
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ShulkerBoxBlockEntity loaded = reload(helper, box, tag -> {
            CompoundTag paper = new CompoundTag();
            paper.putString("otherplugin:key", "theirs");
            tag.put("PublicBukkitValues", paper);
        });
        helper.assertValueEqual(StickerData.read(loaded), Optional.empty(), "no sticker, as Paper showed");
        helper.succeed();
    }

    @GameTest
    public void fabricSavedBoxIsUnchanged(GameTestHelper helper) {
        ShulkerBoxBlockEntity box = TestBoxes.place(helper, BOX, BoxColor.UNDYED, Direction.UP);
        ShulkerBoxBlockEntity loaded = reload(helper, box, tag -> {});
        helper.assertValueEqual(StickerData.read(loaded), Optional.of(TestBoxes.STICKER), "sticker kept");
        helper.succeed();
    }
}
