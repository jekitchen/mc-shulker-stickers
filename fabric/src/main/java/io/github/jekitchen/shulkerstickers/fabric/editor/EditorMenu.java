package io.github.jekitchen.shulkerstickers.fabric.editor;

import io.github.jekitchen.shulkerstickers.core.Anchor;
import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.BoxState;
import io.github.jekitchen.shulkerstickers.core.Changes;
import io.github.jekitchen.shulkerstickers.core.EditorSession;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.Recolor;
import io.github.jekitchen.shulkerstickers.fabric.data.Sticker;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * The Sticker editor (PRD §5.2): a server-side 3-row chest menu whose slots are all
 * display-only buttons. Changes are staged in an {@link EditorSession} and applied on Save.
 */
public final class EditorMenu extends ChestMenu {

    public static final int PREVIEW_SLOT = 18;
    public static final int USE_OFF_HAND_SLOT = 19;
    public static final int REMOVE_ICON_SLOT = 20;
    public static final int RENAME_SLOT = 21;
    public static final int CANCEL_SLOT = 25;
    public static final int SAVE_SLOT = 26;
    private static final int MENU_SIZE = 27;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction clickedFace;
    private final EditorSession session;
    private final SimpleContainer buttons;
    private final Optional<Sticker> originalSticker;
    private Anchor stagedAnchor;
    private int stagedRot;
    private boolean finished;
    private boolean switchingToRename;

    private EditorMenu(int containerId, ServerPlayer player, ServerLevel level, BlockPos pos, Direction clickedFace,
            EditorSession session, Optional<Sticker> originalSticker, SimpleContainer buttons) {
        super(MenuType.GENERIC_9x3, containerId, player.getInventory(), buttons, 3);
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.clickedFace = clickedFace;
        this.session = session;
        this.buttons = buttons;
        this.originalSticker = originalSticker;
        this.stagedAnchor = originalSticker.map(Sticker::anchor).orElse(Anchor.LID);
        this.stagedRot = originalSticker.map(Sticker::rot).orElse(0);
        refreshButtons();
    }

    /** Builds an editor for the box at {@code pos}, opened by clicking {@code clickedFace}. */
    public static EditorMenu forBox(int containerId, ServerPlayer player, ServerLevel level, BlockPos pos, Direction clickedFace) {
        return forSession(containerId, player, level, pos, clickedFace, new EditorSession(readState(level, pos)));
    }

    private static EditorMenu forSession(int containerId, ServerPlayer player, ServerLevel level, BlockPos pos,
            Direction clickedFace, EditorSession session) {
        Optional<Sticker> sticker = level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box
                ? StickerData.read(box) : Optional.empty();
        return new EditorMenu(containerId, player, level, pos, clickedFace, session, sticker, new SimpleContainer(MENU_SIZE));
    }

