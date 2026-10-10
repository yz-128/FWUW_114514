package com.flansmod.client.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** World transparent surface path. SEUS's entity fallback writes a solid G-buffer even for
 * entityTranslucent; the terrain translucent shader preserves smoke's authored texture alpha. */
public final class ShaderSmokeRenderer extends RenderStateShard {
	private ShaderSmokeRenderer() { super("flansmod_shader_smoke",()->{},()->{}); }
	private static final Map<ResourceLocation,RenderType> TYPES=new ConcurrentHashMap<>();
	public static RenderType type(ResourceLocation texture) {
		return TYPES.computeIfAbsent(texture,key->RenderType.create("flansmod_shader_smoke",
				DefaultVertexFormat.BLOCK,VertexFormat.Mode.QUADS,4096,false,true,
				RenderType.CompositeState.builder().setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
						.setTextureState(new TextureStateShard(key,true,false))
						.setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
						.setLightmapState(LIGHTMAP).setDepthTestState(LEQUAL_DEPTH_TEST)
						// Transparent G-buffer composition uses the surface depth to reconstruct it.
						.setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false)));
	}
	public static boolean inWorld(boolean handPass,boolean shaders) { return !handPass || shaders; }
}
