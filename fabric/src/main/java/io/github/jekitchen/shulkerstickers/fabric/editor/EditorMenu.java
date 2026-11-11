package io.github.jekitchen.shulkerstickers.fabric.editor;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import io.github.jekitchen.shulkerstickers.core.BoxState;
import io.github.jekitchen.shulkerstickers.core.Changes;
import io.github.jekitchen.shulkerstickers.core.EditorSession;
import io.github.jekitchen.shulkerstickers.core.Permissions;
import io.github.jekitchen.shulkerstickers.core.config.Cost;
import io.github.jekitchen.shulkerstickers.core.config.StickerConfig;
import io.github.jekitchen.shulkerstickers.core.data.Sticker;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout;
import io.github.jekitchen.shulkerstickers.core.editor.EditorLayout.Button;
import io.github.jekitchen.shulkerstickers.core.rules.BlockedWords;
import io.github.jekitchen.shulkerstickers.core.rules.Costs;
import io.github.jekitchen.shulkerstickers.core.rules.IconRules;
import io.github.jekitchen.shulkerstickers.core.rules.NameRules;
import io.github.jekitchen.shulkerstickers.core.text.TextKeys;
import io.github.jekitchen.shulkerstickers.fabric.Access;
import io.github.jekitchen.shulkerstickers.fabric.Boxes;
import io.github.jekitchen.shulkerstickers.fabric.Perms;
import io.github.jekitchen.shulkerstickers.fabric.Recolor;
import io.github.jekitchen.shulkerstickers.fabric.Settings;
import io.github.jekitchen.shulkerstickers.fabric.Text;
import io.github.jekitchen.shulkerstickers.fabric.data.Icons;
import io.github.jekitchen.shulkerstickers.fabric.data.StickerData;
import io.github.jekitchen.shulkerstickers.fabric.display.StickerDisplays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The Sticker editor (PRD §5.2): a server-side 3-row chest menu whose slots are all
 * display-only buttons. Changes are staged and applied together on Save.
 */
public final class EditorMenu extends ChestMenu {

    /** What survives switching to the rename screen and back. */
    static final class EditState {
        final EditorSession session;
        final Direction clickedFace;
        final StickerConfig config;
        final Optional<Sticker> originalSticker;
        /** Sticker keys are present but this version can't read them; never overwrite them by accident. */
        final boolean unreadableSticker;
        /** The block entity the editor opened on; Save refuses if it was replaced. */
        final @Nullable ShulkerBoxBlockEntity originalBox;
        Optional<Placement> placement;

        EditState(EditorSession session, Direction clickedFace, StickerConfig config, Optional<Sticker> originalSticker,
                boolean unreadableSticker, @Nullable ShulkerBoxBlockEntity originalBox) {
            this.session = session;
            this.clickedFace = clickedFace;
            this.config = config;
            this.originalSticker = originalSticker;
            this.unreadableSticker = unreadableSticker;
            this.originalBox = originalBox;
            this.placement = originalSticker.map(Placement::of);
        }
    }

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos pos;
    private final EditState state;
    private final SimpleContainer buttons;
    private boolean finished;
    private boolean switchingToRename;

    private EditorMenu(int containerId, ServerPlayer player, ServerLevel level, BlockPos pos, EditState state) {
        super(MenuType.GENERIC_9x3, containerId, player.getInventory(), new SimpleContainer(EditorLayout.SIZE), 3);
        this.player = player;
        this.level = level;
        this.pos = pos.immutable();
        this.state = state;
        this.buttons = (SimpleContainer) getContainer();
        refreshButtons();
    }

    /** Builds an editor for the box at {@code pos} without opening it (tests use this). */
    public static EditorMenu forBox(int containerId, ServerPlayer player, ServerLevel level, BlockPos pos,
            Direction clickedFace, StickerConfig config) {
        return new EditorMenu(containerId, player, level, pos, newState(level, pos, clickedFace, config));
    }

    public static EditorMenu forBox(int containerId, ServerPlayer player, ServerLevel level, BlockPos pos, Direction clickedFace) {
        return forBox(containerId, player, level, pos, clickedFace, Settings.config());
    }

    private static EditState newState(ServerLevel level, BlockPos pos, Direction clickedFace, StickerConfig config) {
        ShulkerBoxBlockEntity box = level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity b ? b : null;
        Optional<Sticker> sticker = box == null ? Optional.empty() : StickerData.read(box);
        boolean unreadable = box != null && sticker.isEmpty() && !StickerData.rawValues(box).isEmpty();
        return new EditState(new EditorSession(readState(level, pos)), clickedFace, config, sticker, unreadable, box);
    }

