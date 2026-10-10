package com.flansmod.client;

import com.flansmod.common.FlansMod;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.ItemFlan;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Stops the left mouse button from also being a vanilla attack while a gun is held.
 *
 * <p>1.7.10 could ignore this: it fired on right-click, so left-click kept its ordinary meaning.
 * The port puts the trigger on left-click, which is what the 1.20.1 build does and what a modern
 * player expects — but left-click is also vanilla's attack, so without this the player swings their
 * arm and starts mining under every shot, and the arm animation fights the gun's own recoil.
 *
 * <p>The fix is the same one the 1.20.1 build makes, and it needs both halves: the event is
 * cancelled to stop the mining and the swipe sound, and the swing flag is cleared to stop the arm
 * punching forward. Cancelling alone still leaves vanilla queueing the swing, which reads on screen
 * as the hand lurching in time with the shots.
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class GunInputSuppressor
{
	private GunInputSuppressor() { }

	@SubscribeEvent
	public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event)
	{
		if(!event.isAttack())
			return;

		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null)
			return;

		ItemStack held = mc.player.getItemInHand(event.getHand());
		if(!(held.getItem() instanceof ItemFlan flan) || !(flan.type instanceof GunType gun))
			return;

		// A deployable gun's click places it in the world rather than firing it, so that click has
		// to reach the block underneath.
		if(gun.deployable)
			return;

		event.setCanceled(true);
		event.setSwingHand(false);
	}
}
