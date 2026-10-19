package com.flansmod.client.render;

import com.flansmod.client.AimState;
import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.*;

/** Aperture-tested reticles on the pack's actual sight geometry. */
public final class FmumSightRenderer {
	private FmumSightRenderer() { }
	private static final ResourceLocation WHITE=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
	public static void render(PoseStack pose,MultiBufferSource buffers,AttachmentType at,
			ModifiableType.DefaultAttachment node,GunType view,BakedRig sightRig,int overlay,boolean mirrored) {
		if(sightRig==null || sightRig.animation==null || !"sight".equalsIgnoreCase(at.itsType))return;
		// GameRenderer cancels its inverse camera transform via the shader model-view matrix.
		Matrix4f toEye=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
		Vector3f eye=new Matrix4f(toEye).invert().transformPosition(new Vector3f());
		if(!eye.isFinite())return;
		float pupil=FmumSightPicture.pupilDistance(sightRig.animation,at.modelScale,eye);
		boolean scope=at.scopeGlassModel!=null && !at.scopeGlassModel.isBlank() && !at.scopeGlassModel.equalsIgnoreCase("none");
		var selected=GunSightState.scopeSight(view);
		boolean active=selected!=null && selected.node()==node && !at.isFoldedVariant();
		float ads=AimState.blend(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
		if(scope) {renderLens(pose,buffers,at,view,sightRig,active,ads,overlay,mirrored);return;}
		if(at.isFoldedVariant())return;
		// The selected magnifier composites the whole coaxial group's reticles into its lens.
		var group=GunSightState.selectedGroup(view);
		if(selected!=null && ads>.9F && group!=null && group.sights().stream().anyMatch(s->s.node()==node))return;
		renderDot(pose,buffers,at,node,sightRig,eye,pupil,overlay,mirrored);
	}
	private static void renderLens(PoseStack pose,MultiBufferSource buffers,AttachmentType at,
			GunType view,BakedRig sightRig,boolean active,float ads,int overlay,boolean mirrored) {
		var glass=ModelCache.rigForReference(at.scopeGlassModel,1F);
		if(glass==null || glass.quads.isEmpty())return;
		var model=sightRig.animation;
		Vector3f center=FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(at.modelScale);
		Vector3f scale=FmumSightPicture.vector(model,"scopeGlassModelScale",1,1,1).mul(at.modelScale);
		pose.pushPose();pose.translate(center.x,center.y,center.z);pose.scale(scale.x,scale.y,scale.z);
		if(active && ads>.95F) {
			// Sample the pre-hand world copy at each vertex's own screen position. The world pass is
			// already at the magnified FOV, so this is a 1:1 window onto it: no crop, no stretch, and
			// it opens the closed tube EFT's sight meshes are modelled as.
			if(ScopeWorldTexture.available()) {
				Matrix4f clip=new Matrix4f(RenderSystem.getProjectionMatrix())
						.mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
				var buffer=buffers.getBuffer(ScopeWorldTexture.LENS);
				for(var quad:glass.quads)for(int j=0;j<4;j++) {
					int i=mirrored?3-j:j;
					float[] p=quad.positions()[i];
					Vector4f c=clip.transform(new Vector4f(p[0],p[1],p[2],1F));
					if(!(c.w>1e-6F))continue;
					buffer.addVertex(pose.last().pose(),p[0],p[1],p[2])
							.setUv(c.x/c.w*.5F+.5F,c.y/c.w*.5F+.5F).setColor(1F,1F,1F,1F);
				}
			}
			ScopeReticleRenderer.render(pose,buffers,view,GunSightState.scopeSight(view),sightRig,glass,ScopeMagnification.scopeFov(),overlay,mirrored);
		} else {
			// 1.7.10 drew an unbound scope's glass black (DefaultBlack), never clear. That also
			// covers the hip-fire case, where the magnified world must not leak through the tube.
			var buffer=buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
			RigRenderer.render(pose,buffer,glass,LightTexture.FULL_BRIGHT,overlay,0,0,0,1,mirrored);
		}
		pose.popPose();
	}
	private static void renderDot(PoseStack pose,MultiBufferSource buffers,AttachmentType at,
			ModifiableType.DefaultAttachment node,BakedRig sightRig,Vector3f eye,float pupil,
			int overlay,boolean mirrored) {
		if(at.lightStuffModel==null || at.lightStuffModel.isBlank() || at.lightStuffModel.equalsIgnoreCase("none"))return;
		var model=sightRig.animation;
		float[][] border=model.fmumVectorArrays.get("reticleBorder");
		if(border==null || border.length<3)return; // An unbounded dot would float outside the lens.
		float[][] far=model.fmumVectorArrays.getOrDefault("reticleAt",new float[][]{{0,0,0}});
		float ads=AimState.blend(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
		ResourceLocation skin=at.lightStuffTexture==null?Textures.of(at.texture,false)
				:Textures.of(at.lightStuffTexture+Math.clamp(node.dotFrame,0,Math.max(0,at.lightTextureCount-1)),false);
		if(skin==null)return;
		var dot=ModelCache.rigForReference(at.lightStuffModel,1F,skin);if(dot==null || dot.quads.isEmpty())return;
		Vector3f normal=FmumSightPicture.vector(model,"normalVecOfDotBorder",1,0,0);
		float shrink=FmumActionPose.scalar(model,"reticleScale",1F)*pupil;
		var buffer=buffers.getBuffer(RenderType.entityTranslucentEmissive(skin));
		int hits=0;
		for(int i=0;i<far.length;i++) {
			if(far[i]==null)continue;
			Vector3f hit=FmumSightPicture.intersection(eye,new Vector3f(far[i]),border,normal);if(hit==null)continue;
			hits++;
			List<BakedRig.Quad> quads=far.length==1?dot.quads:dot.quadsByRenderer.get("defaultModel#"+i);
			if(quads==null || quads.isEmpty())continue;
			pose.pushPose();pose.translate(hit.x,hit.y,hit.z);pose.scale(1,shrink,shrink);
			RigRenderer.renderPart(pose,buffer,quads,LightTexture.FULL_BRIGHT,overlay,1,1,1,1,mirrored);
			pose.popPose();
		}
		reportReticle(at,node,skin,border,far.length,hits,pupil,shrink,eye,ads);
	}

	/**
	 * One line per change of outcome, so a missing reticle can be diagnosed from the log alone.
	 *
	 * <p>Reporting only the first frame was actively misleading: that frame is usually mid-raise,
	 * where the eye is legitimately off the optical axis and the dot is correctly hidden. The
	 * interesting state is full ADS, and the useful number is how far the eye sits from the
	 * aperture centre — that is exactly what the aim pivot has to null out.
	 */
	private static final Map<String,String> RETICLE_STATE=new java.util.concurrent.ConcurrentHashMap<>();
	private static void reportReticle(AttachmentType at,ModifiableType.DefaultAttachment node,ResourceLocation skin,
			float[][] border,int farPoints,int hits,float pupil,float shrink,Vector3f eye,float ads) {
		float cx=0F,cy=0F,cz=0F;
		for(float[] point:border) {cx+=point[0];cy+=point[1];cz+=point[2];}
		cx/=border.length;cy/=border.length;cz/=border.length;
		String state=String.format(java.util.Locale.ROOT,"%s hits=%d dx=%.4f dy=%.4f dz=%.4f",
				ads>.9F?"ads":"hip",hits,eye.x-cx,eye.y-cy,eye.z-cz);
		if(state.equals(RETICLE_STATE.put(at.shortName,state)))return;
		var mc=Minecraft.getInstance();
		boolean present=mc.getResourceManager()!=null && mc.getResourceManager().getResource(skin).isPresent();
		com.flansmod.common.FlansMod.LOGGER.info("[Reticle] "+at.shortName
				+" skin="+skin+(present?" present":" MISSING")
				+" border="+border.length+"pts far="+farPoints+"pts hits="+hits
				+String.format(java.util.Locale.ROOT," pupil=%.4f shrink=%.4f ads=%.2f",pupil,shrink,ads)
				+String.format(java.util.Locale.ROOT," eye=(%.3f,%.3f,%.3f) aperture=(%.3f,%.3f,%.3f) offset=(%.4f,%.4f,%.4f)",
						eye.x,eye.y,eye.z,cx,cy,cz,eye.x-cx,eye.y-cy,eye.z-cz)
				+" dotFrame="+node.dotFrame);
	}
}