    /** Opens the editor on the player's screen. */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos, Direction clickedFace) {
        open(player, level, pos, clickedFace, new EditorSession(readState(level, pos)));
    }

    static void open(ServerPlayer player, ServerLevel level, BlockPos pos, Direction clickedFace, EditorSession session) {
        String title = session.staged().name().orElse("Shulker Box");
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> forSession(id, player, level, pos, clickedFace, session),
                Component.literal("Edit: " + title)));
    }

    static BoxState readState(ServerLevel level, BlockPos pos) {
        BoxColor color = Boxes.color(level.getBlockState(pos).getBlock()).orElse(BoxColor.UNDYED);
        if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)) {
            return new BoxState(color, Optional.empty(), Optional.empty());
        }
        Optional<String> name = Optional.ofNullable(box.getCustomName()).map(Component::getString);
        return new BoxState(color, StickerData.read(box).map(Sticker::iconId), name);
    }

    public static int colorSlot(BoxColor color) {
        return color.ordinal();
    }

    public EditorSession session() {
        return session;
    }

    public boolean isButton(int slot) {
        return slot >= 0 && slot < MENU_SIZE;
    }

    // ---- Click handling: only plain clicks on our own slots do anything (PRD §5.2, AC-12) ----

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player clicker) {
        try {
            if (input == ContainerInput.PICKUP && (buttonNum == 0 || buttonNum == 1) && isButton(slotIndex)) {
                onButton(slotIndex);
            }
        } finally {
            // Undo anything the client predicted locally.
            if (!finished && !switchingToRename) {
                sendAllDataToRemote();
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player clicker, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player clicker) {
        return level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock
                && clicker.isWithinBlockInteractionRange(pos, 1.0);
    }

    @Override
    public void removed(Player clicker) {
        super.removed(clicker);
        if (!finished && !switchingToRename && session.isDirty()) {
            player.sendOverlayMessage(Component.literal("Changes discarded"));
        }
    }

    private void onButton(int slot) {
        if (slot < BoxColor.values().length) {
            session.stageColor(BoxColor.values()[slot]);
        } else if (slot == USE_OFF_HAND_SLOT) {
            useOffHand();
        } else if (slot == REMOVE_ICON_SLOT) {
            session.removeIcon();
        } else if (slot == RENAME_SLOT) {
            switchingToRename = true;
            RenameMenu.open(player, session.staged().name().orElse(""), name -> {
                session.stageName(name);
                open(player, level, pos, clickedFace, session);
            }, () -> open(player, level, pos, clickedFace, session));
            return;
        } else if (slot == CANCEL_SLOT) {
            finish();
            return;
        } else if (slot == SAVE_SLOT) {
            save();
            return;
        }
        refreshButtons();
    }

    private void useOffHand() {
        ItemStack offHand = player.getOffhandItem();
        if (offHand.isEmpty()) {
            return;
        }
        session.stageIcon(BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString());
        Direction facing = level.getBlockState(pos).getValue(ShulkerBoxBlock.FACING);
        stagedAnchor = Anchor.of(Boxes.face(facing), Boxes.face(clickedFace));
        stagedRot = stagedAnchor == Anchor.SIDE
                ? DisplayFaces.sideIndex(facing, clickedFace)
                : player.getDirection().get2DDataValue();
    }

    private void save() {
        if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)
                || Boxes.color(level.getBlockState(pos).getBlock()).orElse(null) != session.original().color()) {
            player.sendOverlayMessage(Component.literal("The box changed while you were editing it"));
            finish();
            return;
        }
        Changes changes = session.changes();
        BoxState staged = session.staged();
        if (changes.icon() || stagedAnchor != originalSticker.map(Sticker::anchor).orElse(stagedAnchor)) {
            StickerData.write(box, staged.iconId().map(id -> new Sticker(id, "", stagedAnchor, stagedRot)));
        }
        if (changes.name()) {
            BoxEdits.setName(box, staged.name().map(Component::literal).orElse(null));
        }
        if (changes.color() && Recolor.swap(level, pos, staged.color()) == Recolor.Result.IN_USE) {
            player.sendOverlayMessage(Component.literal("Box is in use: color not changed"));
        }
        StickerDisplays.refresh(level, pos);
        finish();
    }

    private void finish() {
        finished = true;
        if (player.containerMenu == this) {
            player.closeContainer();
        }
    }

    // ---- Button items ----

    private void refreshButtons() {
        BoxState staged = session.staged();
        for (int i = 0; i < MENU_SIZE; i++) {
            buttons.setItem(i, button(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " "));
        }
        for (BoxColor color : BoxColor.values()) {
            ItemStack stack = button(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(color.itemId())),
                    label(color));
            if (color == staged.color()) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            buttons.setItem(colorSlot(color), stack);
        }

        ItemStack preview = button(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(staged.color().itemId())),
                staged.name().orElse("Shulker Box"));
        preview.set(DataComponents.LORE, new ItemLore(List.of(plain("Icon: " + staged.iconId().orElse("none")))));
        buttons.setItem(PREVIEW_SLOT, preview);

        ItemStack offHand = player.getOffhandItem();
        if (offHand.isEmpty()) {
            buttons.setItem(USE_OFF_HAND_SLOT, button(Items.DYE.pick(DyeColor.LIGHT_GRAY), "Use off-hand item (off-hand is empty)"));
        } else {
            ItemStack use = offHand.copyWithCount(1);
            use.set(DataComponents.CUSTOM_NAME, plain("Use off-hand item"));
            buttons.setItem(USE_OFF_HAND_SLOT, use);
        }
        buttons.setItem(REMOVE_ICON_SLOT, button(Items.BARRIER, "Remove icon"));
        buttons.setItem(RENAME_SLOT, button(Items.NAME_TAG, "Rename…"));
        buttons.setItem(CANCEL_SLOT, button(Items.CONCRETE.pick(DyeColor.RED), "Cancel"));
        buttons.setItem(SAVE_SLOT, button(Items.CONCRETE.pick(DyeColor.LIME), "Save"));
        broadcastChanges();
    }

    private static String label(BoxColor color) {
        if (color.dyeName() == null) {
            return "Undyed";
        }
        String spaced = color.dyeName().replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static ItemStack button(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, plain(name));
        return stack;
    }

    private static Component plain(String text) {
        return Component.literal(text).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.WHITE));
    }
}
