package io.github.jekitchen.shulkerstickers.core.editor;

/**
 * The items the editor shows, shared so both platforms look the same (owner decision 11).
 *
 * <ul>
 *   <li>Every slot without a button holds {@link #FILLER}.
 *   <li>A button for a feature the player can't use keeps its own item and gets one extra lore
 *       line, {@code editor.feature_disabled}; clicking it does nothing.
 *   <li>"Use off-hand item" shows the off-hand item itself when it can be used, and
 *       {@link #OFFHAND_PLACEHOLDER} when the off-hand is empty, the item is blocked, or icons
 *       are unavailable.
 * </ul>
 */
public final class EditorLook {

    public static final String FILLER = "minecraft:gray_stained_glass_pane";
    public static final String OFFHAND_PLACEHOLDER = "minecraft:light_gray_dye";
    public static final String REMOVE_ICON = "minecraft:barrier";
    public static final String RENAME = "minecraft:name_tag";
    public static final String CANCEL = "minecraft:red_concrete";
    public static final String SAVE = "minecraft:lime_concrete";

    /** Filler items show a single space as their name, so hovering them shows nothing useful. */
    public static final String FILLER_NAME = " ";

    private EditorLook() {}
}
