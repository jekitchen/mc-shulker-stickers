package io.github.jekitchen.shulkerstickers.core.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class LidMotionTest {

    private static final float EPS = 1e-5f;
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final Vector3f EAST = new Vector3f(1, 0, 0);

    private static void assertVec(Vector3f expected, Vector3f actual) {
        assertTrue(expected.distance(actual) < EPS, "expected " + expected + " but was " + actual);
    }

    /** Where a point v of the display (in its own space) ends up, relative to the box centre. */
    private static Vector3f drawn(Vector3f standOffset, LidMotion.Pose pose, Vector3f v) {
        return pose.leftRotation().transform(new Vector3f(v)).add(pose.translation()).add(standOffset);
    }

    @Test
    void closedIsTheRestPose() {
        Vector3f t = new Vector3f(0.1f, 0.2f, -0.3f);
        Quaternionf r = new Quaternionf().rotationX(0.7f);
        LidMotion.Pose pose = LidMotion.at(0f, UP, new Vector3f(0, 0.51f, 0), t, r);
        assertVec(t, pose.translation());
        assertTrue(pose.leftRotation().equals(r, EPS));
    }

    @Test
    void openRaisesHalfABlockAlongTheFacing() {
        // A display standing on the box's axis with no translation: it only rises (and spins in place).
        Vector3f stand = new Vector3f(0, 0.51f, 0);
        LidMotion.Pose pose = LidMotion.at(1f, UP, stand, new Vector3f(), new Quaternionf());
        assertVec(new Vector3f(0, 0.5f, 0), pose.translation());
        // East-facing box: rises along +X.
        LidMotion.Pose east = LidMotion.at(1f, EAST, new Vector3f(0.51f, 0, 0), new Vector3f(), new Quaternionf());
        assertVec(new Vector3f(0.5f, 0, 0), east.translation());
    }

    @Test
    void openTurnsMinus270DegreesAboutTheFacing() {
        // -270 degrees about +Y is +90 degrees: +X goes to -Z.
        LidMotion.Pose pose = LidMotion.at(1f, UP, new Vector3f(0, 0.51f, 0), new Vector3f(), new Quaternionf());
        assertVec(new Vector3f(0, 0, -1), pose.leftRotation().transform(new Vector3f(1, 0, 0)));
        // Halfway: -135 degrees about +Y.
        LidMotion.Pose half = LidMotion.at(0.5f, UP, new Vector3f(0, 0.51f, 0), new Vector3f(), new Quaternionf());
        float c = (float) Math.cos(Math.toRadians(-135)), s = (float) Math.sin(Math.toRadians(-135));
        assertVec(new Vector3f(c, 0, -s), half.leftRotation().transform(new Vector3f(1, 0, 0)));
    }

    @Test
    void aNameBelowTheIconSwingsAroundTheAxisWithTheLid() {
        // Lid facing up; the name is drawn 0.42 towards +Z from the axis (below the icon in the face's own view).
        Vector3f stand = new Vector3f(0, 0.51f, 0);
        Vector3f rest = new Vector3f(0, 0, 0.42f);
        LidMotion.Pose pose = LidMotion.at(1f, UP, stand, rest, new Quaternionf());
        // +90 degrees about +Y takes +Z to +X; and the lid rose 0.5.
        assertVec(new Vector3f(0.42f, 0.51f + 0.5f, 0), drawn(stand, pose, new Vector3f()));
    }

    @Test
    void aDisplayStandingOffTheAxisStillTurnsAboutTheBoxAxis() {
        // Paper may stand a display just inside the box's chunk, off the axis; the drawn point must
        // be the same as for one standing on the axis.
        Vector3f restDrawnFromCentre = new Vector3f(0.2f, 0.51f, 0.1f);
        Vector3f onAxis = new Vector3f(0, 0.51f, 0);
        Vector3f offAxis = new Vector3f(-0.3f, 0.51f, 0.25f);
        LidMotion.Pose a = LidMotion.at(0.7f, UP, onAxis, new Vector3f(restDrawnFromCentre).sub(onAxis), new Quaternionf());
        LidMotion.Pose b = LidMotion.at(0.7f, UP, offAxis, new Vector3f(restDrawnFromCentre).sub(offAxis), new Quaternionf());
        assertVec(drawn(onAxis, a, new Vector3f()), drawn(offAxis, b, new Vector3f()));
    }

    /**
     * Rebuilds vanilla 26.3's lid rendering independently of LidMotion and checks LidMotion moves
     * points fixed to the lid to the same place, for all six facings. The chain, from bytecode:
     * {@code ShulkerBoxRenderer#createModelTransform}: translate(.5,.5,.5), scale(.9995),
     * {@code Direction#getRotation}, scale(1,-1,-1), translate(0,-1,0); then the lid part
     * ({@code ModelPart#translateAndRotate} after {@code setupAnim}): translate(0,(24-8p)/16,0),
     * rotationZYX(0, 270p degrees, 0). A sign or axis mistake in LidMotion fails here.
     */
    @Test
    void matchesVanillasLidRenderingForEveryFacing() {
        float halfPi = (float) (Math.PI / 2);
        java.util.Map<String, Object[]> facings = new java.util.LinkedHashMap<>();
        // name -> {facing vector, Direction#getRotation}
        facings.put("down", new Object[] {new Vector3f(0, -1, 0), new Quaternionf().rotationX((float) Math.PI)});
        facings.put("up", new Object[] {new Vector3f(0, 1, 0), new Quaternionf()});
        facings.put("north", new Object[] {new Vector3f(0, 0, -1), new Quaternionf().rotationXYZ(halfPi, 0, (float) Math.PI)});
        facings.put("south", new Object[] {new Vector3f(0, 0, 1), new Quaternionf().rotationX(halfPi)});
        facings.put("west", new Object[] {new Vector3f(-1, 0, 0), new Quaternionf().rotationXYZ(halfPi, 0, halfPi)});
        facings.put("east", new Object[] {new Vector3f(1, 0, 0), new Quaternionf().rotationXYZ(halfPi, 0, -halfPi)});
        Vector3f centre = new Vector3f(0.5f, 0.5f, 0.5f);
        // Points fixed to the lid, in the lid part's own space (blocks).
        Vector3f[] lidPoints = {new Vector3f(0, -0.25f, 0), new Vector3f(0.3f, -0.4f, 0.2f), new Vector3f(-0.45f, -0.1f, 0.35f)};
        for (var entry : facings.entrySet()) {
            Vector3f facing = (Vector3f) entry.getValue()[0];
            Quaternionf directionRotation = (Quaternionf) entry.getValue()[1];
            org.joml.Matrix4f box = new org.joml.Matrix4f().translate(0.5f, 0.5f, 0.5f).scale(0.9995f)
                    .rotate(directionRotation).scale(1, -1, -1).translate(0, -1, 0);
            for (float p : new float[] {0.3f, 0.7f, 1f}) {
                org.joml.Matrix4f closed = new org.joml.Matrix4f(box).translate(0, 24f / 16f, 0);
                org.joml.Matrix4f open = new org.joml.Matrix4f(box).translate(0, (24f - 8f * p) / 16f, 0)
                        .rotate(new Quaternionf().rotationZYX(0, (float) Math.toRadians(270 * p), 0));
                for (Vector3f q : lidPoints) {
                    Vector3f atRest = closed.transformPosition(new Vector3f(q));
                    Vector3f vanilla = open.transformPosition(new Vector3f(q));
                    // The same point as a display standing at its resting place, moved by LidMotion.
                    Vector3f standOffset = new Vector3f(atRest).sub(centre);
                    LidMotion.Pose pose = LidMotion.at(p, facing, standOffset, new Vector3f(), new Quaternionf());
                    Vector3f ours = new Vector3f(centre).add(standOffset).add(pose.translation());
                    // Vanilla's 0.9995 scale shortens the rise by at most 0.00025 blocks.
                    assertTrue(vanilla.distance(ours) < 1e-3f,
                            entry.getKey() + " p=" + p + " point " + q + ": vanilla " + vanilla + ", ours " + ours);
                }
            }
        }
    }

    @Test
    void progressIsClamped() {
        LidMotion.Pose over = LidMotion.at(1.5f, UP, new Vector3f(), new Vector3f(), new Quaternionf());
        assertVec(new Vector3f(0, 0.5f, 0), over.translation());
        LidMotion.Pose under = LidMotion.at(-1f, UP, new Vector3f(), new Vector3f(), new Quaternionf());
        assertVec(new Vector3f(), under.translation());
        assertEquals(0.1f, LidMotion.STEP);
    }
}
