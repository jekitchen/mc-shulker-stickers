package io.github.jekitchen.shulkerstickers.core.editor;

import io.github.jekitchen.shulkerstickers.core.BoxColor;
import java.util.Optional;

/**
 * Slot layout of the 3-row Sticker editor (PRD §5.2). Rows 1–2 hold the 17 colors; row 3
 * holds the preview and the action buttons. Every other slot is an inert filler.
 */
public final class EditorLayout {

    public static final int SIZE = 27;
    public static final int PREVIEW = 18;
    public static final int USE_OFFHAND = 20;
    public static final int REMOVE_ICON = 21;
    public static final int RENAME = 22;
    public static final int CANCEL = 25;
    public static final int SAVE = 26;

    public sealed interface Button {
        Button PREVIEW = Action.PREVIEW;
        Button USE_OFFHAND = Action.USE_OFFHAND;
        Button REMOVE_ICON = Action.REMOVE_ICON;
        Button RENAME = Action.RENAME;
        Button CANCEL = Action.CANCEL;
        Button SAVE = Action.SAVE;

        record Color(BoxColor color) implements Button {}

        enum Action implements Button { PREVIEW, USE_OFFHAND, REMOVE_ICON, RENAME, CANCEL, SAVE }
    }

    private EditorLayout() {}

    public static Optional<Button> buttonAt(int slot) {
        if (slot >= 0 && slot < BoxColor.values().length) {
            return Optional.of(new Button.Color(BoxColor.values()[slot]));
        }
        return Optional.ofNullable(switch (slot) {
            case PREVIEW -> Button.PREVIEW;
            case USE_OFFHAND -> Button.USE_OFFHAND;
            case REMOVE_ICON -> Button.REMOVE_ICON;
            case RENAME -> Button.RENAME;
            case CANCEL -> Button.CANCEL;
            case SAVE -> Button.SAVE;
            default -> null;
        });
    }

    public static int slotOf(Button button) {
        return switch (button) {
            case Button.Color c -> c.color().ordinal();
            case Button.Action a -> switch (a) {
                case PREVIEW -> PREVIEW;
                case USE_OFFHAND -> USE_OFFHAND;
                case REMOVE_ICON -> REMOVE_ICON;
                case RENAME -> RENAME;
                case CANCEL -> CANCEL;
                case SAVE -> SAVE;
            };
        };
    }

    /** True for the editor's own 27 slots (not the player's inventory below). */
    public static boolean isMenuSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < SIZE;
    }
}
