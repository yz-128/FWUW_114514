package com.flansmod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

import org.lwjgl.opengl.GL11;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The gunsmith preview's mesh layer.
 *
 * <p>An ordinary translucent entity layer writes colour only. Drawn that way, the depth test
 * passes against the GUI's cleared depth buffer, so a face drawn later simply overwrites an
 * earlier one — the receiver's interior shows through its own shell, and which faces win depends
 * on draw order. The preview therefore needs a depth-writing layer, and the GUI depth
 * buffer has to be cleared inside the preview's scissor rectangle first so stale panel depth does
 * not occlude the weapon.
 *
 * <p>The position/UV/colour shader preserves the per-vertex studio colour. The entity emissive
 * shader still calls minecraft_mix_light, so using it would multiply that colour by the GUI's
 * current light directions a second time and darken the model.
 */
public final class GunModificationRenderLayers extends RenderStateShard
{
	private GunModificationRenderLayers() { super("flansmod_gunsmith_mesh", () -> { }, () -> { }); }
	private static final Map<ResourceLocation, RenderType> MESHES = new ConcurrentHashMap<>();

	/**
	 * Depth-writing, two-sided mesh layer for one skin. TMT's native model renderer blends
	 * texture alpha while retaining depth writes; GUI bodies must retain that same behaviour.
	 *
	 * <p>Two-sided because the GUI's negative Y scale reverses the winding; with depth written the
	 * back faces are behind the front ones and cost nothing but a few vertices.
	 */
	public static RenderType mesh(ResourceLocation texture)
	{
		return MESHES.computeIfAbsent(texture, key -> RenderType.create("flansmod_gunsmith_mesh", DefaultVertexFormat.POSITION_TEX_COLOR,
				VertexFormat.Mode.QUADS, 4096, false, false,
				RenderType.CompositeState.builder()
						.setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
						.setTextureState(new TextureStateShard(key, false, false))
						.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
						.setCullState(NO_CULL)
						.setDepthTestState(LEQUAL_DEPTH_TEST)
						.setWriteMaskState(COLOR_DEPTH_WRITE)
						.createCompositeState(false)));
	}

	/**
	 * Runs {@code draw} with depth writes inside the caller's scissor, then restores every state
	 * it changed — including the depth clear value, which the caller may have set for its own
	 * passes.
	 */
	public static void withDepth(Runnable draw)
	{
		boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
		double clearValue = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
		try
		{
			RenderSystem.enableDepthTest();
			RenderSystem.depthMask(true);
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
			RenderSystem.disableCull();
			// Only the scissored preview area is cleared, so the GUI's own depth stays untouched.
			// Clear to far (1.0) regardless of what the caller had configured: a stale depth clear
			// value such as 0.25 would otherwise clip the whole weapon away.
			GL11.glClearDepth(1.0);
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);
			draw.run();
		}
		finally
		{
			// GUI markers are drawn after the model. At high zoom a model vertex can be nearer
			// than their GUI Z, so its private depth must not hide those later controls.
			RenderSystem.depthMask(true);
			GL11.glClearDepth(1.0);
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);
			RenderSystem.depthMask(depthWrite);
			RenderSystem.depthFunc(depthFunc);
			if(depthEnabled)
				RenderSystem.enableDepthTest();
			else
				RenderSystem.disableDepthTest();
			if(cull)
				RenderSystem.enableCull();
			else
				RenderSystem.disableCull();
			GL11.glClearDepth(clearValue);
		}
	}
}
