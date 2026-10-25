package com.flansmod.common;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

import com.flansmod.common.entity.EntityGunItem;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.ItemFlan;

/**
 * Replaces a thrown gun with the mod's own dropped-gun entity.
 *
 * <p>1.7.10's {@code ItemGun} returned an {@code EntityGunItem} from its drop path, so a gun on
 * the ground was never a plain {@code EntityItem}: it carried the gun plus its magazine and could
 * be swapped with a held gun by right-clicking it. On modern NeoForge the toss is a single event
 * fired for every dropped stack, so the replacement happens here instead: vanilla's item entity is
 * cancelled before it enters the world and {@link EntityGunItem} is spawned in its place.
 *
 * <p>Only guns are replaced. Ammo, attachments, armour and the other definitions still drop as
 * ordinary items, which is what the original did for everything except guns.
 */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class DroppedGuns
{
	private DroppedGuns() { }

	@SubscribeEvent
	public static void onItemToss(ItemTossEvent event)
	{
		ItemEntity tossed = event.getEntity();
		ItemStack stack = tossed.getItem();
		if(!(stack.getItem() instanceof ItemFlan flan) || !(flan.type instanceof GunType))
			return;

		// Cancel first: the vanilla item entity is created but not yet added to the level, and
		// leaving it in would give the player two copies of the gun.
		event.setCanceled(true);

		// The event also fires for the client's own prediction of an inventory drag-and-drop.
		// Entity creation is server work; cancelling is all the client has to do.
		if(tossed.level().isClientSide)
			return;

		Level level = event.getPlayer().level();
		EntityGunItem dropped = new EntityGunItem(level, tossed.getX(), tossed.getY(), tossed.getZ(),
				stack.copy());
		// Take over the velocity the vanilla item entity had been given, so the gun follows the
		// same throw arc out of the player's hand.
		dropped.setDeltaMovement(tossed.getDeltaMovement());
		level.addFreshEntity(dropped);
	}
}
