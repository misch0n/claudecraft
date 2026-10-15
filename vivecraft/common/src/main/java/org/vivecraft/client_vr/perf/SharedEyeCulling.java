package org.vivecraft.client_vr.perf;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.List;

/**
 * claudecraft: {@code sharedEyeCulling} (PLAN Phase 3, FINDINGS V11).
 * <p>
 * Each eye pass normally culls the world on its own: the eye cameras differ, so Sodium rebuilds its visibility
 * graph and render lists twice per frame. With this on, both eye passes cull with one frustum that contains
 * both eye frusta (apex behind the eyes, outer tangents of both), and the second eye pass skips Sodium's
 * {@code setupTerrain} and draws the lists the first eye pass built.
 * <p>
 * Per frame, the first eye pass that culls decides; it falls back to per-eye culling when the eyes are not
 * parallel, a projection is unusable, Sodium's hook has not run yet, or the two eyes and the union apex are not
 * all in the same chunk section (Sodium's occlusion search starts from the apex's section).
 * <p>
 * Also times Sodium's {@code setupTerrain} for the first and second eye pass, toggle on or off, so the CPU
 * saved can be read from the {@code [VCPERF] cull} line. Render thread only.
 */
public final class SharedEyeCulling {
    private SharedEyeCulling() {}

    private static final int UNDECIDED = 0;
    private static final int SHARED = 1;
    private static final int PER_EYE = 2;

    /** set when the Sodium setupTerrain hook first runs: proof that the Sodium mixin applied */
    private static boolean sodiumHooked = false;

    // per frame
    private static boolean frameEnabled = false;
    private static int state = UNDECIDED;
    private static int eyeTerrainCalls = 0;
    private static long terrainStart = -1;
    private static int terrainEye = -1;
    private static Vec3 unionOrigin;
    private static Matrix4f unionProjection;

    // stats since the last [VCPERF] cull line
    private static int framesShared, framesPerEye;
    private static int fallbackSection, fallbackPose, fallbackProjection;
    private static int terrainSkipped;
    private static final long[] terrainNanos = new long[2];
    private static final long[] terrainMaxNanos = new long[2];
    private static final int[] terrainCount = new int[2];
    private static double distanceSum;

    /**
     * start of the pass loop of a frame
     *
     * @param passes        the passes rendered this frame
     * @param eyesAvailable if the eye passes render this frame
     */
    public static void beginFrame(List<RenderPass> passes, boolean eyesAvailable) {
        if (state == SHARED) {
            framesShared++;
        } else if (state == PER_EYE) {
            framesPerEye++;
        }
        VRSettings settings = ClientDataHolderVR.getInstance().vrSettings;
        frameEnabled = settings != null && settings.sharedEyeCulling && sodiumHooked && eyesAvailable &&
            passes.contains(RenderPass.LEFT) && passes.contains(RenderPass.RIGHT);
        state = UNDECIDED;
        eyeTerrainCalls = 0;
        terrainStart = -1;
        unionOrigin = null;
        unionProjection = null;
    }

    private static boolean isEyePass() {
        RenderPass pass = ClientDataHolderVR.getInstance().currentPass;
        return pass == RenderPass.LEFT || pass == RenderPass.RIGHT;
    }

    /**
     * {@code LevelRenderer.prepareCullFrustum} of a VR pass: decides on the first eye pass of the frame
     *
     * @param cameraPosition camera position the frustum would use
     * @param projection     projection matrix the frustum would use
     * @return true if the culling frustum should be {@link #unionOrigin()} / {@link #unionProjection()}
     */
    public static boolean useUnion(Vec3 cameraPosition, Matrix4f projection) {
        if (!frameEnabled || !isEyePass()) {
            return false;
        }
        if (state == UNDECIDED) {
            state = decide(cameraPosition, projection) ? SHARED : PER_EYE;
        }
        return state == SHARED;
    }

    public static Vec3 unionOrigin() {
        return unionOrigin;
    }

    public static Matrix4f unionProjection() {
        return new Matrix4f(unionProjection);
    }

