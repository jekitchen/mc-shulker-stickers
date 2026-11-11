package io.github.jekitchen.shulkerstickers.core.text;

import io.github.jekitchen.shulkerstickers.core.BoxColor;

/** Every translation key the platforms use. English text is in {@code shulker_stickers/lang/en_us.json}. */
public final class TextKeys {

    private static final String P = "shulker_stickers.";

    // Editor (PRD §5.2)
    public static final String EDITOR_TITLE = P + "editor.title";
    public static final String EDITOR_TITLE_UNNAMED = P + "editor.title.unnamed";
    public static final String EDITOR_PREVIEW = P + "editor.preview";
    public static final String EDITOR_PREVIEW_ICON = P + "editor.preview.icon";
    public static final String EDITOR_PREVIEW_NO_ICON = P + "editor.preview.no_icon";
    public static final String EDITOR_USE_OFFHAND = P + "editor.use_offhand";
    public static final String EDITOR_USE_OFFHAND_EMPTY = P + "editor.use_offhand.empty";
    public static final String EDITOR_USE_OFFHAND_BLOCKED = P + "editor.use_offhand.blocked";
    public static final String EDITOR_REMOVE_ICON = P + "editor.remove_icon";
    public static final String EDITOR_RENAME = P + "editor.rename";
    public static final String EDITOR_CANCEL = P + "editor.cancel";
    public static final String EDITOR_SAVE = P + "editor.save";
    public static final String EDITOR_SAVE_COST = P + "editor.save.cost";
    public static final String EDITOR_FEATURE_DISABLED = P + "editor.feature_disabled";
    public static final String EDITOR_DISCARDED = P + "editor.discarded";
    public static final String RENAME_TITLE = P + "rename.title";

    // Costs
    public static final String COST_XP_LEVELS = P + "cost.xp_levels";
    public static final String COST_ITEM = P + "cost.item";

    // Denials (action bar)
    public static final String DENY_PERMISSION = P + "deny.permission";
    public static final String DENY_PROTECTED = P + "deny.protected";
    public static final String DENY_LOCKED = P + "deny.locked";
    public static final String DENY_IN_USE_EDITOR = P + "deny.in_use_editor";
    public static final String DENY_ADVENTURE = P + "deny.adventure";

    // Save results
    public static final String SAVE_BOX_IN_USE = P + "save.box_in_use";
    public static final String SAVE_BOX_GONE = P + "save.box_gone";
    public static final String SAVE_BLOCKED_WORD = P + "save.blocked_word";
    public static final String SAVE_CANNOT_PAY = P + "save.cannot_pay";
    public static final String SAVE_ICON_BLOCKED = P + "save.icon_blocked";
    public static final String SAVE_ICON_TOO_LARGE = P + "save.icon_too_large";

    // Help and commands (PRD §5.6, §8)
    public static final String HELP_FIRST_PLACE = P + "help.first_place";
    public static final String HELP_TEXT = P + "help.text";
    public static final String TOGGLE_OFF = P + "toggle.off";
    public static final String TOGGLE_ON = P + "toggle.on";
    public static final String NO_BOX_IN_VIEW = P + "command.no_box";
    public static final String INSPECT_HEADER = P + "inspect.header";
    public static final String INSPECT_NONE = P + "inspect.none";
    public static final String INSPECT_UNREADABLE = P + "inspect.unreadable";
    public static final String INSPECT_LINE = P + "inspect.line";
    public static final String CLEAR_DONE = P + "clear.done";
    public static final String RELOAD_DONE = P + "reload.done";
    public static final String RELOAD_WARNINGS = P + "reload.warnings";

    private TextKeys() {}

    /** Key for a color button, e.g. {@code shulker_stickers.color.light_blue}; {@code undyed} for the plain box. */
    public static String color(BoxColor color) {
        return P + "color." + (color.dyeName() == null ? "undyed" : color.dyeName());
    }
}
