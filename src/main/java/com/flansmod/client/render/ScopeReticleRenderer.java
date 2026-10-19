package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.GunType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Composes the selected optical line's reticles onto the actual scope glass, without a world pass. */
public final class ScopeReticleRenderer {
    private ScopeReticleRenderer() { }

    private record ImageBasis(float a, float b, float c, float d) { }
    private static final ImageBasis IDENTITY = new ImageBasis(1, 0, 0, 1);
    private static final Map<BakedRig, ImageBasis> IMAGE_BASES = Collections.synchronizedMap(new WeakHashMap<>());

    /** The mesh's own UVs may mirror or rotate the framebuffer image. Preserve UV coverage while
     * aligning image-right with local +Z and image-down with local -Y. Each normalized matrix row
     * removes model size; its inverse is shared by the reticle and world-image paths. */
    private static ImageBasis imageBasis(BakedRig glass) {
        if (glass == null) return IDENTITY;
        return IMAGE_BASES.computeIfAbsent(glass, rig -> {
            for (var quad : rig.quads) {
                if (quad.nx() >= -.9F) continue;
                Vector3f origin = glassPosition(quad, 0, 0), u = glassPosition(quad, 1, 0), v = glassPosition(quad, 0, 1);
                if (origin == null || u == null || v == null) continue;
                u.sub(origin); v.sub(origin);
                float right = (float)Math.hypot(u.z, v.z), down = (float)Math.hypot(u.y, v.y);
                if (right <= 1E-9F || down <= 1E-9F) continue;
                var basis = new ImageBasis(u.z / right, v.z / right, -u.y / down, -v.y / down);
                float determinant = basis.a * basis.d - basis.b * basis.c;
                if (Float.isFinite(determinant) && Math.abs(determinant) > 1E-6F) return basis;
            }
            return IDENTITY;
        });
    }

    /** Convert authored glass UVs into the common square-image convention before scene sampling. */
    public static float[] imageUv(BakedRig glass, float authoredU, float authoredV) {
        var basis = imageBasis(glass);
        float u = authoredU - .5F, v = authoredV - .5F;
        return new float[]{.5F + basis.a * u + basis.b * v, .5F + basis.c * u + basis.d * v};
    }

    /** Inverse of imageUv: place a projected optical image on this particular glass mesh. */
    public static float[] authoredUv(BakedRig glass, float imageU, float imageV) {
        var basis = imageBasis(glass);
        float u = imageU - .5F, v = imageV - .5F, determinant = basis.a * basis.d - basis.b * basis.c;
        return new float[]{.5F + (basis.d * u - basis.b * v) / determinant,
                .5F + (basis.a * v - basis.c * u) / determinant};
    }

    /** Scope framebuffer coordinates and the independently interpolated reticle texture coordinates. */
    public record Projected(float u, float v, float textureU, float textureV) { }

    /** A legacy model's forward axis is +X; framebuffer model UVs have V pointing down. */
    public static float[] project(Vector3f position, Vector3f eye, double scopeFov) {
        if (position == null || eye == null || !position.isFinite() || !eye.isFinite()
                || !Double.isFinite(scopeFov) || scopeFov <= 0 || scopeFov >= 180) return null;
        double depth = position.x - eye.x;
        if (depth <= 1E-7) return null;
        double halfExtent = depth * Math.tan(Math.toRadians(scopeFov) / 2);
        float u = (float)(.5 + (position.z - eye.z) / halfExtent * .5);
        float v = (float)(.5 - (position.y - eye.y) / halfExtent * .5);
        return Float.isFinite(u) && Float.isFinite(v) ? new float[]{u, v} : null;
    }

    /** Sutherland-Hodgman clipping, retaining reticle UVs at newly created glass-strip edges. */
    public static List<Projected> clip(List<Projected> subject, float[][] aperture) {
        if (subject == null || subject.size() < 3 || aperture == null || aperture.length < 3) return List.of();
        double area = 0;
        for (int i = 0; i < aperture.length; i++) {
            float[] a = aperture[i], b = aperture[(i + 1) % aperture.length];
            if (a == null || b == null || a.length < 2 || b.length < 2) return List.of();
            area += a[0] * (double)b[1] - b[0] * (double)a[1];
        }
        if (!Double.isFinite(area) || Math.abs(area) < 1E-12) return List.of();
        double winding = area > 0 ? 1 : -1;
        List<Projected> result = subject;
        for (int edge = 0; edge < aperture.length && !result.isEmpty(); edge++) {
            float[] a = aperture[edge], b = aperture[(edge + 1) % aperture.length];
            List<Projected> input = result;
            result = new ArrayList<>();
            Projected previous = input.getLast();
            double previousSide = side(a, b, previous) * winding;
            for (Projected current : input) {
                double currentSide = side(a, b, current) * winding;
                boolean previousInside = previousSide >= -1E-9, currentInside = currentSide >= -1E-9;
                if (previousInside != currentInside) {
                    double denominator = previousSide - currentSide;
                    if (Math.abs(denominator) > 1E-15) result.add(interpolate(previous, current, previousSide / denominator));
                }
                if (currentInside) result.add(current);
                previous = current;
                previousSide = currentSide;
            }
        }
        return result.size() < 3 ? List.of() : result;
    }