    private static boolean decide(Vec3 cameraPosition, Matrix4f projection) {
        ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
        Matrix4f[] eyeProj = dh.vrRenderer.eyeProj;
        if (eyeProj[0] == null || eyeProj[1] == null) {
            fallbackProjection++;
            return false;
        }
        // tangents of each eye's frustum: x = left (<0), y = right (>0), z = down (<0), w = up (>0)
        float l = Math.min(tanLeft(eyeProj[0]), tanLeft(eyeProj[1]));
        float r = Math.max(tanRight(eyeProj[0]), tanRight(eyeProj[1]));
        float d = Math.min(tanDown(eyeProj[0]), tanDown(eyeProj[1]));
        float u = Math.max(tanUp(eyeProj[0]), tanUp(eyeProj[1]));
        // near/far of the OpenGL style (-1..1 depth) projection actually used
        float near = projection.m32() / (projection.m22() - 1.0F);
        float far = projection.m32() / (projection.m22() + 1.0F);
        if (!(l < -0.05F && r > 0.05F && d < -0.05F && u > 0.05F) || !(near > 0.0F) || !Float.isFinite(far) ||
            far <= near)
        {
            fallbackProjection++;
            return false;
        }

        VRData data = dh.vrPlayer.vrdata_world_render;
        VRData.VRDevicePose left = data.getEye(RenderPass.LEFT);
        VRData.VRDevicePose right = data.getEye(RenderPass.RIGHT);
        Matrix4f rotation = left.getMatrix();
        if (!rotation.equals(right.getMatrix(), 1.0E-4F)) {
            // canted eyes: the tangent union below assumes parallel eye frames
            fallbackPose++;
            return false;
        }
        Vec3 leftPos = left.getPosition();
        Vec3 rightPos = right.getPosition();
        Vec3 center = leftPos.add(rightPos).scale(0.5D);

        // how far behind the eye centre the apex has to be, so the union frustum contains both eye frusta.
        // Eye frame: x right, y up, +z back. For an eye at (x, y, z) relative to the centre, each plane of the
        // union frustum must pass outside the eye's apex: d >= z - x/|l|, z + x/r, z + y/u, z - y/|d|, z
        Matrix4f toEye = new Matrix4f(rotation).transpose();
        float apexDistance = 0.0F;
        for (Vec3 eye : new Vec3[]{leftPos, rightPos}) {
            Vector3f o = toEye.transformDirection(
                new Vector3f((float) (eye.x - center.x), (float) (eye.y - center.y), (float) (eye.z - center.z)));
            apexDistance = Math.max(apexDistance, Math.max(
                Math.max(o.z + o.x / l, o.z + o.x / r),
                Math.max(Math.max(o.z + o.y / u, o.z + o.y / d), o.z)));
        }
        if (!(apexDistance < 0.5F * Math.max(data.worldScale, 0.01F))) {
            fallbackPose++;
            return false;
        }

        // positions relative to this pass's camera, so they match the camera Minecraft actually uses
        Vec3 eyeNow = data.getEye(dh.currentPass).getPosition();
        Vec3 shift = cameraPosition.subtract(eyeNow);
        Vector3f back = rotation.transformDirection(new Vector3f(0.0F, 0.0F, apexDistance));
        Vec3 origin = center.add(shift).add(back.x, back.y, back.z);
        long section = sectionOf(origin);
        if (section != sectionOf(leftPos.add(shift)) || section != sectionOf(rightPos.add(shift))) {
            fallbackSection++;
            return false;
        }

        unionOrigin = origin;
        unionProjection = new Matrix4f().setFrustum(l * near, r * near, d * near, u * near, near,
            far + apexDistance);
        distanceSum += apexDistance;
        return true;
    }

    private static long sectionOf(Vec3 pos) {
        return SectionPos.asLong(SectionPos.posToSectionCoord(pos.x), SectionPos.posToSectionCoord(pos.y),
            SectionPos.posToSectionCoord(pos.z));
    }

    // tangents from an OpenGL frustum matrix: m00 = 2/(r-l), m20 = (r+l)/(r-l), m11 = 2/(u-d), m21 = (u+d)/(u-d)
    private static float tanLeft(Matrix4fc m) {
        return (m.m20() - 1.0F) / m.m00();
    }

    private static float tanRight(Matrix4fc m) {
        return (m.m20() + 1.0F) / m.m00();
    }

    private static float tanDown(Matrix4fc m) {
        return (m.m21() - 1.0F) / m.m11();
    }

    private static float tanUp(Matrix4fc m) {
        return (m.m21() + 1.0F) / m.m11();
    }

    /**
     * head of Sodium's {@code SodiumWorldRenderer.setupTerrain}
     *
     * @return true if this call should be skipped, because an earlier eye pass of this shared frame did it
     */
    public static boolean onTerrainStart() {
        sodiumHooked = true;
        if (!isEyePass()) {
            terrainStart = -1;
            return false;
        }
        int eye = Math.min(eyeTerrainCalls++, 1);
        if (state == SHARED && eye == 1) {
            terrainSkipped++;
            terrainStart = -1;
            return true;
        }
        terrainEye = eye;
        terrainStart = System.nanoTime();
        return false;
    }

    /**
     * return of Sodium's {@code SodiumWorldRenderer.setupTerrain}
     */
    public static void onTerrainEnd() {
        if (terrainStart < 0) {
            return;
        }
        long nanos = System.nanoTime() - terrainStart;
        terrainStart = -1;
        terrainNanos[terrainEye] += nanos;
        terrainCount[terrainEye]++;
        terrainMaxNanos[terrainEye] = Math.max(terrainMaxNanos[terrainEye], nanos);
    }

    /**
     * logs and resets the counters, called with the periodic {@code [VCPERF] mem} line
     */
    public static void logStats(String label) {
        if (framesShared + framesPerEye + terrainCount[0] == 0) {
            return;
        }
        VRSettings settings = ClientDataHolderVR.getInstance().vrSettings;
        VRSettings.LOGGER.info(
            "[VCPERF] cull label={} sharedEyeCulling={} sodiumHook={} framesShared={} framesPerEye={} " +
                "fallback[section={} pose={} projection={}] apexCm={} terrainSkipped={} " +
                "terrainFirstEye={}ms/{} max={}ms terrainSecondEye={}ms/{} max={}ms",
            label, settings != null && settings.sharedEyeCulling, sodiumHooked, framesShared, framesPerEye,
            fallbackSection, fallbackPose, fallbackProjection,
            framesShared > 0 ? String.format("%.1f", 100.0 * distanceSum / framesShared) : "-",
            terrainSkipped,
            avgMs(0), terrainCount[0], ms(terrainMaxNanos[0]), avgMs(1), terrainCount[1], ms(terrainMaxNanos[1]));
        framesShared = framesPerEye = 0;
        fallbackSection = fallbackPose = fallbackProjection = 0;
        terrainSkipped = 0;
        distanceSum = 0.0;
        for (int i = 0; i < 2; i++) {
            terrainNanos[i] = 0;
            terrainMaxNanos[i] = 0;
            terrainCount[i] = 0;
        }
    }

    private static String avgMs(int eye) {
        return terrainCount[eye] == 0 ? "-" : ms(terrainNanos[eye] / terrainCount[eye]);
    }

    private static String ms(long nanos) {
        return String.format("%.3f", nanos / 1.0E6);
    }
}
