package com.flansmod.client.render;

import com.flansmod.client.AimState;
import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Aperture-tested reticles on the pack's actual sight geometry. */
public final class FmumSightRenderer {
	private FmumSightRenderer() { }
	private static final ResourceLocation WHITE=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
	private record Lens(PoseStack pose,AttachmentType type,ModifiableType.DefaultAttachment node,GunType view,
			BakedRig rig,Matrix4f toEye,boolean active,float ads,int overlay,boolean mirrored) { }
	private static final List<Lens> LENSES=new ArrayList<>();
	private static boolean collecting;
	private static Matrix4f savedHandProjection;
	private static com.mojang.blaze3d.vertex.VertexSorting savedSorting;
	public static void beginWeapon(GunType view){
		LENSES.clear();collecting=true;savedHandProjection=null;
		if(GunSightState.scopeSight(view)!=null) {
			savedHandProjection=new Matrix4f(RenderSystem.getProjectionMatrix());savedSorting=RenderSystem.getVertexSorting();
			RenderSystem.setProjectionMatrix(nearProjection(savedHandProjection,.005F),savedSorting);
		}
	}
	/** Close rear glass needs a smaller modern hand near-plane, not a displaced weapon. */
	public static Matrix4f nearProjection(Matrix4f projection,float near) {
		var result=new Matrix4f(projection);float far=projection.m32()/(projection.m22()+1);
		if(Float.isFinite(far) && far>near && near>0)result.m22(-(far+near)/(far-near)).m32(-2*far*near/(far-near));
		return result;
	}
	public static void cancelWeapon(){
		LENSES.clear();collecting=false;
		if(savedHandProjection!=null){RenderSystem.setProjectionMatrix(savedHandProjection,savedSorting);savedHandProjection=null;}
	}
	/** Finish all gun/attachment/arm buffers before drawing a closed tube's optical window. */
	public static void finishWeapon(MultiBufferSource gunBuffers) {
		collecting=false;if(LENSES.isEmpty())return;
		var lenses=new ArrayList<>(LENSES);LENSES.clear();
		lenses.sort(Comparator.comparing(Lens::active));
		if(gunBuffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
		else Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
		try(var memory=new ByteBufferBuilder(4096)) {
			var buffers=MultiBufferSource.immediate(memory);
			ScopeRenderLayers.glass(()->{
				for(var lens:lenses)renderLens(lens.pose,buffers,lens.type,lens.node,lens.view,lens.rig,lens.toEye,
						lens.active,lens.ads,lens.overlay,lens.mirrored);
				buffers.endBatch();
			});
		}
		lenses.stream().filter(Lens::active).findFirst()
				.ifPresent(lens->ScopeProbeCapture.finalGlass(Minecraft.getInstance(),lens.type.shortName,
						lens.node.zoomStep,lens.node.dotFrame));
	}
	public static void render(PoseStack pose,MultiBufferSource buffers,AttachmentType at,
			ModifiableType.DefaultAttachment node,GunType view,BakedRig sightRig,int overlay,boolean mirrored) {
		if(sightRig==null || sightRig.animation==null || !at.isAimingSight())return;
		// GameRenderer cancels its inverse camera transform via the shader model-view matrix.
		Matrix4f toEye=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
		Vector3f eye=new Matrix4f(toEye).invert().transformPosition(new Vector3f());
		if(!eye.isFinite())return;
		float pupil=FmumSightPicture.pupilDistance(sightRig.animation,at.modelScale,eye);
		boolean scope=at.scopeGlassModel!=null && !at.scopeGlassModel.isBlank() && !at.scopeGlassModel.equalsIgnoreCase("none");
		var selected=GunSightState.scopeSight(view);
		boolean active=selected!=null && selected.node()==node && !at.isFoldedVariant();
		float ads=AimState.blend(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
		if(scope) {
			if(collecting) {
				var saved=new PoseStack();saved.last().pose().set(pose.last().pose());saved.last().normal().set(pose.last().normal());
				LENSES.add(new Lens(saved,at,node,view,sightRig,toEye,active,ads,overlay,mirrored));
			} else renderLens(pose,buffers,at,node,view,sightRig,toEye,active,ads,overlay,mirrored);
			return;
		}
		if(at.isFoldedVariant())return;
		// The selected magnifier composites the whole coaxial group's reticles into its lens.
		var group=GunSightState.selectedGroup(view);
		if(selected!=null && ads>.95F && ScopeWorldTexture.available() && group!=null
				&& group.sights().stream().anyMatch(s->s.node()==node))return;
		renderDot(pose,buffers,at,node,sightRig,eye,pupil,ads,overlay,mirrored);
	}
	private static final java.util.Map<String,String> LENS_STATE=new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Set<String> AIM_LOGGED=java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static void renderLens(PoseStack pose,MultiBufferSource buffers,AttachmentType at,
			ModifiableType.DefaultAttachment node,GunType view,BakedRig sightRig,Matrix4f toEye,
			boolean active,float ads,int overlay,boolean mirrored) {
		var glass=ModelCache.rigForReference(at.scopeGlassModel,1F);
		if(glass==null || glass.quads.isEmpty())return;
		var model=sightRig.animation;
		var selected=GunSightState.scopeSight(view);
		int step=selected!=null && selected.node()==node?selected.node().zoomStep:node.zoomStep;
		var picture=ScopeEyeRelief.picture(model,at.modelScale,toEye,step);
		if(com.flansmod.common.FlansMod.LOGGER.isDebugEnabled() && AIM_LOGGED.add(at.shortName)) {
			var cameraGun=new Matrix4f(toEye).invert().transformPosition(new Vector3f());
			var lensCenter=FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(at.modelScale);
			com.flansmod.common.FlansMod.LOGGER.debug("[ScopeAim] "+at.shortName
					+String.format(java.util.Locale.ROOT," cameraGun=(%.5f,%.5f,%.5f) lensCenter=(%.5f,%.5f,%.5f) delta=(%.5f,%.5f,%.5f)",
					cameraGun.x,cameraGun.y,cameraGun.z,lensCenter.x,lensCenter.y,lensCenter.z,
					cameraGun.x-lensCenter.x,cameraGun.y-lensCenter.y,cameraGun.z-lensCenter.z));
		}
		if(com.flansmod.common.FlansMod.LOGGER.isDebugEnabled()) {
			int eyeFacing=glass==null?0:(int)glass.quads.stream().filter(q->q.nx()<-.9F).count();
			String state="active="+active+" ads="+String.format(java.util.Locale.ROOT,"%.2f",ads)
					+" visible="+picture.visible()+" alpha="+String.format(java.util.Locale.ROOT,"%.3f",picture.alpha())
					+" available="+ScopeWorldTexture.available()
					+" glassQuads="+(glass==null?0:glass.quads.size())+" eyeFacing="+eyeFacing;
			if(!state.equals(LENS_STATE.put(at.shortName,state)))
				com.flansmod.common.FlansMod.LOGGER.debug("[ScopeLens] "+at.shortName+" "+state
						+" maskModel="+at.blackMaskModel+" dotModel="+at.lightStuffModel);
		}
		if(active && picture.visible())ScopeWorldTexture.prepare(sightRig,at,view,at,toEye,step,picture.alpha());
		Vector3f center=FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(at.modelScale);
		Vector3f scale=FmumSightPicture.vector(model,"scopeGlassModelScale",1,1,1).mul(at.modelScale);
		pose.pushPose();pose.translate(center.x,center.y,center.z);pose.scale(scale.x,scale.y,scale.z);
		// 1.7.10 draws the scope image whenever the sight's own off-axis alpha passes
		// (RenderGun:239-245); there is no "ADS mostly complete" condition. The eye alignment is
		// already what picture.visible() measures, so waiting for ads>.95 only blacked the lens
		// during the raise.
		if(active && picture.visible() && ScopeWorldTexture.available()) {
			// Keep the complete glass at its authored position. Its UV orientation must be
			// converted to the modern world framebuffer's axes before sampling IMAGE.
			Matrix4f clip=new Matrix4f(RenderSystem.getProjectionMatrix())
					.mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
			renderLensSurface(pose,buffers,glass,clip,picture.alpha(),mirrored);
			flush(buffers);
			// Reticle and mask have already been composed in the scope image.
		} else {
			// 1.7.10 drew an unbound scope's glass black (DefaultBlack), never clear. That also
			// covers the hip-fire case, where the magnified world must not leak through the tube.
			var buffer=buffers.getBuffer(ScopeRenderLayers.blackGlass(WHITE));
			// 1.7.10's DefaultBlack path also rendered the whole glass model.
			RigRenderer.renderPart(pose,buffer,glass.quads,LightTexture.FULL_BRIGHT,overlay,0,0,0,1,mirrored);
			flush(buffers);
		}
		pose.popPose();
	}
	private static void flush(MultiBufferSource buffers){if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();}
	/** Authored UVs to framebuffer sampling: imageUv is right/down, while GL textures are right/up. */
	public static float[] framebufferUv(BakedRig glass,float authoredU,float authoredV) {
		return framebufferUv(glass,authoredU,authoredV,false);
	}
	public static float[] framebufferUv(BakedRig glass,float authoredU,float authoredV,boolean mirrored) {
		float[] image=ScopeReticleRenderer.imageUv(glass,authoredU,authoredV);
		return new float[]{mirrored?1F-image[0]:image[0],1F-image[1]};
	}
	/** Original glass mesh, sampling the world image along the physical lens axes. */
	public static void renderLensSurface(PoseStack pose,MultiBufferSource buffers,BakedRig glass,
			Matrix4f clip,float brightness,boolean mirrored) {
		var buffer=buffers.getBuffer(ScopeWorldTexture.LENS);
		// Resolve the affine orientation once per draw. This preserves wrapping UVs and
		// supports glass whose author UV axes are mirrored or rotated, without per-vertex lookup.
		float[] origin=framebufferUv(glass,0,0,mirrored),uAxis=framebufferUv(glass,1,0,mirrored),vAxis=framebufferUv(glass,0,1,mirrored);
		float uu=uAxis[0]-origin[0],uy=uAxis[1]-origin[1],vu=vAxis[0]-origin[0],vv=vAxis[1]-origin[1];
		// UVs outside 0..1 are authored texture wrapping, not a reliable classification of caps
		// or shell geometry. Dropping those faces creates holes and changes different pack models.
		for(var quad:glass.quads) {
			for(int j=0;j<4;j++) {
				int i=mirrored?3-j:j;float[] p=quad.positions()[i];
				float[] uv=quad.uvs()[i];
				buffer.addVertex(pose.last().pose(),p[0],p[1],p[2])
						.setUv(origin[0]+uu*uv[0]+vu*uv[1],origin[1]+uy*uv[0]+vv*uv[1])
						.setColor(brightness,brightness,brightness,1F);
			}
		}
	}
	public static void renderDot(PoseStack pose,MultiBufferSource buffers,AttachmentType at,
			ModifiableType.DefaultAttachment node,BakedRig sightRig,Vector3f eye,float pupil,
			float ads,int overlay,boolean mirrored) {
		if(at.lightStuffModel==null || at.lightStuffModel.isBlank() || at.lightStuffModel.equalsIgnoreCase("none"))return;
		var model=sightRig.animation;
		float[][] border=model.fmumVectorArrays.get("reticleBorder");
		if(border==null || border.length<3)return; // An unbounded dot would float outside the lens.
		float[][] far=model.fmumVectorArrays.getOrDefault("reticleAt",new float[][]{{0,0,0}});
		ResourceLocation skin=Textures.forReticle(at,node.dotFrame);
		if(skin==null)return;
		var dot=ModelCache.rigForReference(at.lightStuffModel,1F,skin);if(dot==null || dot.quads.isEmpty())return;
		Vector3f normal=FmumSightPicture.vector(model,"normalVecOfDotBorder",1,0,0);
		float shrink=FmumActionPose.scalar(model,"reticleScale",1F)*pupil;
		var mc=Minecraft.getInstance();
		int pixelWidth=mc==null?1280:mc.getWindow().getWidth(),pixelHeight=mc==null?720:mc.getWindow().getHeight();
		var clipFromSight=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
		var buffer=buffers.getBuffer(RenderType.entityTranslucentEmissive(skin));
		int hits=0;
		for(int i=0;i<far.length;i++) {
			if(far[i]==null)continue;
			Vector3f hit=FmumSightPicture.intersection(eye,new Vector3f(far[i]),border,normal);if(hit==null)continue;
			hits++;
			List<BakedRig.Quad> quads=far.length==1?dot.quads:dot.quadsByRenderer.get("defaultModel#"+i);
			if(quads==null || quads.isEmpty())continue;
			float readable=FmumSightPicture.stableReticleScale(dot,clipFromSight,hit,shrink,pixelWidth,pixelHeight);
			pose.pushPose();pose.translate(hit.x,hit.y,hit.z);pose.scale(1,readable,readable);
			RigRenderer.renderPart(pose,buffer,quads,LightTexture.FULL_BRIGHT,overlay,1,1,1,1,mirrored);
			pose.popPose();
		}
		if(hits==0 && ads>.9F) {
			// The eye/pupil reconstruction can place the camera just outside the authored border
			// (notably on rail adapters such as OKP7Pic), which would hide the reticle exactly when
			// the player aims. 1.7.10 still drew the dot at full ADS; fall back to the aperture
			// centre rather than showing an empty optic.
			Vector3f center=new Vector3f();
			for(float[] point:border) center.add(point[0],point[1],point[2]);
			center.mul(1F/border.length);
			float readable=FmumSightPicture.stableReticleScale(dot,clipFromSight,center,shrink,pixelWidth,pixelHeight);
			List<BakedRig.Quad> quads=far.length==1?dot.quads:null;
			if(quads!=null && !quads.isEmpty()) {
				pose.pushPose();pose.translate(center.x,center.y,center.z);pose.scale(1,readable,readable);
				RigRenderer.renderPart(pose,buffer,quads,LightTexture.FULL_BRIGHT,overlay,1,1,1,1,mirrored);
				pose.popPose();
			}
			if(com.flansmod.common.FlansMod.LOGGER.isDebugEnabled())
				com.flansmod.common.FlansMod.LOGGER.debug("[Reticle] "+at.shortName+" ADS fallback at aperture centre "+center);
		}
		if(com.flansmod.common.FlansMod.LOGGER.isDebugEnabled())reportReticle(at,node,skin,border,far.length,hits,pupil,shrink,eye,ads);
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
		String state=(ads>.95F?"ads":"hip")+" hits="+hits+" skin="+skin+" frame="+node.dotFrame;
		if(state.equals(RETICLE_STATE.put(at.shortName,state)))return;
		var mc=Minecraft.getInstance();
		boolean present=mc.getResourceManager()!=null && mc.getResourceManager().getResource(skin).isPresent();
		com.flansmod.common.FlansMod.LOGGER.debug("[Reticle] "+at.shortName
				+" skin="+skin+(present?" present":" MISSING")
				+" border="+border.length+"pts far="+farPoints+"pts hits="+hits
				+String.format(java.util.Locale.ROOT," pupil=%.4f shrink=%.4f ads=%.2f",pupil,shrink,ads)
				+String.format(java.util.Locale.ROOT," eye=(%.3f,%.3f,%.3f) aperture=(%.3f,%.3f,%.3f) offset=(%.4f,%.4f,%.4f)",
						eye.x,eye.y,eye.z,cx,cy,cz,eye.x-cx,eye.y-cy,eye.z-cz)
				+" dotFrame="+node.dotFrame);
	}
}
