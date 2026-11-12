package io.github.jekitchen.shulkerstickers.core.display;

/**
 * Where icons and names sit on a box face, shared so both platforms draw the same thing (owner
 * decision 11). All values are in blocks, in the face's own frame: "down" is toward the bottom
 * of the image as the player reads it. Starting values are the Fabric build's; tune them at the
 * first vanilla-client check.
 */
public final class DisplayLayout {

    /** How far outside the face the displays sit, so they take light from the space in front. */
    public static final double FACE_OFFSET = 0.51;

    /** Icon size, as a fraction of the face. */
    public static final float ICON_SCALE = 0.5f;

    /** Icon offset along the face's "up" when the box also has a name (0: centered). */
    public static final float ICON_LIFT_WITH_NAME = 0f;

    /** Name text size. */
    public static final float NAME_SCALE = 0.3f;

    /** Name offset along the face's "up": under the icon, or near the center when there's no icon. */
    public static final float NAME_OFFSET_UNDER_ICON = -0.42f;
    public static final float NAME_OFFSET_ALONE = -0.05f;

    private DisplayLayout() {}

    public static float iconOffset(boolean hasName) {
        return hasName ? ICON_LIFT_WITH_NAME : 0f;
    }

    public static float nameOffset(boolean hasIcon) {
        return hasIcon ? NAME_OFFSET_UNDER_ICON : NAME_OFFSET_ALONE;
    }
}
