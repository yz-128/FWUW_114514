package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bakes the scope reticle and black mask into the square scope framebuffer, exactly where FMUM
 * 1.7.10 draws them: inside the scope-layer render, before {@code glCopyTexImage2D} turns the
 * result into the ocular image.
 *
 * <p>The optical renderers first transform the authored meshes through the original scope
 * camera. Camera-space triangles retain their true depth through GPU perspective interpolation;
 * atlas UVs remain independent of framebuffer coordinates.
 */
public final class ScopeFramebufferOverlays
{
	private ScopeFramebufferOverlays() { }

	private static final Map<ResourceLocation,RenderType> TYPES=new ConcurrentHashMap<>();

	static RenderType type(ResourceLocation texture)
	{
		return TYPES.computeIfAbsent(texture,key->RenderType.create("flansmod_scope_bake_"+key,
				DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.TRIANGLES,4096,false,false,
				RenderType.CompositeState.builder()
						.setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
						.setTextureState(new RenderStateShard.TextureStateShard(key,false,false))
						.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
						.setCullState(RenderStateShard.NO_CULL)
						.setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
						.setWriteMaskState(RenderStateShard.COLOR_WRITE)
						.createCompositeState(false)));
	}

	/** Screen-space triangle in the square scope image; {@code u,v} are the texture coordinates. */
	public record ImageVertex(float u,float v,float textureU,float textureV) { }

