package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Endpoint-derived attachment motion: matched pieces rotate around a recovered physical hinge. */
public final class SightFoldGeometry {
    private static final float POSITION_EPSILON = 1E-5F, FIT_EPSILON = 1E-4F;
    private static final Map<BakedRig, Map<BakedRig, Plan>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private SightFoldGeometry() { }

    public record Plan(List<Track> tracks) {
        public Plan { tracks = List.copyOf(tracks); }
    }

    public static final class Track {
        private final String sourceKey, targetKey;
        private final List<BakedRig.Quad> source, target;
        private final Fit fit;
        private final boolean textureChanged;
        private final Matrix4f inverseEndpoint;
        private Track(Piece source, Piece target, Fit fit) {
            sourceKey = source == null ? "" : source.key;
            targetKey = target == null ? "" : target.key;
            this.source = source == null ? List.of() : source.quads;
            this.target = target == null ? List.of() : target.quads;
            this.fit = fit;
            textureChanged = fit != null && !sameUv(this.source, this.target, false);
            inverseEndpoint = fit == null ? new Matrix4f() : new Matrix4f().rotation(fit.rotation).setTranslation(fit.translation).invert();
        }
        public String sourceKey() { return sourceKey; }
        public String targetKey() { return targetKey; }
        public List<BakedRig.Quad> source() { return source; }
        public List<BakedRig.Quad> target() { return target; }
        public boolean matched() { return fit != null; }
        public boolean fixed() { return fit != null && fit.fixed; }
        public boolean textureChanged() { return textureChanged; }
        public float error() { return fit == null ? Float.POSITIVE_INFINITY : fit.error; }
        /** Matched tracks draw source once under pose(); unmatched geometry cross-fades in place. */
        public float sourceAlpha(float phase) { return source.isEmpty() ? 0 : matched() ? 1 : 1 - Math.clamp(phase, 0, 1); }
        public float targetAlpha(float phase) { return target.isEmpty() || (matched() && !textureChanged) ? 0 : Math.clamp(phase, 0, 1); }
        /** UV-repacked endpoints can overlay target style on the opaque source at the SAME pose. */
        public Matrix4f targetPose(float phase) {
            if (fit == null || phase >= 1) return new Matrix4f();
            return pose(phase).mul(inverseEndpoint);
        }
        public Matrix4f pose(float phase) {
            if (fit == null || fit.fixed || phase <= 0) return new Matrix4f();
            if (phase >= 1) return new Matrix4f().rotation(fit.rotation).setTranslation(fit.translation);
            if (fit.angle < 1E-5F) return new Matrix4f().translation(new Vector3f(fit.translation).mul(phase));
            Vector3f movingPivot = new Vector3f(fit.pivot).fma(fit.axial * phase, fit.axis);
            return new Matrix4f().translation(movingPivot).rotate(fit.angle * phase, fit.axis)
                    .translate(-fit.pivot.x, -fit.pivot.y, -fit.pivot.z);
        }
    }

    private record Piece(String key, List<BakedRig.Quad> quads) { }
    private record Fit(Quaternionf rotation, Vector3f translation, Vector3f axis, Vector3f pivot,
                       float angle, float axial, float error, boolean fixed) { }

    public static Plan plan(BakedRig from, BakedRig to) {
        if (from == null || to == null) return new Plan(List.of());
        synchronized (CACHE) {
            var targets = CACHE.computeIfAbsent(from, ignored -> new WeakHashMap<>());
            return targets.computeIfAbsent(to, ignored -> build(from, to));
        }
    }

    private static List<Piece> pieces(BakedRig rig) {
        if (rig.quadsByRenderer.isEmpty()) return rig.quads.isEmpty() ? List.of() : List.of(new Piece("whole", List.copyOf(rig.quads)));
        var result = new ArrayList<Piece>();
        for (var entry : rig.quadsByRenderer.entrySet()) result.add(new Piece(entry.getKey(), List.copyOf(entry.getValue())));
        return result;
    }

    private static Plan build(BakedRig from, BakedRig to) {
        List<Piece> source = pieces(from), target = pieces(to);
        Track[] tracks = new Track[source.size()];
        boolean[] used = new boolean[target.size()];
        var byName = new LinkedHashMap<String, Integer>();
        for (int j = 0; j < target.size(); j++) byName.put(target.get(j).key, j);
        // G33 and MP7 retain their renderer indices. Even this fast path must verify geometry,
        // because QBZ's independently exported folded models reuse indices for different boxes.
        for (int i = 0; i < source.size(); i++) {
            Integer j = byName.get(source.get(i).key);
            if (j == null || used[j]) continue;
            Fit fit = fit(source.get(i), target.get(j));
            if (fit != null) { tracks[i] = new Track(source.get(i), target.get(j), fit); used[j] = true; }
        }
        // Re-exported endpoints can reorder array elements and move light boxes into the body.
        // Matching per-face UV chart sizes and a verified rigid transform avoids interpolating
        // merely similar-looking but differently sized pieces into one another.
        for (int i = 0; i < source.size(); i++) if (tracks[i] == null) {
            int best = -1; Fit bestFit = null; float score = Float.POSITIVE_INFINITY;
            for (int j = 0; j < target.size(); j++) if (!used[j]) {
                Fit fit = fit(source.get(i), target.get(j));
                if (fit == null) continue;
                float cost = fit.fixed ? -1 : fit.translation.lengthSquared() + fit.angle * .001F;
                if (cost < score) { score = cost; best = j; bestFit = fit; }
            }
            if (best >= 0) { tracks[i] = new Track(source.get(i), target.get(best), bestFit); used[best] = true; }
        }
        var result = new ArrayList<Track>();
        for (int i = 0; i < source.size(); i++) result.add(tracks[i] == null ? new Track(source.get(i), null, null) : tracks[i]);
        for (int j = 0; j < target.size(); j++) if (!used[j]) result.add(new Track(null, target.get(j), null));
        return new Plan(result);
    }

