import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prints the optical numbers the live scope path actually uses, so "low resolution" can be
 * attributed to a measured quantity rather than guessed. Offline only.
 */
public final class InspectScopeOptics {
    private static ModifiableType.DefaultAttachment node(String name, int slot) {
        var n = new ModifiableType.DefaultAttachment();
        n.name = name; n.slot = slot; return n;
    }
    private static GunType mounted(GunType ak, String optic) {
        var tree = new ArrayList<List<ModifiableType.DefaultAttachment>>();
        for (var group : ak.defaultAttachments) tree.add(new ArrayList<>(group));
        while (tree.size() < ak.slots.length) tree.add(new ArrayList<>());
        if (optic.equals("PSO1")) tree.get(6).add(node(optic, 6));
        else {
            var side = node("SVDLowSideMount", 6);
            var lens = node(optic, 0);
            if (optic.equals("NF7x")) { var ring = node("NF34mm", 0); ring.children.add(lens); side.children.add(ring); }
            else side.children.add(lens);
            tree.get(6).add(side);
        }
        return ak.withAttachments(tree);
    }
    private static Matrix4f gunPose(GunType view, ModelGeometry.Model body) {
        Matrix4f pose = new Matrix4f().translate(.56F, -.52F, -.72F);
        FmumFirstPersonPose.itemFrame(pose);
        FmumFirstPersonPose.placement(pose, body, 1, 0, 0);
        FmumFirstPersonPose.aim(pose, view, 1);
        return pose;
    }
    private static void optic(GunType ak, ModelGeometry.Model body, String name,
                              double worldFov, int screenW, int screenH) {
        var view = mounted(ak, name);
        var sight = GunSightState.scopeSight(view);
        if (sight == null) { System.out.println(name + ": not selected"); return; }
        var type = sight.type();
        var rig = ModelCache.rigFor(type, type.modelScale);
        var model = rig.animation;
        Vector3f mount = ScopeReticleRenderer.mount(sight);
        Matrix4f toEye = gunPose(view, body).translate(mount)
                .rotateX((float) Math.toRadians(sight.roll() - type.aimCenterX));
        Vector3f eye = new Matrix4f(toEye).invert().transformPosition(new Vector3f());
        float pupil = FmumSightPicture.pupilDistance(model, type.modelScale, eye);
        int step = Math.clamp(sight.node().zoomStep, 0,
                Math.max(0, type.fovZoomLevels.length - 1));
        float raw = type.fovZoomLevels.length > 0 ? type.fovZoomLevels[step] : type.FOVZoomLevel;
        double divisor = raw * pupil;
        double scopeFov = worldFov / Math.max(1, divisor);
        double ratio = Math.tan(Math.toRadians(scopeFov) / 2)
                / Math.tan(Math.toRadians(worldFov) / 2);
        double sourcePixelsW = screenW * ratio;
        double sourcePixelsH = screenH * ratio;
        System.out.printf(Locale.ROOT,
                "%s step=%d raw=%.3f pupil=%.4f divisor=%.3f scopeFov=%.3f deg crop=%.4f "
                        + "sourcePixels=%dx%d at %dx%d%n",
                name, step, raw, pupil, divisor, scopeFov, ratio,
                Math.round(sourcePixelsW), Math.round(sourcePixelsH), screenW, screenH);

        // Lens on-screen size: project the physical glass bounds through the same pose.
        if (type.scopeGlassModel != null && !type.scopeGlassModel.isBlank()) {
            var glass = ModelCache.rigForReference(type.scopeGlassModel, 1F);
            if (glass != null) {
                Vector3f center = FmumSightPicture.vector(model, "pupilRefCenter", 0, 0, 0).mul(type.modelScale);
                Vector3f scale = FmumSightPicture.vector(model, "scopeGlassModelScale", 1, 1, 1).mul(type.modelScale);
                Matrix4f lens = new Matrix4f(toEye).translate(center).scale(scale);
                float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
                float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
                for (var quad : glass.quads) for (int i = 0; i < 4; i++) {
                    float[] p = quad.positions()[i];
                    Vector3f e = lens.transformPosition(new Vector3f(p[0], p[1], p[2]));
                    if (e.z >= 0) continue;
                    float half = (float) (-e.z * Math.tan(Math.toRadians(worldFov) / 2));
                    float ndcX = e.x / half, ndcY = e.y / half;
                    minX = Math.min(minX, ndcX); maxX = Math.max(maxX, ndcX);
                    minY = Math.min(minY, ndcY); maxY = Math.max(maxY, ndcY);
                }
                double lensPxW = (maxX - minX) * screenW / 2, lensPxH = (maxY - minY) * screenH / 2;
                System.out.printf(Locale.ROOT,
                        "    lens on screen ~%.0fx%.0f px; source detail stretched %.2fx horizontally%n",
                        lensPxW, lensPxH, lensPxW / Math.max(1, sourcePixelsW));
            }
        }
    }
    private static void mask(String reference) {
        var model = ModelCache.load(reference);
        if (model == null) { System.out.println(reference + ": missing"); return; }
        var rig = BakedRig.bake(model, 1F);
        System.out.printf(Locale.ROOT,
                "%s quads=%d bounds x[%.3f,%.3f] y[%.3f,%.3f] z[%.3f,%.3f]%n",
                reference, rig.quads.size(), rig.minX, rig.maxX, rig.minY, rig.maxY, rig.minZ, rig.maxZ);
        for (var key : model.fmumScalars.keySet())
            System.out.printf(Locale.ROOT, "    scalar %s = %.4f%n", key, model.fmumScalars.get(key));
        for (var key : model.fmumPose.keySet())
            System.out.printf(Locale.ROOT, "    vector %s = %s%n", key,
                    java.util.Arrays.toString(model.fmumPose.get(key)));
    }
    /** Reports plates perpendicular to the bore, which would block a see-through aperture. */
    private static void discs(String reference) {
        var model = ModelCache.load(reference);
        if (model == null) { System.out.println(reference + ": missing"); return; }
        var rig = BakedRig.bake(model, 1F);
        System.out.println(reference + " end-face plates (|nx|>0.9):");
        for (var q : rig.quads) {
            if (Math.abs(q.nx()) <= .9F) continue;
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (float[] p : q.positions()) {
                minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
                minZ = Math.min(minZ, p[2]); maxZ = Math.max(maxZ, p[2]);
            }
            System.out.printf(Locale.ROOT, "    x=%.3f..%.3f y=%.3f..%.3f z=%.3f..%.3f%n",
                    q.positions()[0][0], q.positions()[2][0], minY, maxY, minZ, maxZ);
        }
    }

    public static void main(String[] args) throws Exception {
        var pack = new OfflinePack(Path.of(args[0]));
        pack.readTypes();
        var ak = (GunType) InfoType.infoTypes.stream()
                .filter(t -> t instanceof GunType && "AK74M".equals(t.shortName))
                .findFirst().orElseThrow();
        var body = ModelCache.load(ak);
        for (String optic : List.of("PSO1", "NF7x", "SeecterDR1x4x", "G33Enabled"))
            optic(ak, body, optic, 70, 1920, 1080);
        System.out.println();
        mask("EFTRAtt.eftBlackMask");
        discs("EFTRAtt.PSO1");
        discs("EFTRAtt.PSO1Glass");
        System.out.println();
        var pso1 = ModelCache.load("EFTRAtt.PSO1");
        if (pso1 != null) {
            for (var key : pso1.fmumScalars.keySet())
                System.out.printf(Locale.ROOT, "PSO1 scalar %s = %.4f%n", key, pso1.fmumScalars.get(key));
            for (var key : pso1.fmumPose.keySet())
                System.out.printf(Locale.ROOT, "PSO1 vector %s = %s%n", key,
                        java.util.Arrays.toString(pso1.fmumPose.get(key)));
        }
    }
}