	/** The secondary world's camera does not belong in this pass. Use the original aspect-one
	 * hand projection and keep this scope alive through endBatch:
	 * shader matrices are sampled at upload/draw time, after the vertices were emitted. */
	private static final class ImageView implements AutoCloseable {
		private final Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());
		private final VertexSorting sorting=RenderSystem.getVertexSorting();
		private final float[] color=RenderSystem.getShaderColor().clone();
		private final org.joml.Matrix4fStack modelView=RenderSystem.getModelViewStack();
		private final boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
		private final boolean depthWrite=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
		private final int depthFunc=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
		private final boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
		private final boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
		private final int srcRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
		private final int dstRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
		private final int srcAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
		private final int dstAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
		private final net.minecraft.client.renderer.ShaderInstance shader=RenderSystem.getShader();
		private final int[] viewport=new int[4];
		private ImageView(int width,int height,double imageFov) {
			if(!Double.isFinite(imageFov)||imageFov<=0||imageFov>=180)
				throw new IllegalArgumentException("Invalid square scope FOV: "+imageFov);
			modelView.pushMatrix();modelView.identity();RenderSystem.applyModelViewMatrix();
			RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(imageFov),1F,.05F,1000F),
					VertexSorting.DISTANCE_TO_ORIGIN);
			RenderSystem.setShaderColor(1,1,1,1);
			// scopeTarget is rectangular (854x480 in the probe) while IMAGE is square; copySquareColor
			// later crops the centre square. Mapping 0..1 over the whole rectangle would stretch U by
			// W/S (here 1.78x) before that crop. Draw the overlay into exactly the same centre square
			// the copy will keep, so the baked IMAGE stays square.
			org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,viewport);
			int size=Math.min(width,height);
			if(size>0)RenderSystem.viewport((width-size)/2,(height-size)/2,size,size);
			// The returned shard name NO_DEPTH_TEST does not switch an already enabled depth test;
			// the world pass just left it on. Disable it explicitly for this 2D composition and
			// restore the exact previous function/write-mask afterwards.
			RenderSystem.disableDepthTest();
			RenderSystem.depthMask(false);
		}
		@Override public void close() {
			RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
			if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
			if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
			RenderSystem.depthFunc(depthFunc);
			RenderSystem.depthMask(depthWrite);
			if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
			RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
			modelView.popMatrix();RenderSystem.applyModelViewMatrix();
			RenderSystem.setProjectionMatrix(projection,sorting);
			RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
			RenderSystem.setShader(()->shader);
		}
	}

	/** The synchronous image pass owns emission, upload and the staged probe under one camera. */
	public static void render(net.minecraft.client.Minecraft mc,com.mojang.blaze3d.pipeline.RenderTarget target,
			GunType view,AttachmentType scopeType,BakedRig scopeRig,
			Matrix4f toEye,double imageFov,float alpha,int step) {
		render(mc,target,view,scopeType,scopeRig,toEye,imageFov,alpha,step,1F);
	}
	public static void render(net.minecraft.client.Minecraft mc,com.mojang.blaze3d.pipeline.RenderTarget target,
			GunType view,AttachmentType scopeType,BakedRig scopeRig,
			Matrix4f toEye,double imageFov,float alpha,int step,float ads) {
		try(var memory=new com.mojang.blaze3d.vertex.ByteBufferBuilder(8192);
				var imageView=new ImageView(target==null?0:target.width,target==null?0:target.height,imageFov)) {
			var buffers=MultiBufferSource.immediate(memory);
			var scope=GunSightState.scopeSight(view);
			if(scope==null)return;
			// Native static glow is white/opaque in the image. The picture alpha is
			// applied when the physical glass samples IMAGE, once for the whole picture.
			ScopeReticleRenderer.bake(buffers,view,scope,scopeRig,toEye,imageFov,1F,ads);
			buffers.endBatch();
			if(scope!=null && target!=null)
				ScopeProbeCapture.stage(mc,scope.type().shortName,scopeType,view,step,"1-reticle",
						target.getColorTextureId(),target.width,target.height,imageFov,ScopeWorldTexture.renderFov());
			ScopeMaskRenderer.bake(buffers,scopeRig,scope,toEye,imageFov,ads);
			buffers.endBatch();
			if(target!=null)
				ScopeProbeCapture.stage(mc,scope.type().shortName,scopeType,view,step,"2-mask",
						target.getColorTextureId(),target.width,target.height,imageFov,ScopeWorldTexture.renderFov());
		}
	}

	/** Emits one triangle given in image coordinates (0..1, V down) into the scope framebuffer. */
	public static void triangle(com.mojang.blaze3d.vertex.VertexConsumer buffer,
			ImageVertex a,ImageVertex b,ImageVertex c,float brightness,float alpha)
	{
		for(var vertex:new ImageVertex[]{a,b,c})
			buffer.addVertex(vertex.u()*2F-1F,1F-vertex.v()*2F,0F)
					.setUv(vertex.textureU(),vertex.textureV())
					.setColor(brightness,brightness,brightness,alpha);
	}

	/** Camera-space triangle. Preserve z and let the GPU divide by clip W, so sampled atlas
	 * coordinates follow the original perspective-correct interpolation across each face. */
	public static void cameraTriangle(com.mojang.blaze3d.vertex.VertexConsumer buffer,
			LegacyScopeProjection.Vertex a,LegacyScopeProjection.Vertex b,LegacyScopeProjection.Vertex c,
			float brightness,float alpha)
	{
		for(var vertex:new LegacyScopeProjection.Vertex[]{a,b,c})
			buffer.addVertex(vertex.x(),vertex.y(),vertex.z())
					.setUv(vertex.textureU(),vertex.textureV())
					.setColor(brightness,brightness,brightness,alpha);
	}

	/** Order matches FMUM: reticles first, black mask last. */
	public static void bake(MultiBufferSource buffers,GunType view,AttachmentType scopeType,
			BakedRig scopeRig,Matrix4f toEye,double imageFov,float alpha)
	{
		bake(buffers,view,scopeType,scopeRig,toEye,imageFov,alpha,1F);
	}
	public static void bake(MultiBufferSource buffers,GunType view,AttachmentType scopeType,
			BakedRig scopeRig,Matrix4f toEye,double imageFov,float alpha,float ads)
	{
		var scope=GunSightState.scopeSight(view);
		if(scope==null)return;
		ScopeReticleRenderer.bake(buffers,view,scope,scopeRig,toEye,imageFov,1F,ads);
		ScopeMaskRenderer.bake(buffers,scopeRig,scope,toEye,imageFov,ads);
	}
}
