package com.flansmod.client.render;

import com.flansmod.client.AimState;
import com.flansmod.common.FlansMod;
import com.flansmod.common.GunAttachments;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.mojang.blaze3d.platform.TextureUtil;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/**
 * A copy of the finished world frame, sampled back onto the ocular at the same screen position.
 *
 * <p>The world pass is rendered at the optic's magnified FOV (see {@link ScopeMagnification}), so
 * the ocular must simply show the pixels that are already behind it. Sampling them 1:1 costs nothing
 * in sharpness, and it also covers whatever the sight model draws across the aperture — EFT's sight
 * meshes carry a plate at the ocular that is opaque, so letting the world show through unfiltered
 * would leave the player staring at that plate.
 *
 * <p>This is not the old crop-and-stretch: a vertex's texture coordinate is its own screen position,
 * not a linearly magnified offset from the centre.
 */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class ScopeWorldTexture extends RenderStateShard {
	public static final ResourceLocation IMAGE=ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"dynamic/scope_world");
	private static final WorldCopy TEXTURE=new WorldCopy();
	private static boolean registered,ready,failed;
	public static final RenderType LENS=RenderType.create("flansmod_scope_world",DefaultVertexFormat.POSITION_TEX_COLOR,
			VertexFormat.Mode.QUADS,4096,false,false,RenderType.CompositeState.builder()
					.setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
					.setTextureState(new TextureStateShard(IMAGE,false,false)).setCullState(NO_CULL)
					.setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
	private ScopeWorldTexture() { super("flansmod_scope_world",()->{},()->{}); }
	public static boolean available() {return ready && !failed;}
	@SubscribeEvent public static void begin(RenderFrameEvent.Pre event) {ready=false;}
	@SubscribeEvent(priority=EventPriority.HIGHEST)
	public static void beforeHand(RenderHandEvent event) {
		if(ready || failed || event.isCanceled())return;
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
		if(!registered) {mc.getTextureManager().register(IMAGE,TEXTURE);registered=true;}
		try {copyColor(width,height);}
		catch(RuntimeException error) {
			failed=true;FlansMod.LOGGER.warn("Scope colour copy unavailable; the ocular will stay black",error);
		}
	}
	/** Copies the currently bound read framebuffer, preserving both active unit and texture binding. */
	public static void copyColor(int width,int height) {
		if(width<1 || height<1)throw new IllegalArgumentException("Empty scope image");
		int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		RenderSystem.activeTexture(GL13.GL_TEXTURE0);
		int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		try {
			TEXTURE.prepare(width,height);
			// RenderHandEvent precedes main-hand vertices; the current read framebuffer holds the world.
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,width,height);
			ready=true;
		} finally {RenderSystem.bindTexture(bound);RenderSystem.activeTexture(active);}
	}
	private static final class WorldCopy extends AbstractTexture {
		int width,height;
		void prepare(int w,int h) {
			if(w!=width || h!=height) {TextureUtil.prepareImage(getId(),w,h);width=w;height=h;}
			RenderSystem.bindTexture(getId());
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
		}
		@Override public void load(ResourceManager manager) {releaseId();width=height=0;ready=false;failed=false;}
	}
}
