package com.flansmod.client.render;

import com.flansmod.common.FlansMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** First-person flash after Iris finishes the scene, before the HUD, with vanilla unlit shaders.
 * Shader packs can replace hand alpha/emission, so full-bright entity meshes alone are insufficient. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class ShaderMuzzleRenderer extends RenderStateShard {
	private ShaderMuzzleRenderer() { super("flansmod_shader_muzzle",()->{},()->{}); }
	private static final FmumShaderFlash.Frames FRAMES=new FmumShaderFlash.Frames();
	private static final Map<ResourceLocation,RenderType> TYPES=new ConcurrentHashMap<>();
	private static final RenderType GLOW=RenderType.create("flansmod_shader_muzzle_glow",
			DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,4096,false,false,
			RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
					.setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
					.setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
	private static final ShaderStateShard FLASH_SHADER=new ShaderStateShard(GameRenderer::getPositionTexColorShader);
	private static Object iris;
	private static Method enabled,shadow;
	private static boolean checked;
	public static boolean shadersEnabled() {
		if(!checked) {
			checked=true;
			try {
				var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				iris=api.getMethod("getInstance").invoke(null);
				enabled=api.getMethod("isShaderPackInUse");shadow=api.getMethod("isRenderingShadowPass");
			} catch(ReflectiveOperationException | LinkageError unavailable) { iris=null; }
		}
		try { return iris!=null && Boolean.TRUE.equals(enabled.invoke(iris)); }
		catch(ReflectiveOperationException unavailable) { return false; }
	}
	public static boolean shadowPass() {
		try { return iris!=null && Boolean.TRUE.equals(shadow.invoke(iris)); }
		catch(ReflectiveOperationException unavailable) { return false; }
	}
	public static void capture(boolean offHand,FmumShaderFlash.Frame frame) { FRAMES.put(offHand,frame); }
	public static RenderType flashType(ResourceLocation texture) {
		return TYPES.computeIfAbsent(texture,key->RenderType.create("flansmod_shader_muzzle_flash",
				DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,false,
				RenderType.CompositeState.builder().setShaderState(FLASH_SHADER)
						.setTextureState(new TextureStateShard(key,true,false))
						.setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
						.setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));
	}
	@SubscribeEvent
	public static void beginFrame(RenderFrameEvent.Pre event) { FRAMES.clear(); }
	@SubscribeEvent(priority=EventPriority.LOWEST)
	public static void afterScene(RenderGuiEvent.Pre event) {
		var frames=FRAMES.take();var mc=Minecraft.getInstance();
		if(frames.isEmpty() || event.isCanceled() || !shadersEnabled() || mc.player==null
				|| !mc.options.getCameraType().isFirstPerson() || mc.options.hideGui)return;
		event.getGuiGraphics().flush();
		var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
		boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		float[] colour=RenderSystem.getShaderColor().clone();
		var shader=RenderSystem.getShader();
		var view=RenderSystem.getModelViewStack();view.pushMatrix();view.identity();RenderSystem.applyModelViewMatrix();
		// NO_DEPTH_TEST's native shard is a no-op, so explicitly disable and restore the GUI's depth state.
		RenderSystem.disableDepthTest();RenderSystem.setShaderColor(1F,1F,1F,1F);
		try(var memory=new ByteBufferBuilder(4096)) {
			var buffers=MultiBufferSource.immediate(memory);
			for(var frame:frames) {
				RenderSystem.setProjectionMatrix(frame.projection(),sorting);
				var type=flashType(frame.texture());
				FmumShaderFlash.emit(buffers.getBuffer(type),frame);buffers.endBatch(type);
				FmumMuzzleGlow.emit(buffers.getBuffer(GLOW),frame.glow());buffers.endBatch(GLOW);
			}
		} finally {
			view.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);
			if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
			RenderSystem.setShaderColor(colour[0],colour[1],colour[2],colour[3]);RenderSystem.setShader(()->shader);
		}
	}
}