    /**
     * Opens the editor if the player may edit this box and nobody else is editing it; otherwise
     * shows the reason in the action bar. Returns true if the editor opened.
     */
    public static boolean tryOpen(ServerPlayer player, ServerLevel level, BlockPos pos, Direction clickedFace) {
        StickerConfig config = Settings.config();
        Optional<String> deny = Access.denyReason(player, level, pos, config);
        if (deny.isPresent()) {
            player.sendOverlayMessage(Text.tr(player, deny.get()));
            return false;
        }
        Optional<String> other = EditorLocks.tryAcquire(level, pos, player);
        if (other.isPresent()) {
            player.sendOverlayMessage(Text.tr(player, TextKeys.DENY_IN_USE_EDITOR, other.get()));
            return false;
        }
        open(player, level, pos, newState(level, pos, clickedFace, config));
        Feedback.opened(player);
        return true;
    }

    private static void open(ServerPlayer player, ServerLevel level, BlockPos pos, EditState state) {
        String name = state.session.staged().name().orElse(Text.string(player, TextKeys.EDITOR_TITLE_UNNAMED));
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new EditorMenu(id, player, level, pos, state),
                Text.tr(player, TextKeys.EDITOR_TITLE, name)));
    }

    static BoxState readState(ServerLevel level, BlockPos pos) {
        BoxColor color = Boxes.color(level.getBlockState(pos).getBlock()).orElse(BoxColor.UNDYED);
        if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)) {
            return new BoxState(color, Optional.empty(), Optional.empty());
        }
        Optional<String> name = Optional.ofNullable(box.getCustomName()).map(Component::getString);
        Optional<Sticker> sticker = StickerData.read(box);
        return new BoxState(color, sticker.flatMap(Sticker::iconId), sticker.flatMap(Sticker::iconVisual), name);
    }

    /** With color, icon and name editing all turned off, the gesture does nothing (PRD §5.1). */
    public static boolean anyEditingEnabled(StickerConfig config) {
        return config.colorEnabled() || config.iconEnabled() || config.nameEditEnabled();
    }

    public EditorSession session() {
        return state.session;
    }

    public boolean isButton(int slot) {
        return EditorLayout.isMenuSlot(slot);
    }

    // ---- Click handling: only plain clicks on our own slots do anything (PRD §5.2, AC-12) ----

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player clicker) {
        try {
            if (input == ContainerInput.PICKUP && (buttonNum == 0 || buttonNum == 1)) {
                EditorLayout.buttonAt(slotIndex).ifPresent(this::onButton);
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
        return clicker.level() == level
                && level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock
                && clicker.isWithinBlockInteractionRange(pos, 1.0);
    }

    @Override
    public void removed(Player clicker) {
        super.removed(clicker);
        if (switchingToRename) {
            return;
        }
        EditorLocks.release(level, pos, player);
        if (!finished && state.session.isDirty()) {
            player.sendOverlayMessage(Text.tr(player, TextKeys.EDITOR_DISCARDED));
        }
    }

    private boolean colorAllowed() {
        return state.config.colorEnabled() && Perms.has(player, Permissions.COLOR);
    }

    private boolean iconAllowed() {
        return state.config.iconEnabled() && Perms.has(player, Permissions.ICON);
    }

    private boolean nameAllowed() {
        return state.config.nameEditEnabled() && Perms.has(player, Permissions.NAME);
    }

    private void onButton(Button button) {
        switch (button) {
            case Button.Color c -> {
                if (colorAllowed()) {
                    state.session.stageColor(c.color());
                }
            }
            case Button.Action a -> {
                switch (a) {
                    case PREVIEW -> { }
                    case USE_OFFHAND -> useOffHand();
                    case REMOVE_ICON -> {
                        if (iconAllowed()) {
                            state.session.removeIcon();
                        }
                    }
                    case RENAME -> {
                        if (nameAllowed()) {
                            openRename();
                            return;
                        }
                    }
                    case CANCEL -> {
                        Feedback.closed(player);
                        finish();
                        return;
                    }
                    case SAVE -> {
                        save();
                        return;
                    }
                }
            }
        }
        refreshButtons();
    }

    private void openRename() {
        switchingToRename = true;
        RenameMenu.open(player, state.session.staged().name().orElse(""), name -> {
            state.session.stageName(NameRules.clamp(name));
            reopen();
        }, this::reopen);
    }

    /**
     * Back from the rename screen. Runs a tick later (see {@link RenameMenu}), after vanilla has
     * finished closing the rename screen. If the player left or another menu took over, the
     * editing session ends here.
     */
    private void reopen() {
        if (player.containerMenu instanceof EditorMenu newer && newer != this
                && newer.level == level && newer.pos.equals(pos)) {
            // The player already reopened this box's editor; that one owns the lock now.
            return;
        }
        boolean free = player.containerMenu == player.inventoryMenu || player.containerMenu instanceof RenameMenu;
        if (player.hasDisconnected() || !free) {
            EditorLocks.release(level, pos, player);
            return;
        }
        open(player, level, pos, state);
    }

    private void useOffHand() {
        ItemStack offHand = player.getOffhandItem();
        if (!iconAllowed() || offHand.isEmpty()) {
            return;
        }
        if (Icons.isBlocked(offHand, state.config)) {
            player.sendOverlayMessage(Text.tr(player, TextKeys.SAVE_ICON_BLOCKED));
            return;
        }
        String id = Icons.idOf(offHand);
        Optional<String> visual = Icons.visualOf(offHand, level.registryAccess());
        if (!IconRules.fitsSize(new Sticker(Optional.of(id), visual, io.github.jekitchen.shulkerstickers.core.Anchor.LID, 0, Optional.empty()),
                state.config.iconMaxDataBytes())) {
            player.sendOverlayMessage(Text.tr(player, TextKeys.SAVE_ICON_TOO_LARGE));
            return;
        }
        state.session.stageIcon(id, visual);
        state.placement = Optional.of(clickedPlacement());
    }

    private Placement clickedPlacement() {
        Direction facing = level.getBlockState(pos).getValue(ShulkerBoxBlock.FACING);
        return Placement.clicked(facing, state.clickedFace, player.getDirection());
    }

    /** The sticker to store for the staged state: an icon, or just a face for the name, or nothing. */
    private Optional<Sticker> stagedSticker() {
        BoxState staged = state.session.staged();
        if (staged.iconId().isEmpty() && staged.name().isEmpty()) {
            return Optional.empty();
        }
        Placement placement = state.placement.orElseGet(this::clickedPlacement);
        return Optional.of(placement.sticker(staged.iconId(), staged.iconVisual()));
    }

    /** PRD §6.3. Failures leave the editor open with the reason in the action bar. */
    private void save() {
        EditorSession session = state.session;
        if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)
                || box != state.originalBox
                || Boxes.color(level.getBlockState(pos).getBlock()).orElse(null) != session.original().color()) {
            player.sendOverlayMessage(Text.tr(player, TextKeys.SAVE_BOX_GONE));
            finish();
            return;
        }
        Optional<String> deny = Access.denyReason(player, level, pos, state.config);
        if (deny.isPresent()) {
            player.sendOverlayMessage(Text.tr(player, deny.get()));
            return;
        }
        Changes changes = session.changes();
        BoxState staged = session.staged();
        // Permissions or config may have changed since a part was staged (e.g. a permissions
        // mod revoked a node): every changed part must still be allowed.
        if ((changes.color() && !colorAllowed()) || (changes.icon() && !iconAllowed()) || (changes.name() && !nameAllowed())) {
            player.sendOverlayMessage(Text.tr(player, TextKeys.DENY_PERMISSION));
            return;
        }
        if (changes.name() && staged.name().isPresent()) {
            BlockedWords words = Settings.loaded().config() == state.config
                    ? Settings.loaded().blockedWords() : BlockedWords.compile(state.config.nameBlockedWords());
            if (words.firstMatch(staged.name().get()).isPresent()) {
                player.sendOverlayMessage(Text.tr(player, TextKeys.SAVE_BLOCKED_WORD));
                return;
            }
        }
        Optional<Sticker> sticker = stagedSticker();
        if (changes.icon() && sticker.isPresent() && sticker.get().hasIcon()) {
            if (!IconRules.fitsSize(sticker.get(), state.config.iconMaxDataBytes())) {
                player.sendOverlayMessage(Text.tr(player, TextKeys.SAVE_ICON_TOO_LARGE));
                return;
            }
            if (Icons.displayStack(sticker.get(), state.config, level.registryAccess()).isEmpty()) {
                player.sendOverlayMessage(Text.tr(player, TextKeys.SAVE_ICON_BLOCKED));
                return;
            }
        }
        List<Cost> costs = Costs.forChanges(changes, state.config);
        if (!Payments.canPay(player, costs)) {
            player.sendOverlayMessage(Text.tr(player, TextKeys.SAVE_CANNOT_PAY, Payments.describe(player, costs)));
            return;
        }

        // Data this version can't read is kept unless the player replaced or removed the icon.
        boolean keepUnreadable = state.unreadableSticker && !changes.icon();
        // Only an icon or name change moves where things are drawn; a color-only Save
        // leaves an anvil name on the lid with no sticker keys (matches Paper).
        boolean placementChanged = changes.icon() || changes.name();
        if (!keepUnreadable && placementChanged && !sticker.equals(state.originalSticker)) {
            StickerData.write(box, sticker);
        }
        if (changes.name()) {
            BoxEdits.setName(box, staged.name().map(Component::literal).orElse(null));
            StickerData.setNameHidden(box, staged.name().isPresent() && !Perms.has(player, Permissions.NAME_DISPLAY));
        }
        boolean colorApplied = !changes.color();
        if (changes.color()) {
            colorApplied = Recolor.swap(level, pos, staged.color()) != Recolor.Result.IN_USE;
            if (!colorApplied) {
                player.sendOverlayMessage(Text.tr(player, TextKeys.SAVE_BOX_IN_USE));
            }
        }
        Payments.charge(player, Costs.forChanges(
                new Changes(changes.color() && colorApplied, changes.icon(), changes.name()), state.config));
        StickerDisplays.refresh(level, pos);
        Feedback.closed(player);
        finish();
    }

    private void finish() {
        finished = true;
        if (player.containerMenu == this) {
            player.closeContainer();
        } else {
            EditorLocks.release(level, pos, player);
        }
    }

    // ---- Button items ----

    private void refreshButtons() {
        BoxState staged = state.session.staged();
        ItemStack filler = button(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), " ");
        for (int i = 0; i < EditorLayout.SIZE; i++) {
            buttons.setItem(i, filler.copy());
        }
        boolean colorAllowed = colorAllowed();
        for (BoxColor color : BoxColor.values()) {
            ItemStack stack = button(Boxes.item(color), Text.string(player, TextKeys.color(color)));
            if (color == staged.color()) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            if (!colorAllowed) {
                disabledLore(stack);
            }
            buttons.setItem(EditorLayout.slotOf(new Button.Color(color)), stack);
        }

        String unnamed = Text.string(player, TextKeys.EDITOR_TITLE_UNNAMED);
        ItemStack preview = button(Boxes.item(staged.color()), staged.name().orElse(unnamed));
        preview.set(DataComponents.LORE, new ItemLore(List.of(Text.grey(staged.iconId()
                .map(id -> Text.string(player, TextKeys.EDITOR_PREVIEW_ICON, id))
                .orElse(Text.string(player, TextKeys.EDITOR_PREVIEW_NO_ICON))))));
        buttons.setItem(EditorLayout.PREVIEW, preview);

        ItemStack offHand = player.getOffhandItem();
        ItemStack use;
        if (!iconAllowed()) {
            use = disabledLore(button(Items.DYE.pick(DyeColor.LIGHT_GRAY), Text.string(player, TextKeys.EDITOR_USE_OFFHAND)));
        } else if (offHand.isEmpty()) {
            use = button(Items.DYE.pick(DyeColor.LIGHT_GRAY), Text.string(player, TextKeys.EDITOR_USE_OFFHAND_EMPTY));
        } else if (Icons.isBlocked(offHand, state.config)) {
            use = button(Items.DYE.pick(DyeColor.LIGHT_GRAY), Text.string(player, TextKeys.EDITOR_USE_OFFHAND_BLOCKED));
        } else {
            use = offHand.copyWithCount(1);
            use.set(DataComponents.CUSTOM_NAME, Text.plain(Text.string(player, TextKeys.EDITOR_USE_OFFHAND)));
        }
        buttons.setItem(EditorLayout.USE_OFFHAND, use);

        ItemStack remove = button(Items.BARRIER, Text.string(player, TextKeys.EDITOR_REMOVE_ICON));
        buttons.setItem(EditorLayout.REMOVE_ICON, iconAllowed() ? remove : disabledLore(remove));
        ItemStack rename = button(Items.NAME_TAG, Text.string(player, TextKeys.EDITOR_RENAME));
        buttons.setItem(EditorLayout.RENAME, nameAllowed() ? rename : disabledLore(rename));
        buttons.setItem(EditorLayout.CANCEL, button(Items.CONCRETE.pick(DyeColor.RED), Text.string(player, TextKeys.EDITOR_CANCEL)));

        ItemStack save = button(Items.CONCRETE.pick(DyeColor.LIME), Text.string(player, TextKeys.EDITOR_SAVE));
        List<Cost> costs = player.isCreative() ? List.of() : Costs.forChanges(state.session.changes(), state.config);
        if (!costs.isEmpty()) {
            save.set(DataComponents.LORE, new ItemLore(List.of(
                    Text.grey(Text.string(player, TextKeys.EDITOR_SAVE_COST, Payments.describe(player, costs))))));
        }
        buttons.setItem(EditorLayout.SAVE, save);
        broadcastChanges();
    }

    private ItemStack disabledLore(ItemStack stack) {
        List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.add(Text.grey(Text.string(player, TextKeys.EDITOR_FEATURE_DISABLED)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack button(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Text.plain(name));
        return stack;
    }
}
