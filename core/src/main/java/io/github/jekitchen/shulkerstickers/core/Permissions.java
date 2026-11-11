package io.github.jekitchen.shulkerstickers.core;

/** Permission nodes (PRD §8). Everyone has the first four by default; admin needs op level 2. */
public final class Permissions {

    public static final String COLOR = "shulker_stickers.color";
    public static final String ICON = "shulker_stickers.icon";
    public static final String NAME = "shulker_stickers.name";
    /** Names set by players without this aren't shown on boxes. */
    public static final String NAME_DISPLAY = "shulker_stickers.name.display";
    public static final String ADMIN = "shulker_stickers.admin";

    public static final int ADMIN_OP_LEVEL = 2;

    private Permissions() {}
}
