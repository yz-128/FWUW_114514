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
import org.joml.Vector4f;

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

    /** Normalize differently oriented authored UVs to the shared native square image. */
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

    /** Recover the authored UV of a point on a real glass plane, independently of its size/UV orientation. */
    public static float[] glassUv(BakedRig.Quad quad,Vector3f eye,Vector3f target) {
        var origin=new Vector3f(quad.positions()[0]);
        var a=new Vector3f(quad.positions()[1]).sub(origin);
        var b=new Vector3f(quad.positions()[3]).sub(origin);
        var normal=new Vector3f(a).cross(b);
        var direction=new Vector3f(target).sub(eye);
        float denominator=direction.dot(normal);
        if(!eye.isFinite() || !target.isFinite() || Math.abs(denominator)<1E-12F)return null;
        float t=new Vector3f(origin).sub(eye).dot(normal)/denominator;
        if(!Float.isFinite(t) || t<=0)return null;
        var p=direction.mul(t).add(eye).sub(origin);
        float aa=a.dot(a),ab=a.dot(b),bb=b.dot(b),det=aa*bb-ab*ab;
        if(Math.abs(det)<1E-15F)return null;
        float pa=p.dot(a),pb=p.dot(b),s=(pa*bb-pb*ab)/det,r=(pb*aa-pa*ab)/det;
        var uv=quad.uvs();
        return new float[]{uv[0][0]+s*(uv[1][0]-uv[0][0])+r*(uv[3][0]-uv[0][0]),
                uv[0][1]+s*(uv[1][1]-uv[0][1])+r*(uv[3][1]-uv[0][1])};
    }

    /** Map an optical vertex through the magnified world camera back onto the physical glass.
     * This uses the live eye and the hand projection; authored glass UVs never stand in for screen UVs. */
    public static Vector3f lensRay(Vector3f vertex,Matrix4f scopeToEye,Matrix4f handProjection,
                                  Matrix4f lensFromClip,double worldFov) {
        if(!Double.isFinite(worldFov) || worldFov<=0 || worldFov>=180)return null;
        double power=1/(Math.abs(handProjection.m11())*Math.tan(Math.toRadians(worldFov)/2));
        Vector4f point=scopeToEye.transform(new Vector4f(vertex,1));
        point.x*=power;point.y*=power;
        handProjection.transform(point);
        if(!point.isFinite() || point.w<=1E-7F)return null;
        Vector4f ray=lensFromClip.transform(new Vector4f(point.x/point.w,point.y/point.w,-1,1));
        return !ray.isFinite() || Math.abs(ray.w)<1E-9F?null:new Vector3f(ray.x/ray.w,ray.y/ray.w,ray.z/ray.w);
    }

    /** RenderGun's virtual scope camera projects into an aspect-one image before the glass. */
    public static float[] scopeImage(Vector3f vertex,Matrix4f opticalToEye,double fov) {
        if(!Double.isFinite(fov) || fov<=0 || fov>=180)return null;
        var point=opticalToEye.transformPosition(new Vector3f(vertex));
        if(!point.isFinite() || point.z>=-1E-7F)return null;
        double focal=.5/Math.tan(Math.toRadians(fov)/2);
        return new float[]{(float)(.5+point.x/-point.z*focal),(float)(.5-point.y/-point.z*focal)};
    }

    /** lensPose already contains the pupil translation and attachment/glass scale. */
    public static void render(PoseStack lensPose, MultiBufferSource buffers, GunType view,
                              GunSightState.Sight scope, BakedRig scopeRig, BakedRig glass,
                              Matrix4f scopeToEye,Matrix4f handProjection,double scopeFov, int overlay, boolean mirrored) {
        render(lensPose,buffers,view,scope,scopeRig,glass,scopeToEye,handProjection,scopeFov,overlay,mirrored,1);
    }
    public static void render(PoseStack lensPose, MultiBufferSource buffers, GunType view,
                              GunSightState.Sight scope, BakedRig scopeRig, BakedRig glass,
                              Matrix4f scopeToEye,Matrix4f handProjection,double scopeFov,int overlay,boolean mirrored,float brightness) {
        if (scope == null || scopeRig == null || scopeRig.animation == null || glass == null) return;
        var group = GunSightState.selectedGroup(view);
        if (group == null) return;
        if(!scopeToEye.isFinite())return;
        // RenderGun's scope branch moves the objective to viewPointXForScope=-69.5/160,
        // while the virtual eye is -81.5/160: exactly 0.075 blocks behind the objective.
        // Projecting this atlas from the physical hand camera shrinks its authored outer mask
        // into the centre of the image, even when all UVs and clip coordinates are finite.
        Vector3f eye=scopeEye(scopeRig.animation);
        Matrix4f opticalToEye=new Matrix4f(scopeToEye).setTranslation(0,0,0).translate(new Vector3f(eye).negate());
        for (var sight : group.sights()) {
            var type = sight.type();
            if (type.isFoldedVariant() || type.lightStuffModel == null || type.lightStuffModel.isBlank()
                    || type.lightStuffModel.equalsIgnoreCase("none")) continue;
            var sightRig = sight.node() == scope.node() ? scopeRig : ModelCache.rigFor(type, type.modelScale);
            if (sightRig == null || sightRig.animation == null) continue;
            var model = sightRig.animation;
            float[][] border = model.fmumVectorArrays.get("reticleBorder");
            if (border == null || border.length < 3) continue;
            var skin = Textures.forReticle(type,sight.node().dotFrame);
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
            // FMUM's scope-framebuffer branch uses getDivisionScale() directly. Pupil-distance
            // scaling belongs only to the direct, unscoped dot path. Applying it here shrinks
            // the authored outer mask into an opaque square/ring in the centre of the ocular.
            if (FmumActionPose.scalar(model, "relativeReticleSize", 0) != 0 && type.fovZoomLevels.length > 0) {
                int step = Math.clamp(sight.node().zoomStep, 0, type.fovZoomLevels.length - 1);
                scale *= type.fovZoomLevels[0] / Math.max(.001F, type.fovZoomLevels[step]);
            }
            VertexConsumer buffer = buffers.getBuffer(ScopeRenderLayers.overlay(skin));
            for (int index = 0; index < targets.length; index++) {
                if (targets[index] == null || targets[index].length < 3) continue;
                Vector3f far = transform.transformPosition(new Vector3f(targets[index]));
                Vector3f hit = FmumSightPicture.intersection(eye, far, transformedBorder, normal);
                if (hit == null) continue;
                List<BakedRig.Quad> parts = targets.length == 1 ? dot.quads : dot.quadsByRenderer.get("defaultModel#" + index);
                if (parts == null) continue;
                for (var quad : parts) {
                    // 1.7.10 called lightStuffModel.defaultModel[i].render(f), which draws the
                    // whole reticle array. Filtering by the quad normal dropped the crosshair
                    // strokes that are not exactly eye-facing and left only the centre dot.
                    var imagePoints=new float[quad.positions().length][];
                    boolean visible=true;
                    for(int i=0;i<imagePoints.length;i++) {
                        float[] p=quad.positions()[i];
                        var vertex=transform.transformDirection(new Vector3f(p[0],p[1]*scale,p[2]*scale)).add(hit);
                        imagePoints[i]=scopeImage(vertex,opticalToEye,scopeFov);
                        if(imagePoints[i]==null){visible=false;break;}
                    }
                    if(!visible)continue;
                    for (var lensQuad : glass.quads) {
                        if (lensQuad.nx() >= -.9F) continue;
                        var polygon=new ArrayList<Projected>();
                        for(int i=0;i<imagePoints.length;i++) {
                            var uv=quad.uvs()[i];
                            polygon.add(new Projected(imagePoints[i][0],imagePoints[i][1],uv[0],uv[1]));
                        }
                        float[][] aperture=new float[lensQuad.uvs().length][];
                        for(int i=0;i<aperture.length;i++)aperture[i]=imageUv(glass,lensQuad.uvs()[i][0],lensQuad.uvs()[i][1]);
                        var clipped = clip(polygon, aperture);
                        if (clipped.isEmpty()) continue;
                        for (int i = 1; i + 1 < clipped.size(); i++) {
                            Projected[] triangle = {clipped.getFirst(), clipped.get(i), clipped.get(i + 1)};
                            int[] order = mirrored ? new int[]{2, 1, 0, 0} : new int[]{0, 1, 2, 2};
                            for (int k : order) {
                                var v=triangle[k];var uv=authoredUv(glass,v.u,v.v);
                                emit(lensPose, buffer, lensQuad,new Projected(uv[0],uv[1],v.textureU,v.textureV),overlay,brightness);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Draws the coaxial group's reticles into the square scope framebuffer (FMUM's scope layer),
     * before the framebuffer is copied to the ocular texture. Image coordinates are 0..1 with V
     * pointing down, matching {@link #scopeImage}.
     */
    public static void bake(MultiBufferSource buffers, GunType view,
                            GunSightState.Sight scope, BakedRig scopeRig, Matrix4f scopeToEye,
                            double scopeFov, float brightness) {
        bake(buffers, view, scope, scopeRig, scopeToEye, scopeFov, brightness, 1F);
    }

    public static void bake(MultiBufferSource buffers, GunType view,
                            GunSightState.Sight scope, BakedRig scopeRig, Matrix4f scopeToEye,
                            double scopeFov, float brightness, float ads) {
        if (scope == null || scopeRig == null || scopeRig.animation == null) return;
        var group = GunSightState.selectedGroup(view);
        if (group == null || scopeToEye == null || !scopeToEye.isFinite()) return;
        var model = scopeRig.animation;
        // The plane belongs to the virtual scope gun, while the distant ray belongs to the live
        // hand pose (RenderGun 773-774, then 1043-1048). Moving both through the virtual pose
        // cancels the parallax the original renderer deliberately preserves.
        Vector3f objective = localToGun(scope, scope.type(),
                FmumSightPicture.vector(model, "scopeObjectiveLensCenter", 0, 0, 0));
        float scopeRoll = scope.roll() - scope.type().aimCenterX;
        Matrix4f placement = LegacyScopeProjection.scopePlacement(objective, scopeRoll, ads);
        Matrix4f base = LegacyScopeProjection.base();
        Matrix4f liveScopeToLegacy = LegacyScopeProjection.physicalToLegacy(scopeToEye);
        Vector3f eye = new Vector3f(LegacyScopeProjection.EYE_X, LegacyScopeProjection.EYE_Y, 0F);
        for (var sight : group.sights()) {
            var type = sight.type();
            if (type.isFoldedVariant() || type.lightStuffModel == null || type.lightStuffModel.isBlank()
                    || type.lightStuffModel.equalsIgnoreCase("none")) continue;
            var sightRig = sight.node() == scope.node() ? scopeRig : ModelCache.rigFor(type, type.modelScale);
            if (sightRig == null || sightRig.animation == null) continue;
            var sightModel = sightRig.animation;
            float[][] border = sightModel.fmumVectorArrays.get("reticleBorder");
            if (border == null || border.length < 3) continue;
            var skin = Textures.forReticle(type, sight.node().dotFrame);
            if (skin == null) continue;
            var dot = ModelCache.rigForReference(type.lightStuffModel, 1F, skin);
            if (dot == null || dot.quads.isEmpty()) continue;
            Matrix4f toVirtual = new Matrix4f(placement).mul(gunTransform(sight, type));
            Matrix4f rayToLegacy = new Matrix4f(liveScopeToLegacy).mul(intoScope(sight, scope));
            float[][] borderVirtual = new float[border.length][3];
            boolean complete = true;
            for (int i = 0; i < border.length; i++) {
                if (border[i] == null || border[i].length < 3) { complete = false; break; }
                Vector3f point = toVirtual.transformPosition(new Vector3f(border[i]));
                borderVirtual[i] = new float[]{point.x, point.y, point.z};
            }
            if (!complete) continue;
            Vector3f normalVirtual = toVirtual.transformDirection(
                    FmumSightPicture.vector(sightModel, "normalVecOfDotBorder", 1, 0, 0));
            float[][] targets = sightModel.fmumVectorArrays.getOrDefault("reticleAt", new float[][]{{0, 0, 0}});
            float scale = FmumActionPose.scalar(sightModel, "reticleScale", 1F);
            if (FmumActionPose.scalar(sightModel, "relativeReticleSize", 0) != 0 && type.fovZoomLevels.length > 0) {
                int step = Math.clamp(sight.node().zoomStep, 0, type.fovZoomLevels.length - 1);
                scale *= type.fovZoomLevels[0] / Math.max(.001F, type.fovZoomLevels[step]);
            }
            float dotRotate = LegacyScopeProjection.dotRotation(sight.roll() - type.aimCenterX, scopeRoll, ads);
            Matrix4f glyphRotation = new Matrix4f().rotateX((float)Math.toRadians(dotRotate));
            if (targets.length == 0 || targets[0] == null || targets[0].length < 3) continue;
            Vector3f far0 = rayToLegacy.transformPosition(new Vector3f(targets[0]));
            // Multi-part reticles share the first ray's intersection even if that first ray is
            // outside the aperture. Further rays only decide which defaultModel[i] is visible.
            Vector3f anchor = planeIntersection(eye, far0, borderVirtual[0], normalVirtual);
            if (anchor == null) continue;
            var target = buffers.getBuffer(ScopeFramebufferOverlays.type(skin));
            for (int index = 0; index < targets.length; index++) {
                if (targets[index] == null || targets[index].length < 3) continue;
                Vector3f farGun = rayToLegacy.transformPosition(new Vector3f(targets[index]));
                Vector3f hit = FmumSightPicture.intersection(eye, farGun, borderVirtual, normalVirtual);
                if (hit == null) continue;
                if(com.flansmod.common.FlansMod.LOGGER.isDebugEnabled()) {
                    String key=type.shortName+"|"+sight.node().zoomStep+"|"+sight.node().dotFrame;
                    if(CHAIN_LOGGED.add(key)) {
                        var mount=mount(sight);
                        com.flansmod.common.FlansMod.LOGGER.debug("[ScopeChain] "+key
                                +String.format(java.util.Locale.ROOT,
                                " mount=(%.5f,%.5f,%.5f) roll=%.3f objective=(%.5f,%.5f,%.5f) eye=(%.5f,%.5f,%.5f)"
                                +" border0=(%.5f,%.5f,%.5f) far0=(%.5f,%.5f,%.5f) hit=(%.5f,%.5f,%.5f)"
                                +" scale=%.5f dotRotate=%.3f",
                                mount.x,mount.y,mount.z,sight.roll()-type.aimCenterX,
                                objective.x,objective.y,objective.z,eye.x,eye.y,eye.z,
                                borderVirtual[0][0],borderVirtual[0][1],borderVirtual[0][2],
                                farGun.x,farGun.y,farGun.z,hit.x,hit.y,hit.z,
                                scale,dotRotate));
                    }
                }
                var parts = targets.length == 1 ? dot.quads : dot.quadsByRenderer.get("defaultModel#" + index);
                if (parts == null) continue;
                for (var quad : parts) {
                    var cameraPolygon = new ArrayList<LegacyScopeProjection.Vertex>(quad.positions().length);
                    for (int i = 0; i < quad.positions().length; i++) {
                        float[] p = quad.positions()[i];
                        var glyph = glyphRotation.transformPosition(new Vector3f(p[0], p[1] * scale, p[2] * scale));
                        var camera = base.transformPosition(new Vector3f(anchor).add(glyph));
                        var uv = quad.uvs()[i];
                        cameraPolygon.add(new LegacyScopeProjection.Vertex(camera, uv[0], uv[1]));
                    }
                    var clipped = LegacyScopeProjection.clipCameraPolygon(cameraPolygon, scopeFov, .05F);
                    for (int triangle = 1; triangle + 1 < clipped.size(); triangle++) {
                        var a = clipped.getFirst(); var b = clipped.get(triangle); var c = clipped.get(triangle + 1);
                        // Keep camera depth until the GPU projection: dividing on the CPU and
                        // submitting w=1 makes tilted atlas faces interpolate their UVs affinely.
                        ScopeFramebufferOverlays.cameraTriangle(target, a, b, c, brightness, 1F);
                    }
                }
            }
        }
    }

    private static Vector3f planeIntersection(Vector3f eye, Vector3f far, float[] origin, Vector3f normal) {
        if (!eye.isFinite() || !far.isFinite() || !normal.isFinite()) return null;
        Vector3f direction = new Vector3f(far).sub(eye);
        float denominator = direction.dot(normal);
        if (Math.abs(denominator) < 1E-7F) return null;
        float t = new Vector3f(origin).sub(eye).dot(normal) / denominator;
        Vector3f result = direction.mul(t).add(eye);
        return t >= 0F && result.isFinite() ? result : null;
    }

    /**
     * Author optical vectors (objective, border, rays, normals) are already in gun units in the
     * original: RenderGun adds them to the assembly position without any modelScale. Use the
     * physical mount (AimCenter removed again) and the physical roll only; modelScale belongs to
     * pupilRefCenter elsewhere, not to these vectors.
     */
    private static Matrix4f gunTransform(GunSightState.Sight sight, com.flansmod.common.types.AttachmentType type) {
        float d = (float) (Math.PI / 180D);
        Vector3f mount = mount(sight);
        return new Matrix4f()
                .translate(mount.x, mount.y, mount.z)
                .rotateX((sight.roll() - sight.type().aimCenterX) * d);
    }

    private static Vector3f localToGun(GunSightState.Sight sight, com.flansmod.common.types.AttachmentType type,
                                       Vector3f local) {
        return gunTransform(sight, type).transformPosition(new Vector3f(local));
    }

    private static final java.util.Set<String> CHAIN_LOGGED=java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static Vector3f scopeEye(com.flansmod.client.model.loader.ModelGeometry.Model model) {
        return FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0).sub(.075F,0,0);
    }

    private static void emit(PoseStack pose, VertexConsumer buffer, BakedRig.Quad quad, Projected vertex, int overlay,float brightness) {
        Vector3f p = glassPosition(quad, vertex.u, vertex.v);
        if (p == null) return;
        Vector3f normal = pose.last().normal().transform(new Vector3f(quad.nx(), quad.ny(), quad.nz())).normalize();
        buffer.addVertex(pose.last().pose(), p.x - .001F, p.y, p.z).setColor(brightness,brightness,brightness,1F)
                .setUv(vertex.textureU, vertex.textureV).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x, normal.y, normal.z);
    }
}
