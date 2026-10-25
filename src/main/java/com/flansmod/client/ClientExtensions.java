package com.flansmod.client;

import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import com.flansmod.client.render.TurborigItemRenderer;
import com.flansmod.common.FlansMod;
import com.flansmod.common.ContentRegistry;

/**
 * Gives every content-pack item its 3D renderer.
 *
 * <p>This is the modern replacement for 1.7.10's {@code IItemRenderer}. The old mod registered a
 * renderer per item class up front; 1.21 instead asks each item for a set of client extensions,
 * and the answer is registered once per item here. {@code Item#initializeClient} still exists but
 * is deprecated for removal, so the port uses the event.
 *
 * <p>The registration is deliberately uniform: every item gets the same renderer, which decides
 * per stack whether the pack actually ships a model for it and falls back to the flat icon when
 * it does not. That keeps the decision in one place instead of duplicating "does this definition
 * have geometry" across the registration path.
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class ClientExtensions
{
	private ClientExtensions() { }

	@SubscribeEvent
	public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event)
	{
		int registered = 0;
		for(Item item : ContentRegistry.registeredItems())
		{
			event.registerItem(new IClientItemExtensions()
			{
				@Override
				public net.minecraft.client.model.Model getGenericArmorModel(
						net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack,
						net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<?> original)
				{
					if(com.flansmod.common.types.InfoType.getType(stack) instanceof com.flansmod.common.types.ArmourType armour)
						return com.flansmod.client.render.LegacyArmourModel.forItem(armour, original);
					return original;
				}

				@Override
				public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(
						net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand,
						net.minecraft.world.item.ItemStack stack)
				{
					return LegacyGunArmPose.forItem(entity, hand, stack);
				}

				@Override
				public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer()
				{
					return TurborigItemRenderer.get();
				}
			}, item);
			registered++;
		}
		FlansMod.log("Registered the model renderer for " + registered + " content-pack item(s)");
	}

	/**
	 * Registers the flat icons that 3D items show in the inventory.
	 *
	 * <p>An item has exactly one {@code #inventory} model, and for anything with a rig that model
	 * is {@code builtin/entity} — which draws nothing on its own. The generated assets therefore
	 * keep a second, ordinary icon model beside it, and this is where it becomes loadable: a
	 * model the game did not discover through the item registry has to be registered by hand with
	 * the standalone variant before {@code ModelManager.getModel} will return it.
	 */
	@SubscribeEvent
	public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event)
	{
		for(net.minecraft.resources.ResourceLocation iconId : com.flansmod.common.GeneratedAssets.iconModelIds())
			event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(iconId));
	}

	/**
	 * Gives the fired-round entity its renderer.
	 *
	 * <p>1.7.10 registered {@code RenderBullet} while the mod was loading; a modern client
	 * registers entity renderers on {@code EntityRenderersEvent.RegisterRenderers}, which is
	 * dispatched once the entity types themselves exist.
	 */
	@SubscribeEvent
	public static void onRegisterRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event)
	{
		event.registerEntityRenderer(com.flansmod.common.FlansMod.BULLET.get(),
				com.flansmod.client.render.EntityBulletRenderer::new);
		event.registerEntityRenderer(com.flansmod.common.FlansMod.GRENADE.get(),
				com.flansmod.client.render.GrenadeRenderer::new);
		event.registerEntityRenderer(com.flansmod.common.FlansMod.GUN_ITEM.get(),
				com.flansmod.client.render.GunItemRenderer::new);
	}
}
