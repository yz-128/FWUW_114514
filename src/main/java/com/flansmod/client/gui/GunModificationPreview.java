package com.flansmod.client.gui;

import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.render.FmumReloadPose;
import com.flansmod.client.render.Textures;
import com.flansmod.common.GunActionState;
import com.flansmod.common.GunMagazine;
import com.flansmod.common.GunReloadState;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ModifiableType;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real pack geometry in assembly space; deliberately independent of first-person animations. */
public final class GunModificationPreview {
    private record Piece(BakedRig rig, ResourceLocation texture, Matrix4f transform, int[] path) { }
    private final List<Piece> pieces = new ArrayList<>();
    private float yaw = -24F, pitch = 0F, zoom = 1F;
    private GunModificationView.Frame renderedFrame;
    private GunModificationView.Frame frozenFrame;
    private int renderedX, renderedY, renderedWidth, renderedHeight;
    /** 1.7.10's fireModeNum for the previewed stack; the selector is sliced by it. */
    private int fireMode;

    /** A projected opaque face; larger GUI Z is closer to the viewer. */
    public record PickQuad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int[] path) { }

    public GunModificationView.Frame frame() { return renderedFrame; }

    /** Rail drags keep their initial camera frame while the assembly silhouette changes. */
    public void lockFrame(boolean locked) { frozenFrame = locked ? renderedFrame : null; }

    /** Returns only an actual visible attachment; receiver and magazine faces still occlude it. */
    public int[] pickAttachment(double mouseX, double mouseY) {
        if(renderedFrame == null) return new int[0];
        var faces = new ArrayList<PickQuad>();
        for(var piece : pieces) {
            var transform = new Matrix4f(renderedFrame.matrix()).mul(piece.transform);
            var hidden = piece.rig.switchModelHidden(fireMode);
            for(var quad : piece.rig.quads) {
                if(!hidden.isEmpty() && hidden.contains(quad)) continue;
                if(piece.rig.hasEmissiveProfile() && !piece.rig.attachmentVisible(quad,fireMode)) continue;
                var points = new Vector3f[4];
                for(int i = 0; i < 4; i++) {
                    var p = quad.positions()[i];
                    points[i] = transform.transformPosition(new Vector3f(p[0], p[1], p[2]));
                }
                // Match renderMesh's reversed vertex emission, including its triangle diagonal.
                faces.add(new PickQuad(points[3], points[2], points[1], points[0], piece.path));
            }
        }
        return pickProjected(faces, mouseX, mouseY, renderedX, renderedY, renderedWidth, renderedHeight);
    }

    /** Pure orthographic hit test, including front-face interpolation and opaque non-part blockers. */
    public static int[] pickProjected(List<PickQuad> faces, double mouseX, double mouseY,
                                      int x, int y, int width, int height) {
        if(faces == null || !Double.isFinite(mouseX) || !Double.isFinite(mouseY) || width <= 0 || height <= 0
                || mouseX < x || mouseY < y || mouseX >= (double)x + width || mouseY >= (double)y + height)
            return new int[0];
        double front = Double.NEGATIVE_INFINITY;
        int[] hit = null;
        for(var face : faces) {
            if(face == null) continue;
            double first = triangleDepth(face.a, face.b, face.c, mouseX, mouseY);
            double second = triangleDepth(face.a, face.c, face.d, mouseX, mouseY);
            double depth = Double.isFinite(first) ? first : second;
            if(Double.isFinite(second) && (!Double.isFinite(depth) || second > depth)) depth = second;
            // LEQUAL rendering lets the later face win when two surfaces are coplanar.
            if(Double.isFinite(depth) && depth >= front) { front = depth; hit = face.path; }
        }
        if(hit == null || hit.length == 0 || hit[0] < 0) return new int[0];
        return Arrays.copyOf(hit, hit.length);
    }

    private static double triangleDepth(Vector3f a, Vector3f b, Vector3f c, double x, double y) {
        if(a == null || b == null || c == null || !a.isFinite() || !b.isFinite() || !c.isFinite()) return Double.NaN;
        double abX = (double)b.x - a.x, abY = (double)b.y - a.y;
        double acX = (double)c.x - a.x, acY = (double)c.y - a.y;
        double area = abX * acY - abY * acX;
        if(Math.abs(area) < 1E-8) return Double.NaN;
        double u = ((x - a.x) * acY - (y - a.y) * acX) / area;
        double v = (abX * (y - a.y) - abY * (x - a.x)) / area;
        if(u < -1E-7 || v < -1E-7 || u + v > 1 + 1E-7) return Double.NaN;
        return (1 - u - v) * a.z + u * b.z + v * c.z;
    }

    public void reset() { yaw = -24F; pitch = 0F; zoom = 1F; }
    public void drag(double dx, double dy) {
        yaw = (yaw + (float)dx * .65F) % 360F;
        pitch = Math.max(-80F, Math.min(80F, pitch + (float)dy * .55F));
    }
    public void zoom(double amount) { zoom = Math.max(.5F, Math.min(2.8F, zoom * (float)Math.pow(1.12, amount))); }

    /** Hit identities come from the real assembly; the same rendered frame handles preview zoom. */
    public Vector3f projectMount(GunType gun, List<List<DefaultAttachment>> realTree,
                                 int[] parentPath, int slot, float railPosition) {
        return GunModificationView.projectMount(renderedFrame, gun, realTree, parentPath, slot, railPosition);
    }

    /** Returns false while the asynchronous model cache has not produced the receiver yet. */
    public boolean render(GuiGraphics graphics, GunType gun, ItemStack stack,
                          List<List<DefaultAttachment>> tree, int[] highlighted,
                          int x, int y, int width, int height) {
        pieces.clear();
        renderedFrame = null;
        renderedX = x; renderedY = y; renderedWidth = width; renderedHeight = height;
        var receiver = ModelCache.rigFor(gun, gun.modelScale);
        fireMode = gun.fireModeIndex(stack);
        add(gun, receiver, new Matrix4f(), new int[0]);
        for(var group : tree) for(int i = 0; i < group.size(); i++) {
            var node = group.get(i);
            append(gun, node, new Matrix4f(), new int[] {node.slot, i}, 0);
        }
        if(receiver != null) {
            var magazine = GunMagazine.get(stack, gun, 0);
            if(InfoType.getType(magazine) instanceof GunType mag && mag.itsType == GunType.GunItsType.MAG) {
                var transform = FmumReloadPose.magazine(new Matrix4f(), receiver.animation,
                        GunReloadState.Frame.IDLE, GunActionState.Frame.IDLE);
                add(mag, ModelCache.rigFor(mag, mag.modelScale), transform, new int[] {-1, -1});
            }
        }
        if(pieces.isEmpty() || width < 4 || height < 4) return false;

        // Include all nested attachments and the seated magazine when framing the weapon.
        List<Vector3f> corners=new ArrayList<>();
        Vector3f point = new Vector3f();
        for(var piece : pieces) {
            BakedRig r = piece.rig;
            for(int corner = 0; corner < 8; corner++) {
                point.set((corner & 1) == 0 ? r.minX : r.maxX,
                        (corner & 2) == 0 ? r.minY : r.maxY,
                        (corner & 4) == 0 ? r.minZ : r.maxZ);
                piece.transform.transformPosition(point);corners.add(new Vector3f(point));
            }
        }
        var frame=frozenFrame != null ? frozenFrame : GunModificationView.frame(corners,yaw,pitch,zoom,x,y,width,height);
        if(frame==null) return false;
        renderedFrame = frame;
        var pose = graphics.pose();
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        graphics.flush();
        graphics.enableScissor(x, y, x + width, y + height);
        pose.pushPose();
        try {
            // Depth-writing layer inside a scoped depth guard: without it the receiver's
            // interior shows through its own shell (see GunModificationRenderLayers).
            GunModificationRenderLayers.withDepth(() -> {
                pose.mulPose(frame.matrix());
                for(var piece : pieces) {
                    pose.pushPose();
                    try {
                        pose.mulPose(piece.transform);
                        boolean selected = highlighted != null && highlighted.length > 0 && Arrays.equals(piece.path, highlighted);
                        var buffer = graphics.bufferSource().getBuffer(GunModificationRenderLayers.mesh(piece.texture));
                        renderMesh(pose,buffer,piece.rig,selected,fireMode);
                    } finally { pose.popPose(); }
                }
                graphics.bufferSource().endBatch();
            });
        } finally {
            pose.popPose();
            graphics.flush();
            graphics.disableScissor();
            if(depthWasEnabled) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
        return receiver != null;
    }

    /** Offline compatibility entry; uses the legacy default mode when no stack is supplied. */
    private static void renderMesh(PoseStack pose,VertexConsumer buffer,BakedRig rig,boolean selected) {
        renderMesh(pose,buffer,rig,selected,-1);
    }

    private static void renderMesh(PoseStack pose,VertexConsumer buffer,BakedRig rig,boolean selected,int fireMode) {
        if(rig.hasEmissiveProfile()) {
            renderQuads(pose,buffer,rig.attachmentBody(fireMode),selected,rig.switchModelHidden(fireMode),null);
            for(var pass:rig.emissivePasses(fireMode))
                renderQuads(pose,buffer,pass.quads(),selected,rig.switchModelHidden(fireMode),pass.light());
            return;
        }
        renderQuads(pose,buffer,rig.quads,selected,rig.switchModelHidden(fireMode),null);
    }

    private static void renderQuads(PoseStack pose,VertexConsumer buffer,java.util.List<BakedRig.Quad> quads,
                                    boolean selected,java.util.Set<BakedRig.Quad> hidden,
                                    com.flansmod.client.model.loader.ModelGeometry.GlowLight emission) {
        for(var quad:quads) {
            if(!hidden.isEmpty() && hidden.contains(quad)) continue;
            var normal=pose.last().normal().transform(new Vector3f(quad.nx(),quad.ny(),quad.nz()));
            float light=GunModificationView.shade(normal);
            if(emission!=null) light=emission.absolute()?1F:Math.clamp(light+emission.strength()/15F,0F,1F);
            if(normal.lengthSquared()<.000001F || !Float.isFinite(normal.lengthSquared())) normal.set(0,0,1);
            else normal.normalize();
            // The GUI's negative Y scale reverses winding. Transform normals with the same pose.
            for(int i=3;i>=0;i--) {
                var point=quad.positions()[i];var uv=quad.uvs()[i];
                buffer.addVertex(pose.last().pose(),point[0],point[1],point[2])
                        .setColor(light,light*(selected?.94F:1F),light*(selected?.74F:1F),1F)
                        .setUv(uv[0],uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(normal.x,normal.y,normal.z);
            }
        }
    }

    private void add(InfoType type, BakedRig rig, Matrix4f transform, int[] path) {
        if(rig == null || rig.quads.isEmpty()) return;
        var texture = Textures.forType(type);
        if(texture == null) texture = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        pieces.add(new Piece(rig, texture, transform, path));
    }

    private void append(ModifiableType owner, DefaultAttachment node, Matrix4f parent, int[] path, int depth) {
        if(depth >= 8 || node.slot < 0 || node.slot >= owner.slots.length) return;
        var type = AttachmentType.getAttachment(node.name);
        if(type == null) return;
        var slot = owner.slots[node.slot];
        var transform = new Matrix4f(parent).translate(slot.x + node.pos, slot.y, slot.z)
                .rotateX((float)Math.toRadians(slot.rotate));
        add(type, ModelCache.rigFor(type, type.modelScale), transform, path);
        int[] indices = new int[type.slots.length];
        for(var child : node.children) {
            if(child.slot < 0 || child.slot >= indices.length) continue;
            var childPath = Arrays.copyOf(path, path.length + 2);
            childPath[path.length] = child.slot;
            childPath[path.length + 1] = indices[child.slot]++;
            append(type, child, transform, childPath, depth + 1);
        }
    }
}
