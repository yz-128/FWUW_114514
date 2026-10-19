package com.flansmod.client.render;

import com.flansmod.client.SightFoldAnimations;
import com.flansmod.client.model.loader.*;
import com.flansmod.common.network.GunSightAnimationPayload.Part;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Rigid moving pieces, stationary mounts and a supporting-hand gesture, driven by accepted state. */
public final class SightFoldRenderer {
    private SightFoldRenderer() { }
    private static boolean visible(BakedRig rig,String key) {
        var body=rig.quadsByPart.get("attachmentModel");return body==null || body.isEmpty() || key.startsWith("attachmentModel#");
    }
    private static ResourceLocation skin(AttachmentType type) {
        var result=Textures.of(type.texture,false);
        return result==null?ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"):result;
    }
    public static boolean render(PoseStack pose,MultiBufferSource buffers,Part part,float phase,
                                 int light,int overlay,boolean mirrored,boolean firstPerson) {
        var from=AttachmentType.getAttachment(part.from());var to=AttachmentType.getAttachment(part.to());
        if(from==null || to==null)return false;
        var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
        if(a==null)return false;
        if(b==null) {
            var body=a.quadsByPart.getOrDefault("attachmentModel",a.quads);
            draw(pose,buffers,skin(from),body,new Matrix4f(),1,light,overlay,mirrored,false);return true;
        }
        var plan=SightFoldGeometry.plan(a,b);
        if(phase>=1)draw(pose,buffers,skin(to),b.quadsByPart.getOrDefault("attachmentModel",b.quads),new Matrix4f(),1,light,overlay,mirrored,false);
        else for(var track:plan.tracks()) {
            boolean source=!track.source().isEmpty() && visible(a,track.sourceKey());
            boolean target=!track.target().isEmpty() && visible(b,track.targetKey());
            if(track.matched() && source && target) {
                draw(pose,buffers,skin(from),track.source(),track.pose(phase),1,light,overlay,mirrored,false);
                if(track.textureChanged() || !skin(from).equals(skin(to)))
                    drawUv(pose,buffers,skin(to),track.source(),track.target(),track.pose(phase),phase,light,overlay,mirrored,false);
            } else {
                if(source)draw(pose,buffers,skin(from),track.source(),track.pose(phase),1-phase,light,overlay,mirrored,false);
                if(target)draw(pose,buffers,skin(to),track.target(),track.targetPose(phase),phase,light,overlay,mirrored,false);
            }
        }
        // No magnified image or floating reticle during the gesture. Its black glass follows the hinge.
        if(firstPerson && from.scopeGlassModel!=null && a.animation.fmumPose.containsKey("pupilRefCenter")) {
            var glass=ModelCache.rigForReference(from.scopeGlassModel,1F);
            var moving=plan.tracks().stream().filter(t->t.matched() && !t.fixed()).findFirst().orElse(null);
            if(glass!=null && moving!=null) {
                var center=FmumSightPicture.vector(a.animation,"pupilRefCenter",0,0,0).mul(from.modelScale);
                var scale=FmumSightPicture.vector(a.animation,"scopeGlassModelScale",1,1,1).mul(from.modelScale);
                var matrix=moving.pose(phase).translate(center).scale(scale);
                draw(pose,buffers,skin(from),glass.quads,matrix,1,LightTexture.FULL_BRIGHT,overlay,mirrored,true);
            }
        }
        return true;
    }
    private static void draw(PoseStack pose,MultiBufferSource buffers,ResourceLocation skin,List<BakedRig.Quad> quads,
                             Matrix4f matrix,float alpha,int light,int overlay,boolean mirrored,boolean black) {
        drawUv(pose,buffers,skin,quads,quads,matrix,alpha,light,overlay,mirrored,black);
    }
    private static void drawUv(PoseStack pose,MultiBufferSource buffers,ResourceLocation skin,List<BakedRig.Quad> quads,
                               List<BakedRig.Quad> uvQuads,Matrix4f matrix,float alpha,int light,int overlay,boolean mirrored,boolean black) {
        if(alpha<=0 || quads.isEmpty())return;
        var type=black?RenderType.entityTranslucentEmissive(skin):alpha>=1?RenderType.entityCutout(skin):RenderType.entityTranslucent(skin);
        var buffer=buffers.getBuffer(type);pose.pushPose();pose.mulPose(matrix);
        for(int q=0;q<quads.size();q++) {
            var quad=quads.get(q);
            // Legacy boxes can already be rotated in the bake; derive their actual face normal.
            var normal=new Vector3f(quad.positions()[1]).sub(new Vector3f(quad.positions()[0]))
                    .cross(new Vector3f(quad.positions()[2]).sub(new Vector3f(quad.positions()[0])));
            if(normal.lengthSquared()<1e-14F)normal.set(quad.nx(),quad.ny(),quad.nz());
            pose.last().normal().transform(normal).normalize();
            for(int j=0;j<4;j++) {
                int i=mirrored?3-j:j;var p=quad.positions()[i];var uv=uvQuads.get(q).uvs()[i];
                buffer.addVertex(pose.last().pose(),p[0],p[1],p[2]).setColor(black?0F:1F,black?0F:1F,black?0F:1F,alpha)
                        .setUv(uv[0],uv[1]).setOverlay(overlay).setLight(light).setNormal(normal.x,normal.y,normal.z);
            }
        }
        pose.popPose();
    }
    /** Child mounts are already expressed at the received endpoint, so undo the remaining rotation. */
    public static Matrix4f children(Part part,float phase,AttachmentType current) {
        var from=AttachmentType.getAttachment(part.from());var to=AttachmentType.getAttachment(part.to());
        var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
        if(a!=null && b!=null)for(var track:SightFoldGeometry.plan(a,b).tracks())if(track.matched() && !track.fixed())
            return current==from?track.pose(phase):track.targetPose(phase);
        return new Matrix4f();
    }
    public static Vector3f contact(Part part,float phase) {
        var from=AttachmentType.getAttachment(part.from());var to=AttachmentType.getAttachment(part.to());
        var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
        if(a==null || b==null)return null;
        Vector3f center=new Vector3f();int count=0;
        for(var track:SightFoldGeometry.plan(a,b).tracks())if(track.matched() && !track.fixed() && visible(a,track.sourceKey())) {
            Matrix4f matrix=track.pose(phase);
            for(var quad:track.source())for(var p:quad.positions()) {center.add(matrix.transformPosition(new Vector3f(p)));count++;}
        }
        return count==0?null:center.div(count).add(0,-.05F,-.09F);
    }
    public static Matrix4f arm(Matrix4f holding,SightFoldAnimations.Frame frame,GunType view) {
        if(!frame.active())return holding;
        var part=frame.parts().getFirst();var mount=SightFoldPose.mount(view,part.path());
        var point=contact(part,frame.phase());if(mount==null || point==null)return holding;
        mount.transformPosition(point);
        return SightFoldPose.hand(holding,point,frame.progress(),AttachmentType.getAttachment(part.to()).isFoldedVariant());
    }
}