    private static double side(float[] a, float[] b, Projected p) {
        return (b[0] - (double)a[0]) * (p.v - a[1]) - (b[1] - (double)a[1]) * (p.u - a[0]);
    }

    private static Projected interpolate(Projected a, Projected b, double amount) {
        float t = (float)Math.clamp(amount, 0, 1);
        return new Projected(a.u + (b.u - a.u) * t, a.v + (b.v - a.v) * t,
                a.textureU + (b.textureU - a.textureU) * t, a.textureV + (b.textureV - a.textureV) * t);
    }

    /** Affine inverse of a planar glass quad's authored model UVs. */
    public static Vector3f glassPosition(BakedRig.Quad quad, float u, float v) {
        float[][] uv = quad.uvs(), position = quad.positions();
        for (int i = 1; i < uv.length - 1; i++) for (int j = i + 1; j < uv.length; j++) {
            double au = uv[i][0] - uv[0][0], av = uv[i][1] - uv[0][1];
            double bu = uv[j][0] - uv[0][0], bv = uv[j][1] - uv[0][1];
            double determinant = au * bv - av * bu;
            if (Math.abs(determinant) < 1E-12) continue;
            double du = u - uv[0][0], dv = v - uv[0][1];
            float a = (float)((du * bv - dv * bu) / determinant);
            float b = (float)((au * dv - av * du) / determinant);
            return new Vector3f(position[0]).add(new Vector3f(position[i]).sub(new Vector3f(position[0])).mul(a))
                    .add(new Vector3f(position[j]).sub(new Vector3f(position[0])).mul(b));
        }
        return null;
    }

    /** Remove AimCenter (already scaled by the definition reader) to recover the physical mount. */
    public static Vector3f mount(GunSightState.Sight sight) {
        double roll = Math.toRadians(sight.roll() - sight.type().aimCenterX);
        float sin = (float)Math.sin(roll), cos = (float)Math.cos(roll);
        return new Vector3f(sight.x(),
                sight.y() - sight.type().aimCenterY * cos + sight.type().aimCenterZ * sin,
                sight.z() - sight.type().aimCenterY * sin - sight.type().aimCenterZ * cos);
    }

    public static Matrix4f intoScope(GunSightState.Sight part, GunSightState.Sight scope) {
        Vector3f delta = mount(part).sub(mount(scope));
        return new Matrix4f().rotationX((float)Math.toRadians(-(scope.roll() - scope.type().aimCenterX)))
                .translate(delta).rotateX((float)Math.toRadians(part.roll() - part.type().aimCenterX));
    }

