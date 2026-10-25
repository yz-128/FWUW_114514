package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.FlansMod;
import com.flansmod.common.entity.EntityGrenade;
import com.flansmod.common.types.GrenadeType;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a thrown grenade, ported from 1.7.10's {@code RenderGrenade}.
 *
 * <p>The original bound the definition's skin and drew the pack's model directly in world space at
 * 1/16 scale ({@code RenderGrenade.java:51-53}), letting the entity's rotated axes carry the tumble.
 * This renderer does the same: it draws the baked rig at the geometry's own scale.
 *
 * <p>It deliberately does not go through the item renderer. The port's item models carry no
 * {@code display} block (1.7.10 never consulted one), so {@code ItemDisplayContext.GROUND} is a
 * held/inventory placement: drawn that way a grenade covered more than a block on the ground.
 *
 * <p>A detonated grenade that is still in the world is the smoke cloud, and draws nothing; its
 * particles come from {@code EntityGrenade}'s client tick.
 */
public class GrenadeRenderer extends EntityRenderer<EntityGrenade>
{
	public GrenadeRenderer(EntityRendererProvider.Context context)
	{
		super(context);
		shadowRadius = 0.15F;
		shadowStrength = 0.5F;
	}

	@Override
	public void render(EntityGrenade grenade, float entityYaw, float partialTick, PoseStack pose,
					   MultiBufferSource buffers, int light)
	{
		GrenadeType type = grenade.grenadeType();
		// No definition, no model or a spent cloud: nothing to draw.
		if(type == null || grenade.smoking())
			return;
		BakedRig rig = ModelCache.rigFor(type, type.modelScale);
		if(rig == null || rig.quads.isEmpty())
			return;

		pose.pushPose();
		// The packs author these models at a large base scale (1.7.10's RenderGrenade multiplied
		// the model by 1/16 at draw time). The baked rig here is already in blocks, so scale it to
		// the definition's own hit box instead of trusting the authored size.
		float rigHeight = Math.max(1.0E-4F, rig.maxY - rig.minY);
		float wanted = Math.max(0.08F, Math.min(0.5F, type.hitBoxSize));
		float scale = wanted / rigHeight;
		if(!Float.isFinite(scale) || scale <= 0F) return;
		pose.scale(scale, scale, scale);
		// 1.7.10 spun the entity's axes in flight; the port spins the model by the same rule, and
		// freezes a sticky grenade once it has fixed itself to a surface.
		if(type.spinWhenThrown && !grenade.isStuck())
		{
			float spin = (grenade.tickCount + partialTick) * 12F;
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			pose.mulPose(Axis.XP.rotationDegrees(spin * 0.63F));
		}
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(texture(grenade)));
		RigRenderer.render(pose, buffer, rig, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(EntityGrenade grenade)
	{
		return texture(grenade);
	}

	/** The definition's skin, resolved through the same rule the guns and the baker use. */
	private static ResourceLocation texture(EntityGrenade grenade)
	{
		GrenadeType type = grenade.grenadeType();
		ResourceLocation texture = type == null ? null : Textures.forType(type);
		return texture != null ? texture
				: ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "skins/defaultbullet.png");
	}
}
