package com.flansmod.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Additive colour, normal occlusion, and no depth write from the transparent halo. */
public final class MuzzleGlowRenderer extends RenderStateShard {
	private MuzzleGlowRenderer() { super("flansmod_muzzle_glow", () -> { }, () -> { }); }
	public static final RenderType TYPE = RenderType.create("flansmod_muzzle_glow",
			DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
			RenderType.CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
					.setTransparencyState(LIGHTNING_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
					.setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
}
