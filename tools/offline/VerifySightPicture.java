import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.BytecodeModel;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.model.loader.ModelGeometry;
import com.flansmod.client.render.FmumSightPicture;
import com.flansmod.client.render.ScopeMagnification;
import com.flansmod.common.types.AttachmentType;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Locale;

/** Real legacy optics and independent ray/projection examples; no Minecraft or OpenGL execution. */
public final class VerifySightPicture implements Opcodes {
    private static int checks;

    private static void check(boolean ok, String description) {
        checks++;
        if (!ok) throw new AssertionError(description);
    }

    private static void near(float actual, float expected, String description) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < 2E-5F,
                description + ": actual=" + actual + ", expected=" + expected);
    }

    private static void point(Vector3f actual, Vector3f expected, String description) {
        check(actual != null, description + " exists");
        near(actual.x, expected.x, description + " X");
        near(actual.y, expected.y, description + " Y");
        near(actual.z, expected.z, description + " Z");
    }

    private static ModelGeometry.Model model(String reference) {
        var model = ModelCache.load(reference);
        check(model != null, "actual pack model loaded: " + reference);
        return model;
    }

    private static float[][] array(ModelGeometry.Model model, String key, int length) {
        float[][] values = model.fmumVectorArrays.get(key);
        check(values != null && values.length == length, model.className + " " + key + " length " + length);
        for (float[] value : values) {
            check(value != null && value.length == 3, key + " complete XYZ entry");
            for (float coordinate : value) check(Float.isFinite(coordinate), key + " finite coordinate");
        }
        return values;
    }

    private static Vector3f vector(ModelGeometry.Model model, String key) {
        float[] value = model.fmumPose.get(key);
        check(value != null && value.length == 3, model.className + " retains optical vector " + key);
        return new Vector3f(value);
    }

    private static void realModels() {
        var cobra = model("EFTRAtt.CobraEKPrefiexSightPic");
        float[][] cobraDot = array(cobra, "reticleAt", 1);
        point(new Vector3f(cobraDot[0]), new Vector3f(4096F, .71875F, 0F),
                "Cobra inherited reticleAt survives its AASTORE");
        float[][] cobraBorder = array(cobra, "reticleBorder", 8);
        float cobraX = cobraBorder[0][0];
        for (float[] p : cobraBorder) near(p[0], cobraX, "Cobra mirror-plane X");
        near(cobra.fmumScalars.getOrDefault("reticleScale", Float.NaN), .3F, "Cobra authored reticle scale");
        // reticleAt is a distant ray target; its Y coordinate is not the glass center.
        Vector3f cobraEye = new Vector3f(cobraX - 1F, .09F, 0F);
        float cobraHitY = .09F + (.71875F - .09F) / (4096F - cobraEye.x);
        point(FmumSightPicture.intersection(cobraEye, new Vector3f(cobraDot[0]), cobraBorder, new Vector3f(1, 0, 0)),
                new Vector3f(cobraX, cobraHitY, 0F), "Cobra distant target projects into actual aperture");
        check(FmumSightPicture.intersection(new Vector3f(cobraX - 1F, 10F, 0F),
                new Vector3f(4096F, 10F, 0F), cobraBorder, new Vector3f(1, 0, 0)) == null,
                "Cobra off-axis ray stays outside actual glass");

        var pso = model("EFTRAtt.PSO1");
        float[][] psoDot = array(pso, "reticleAt", 1);
        point(new Vector3f(psoDot[0]), new Vector3f(4096F, .22357723F, .032520324F),
                "PSO1 inherited reticleAt survives its AASTORE");
        float[][] psoBorder = array(pso, "reticleBorder", 4);
        point(new Vector3f(psoBorder[0]), new Vector3f(.3875F, 1.125F, -.1875F), "PSO1 first border corner");
        point(new Vector3f(psoBorder[2]), new Vector3f(.3875F, -.25F, .1875F), "PSO1 opposite border corner");
        point(vector(pso, "pupilRefCenter"), new Vector3f(-2.25F, 1.71875F, .25F),
                "PSO1 authored pupil point");
        point(vector(pso, "scopeGlassModelScale"), new Vector3f(.07F), "PSO1 authored lens scale");
        Vector3f eye = new Vector3f(-.7F, .3F, .1F);
        float expectedDistance = new Vector3f(-2.25F * .13F, 1.71875F * .13F, .25F * .13F).distance(eye);
        near(FmumSightPicture.pupilDistance(pso, .13F, eye), expectedDistance,
                "PSO1 distance includes actual eye and attachment model scale");
        check(Math.abs(FmumSightPicture.pupilDistance(pso, .13F, eye) -
                FmumSightPicture.pupilDistance(pso, .13F, new Vector3f(-1.2F, .3F, .1F))) > .4F,
                "pupil distance responds to eye relief rather than a fixed calibration constant");

        var nf = model("EFTRAtt.NF7x");
        point(new Vector3f(array(nf, "reticleAt", 1)[0]), new Vector3f(4096F, 5F, 0F), "NF7x authored ray target");
        array(nf, "reticleBorder", 4);
        near(nf.fmumScalars.getOrDefault("reticleScale", Float.NaN), .0175F, "NF7x small authored reticle scale");

        var glass = BakedRig.bake(model("EFTRAtt.PSO1Glass"), 1F);
        check(glass.quads.size() == 29 * 6, "actual PSO1 glass retains 29 strip boxes");
        near(glass.maxY - glass.minY, 98F / 16F, "actual unit lens height");
        near(glass.maxZ - glass.minZ, 100F / 16F, "actual unit lens width");
    }

    private static float[][] square(float x) {
        return new float[][]{{x, -1, -1}, {x, 1, -1}, {x, 1, 1}, {x, -1, 1}};
    }

    private static void rayGeometry() {
        float[][] border = square(1F);
        Vector3f eye = new Vector3f(-1, 0, 0), far = new Vector3f(4096, 0, 0), normal = new Vector3f(1, 0, 0);
        point(FmumSightPicture.intersection(eye, far, border, normal), new Vector3f(1, 0, 0), "center ray");
        point(eye, new Vector3f(-1, 0, 0), "intersection leaves caller eye unchanged");
        point(far, new Vector3f(4096, 0, 0), "intersection leaves caller target unchanged");
        float[][] reversed = new float[][]{border[3], border[2], border[1], border[0]};
        point(FmumSightPicture.intersection(eye, far, reversed, normal), new Vector3f(1, 0, 0), "either border winding");
        point(FmumSightPicture.intersection(eye, far, border, new Vector3f(-2, 0, 0)),
                new Vector3f(1, 0, 0), "normal sign and length do not move intersection");
        point(FmumSightPicture.intersection(new Vector3f(-1, 1, 0), new Vector3f(4096, 1, 0), border, normal),
                new Vector3f(1, 1, 0), "aperture edge remains visible");
        check(FmumSightPicture.intersection(new Vector3f(-1, 1.01F, 0), new Vector3f(4096, 1.01F, 0), border, normal) == null,
                "ray beyond aperture edge is hidden");
        check(FmumSightPicture.intersection(eye, new Vector3f(-1, 4, 0), border, normal) == null, "parallel ray is hidden");
        check(FmumSightPicture.intersection(new Vector3f(2, 0, 0), far, border, normal) == null, "plane behind eye is hidden");
        check(FmumSightPicture.intersection(eye, eye, border, normal) == null, "zero-length ray is hidden");
        check(FmumSightPicture.intersection(eye, far, new float[0][3], normal) == null, "absent aperture is tolerated");
        check(FmumSightPicture.intersection(eye, far, new float[][]{{1, 0, 0}, {1, 1, 1}}, normal) == null,
                "incomplete aperture is tolerated");

        var rotation = new Matrix3f().rotationZ((float)Math.toRadians(37));
        float[][] tilted = new float[border.length][3];
        for (int i = 0; i < border.length; i++) {
            var corner = rotation.transform(new Vector3f(border[i]));
            tilted[i] = new float[]{corner.x, corner.y, corner.z};
        }
        Vector3f tiltedNormal = rotation.transform(new Vector3f(1, 0, 0));
        point(FmumSightPicture.intersection(new Vector3f(), new Vector3f(tiltedNormal).mul(4096), tilted, tiltedNormal),
                new Vector3f(tiltedNormal), "tilted aperture ray uses plane normal");

        // Some legacy ModelAtSight subclasses genuinely omit this inherited setting. The origin
        // is a direction target, not a reason to reject an otherwise valid ray beyond it.
        point(FmumSightPicture.intersection(new Vector3f(-2, 0, 0), new Vector3f(), border, normal),
                new Vector3f(1, 0, 0), "omitted reticleAt origin remains a valid ray target");
    }

    private static void omittedLegacySettings() throws Exception {
        var writer = new ClassWriter(0);
        writer.visit(V1_7, ACC_PUBLIC, "fixture/ModelMissingOptics", null,
                "com/flansmod/client/model/ModelAtSight", null);
        var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "com/flansmod/client/model/ModelAtSight", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        var model = BytecodeModel.fromStream(new ByteArrayInputStream(writer.toByteArray()));
        check(model != null, "legacy sight without optical declarations still loads");
        float[][] values = model.fmumVectorArrays.get("reticleAt");
        check(values == null || (values.length == 1 && new Vector3f(values[0]).lengthSquared() == 0F),
                "genuinely omitted reticleAt does not invent an authored far point");
        near(FmumSightPicture.pupilDistance(model, .2F, new Vector3f(-.3F, .4F, 0)), .5F,
                "missing pupil point defaults to attachment origin");
    }

    /**
     * Magnification happens on the world camera now, not by cropping the finished frame. A crop
     * can never carry more detail than the frame it came from: at 4x it kept about a fifth of the
     * screen width and stretched that back over the ocular.
     */
    private static void worldMagnification() {
        near(AttachmentType.getAttachment("PSO1").zoomDivisor(0), 4F, "PSO1 declares 4x");
        near(AttachmentType.getAttachment("NF7x").zoomDivisor(0), 7F, "NF7x declares 7x");
        near((float)ScopeMagnification.zoom(AttachmentType.getAttachment("PSO1"), 0), 4F,
                "world pass uses the declared 4x");
        ScopeMagnification.frame(70, 4);
        near((float)ScopeMagnification.baseFov(), 70F, "hand pass keeps the undivided FOV");
        near((float)ScopeMagnification.scopeFov(), 17.5F,
                "reticle projection uses the same 70/4 optical FOV");
        near((float)ScopeMagnification.zoom(AttachmentType.getAttachment("Crossline"), 0), 1F,
                "an optic without ZoomLevel leaves the world unzoomed");
        ScopeMagnification.frame(70, 1);
        near((float)ScopeMagnification.scopeFov(), 70F,
                "an unzoomed optic projects at the world FOV");
    }

    /**
     * The ADS pivot must put the sight's aim centre on the camera, not on 1.7.10's fixed
     * {@code y = 0.4}. Measured in this port the camera sits at a different gun-space height for
     * every gun (AK74M 0.320, DT MDR 0.269), so a constant target leaves a red dot's aperture
     * 0.05–0.13 blocks above the eye and its ray misses the frame entirely.
     */
    private static void aimPivot() {
        // A synthetic pre-aim frame with an off-centre camera, like a real first-person pose.
        var camera = new Vector3f(1.337F, -1.301F, 0.101F);
        var sight = new com.flansmod.client.render.LegacySightPose.Center("synthetic", 0F, 0.138F, 0.389F, 0F);
        var pose = new org.joml.Matrix4f();
        var aimed = com.flansmod.client.render.FmumFirstPersonPose.aim(new org.joml.Matrix4f(), sight, 1F, camera);
        // The aim point (the sight's y/z, since x is eye relief) must land on the camera.
        var aimPoint = aimed.transformPosition(new Vector3f(0F, sight.y(), sight.z()));
        near(aimPoint.x, 0F, "aim pivot leaves the mount on the bore axis (eye relief is not changed)");
        near(aimPoint.y, camera.y, "aim pivot places the aim centre on the camera height");
        near(aimPoint.z, camera.z, "aim pivot places the aim centre on the camera offset");
        // Hip fire is untouched: the aim point stays on the mount.
        var hip = com.flansmod.client.render.FmumFirstPersonPose.aim(new org.joml.Matrix4f(), sight, 0F, camera);
        var hipPoint = hip.transformPosition(new Vector3f(0F, sight.y(), sight.z()));
        near(hipPoint.y, sight.y(), "hip fire leaves the sight on its mount");
        near(hipPoint.z, sight.z(), "hip fire leaves the sight laterally unmoved");
        // And the legacy fixed point is still what a caller without a camera gets.
        var legacy = com.flansmod.client.render.FmumFirstPersonPose.aim(new org.joml.Matrix4f(), sight, 1F);
        near(legacy.transformPosition(new Vector3f(0F, sight.y(), sight.z())).y, 0.4F,
                "1.7.10's fixed 0.4 target remains the default");
    }

    /** A real red-dot sight's ray must land inside its own authored aperture. */
    private static void redDotRay() {
        var cic = ModelCache.load("OCRemakeAttachments.CICNDOpticsSightIIPlus");
        if (cic == null) { check(false, "real CIC sight model loaded"); return; }
        float[][] border = array(cic, "reticleBorder", 6);
        float[][] far = array(cic, "reticleAt", 1);
        var normal = FmumSightPicture.vector(cic, "normalVecOfDotBorder", 1F, 0F, 0F);
        // The aim centre is the pivot the renderer uses; the eye ends up exactly there.
        float aimY = AttachmentType.getAttachment("CICNDOpticsSightIIPlus").aimCenterY;
        float aimZ = 0F;
        // Eye relief is arbitrary: a distant ray target makes the hit point insensitive to it.
        var eye = new Vector3f(aimY - 0.8F, aimY, aimZ);
        near(far[0][1], aimY, "CIC authors its ray target at the same height as AimCenter");
        var hit = FmumSightPicture.intersection(eye, new Vector3f(far[0]), border, normal);
        check(hit != null, "CIC ray from the aim centre lands inside its real aperture");
        if (hit != null) {
            near(hit.y, aimY, "CIC hit height is the aperture centre");
            near(hit.z, aimZ, "CIC hit stays on the optical axis");
        }
    }

    private static void mouseSensitivity() {
        for (double configured : new double[]{.2, .5, 1}) {
            near((float)FmumSightPicture.sensitivity(configured, 1), (float)configured,
                    "unity optic preserves vanilla sensitivity " + configured);
            double originalCurve = Math.pow(configured * .6 + .2, 3);
            for (double multiplier : new double[]{.3, .6}) {
                double adjusted = FmumSightPicture.sensitivity(configured, multiplier);
                double adjustedCurve = Math.pow(adjusted * .6 + .2, 3);
                near((float)(adjustedCurve / originalCurve), (float)multiplier,
                        "optic slows actual cubic mouse response at sensitivity " + configured);
                check(adjusted < configured, "slower optic lowers only the effective sensitivity value");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        check(args.length >= 1, "usage: VerifySightPicture <EFT pack directory> [Octagon pack directory]");
        var pack = new OfflinePack(Path.of(args[0]));
        pack.readTypes();
        // The CIC red dot lives in the Octagon pack; the EFT checks do not depend on it.
        if (args.length > 1) new OfflinePack(Path.of(args[1])).readTypes();
        realModels();
        rayGeometry();
        omittedLegacySettings();
        worldMagnification();
        aimPivot();
        redDotRay();
        mouseSensitivity();
        System.out.printf(Locale.ROOT,
                "PASS: %d assertions; real Cobra/PSO1/NF7x optical bytecode, actual glass bounds, ray-plane aperture visibility, dynamic pupil distance and world-pass magnification. Offline only; no OpenGL/game executed.%n",
                checks);
    }
}
