package io.github.jekitchen.shulkerstickers.core.display;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * How a shulker box's lid moves as the box opens, so displays on the lid face can move with it
 * (PRD §5.5, v0.2). Taken from vanilla 26.3:
 * <ul>
 *   <li>{@code ShulkerBoxBlockEntity}: progress goes from 0 (closed) to 1 (open) in steps of 0.1 per
 *       tick, and back the same way when the box closes.</li>
 *   <li>{@code ShulkerBoxRenderer$ShulkerBoxModel#setupAnim}: at progress {@code p} the lid is raised
 *       by {@code 0.5 * p} blocks and turned by {@code 270 * p} degrees about the model's Y axis.
 *       The renderer maps model Y to the opposite of the box's facing ({@code scale(1, -1, -1)} after
 *       {@code facing.getRotation()}), so in the world the lid rises along the facing and turns by
 *       {@code -270 * p} degrees about it (right-hand rule).</li>
 * </ul>
 * The turn is about the box's own axis, through the centre of the box.
 */
public final class LidMotion {

    /** Progress gained or lost per tick while the box opens or closes. */
    public static final float STEP = 0.1f;
    /** How far the lid rises when fully open, in blocks. */
    public static final float RISE = 0.5f;
    /** How far the lid turns about the facing when fully open, in degrees (right-hand rule). */
    public static final float TURN_DEGREES = -270f;

    private LidMotion() {
    }

    /** A display's translation and left rotation, as on a display entity (both in world axes). */
    public record Pose(Vector3f translation, Quaternionf leftRotation) {
    }

    /**
     * Where a display on the lid face is drawn at {@code progress}.
     *
     * @param facing       unit vector the box (and its lid) faces
     * @param standOffset  where the display entity stands, minus the centre of the box
     * @param restTranslation the display's translation with the box closed
     * @param restRotation    the display's left rotation with the box closed
     */
    public static Pose at(float progress, Vector3fc facing, Vector3fc standOffset,
            Vector3fc restTranslation, Quaternionfc restRotation) {
        float p = Math.clamp(progress, 0f, 1f);
        Quaternionf turn = new Quaternionf().fromAxisAngleRad(facing.x(), facing.y(), facing.z(),
                (float) Math.toRadians(TURN_DEGREES * p));
        // A drawn point is centre + standOffset + translation + rotation * v. Turning it about the
        // box's axis and raising it gives the same form with a new translation and rotation.
        Vector3f translation = turn.transform(new Vector3f(standOffset).add(restTranslation))
                .sub(standOffset)
                .add(new Vector3f(facing).mul(RISE * p));
        Quaternionf rotation = new Quaternionf(turn).mul(restRotation);
        return new Pose(translation, rotation);
    }
}
