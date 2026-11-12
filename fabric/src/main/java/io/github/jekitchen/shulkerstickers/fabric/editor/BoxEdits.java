package io.github.jekitchen.shulkerstickers.fabric.editor;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** Small edits to a placed box's block entity. */
public final class BoxEdits {

    private BoxEdits() {
    }

    /**
     * Sets or removes the box's name. Block entities only take a name through
     * {@code applyComponents}, which also rewrites contents and lock, so the full current
     * component set is passed back in with only the name changed.
     */
    public static void setName(BlockEntity blockEntity, @Nullable Component name) {
        DataComponentMap current = blockEntity.collectComponents();
        DataComponentPatch.Builder patch = DataComponentPatch.builder();
        for (TypedDataComponent<?> component : current) {
            if (component.type() != DataComponents.CUSTOM_NAME) {
                patch.set(component);
            }
        }
        if (name != null) {
            patch.set(DataComponents.CUSTOM_NAME, name);
        }
        blockEntity.applyComponents(DataComponentMap.EMPTY, patch.build());
        blockEntity.setChanged();
    }
}
