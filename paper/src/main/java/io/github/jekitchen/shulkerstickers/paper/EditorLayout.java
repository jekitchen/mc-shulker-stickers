package io.github.jekitchen.shulkerstickers.paper;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import java.util.Optional;
import org.bukkit.event.inventory.ClickType;

/**
 * Slot layout of the 3-row Sticker editor (PRD §5.2).
 * TODO(core): layout and click policy are platform-free and belong in core.
 */
final class EditorLayout {

    static final int SIZE = 27;
    static final int PREVIEW = 18;
    static final int USE_OFFHAND = 20;
    static final int REMOVE_ICON = 21;
    static final int RENAME = 22;
    static final int CANCEL = 25;
    static final int SAVE = 26;

    sealed interface Button {
        record Color(BoxColor color) implements Button {}

        enum Preview implements Button { INSTANCE }

        enum UseOffhand implements Button { INSTANCE }

        enum RemoveIcon implements Button { INSTANCE }

        enum Rename implements Button { INSTANCE }

        enum Cancel implements Button { INSTANCE }

        enum Save implements Button { INSTANCE }
    }

    private EditorLayout() {}

    static Optional<Button> buttonAt(int slot) {
        if (slot >= 0 && slot < BoxColor.values().length) {
            return Optional.of(new Button.Color(BoxColor.values()[slot]));
        }
        return Optional.ofNullable(switch (slot) {
            case PREVIEW -> Button.Preview.INSTANCE;
            case USE_OFFHAND -> Button.UseOffhand.INSTANCE;
            case REMOVE_ICON -> Button.RemoveIcon.INSTANCE;
            case RENAME -> Button.Rename.INSTANCE;
            case CANCEL -> Button.Cancel.INSTANCE;
            case SAVE -> Button.Save.INSTANCE;
            default -> null;
        });
    }

    /**
     * Anti-duplication rule: only a plain left or right click on a slot of the menu itself
     * counts. Shift, number keys, off-hand swap, double-click, drops and clicks in the
     * player's inventory or outside the window are all rejected (AC-12).
     */
    static boolean isButtonPress(ClickType type, int rawSlot) {
        return (type == ClickType.LEFT || type == ClickType.RIGHT) && rawSlot >= 0 && rawSlot < SIZE;
    }
}
