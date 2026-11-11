package io.github.jekitchen.shulkerstickers.fabric.editor;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Text input for renaming (PRD §5.4): a vanilla anvil screen with no anvil block, no item cost
 * and no XP. The client sends each edit as {@code ServerboundRenameItemPacket}, which the server
 * passes to {@link #setItemName}. Clicking the result slot confirms; closing cancels.
 */
public final class RenameMenu extends AnvilMenu {

    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    private String typed;
    private boolean done;

    private RenameMenu(int containerId, ServerPlayer player, String current, Consumer<String> onConfirm, Runnable onCancel) {
        // ContainerLevelAccess.NULL: no anvil block, so stillValid() is always true.
        super(containerId, player.getInventory(), ContainerLevelAccess.NULL);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.typed = current;
        ItemStack paper = new ItemStack(Items.PAPER);
        if (!current.isEmpty()) {
            paper.set(DataComponents.CUSTOM_NAME, Component.literal(current));
        }
        // Display-only: cleared in removed() so it never reaches the player's inventory.
        inputSlots.setItem(0, paper);
    }

    public static void open(ServerPlayer player, String current, Consumer<String> onConfirm, Runnable onCancel) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new RenameMenu(id, player, current, onConfirm, onCancel),
                io.github.jekitchen.shulkerstickers.fabric.Text.tr(player, io.github.jekitchen.shulkerstickers.core.text.TextKeys.RENAME_TITLE)));
    }

    @Override
    public boolean setItemName(String name) {
        boolean changed = super.setItemName(name);
        if (name.length() <= MAX_NAME_LENGTH) {
            typed = name;
        }
        return changed;
    }

    @Override
    public void createResult() {
        ItemStack result = inputSlots.getItem(0).copy();
        if (typed.isBlank()) {
            result.remove(DataComponents.CUSTOM_NAME);
        } else {
            result.set(DataComponents.CUSTOM_NAME, Component.literal(typed));
        }
        resultSlots.setItem(0, result);
        broadcastChanges();
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player clicker) {
        if (slotIndex == RESULT_SLOT && input == ContainerInput.PICKUP && !done) {
            done = true;
            onConfirm.accept(typed);
            return;
        }
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player clicker, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player clicker) {
        inputSlots.clearContent();
        resultSlots.clearContent();
        super.removed(clicker);
        if (!done) {
            done = true;
            onCancel.run();
        }
    }
}
