package com.flansmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.flansmod.common.entity.EntityGunItem;

/**
 * Draws a dropped gun, ported from 1.7.10's {@code RenderGunItem}.
 *
 * <p>The original rendered the pack's model straight out of the {@code EntityGunItem}'s item
 * stack. The port asks the ordinary item renderer for that stack in the {@code GROUND} display
 * context instead: that path already knows how to draw a content-pack gun (its 3D rig or, when the
 * pack ships no class for it, its flat icon), so this renderer only has to place the stack and
 * animate it.
 *
 * <p>Standing in for the original's slow spin of dropped items: the model is drawn flat by the
 * ground transform, so without a turn a rifle reads as a bar seen edge-on from most angles. The
 * turn is driven by {@code tickCount} rather than {@code age} so it also advances on the client,
 * where the age is not simulated.
 */
public class GunItemRenderer extends EntityRenderer<EntityGunItem>
{
	/** Degrees of turn per tick; one revolution every twelve seconds, slow enough to read. */
	private static final float SPIN_DEGREES_PER_TICK = 1.5F;

	public GunItemRenderer(EntityRendererProvider.Context context)
	{
		super(context);
		shadowRadius = 0.15F;
		shadowStrength = 0.75F;
	}

	@Override
	public void render(EntityGunItem entity, float entityYaw, float partialTick, PoseStack pose,
					   MultiBufferSource buffers, int light)
	{
		ItemStack stack = entity.getItemStack();
		if(!stack.isEmpty())
		{
			pose.pushPose();
			// The entity's origin sits at its feet; lift the gun so it lies inside the hitbox
			// instead of being half swallowed by the block below it.
			pose.translate(0.0F, 0.25F, 0.0F);
			pose.mulPose(Axis.YP.rotationDegrees(
					(entity.tickCount + partialTick) * SPIN_DEGREES_PER_TICK));
			Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
					light, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
			pose.popPose();
		}

		super.render(entity, entityYaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityGunItem entity)
	{
		// The item renderer resolves the real texture from the stack's model, so this only has to
		// be a valid atlas for the entity renderer's own bookkeeping.
		return InventoryMenu.BLOCK_ATLAS;
	}
}
