package com.flansmod.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** The original scope layer's coordinate contract, without Minecraft or OpenGL state. */
public final class LegacyScopeProjection {
    private LegacyScopeProjection() { }

    public static final float SCOPE_X = -69.5F / 160F;
    public static final float EYE_X = -81.5F / 160F;
    public static final float EYE_Y = .4F;

    /** Static ItemRenderer -> Forge -> RenderGun parent, before the virtual gun placement. */
    public static Matrix4f base() {
        float d = (float)(Math.PI / 180D);
        return new Matrix4f().translate(.56F, -.52F, -.72F)
                .rotateY(45F * d).scale(.4F)
                .translate(0F, -.3F, 0F).scale(1.5F)
                .rotateY(50F * d).rotateZ(335F * d).translate(-.9375F, -.0625F, 0F)
                .rotateZ(20F * d).rotateY(-5F * d).translate(.15F, .375F, -1.005F)
                .rotateX(.5F * d).rotateZ(5F * d).translate(0F, .7F / 160F - .03F, -.35F / 160F);
    }

    /** Express a live attachment-to-camera pose in the original reference frame. */
    public static Matrix4f physicalToLegacy(Matrix4f toEye) {
        return base().invert().mul(toEye);
    }

    /** RenderGun 1026-1033: translation/ADS roll/translation must retain their order. */
    public static Matrix4f scopePlacement(Vector3f objective, float physicalRoll, float ads) {
        return new Matrix4f().translate(SCOPE_X - objective.x, EYE_Y, 0F)
                .rotateX((float)Math.toRadians(-physicalRoll * Math.clamp(ads, 0F, 1F)))
                .translate(0F, -objective.y, -objective.z);
    }

    /** RenderGun 1017/1029/1055: the angle is runtime state, not a model field. */
    public static float dotRotation(float partRoll, float scopeRoll, float ads) {
        return partRoll - scopeRoll * Math.clamp(ads, 0F, 1F);
    }

    /** Perspective from camera coordinates to the square image, V down. */
    public static float[] project(Vector3f camera, double fov) {
        if (camera == null || !camera.isFinite() || camera.z >= -1E-7F
                || !Double.isFinite(fov) || fov <= 0D || fov >= 180D) return null;
        double focal = .5D / Math.tan(Math.toRadians(fov) * .5D);
        float u = (float)(.5D + camera.x / -camera.z * focal);
        float v = (float)(.5D - camera.y / -camera.z * focal);
        return Float.isFinite(u) && Float.isFinite(v) ? new float[]{u, v} : null;
    }

    /** Camera-space vertex, retaining authored atlas coordinates during clipping. */
    public record Vertex(float x, float y, float z, float textureU, float textureV) {
        public Vertex(Vector3f position, float u, float v) { this(position.x, position.y, position.z, u, v); }
        public Vector3f position() { return new Vector3f(x, y, z); }
    }

    /**
     * Clip before perspective division. Rejecting a complete face when one corner crosses the
     * eye made an authored vignette disappear abruptly; projecting it first produced huge UVs.
     * UVs are interpolated on the original 3D edges, as the GL clipper does.
     */
    public static List<Vertex> clipCameraPolygon(List<Vertex> subject, double fov, float near) {
        if (subject == null || subject.size() < 3 || !Double.isFinite(fov) || fov <= 0D || fov >= 180D
                || !Float.isFinite(near) || near <= 0F) return List.of();
        for (Vertex p : subject) if (p == null || !Float.isFinite(p.x) || !Float.isFinite(p.y)
                || !Float.isFinite(p.z) || !Float.isFinite(p.textureU) || !Float.isFinite(p.textureV)) return List.of();
        float tangent = (float)Math.tan(Math.toRadians(fov) * .5D);
        List<Vertex> result = subject;
        for (int plane = 0; plane < 5 && !result.isEmpty(); plane++) {
            var input = result;
            result = new ArrayList<>(input.size() + 2);
            Vertex previous = input.getLast();
            float previousSide = side(previous, plane, tangent, near);
            for (Vertex current : input) {
                float currentSide = side(current, plane, tangent, near);
                boolean before = previousSide >= 0F, after = currentSide >= 0F;
                if (before != after) {
                    float t = previousSide / (previousSide - currentSide);
                    result.add(lerp(previous, current, t));
                }
                if (after) result.add(current);
                previous = current;
                previousSide = currentSide;
            }
        }
        return result.size() < 3 ? List.of() : result;
    }

    private static float side(Vertex p, int plane, float tangent, float near) {
        return switch (plane) {
            case 0 -> -p.z - near;
            case 1 -> -p.z * tangent + p.x;
            case 2 -> -p.z * tangent - p.x;
            case 3 -> -p.z * tangent + p.y;
            default -> -p.z * tangent - p.y;
        };
    }

    private static Vertex lerp(Vertex a, Vertex b, float t) {
        return new Vertex(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t,
                a.textureU + (b.textureU - a.textureU) * t, a.textureV + (b.textureV - a.textureV) * t);
    }
}
