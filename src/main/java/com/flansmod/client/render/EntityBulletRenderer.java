package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.FlansMod;
import com.flansmod.common.entity.EntityBullet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a fired round, ported from 1.7.10's {@code RenderBullet}.
 *
 * <p>The original bound the definition's skin, turned the entity by its yaw and then by
 * {@code 90 - pitch} — one axis more than the entity's own rotation needs, because a
 * {@code ModelBullet} points its box along +Y rather than along the direction of travel — and drew
 * the model at 1/16 scale. That is exactly the chain below; the 1.20.1 build needs the same
 * correction for the same reason.
 *
 * <p>One thing is deliberately different: 1.7.10 skipped drawing the shooter's own round for its
 * first tick, so a shot did not flash in the middle of the screen. That test cannot be repeated
 * here — the renderer runs on the client and the client no longer knows which entity fired at the
 * moment of the first frame — so the round is simply drawn from the start, which is what the
 * modern client does for every other projectile.
 */
public class EntityBulletRenderer extends EntityRenderer<EntityBullet>
{
	/** Round ids already reported, so the diagnostic above fires once per round rather than per frame. */
	private static final java.util.Set<Integer> DIAGNOSED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public EntityBulletRenderer(EntityRendererProvider.Context context)
	{
		super(context);
		shadowRadius = 0F;
		shadowStrength = 0F;
	}

	@Override
	public void render(EntityBullet bullet, float entityYaw, float partialTick, PoseStack pose,
					   MultiBufferSource buffers, int light)
	{
		BakedRig rig = rigOf(bullet);
		// One report per round, so a debug run says whether the client got the entity, resolved
		// its definition and found geometry for it — the three ways this renderer can silently
		// draw nothing.
		if(com.flansmod.common.FlansMod.LOGGER.isDebugEnabled() && DIAGNOSED.add(bullet.getId()))
			com.flansmod.common.FlansMod.LOGGER.info("[BulletRender] id=" + bullet.getId()
					+ " type=" + (bullet.bulletType() == null ? "null"
							: bullet.bulletType().shortName)
					+ " rig=" + (rig == null ? "null" : rig.quads.size() + " quads")
					+ " light=" + light + " pos=" + bullet.position() + " yRot=" + bullet.getYRot()
					+ " xRot=" + bullet.getXRot() + " texture=" + texture(bullet));
		if(rig == null || rig.quads.isEmpty())
			return;

		// The original read the definition's HasLight flag and drew the round full-bright when it
		// was set, so tracers stay visible at night.
		if(bullet.hasLight())
			light = 0xF000F0;

		// Interpolated angles, which is what the old renderer was handed as (f, f1).
		float yaw = Mth.rotLerp(partialTick, bullet.yRotO, bullet.getYRot());
		float pitch = Mth.rotLerp(partialTick, bullet.xRotO, bullet.getXRot());

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(90F - pitch));

		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(texture(bullet)));
		RigRenderer.render(pose, buffer, rig, light, 0xF000F0, 1F, 1F, 1F, 1F);

		pose.popPose();
	}

	/** The definition's model, or the stock bullet when it does not name one. */
	private static BakedRig rigOf(EntityBullet bullet)
	{
		com.flansmod.common.types.BulletType type = bullet.bulletType();
		if(type == null)
			return null;
		return ModelCache.rigFor(type, type.modelScale);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityBullet bullet)
	{
		return texture(bullet);
	}

	/** The definition's skin, resolved through the same rule the guns and the baker use. */
	private static ResourceLocation texture(EntityBullet bullet)
	{
		ResourceLocation resolved = Textures.forBullet(bullet.bulletType());
		return resolved != null ? resolved
				: ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "skins/defaultbullet.png");
	}
}