    /** lensPose already contains the pupil translation and attachment/glass scale. */
    public static void render(PoseStack lensPose, MultiBufferSource buffers, GunType view,
                              GunSightState.Sight scope, BakedRig scopeRig, BakedRig glass,
                              double scopeFov, int overlay, boolean mirrored) {
        if (scope == null || scopeRig == null || scopeRig.animation == null || glass == null) return;
        var group = GunSightState.selectedGroup(view);
        if (group == null) return;
        // Original scope pass translates the objective to -69.5/160, with eye at -81.5/160.
        Vector3f eye = FmumSightPicture.vector(scopeRig.animation, "scopeObjectiveLensCenter", 0, 0, 0)
                .add(-12F / 160F, 0, 0);
        for (var sight : group.sights()) {
            var type = sight.type();
            if (type.isFoldedVariant() || type.lightStuffModel == null || type.lightStuffModel.isBlank()
                    || type.lightStuffModel.equalsIgnoreCase("none")) continue;
            var sightRig = sight.node() == scope.node() ? scopeRig : ModelCache.rigFor(type, type.modelScale);
            if (sightRig == null || sightRig.animation == null) continue;
            var model = sightRig.animation;
            float[][] border = model.fmumVectorArrays.get("reticleBorder");
            if (border == null || border.length < 3) continue;
            var skin = type.lightStuffTexture == null || type.lightStuffTexture.isBlank() ? Textures.of(type.texture, false)
                    : Textures.of(type.lightStuffTexture + Math.clamp(sight.node().dotFrame, 0, Math.max(0, type.lightTextureCount - 1)), false);
            if (skin == null) continue;
            var dot = ModelCache.rigForReference(type.lightStuffModel, 1F, skin);
            if (dot == null || dot.quads.isEmpty()) continue;
            Matrix4f transform = intoScope(sight, scope);
            float[][] transformedBorder = new float[border.length][3];
            boolean complete = true;
            for (int i = 0; i < border.length; i++) {
                if (border[i] == null || border[i].length < 3) { complete = false; break; }
                Vector3f p = transform.transformPosition(new Vector3f(border[i]));
                transformedBorder[i] = new float[]{p.x, p.y, p.z};
            }
            if (!complete) continue;
            Vector3f normal = transform.transformDirection(FmumSightPicture.vector(model, "normalVecOfDotBorder", 1, 0, 0));
            float[][] targets = model.fmumVectorArrays.getOrDefault("reticleAt", new float[][]{{0, 0, 0}});
            float scale = FmumActionPose.scalar(model, "reticleScale", 1F);
            if (FmumActionPose.scalar(model, "relativeReticleSize", 0) != 0 && type.fovZoomLevels.length > 0) {
                int step = Math.clamp(sight.node().zoomStep, 0, type.fovZoomLevels.length - 1);
                scale *= type.fovZoomLevels[0] / Math.max(.001F, type.fovZoomLevels[step]);
            }
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(skin));
            for (int index = 0; index < targets.length; index++) {
                if (targets[index] == null || targets[index].length < 3) continue;
                Vector3f far = transform.transformPosition(new Vector3f(targets[index]));
                Vector3f hit = FmumSightPicture.intersection(eye, far, transformedBorder, normal);
                if (hit == null) continue;
                List<BakedRig.Quad> parts = targets.length == 1 ? dot.quads : dot.quadsByRenderer.get("defaultModel#" + index);
                if (parts == null) continue;
                for (var quad : parts) {
                    // Flatten only the eye-facing plate; projecting the rear and side faces too
                    // would superimpose multiple copies of its translucent texture on the lens.
                    if (quad.nx() >= -.001F) continue;
                    var polygon = new ArrayList<Projected>();
                    for (int i = 0; i < quad.positions().length; i++) {
                        float[] p = quad.positions()[i], uv = quad.uvs()[i];
                        Vector3f vertex = transform.transformDirection(new Vector3f(p[0], p[1] * scale, p[2] * scale)).add(hit);
                        float[] projected = project(vertex, eye, scopeFov);
                        if (projected == null) { polygon.clear(); break; }
                        float[] authored = authoredUv(glass, projected[0], projected[1]);
                        polygon.add(new Projected(authored[0], authored[1], uv[0], uv[1]));
                    }
                    if (polygon.size() < 3) continue;
                    for (var lensQuad : glass.quads) {
                        if (lensQuad.nx() >= -.9F) continue;
                        var clipped = clip(polygon, lensQuad.uvs());
                        if (clipped.isEmpty()) continue;
                        for (int i = 1; i + 1 < clipped.size(); i++) {
                            Projected[] triangle = {clipped.getFirst(), clipped.get(i), clipped.get(i + 1)};
                            int[] order = mirrored ? new int[]{2, 1, 0, 0} : new int[]{0, 1, 2, 2};
                            for (int k : order) emit(lensPose, buffer, lensQuad, triangle[k], overlay);
                        }
                    }
                }
            }
        }
    }

    private static void emit(PoseStack pose, VertexConsumer buffer, BakedRig.Quad quad, Projected vertex, int overlay) {
        Vector3f p = glassPosition(quad, vertex.u, vertex.v);
        if (p == null) return;
        Vector3f normal = pose.last().normal().transform(new Vector3f(quad.nx(), quad.ny(), quad.nz())).normalize();
        buffer.addVertex(pose.last().pose(), p.x - .001F, p.y, p.z).setColor(1F, 1F, 1F, 1F)
                .setUv(vertex.textureU, vertex.textureV).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x, normal.y, normal.z);
    }
}
