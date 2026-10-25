package com.flansmod.client.render;

import com.flansmod.client.AimState;
import com.flansmod.common.FlansMod;
import com.flansmod.common.GunAttachments;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.client.model.loader.BakedRig;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * FMUM's second world camera, rendered once the live hand pose supplies the pupil distance.
 * Its central square is mapped to the authored ocular at its original depth. The surrounding
 * world keeps its original FOV. Iris uses the established single-pass compatibility path.
 */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class ScopeWorldTexture extends RenderStateShard {
	public static final ResourceLocation IMAGE=ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"dynamic/scope_world");
	private static final WorldCopy TEXTURE=new WorldCopy();
	private static boolean registered,ready,failed,scopePass,dualFailed,imageComposed;
	private static boolean g33ProbeWritten;
	private static final java.util.Set<String> ELCAN_PROBES=new java.util.HashSet<>();
	private static RenderTarget scopeTarget;
	private static double imageFov=70,renderFov=70;
	public static final RenderType LENS=RenderType.create("flansmod_scope_world",DefaultVertexFormat.POSITION_TEX_COLOR,
			VertexFormat.Mode.QUADS,4096,false,false,RenderType.CompositeState.builder()
					.setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
					.setTextureState(new TextureStateShard(IMAGE,false,false)).setCullState(CULL)
					.setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
	private ScopeWorldTexture() { super("flansmod_scope_world",()->{},()->{}); }
	public static boolean available() {return ready && !failed;}
	public static boolean inScopePass() {return scopePass;}
	public static double imageFov() {return imageFov;}
	public static double renderFov() {return renderFov;}
	public static boolean dualRenderEnabled() {
		// Fabulous's transparency PostChain retains its original main output target. Swapping
		// mc.mainRenderTarget alone would overwrite the already rendered surrounding scene.
		return !dualFailed && !ShaderMuzzleRenderer.shadersEnabled() && !Minecraft.useShaderTransparency();
	}
	@SubscribeEvent public static void begin(RenderFrameEvent.Pre event) {
		RenderPerf.frame();
		ready=false;imageComposed=false;
	}
	/** The live hand pose supplies eye relief before the synchronous secondary camera is entered. */
	public static void prepare(BakedRig rig,AttachmentType type,GunType view,AttachmentType sightType,
			Matrix4f toEye,int step,float alpha) {
		// The shader/Fabulous fallback was copied before this hand pass; keep its probe useful too.
		if(ready && !scopePass) {
			var mc=Minecraft.getInstance();
			// Shader/Fabulous captured only the world before the hand pass. Composite the same
			// optical layers in a separate target, so the surrounding world is never painted on.
			if(!imageComposed) {
				try {
					ScopeImageComposer.compose(TEXTURE.getId(),TEXTURE.width,TEXTURE.height,target->{
						ScopeProbeCapture.stage(mc,type.shortName,type,view,step,"0-world",target.getColorTextureId(),
								target.width,target.height,imageFov,renderFov);
						ScopeFramebufferOverlays.render(mc,target,view,sightType,rig,toEye,imageFov,alpha,step,
								AimState.blend(mc.getTimer().getGameTimeDeltaPartialTick(true)));
					});
					imageComposed=true;
				} catch(RuntimeException error) {
					ready=false;failed=true;
					FlansMod.LOGGER.warn("Could not compose the compatibility scope image",error);
				}
			}
			captureElcanProbe(mc,type,toEye,RenderSystem.getProjectionMatrix());
		}
		if(ready || failed || scopePass || !dualRenderEnabled() || rig==null || rig.animation==null)return;
		var mc=Minecraft.getInstance();
		if(mc.level==null || mc.player==null || mc.screen!=null || !mc.options.getCameraType().isFirstPerson())return;
		var eye=new Matrix4f(toEye).invert().transformPosition(new org.joml.Vector3f());
		float distance=FmumSightPicture.pupilDistance(rig.animation,type.modelScale,eye);
		double fov=ScopeMagnification.nativeFov(ScopeMagnification.baseFov(),type,step,distance);
		if(!Double.isFinite(fov) || fov<=0 || fov>=179)return;
		// The hand pass queues the gun and sight geometry in the same shared BufferSource the
		// nested world render flushes. Flush it into the real target first, otherwise the nested
		// render's endBatch draws those pending vertices into the scope target and the ocular shows
		// a huge dark gun/receiver/rail instead of the plain world.
		flushPendingHandGeometry(mc);
		RenderTarget original=mc.getMainRenderTarget();
		if(original.viewWidth<1 || original.viewHeight<1)return;
		imageFov=fov;renderFov=ScopeMagnification.squareRenderFov(fov,original.viewWidth,original.viewHeight);
		int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int[] bindings=new int[12],shaderTextures=new int[12];
		for(int unit=0;unit<bindings.length;unit++) {
			RenderSystem.activeTexture(GL13.GL_TEXTURE0+unit);
			bindings[unit]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);shaderTextures[unit]=RenderSystem.getShaderTexture(unit);
		}
		RenderSystem.activeTexture(activeTexture);
		var textureMatrix=new Matrix4f(RenderSystem.getTextureMatrix());
		float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
		var fogColor=RenderSystem.getShaderFogColor().clone();var fogShape=RenderSystem.getShaderFogShape();
		boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);
		int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
		int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
		boolean renderHand=mc.gameRenderer.renderHand;
		var hit=mc.hitResult;var pickedEntity=mc.crosshairPickEntity;
		var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
		var shader=RenderSystem.getShader();float[] color=RenderSystem.getShaderColor().clone();
		boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
		int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		int drawTarget=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),readTarget=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
		var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();
		try(var targetWrites=new TargetWrites();var worldView=new WorldView()) {
			if(scopeTarget==null)scopeTarget=new TextureTarget(original.viewWidth,original.viewHeight,true,Minecraft.ON_OSX);
			else if(scopeTarget.viewWidth!=original.viewWidth || scopeTarget.viewHeight!=original.viewHeight)
				scopeTarget.resize(original.viewWidth,original.viewHeight,Minecraft.ON_OSX);
			if(original.isStencilEnabled() && !scopeTarget.isStencilEnabled())scopeTarget.enableStencil();
			// Only this synchronous render changes the target. Game ticks and options are untouched.
			mc.mainRenderTarget=scopeTarget;scopePass=true;mc.gameRenderer.setRenderHand(false);
			scopeTarget.bindWrite(true);
			mc.getProfiler().push("flansmod_scope");
			long passStart=RenderPerf.ENABLED?System.nanoTime():0L;
			try {mc.gameRenderer.renderLevel(mc.getTimer());}
			finally {mc.getProfiler().pop();if(RenderPerf.ENABLED)RenderPerf.addScopePass(System.nanoTime()-passStart);}
			// Same frame, composition order: world-only first, then the baked optical overlays.
			ScopeProbeCapture.stage(mc,type.shortName,type,view,step,"0-world",
					scopeTarget.getColorTextureId(),scopeTarget.width,scopeTarget.height,imageFov,renderFov);
			// FMUM draws the reticle and black mask in this scope layer, before the framebuffer is
			// copied to the ocular texture; they are part of the image, not overlays on the glass.
			ScopeFramebufferOverlays.render(mc,scopeTarget,view,sightType,rig,toEye,imageFov,alpha,step,
					AimState.blend(mc.getTimer().getGameTimeDeltaPartialTick(true)));
			register(mc);scopeTarget.bindWrite(false);copySquareColor(scopeTarget.viewWidth,scopeTarget.viewHeight);
			imageComposed=true;
			captureG33Probe(mc,type,toEye);
			captureElcanProbe(mc,type,toEye,projection);
		} catch(RuntimeException error) {
			dualFailed=true;ready=false;
			FlansMod.LOGGER.warn("Independent scope camera unavailable; using single-pass optical zoom",error);
		} finally {
			scopePass=false;mc.mainRenderTarget=original;mc.gameRenderer.setRenderHand(renderHand);
			mc.hitResult=hit;mc.crosshairPickEntity=pickedEntity;
			original.bindWrite(true);
			// Hand-rendering optimizers may be drawing into a target different from mc.mainRenderTarget.
			com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawTarget);
			com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readTarget);
			RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
			for(int unit=0;unit<bindings.length;unit++) {
				RenderSystem.setShaderTexture(unit,shaderTextures[unit]);
				RenderSystem.activeTexture(GL13.GL_TEXTURE0+unit);RenderSystem.bindTexture(bindings[unit]);
			}
			RenderSystem.activeTexture(activeTexture);RenderSystem.setTextureMatrix(textureMatrix);
			modelView.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
			RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);RenderSystem.setShader(()->shader);
			RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
			RenderSystem.setShaderFogColor(fogColor[0],fogColor[1],fogColor[2],fogColor[3]);RenderSystem.setShaderFogShape(fogShape);
			RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
			if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
			if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthFunc(depthFunc);
		}
	}
	/** Draws whatever the outer hand pass has queued so far, into the real main target. */
	private static void flushPendingHandGeometry(Minecraft mc) {
		var buffers=mc.renderBuffers();
		if(buffers==null)return;
		var shared=buffers.bufferSource();
		shared.endBatch();
		shared.endLastBatch();
	}
	/** LevelRenderer multiplies its world camera onto the global model-view stack. The normal
	 * frame enters with identity, while a call from renderItemInHand still has its hand camera
	 * installed. Isolate that camera until the secondary scene is complete. */
	private static final class WorldView implements AutoCloseable {
		private final org.joml.Matrix4fStack stack=RenderSystem.getModelViewStack();
		private WorldView() {
			stack.pushMatrix();stack.identity();RenderSystem.applyModelViewMatrix();
		}
		@Override public void close() {stack.popMatrix();RenderSystem.applyModelViewMatrix();}
	}

	/** RenderTarget allocation/resize clears obey the caller's scissor and write masks as well.
	 * Establish complete writes before allocation, then restore even when a renderer fails. Use
	 * RenderSystem setters so vanilla/Sodium state caches agree with the actual driver state. */
	static final class TargetWrites implements AutoCloseable {
		private final boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
		private final boolean depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		private final int[] box=new int[4];
		private final boolean red,green,blue,alpha;
		private final double clearDepth=GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
		private final float[] clearColor=new float[4];
		TargetWrites() {
			GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,box);
			GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clearColor);
			try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
				var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
				red=mask.get(0)!=0;green=mask.get(1)!=0;blue=mask.get(2)!=0;alpha=mask.get(3)!=0;
			}
			RenderSystem.disableScissor();RenderSystem.depthMask(true);RenderSystem.colorMask(true,true,true,true);
		}
		@Override public void close() {
			RenderSystem.colorMask(red,green,blue,alpha);RenderSystem.depthMask(depthWrite);
			RenderSystem.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);
			RenderSystem.clearDepth(clearDepth);
			RenderSystem.enableScissor(box[0],box[1],box[2],box[3]);
			if(!scissor)RenderSystem.disableScissor();
		}
	}
	/** One local texture capture separates a captured solid cap from later optical-layer errors. */
	private static void captureG33Probe(Minecraft mc,AttachmentType type,Matrix4f toEye) {
		if(!Boolean.parseBoolean(System.getProperty("flansmod.legacyScopeProbe","false")))return;
		if(g33ProbeWritten || !"G33Enabled".equals(type.shortName)
				|| !Boolean.parseBoolean(System.getProperty("flansmod.scopeProbe","true"))
				|| AimState.blend(mc.getTimer().getGameTimeDeltaPartialTick(true))<.99F)return;
		g33ProbeWritten=true;
		int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		RenderSystem.activeTexture(GL13.GL_TEXTURE0);
		int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),pack=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
		try {
			var directory=mc.gameDirectory.toPath().resolve("flansmod-diagnostics/optics-0.5.9");
			java.nio.file.Files.createDirectories(directory);
			TextureUtil.writeAsPNG(directory,"g33-scope-world",TEXTURE.getId(),0,TEXTURE.width,TEXTURE.height);
			String details="version=0.5.9\nscope="+type.shortName+"\nimageSize="+TEXTURE.width+"x"+TEXTURE.height
					+"\nimageTexture="+TEXTURE.getId()+"\nreadFramebuffer="+GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
					+"\ndrawFramebuffer="+GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
					+"\nimageFov="+imageFov+"\nrenderFov="+renderFov+"\nrenderHand="+mc.gameRenderer.renderHand
					+"\ntoEye="+toEye+"\n";
			java.nio.file.Files.writeString(directory.resolve("g33-scope-world.txt"),details,java.nio.charset.StandardCharsets.UTF_8);
			FlansMod.LOGGER.info("[ScopeProbe] Saved one full-ADS G33 world texture to {}",directory.toAbsolutePath());
		} catch(Exception error) {
			FlansMod.LOGGER.warn("Could not save the one-time G33 scope diagnostic",error);
		} finally {
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,pack);
			RenderSystem.bindTexture(bound);RenderSystem.activeTexture(active);
		}
	}
	/** Two bounded, opt-out captures distinguish the raw ELCAN scene from later body/mask layers. */
	private static void captureElcanProbe(Minecraft mc,AttachmentType type,Matrix4f toEye,Matrix4f handProjection) {
		if(!Boolean.parseBoolean(System.getProperty("flansmod.legacyScopeProbe","false")))return;
		if(type==null || toEye==null || !("SeecterDR1x4x".equals(type.shortName) || "ELCANSpecterDR1x4x".equals(type.shortName))
				|| ELCAN_PROBES.contains(type.shortName) || !ready
				|| !Boolean.parseBoolean(System.getProperty("flansmod.scopeProbe","true"))
				|| AimState.blend(mc.getTimer().getGameTimeDeltaPartialTick(true))<.99F)return;
		ELCAN_PROBES.add(type.shortName); // One attempt per optic per client process, including IO failures.
		int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		RenderSystem.activeTexture(GL13.GL_TEXTURE0);
		int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),pack=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
		try {
			var directory=mc.gameDirectory.toPath().resolve("flansmod-diagnostics/optics-0.5.40");
			java.nio.file.Files.createDirectories(directory);
			String name=type.shortName.toLowerCase(java.util.Locale.ROOT)+"-scope-world";
			TextureUtil.writeAsPNG(directory,name,TEXTURE.getId(),0,TEXTURE.width,TEXTURE.height);
			int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
			String details="version=0.5.40\nscope="+type.shortName+"\nsource="+(dualRenderEnabled()?"independent-scope-world":"single-pass-copy")
					+"\nimageSize="+TEXTURE.width+"x"+TEXTURE.height+"\nimageTexture="+TEXTURE.getId()
					+"\nimageFov="+imageFov+"\nrenderFov="+renderFov+"\nrenderHand="+mc.gameRenderer.renderHand
					+"\nreadFramebuffer="+GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
					+"\ndrawFramebuffer="+GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
					+"\nviewport="+java.util.Arrays.toString(viewport)+"\nscissor="+GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
					+"\ndepthTest="+GL11.glIsEnabled(GL11.GL_DEPTH_TEST)+"\ndepthWrite="+GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
					+"\nshaderPack="+ShaderMuzzleRenderer.shadersEnabled()+"\nfabulous="+Minecraft.useShaderTransparency()
					+"\ngpu="+GL11.glGetString(GL11.GL_RENDERER)+"\nglVersion="+GL11.glGetString(GL11.GL_VERSION)
					+"\ntoEye="+toEye+"\nhandProjection="+handProjection
					+"\nprojectionAtProbe="+RenderSystem.getProjectionMatrix()+"\nmodelViewAtProbe="+RenderSystem.getModelViewMatrix()+"\n";
			java.nio.file.Files.writeString(directory.resolve(name+".txt"),details,java.nio.charset.StandardCharsets.UTF_8);
			FlansMod.LOGGER.info("[ScopeProbe] Saved one full-ADS {} raw world texture to {}",type.shortName,directory.toAbsolutePath());
		} catch(Exception error) {
			FlansMod.LOGGER.warn("Could not save the one-time {} scope diagnostic",type.shortName,error);
		} finally {
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,pack);
			RenderSystem.bindTexture(bound);RenderSystem.activeTexture(active);
		}
	}
	private static void register(Minecraft mc) {
		if(!registered) {mc.getTextureManager().register(IMAGE,TEXTURE);registered=true;}
	}
	@SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
		ready=false;dualFailed=false;imageComposed=false;
		if(scopeTarget!=null) {scopeTarget.destroyBuffers();scopeTarget=null;}
		ScopeImageComposer.release();
	}
	@SubscribeEvent(priority=EventPriority.HIGHEST)
	public static void beforeHand(RenderHandEvent event) {
		if(ready || failed || scopePass || event.isCanceled())return;
		var mc=Minecraft.getInstance();
		// Iris renders the world into its own targets; a copy taken during its shadow pass would
		// capture the depth-only view. Everything else is left to the normal path, where a copy of
		// whatever is bound still yields a magnified world image.
		ShaderMuzzleRenderer.shadersEnabled(); // Resolve Iris before the first shadow-pass query.
		if(ShaderMuzzleRenderer.shadowPass())return;
		if(mc.player==null || !mc.options.getCameraType().isFirstPerson() || AimState.blend(event.getPartialTick())<.01F)return;
		if(com.flansmod.common.PlayerHandler.getPlayerData(mc.player).sightFolding(mc.player))return;
		var held=mc.player.getMainHandItem();
		if(!(InfoType.getType(held) instanceof GunType gun) || GunSightState.scopeSight(GunAttachments.view(gun,held))==null)return;
		int width=mc.getMainRenderTarget().viewWidth,height=mc.getMainRenderTarget().viewHeight;
		if(width<1 || height<1)return;
		// An independent pass already supplied this frame; never replace it with the unzoomed world.
		if(dualRenderEnabled())return;
		register(mc);
		renderFov=ScopeMagnification.scopeFov();
		imageFov=ScopeMagnification.squareImageFov(renderFov,width,height);
		try {copySquareColor(width,height);}
		catch(RuntimeException error) {
			failed=true;FlansMod.LOGGER.warn("Scope colour copy unavailable; the ocular will stay black",error);
		}
	}
	/** Copies the currently bound read framebuffer, preserving both active unit and texture binding. */
	public static void copyColor(int width,int height) {
		copyColorRegion(0,0,width,height);
	}
	/** A central square is the same aspect-one camera as FMUM's square framebuffer. */
	public static void copySquareColor(int width,int height) {
		int size=Math.min(width,height);copyColorRegion((width-size)/2,(height-size)/2,size,size);
	}
	private static void copyColorRegion(int x,int y,int width,int height) {
		if(width<1 || height<1)throw new IllegalArgumentException("Empty scope image");
		int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		RenderSystem.activeTexture(GL13.GL_TEXTURE0);
		int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		try {
			TEXTURE.prepare(width,height);
			// RenderHandEvent precedes main-hand vertices; the current read framebuffer holds the world.
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,x,y,width,height);
			ready=true;
			imageComposed=false;
		} finally {RenderSystem.bindTexture(bound);RenderSystem.activeTexture(active);}
	}
	private static final class WorldCopy extends AbstractTexture {
		int width,height;
		void prepare(int w,int h) {
			boolean allocated=w!=width || h!=height;
			// The original scope image is GL_RGB. Modern world targets may contain zero
			// alpha in the sky; copying that alpha must not punch holes through opaque glass.
			if(allocated) {TextureUtil.prepareImage(com.mojang.blaze3d.platform.NativeImage.InternalGlFormat.RGB,getId(),w,h);width=w;height=h;}
			RenderSystem.bindTexture(getId());
			if(allocated) {
				// RenderGun creates nearest-filtered textures with the GL_REPEAT default. The
				// authored glass includes UVs outside 0..1; preserve their sampling contract.
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
			}
		}
		@Override public void load(ResourceManager manager) {
			releaseId();width=height=0;ready=false;failed=false;dualFailed=false;imageComposed=false;
			ScopeImageComposer.release();
		}
	}
}