    private static boolean sameUv(List<BakedRig.Quad> a, List<BakedRig.Quad> b, boolean allowAtlasTranslation) {
        if (a.isEmpty() || a.size() != b.size()) return false;
        float du = allowAtlasTranslation ? a.getFirst().uvs()[0][0] - b.getFirst().uvs()[0][0] : 0;
        float dv = allowAtlasTranslation ? a.getFirst().uvs()[0][1] - b.getFirst().uvs()[0][1] : 0;
        for (int i = 0; i < a.size(); i++) {
            float[][] au = a.get(i).uvs(), bu = b.get(i).uvs();
            if (au.length != bu.length || a.get(i).positions().length != b.get(i).positions().length) return false;
            for (int j = 0; j < au.length; j++)
                if (Math.abs(au[j][0] - bu[j][0] - du) > 2E-6F || Math.abs(au[j][1] - bu[j][1] - dv) > 2E-6F) return false;
        }
        return true;
    }

    private static List<Vector3f> vertices(Piece piece) {
        var result = new ArrayList<Vector3f>();
        for (var quad : piece.quads) for (float[] p : quad.positions()) result.add(new Vector3f(p));
        return result;
    }

    private static Fit fit(Piece source, Piece target) {
        if (!sameUv(source.quads, target.quads, true)) return null;
        List<Vector3f> a = vertices(source), b = vertices(target);
        float displacement = 0;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).isFinite() || !b.get(i).isFinite()) return null;
            displacement = Math.max(displacement, a.get(i).distance(b.get(i)));
        }
        if (displacement <= POSITION_EPSILON) return new Fit(new Quaternionf(), new Vector3f(), new Vector3f(1, 0, 0),
                new Vector3f(), 0, 0, displacement, true);
        Vector3f origin = a.getFirst();
        int first = 0, second = 0; float longest = -1, area = -1;
        for (int i = 0; i < a.size(); i++) {
            float distance = a.get(i).distanceSquared(origin);
            if (distance > longest) { longest = distance; first = i; }
        }
        if (longest < 1E-14F) return null;
        Vector3f x = new Vector3f(a.get(first)).sub(origin).normalize();
        for (int i = 0; i < a.size(); i++) {
            float distance = new Vector3f(a.get(i)).sub(origin).cross(x).lengthSquared();
            if (distance > area) { area = distance; second = i; }
        }
        if (area < 1E-14F) return null;
        Vector3f y = new Vector3f(a.get(second)).sub(origin);
        y.sub(new Vector3f(x).mul(y.dot(x))).normalize();
        Vector3f z = new Vector3f(x).cross(y);
        Vector3f bx = new Vector3f(b.get(first)).sub(b.getFirst()).normalize();
        Vector3f by = new Vector3f(b.get(second)).sub(b.getFirst());
        by.sub(new Vector3f(bx).mul(by.dot(bx))).normalize();
        Vector3f bz = new Vector3f(bx).cross(by);
        Quaternionf rotation = new Matrix3f(bx, by, bz).mul(new Matrix3f(x, y, z).transpose())
                .getNormalizedRotation(new Quaternionf()).normalize();
        if (!Float.isFinite(rotation.w) || !Float.isFinite(rotation.x) || !Float.isFinite(rotation.y) || !Float.isFinite(rotation.z)) return null;
        if (rotation.w < 0) rotation.set(-rotation.x, -rotation.y, -rotation.z, -rotation.w);
        Vector3f translation = new Vector3f(b.getFirst()).sub(rotation.transform(new Vector3f(origin)));
        float error = 0;
        for (int i = 0; i < a.size(); i++) error = Math.max(error,
                rotation.transform(new Vector3f(a.get(i))).add(translation).distance(b.get(i)));
        if (!Float.isFinite(error) || error > FIT_EPSILON) return null;
        float sine = (float)Math.sqrt(rotation.x * rotation.x + rotation.y * rotation.y + rotation.z * rotation.z);
        float angle = 2 * (float)Math.atan2(sine, rotation.w);
        Vector3f axis = sine < 1E-7F ? new Vector3f(1, 0, 0) : new Vector3f(rotation.x, rotation.y, rotation.z).div(sine);
        float axial = translation.dot(axis);
        Vector3f perpendicular = new Vector3f(translation).fma(-axial, axis);
        Vector3f pivot = angle < 1E-5F ? new Vector3f() : new Vector3f(perpendicular)
                .add(new Vector3f(axis).cross(perpendicular).mul((float)(1 / Math.tan(angle / 2)))).mul(.5F);
        return new Fit(rotation, translation, axis, pivot, angle, axial, error, false);
    }
}
