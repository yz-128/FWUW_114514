package com.flansmod.client;

import com.flansmod.common.FlansMod;
import com.flansmod.common.Tools;
import com.flansmod.common.network.MedkitUsePayload;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ToolType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Holds a first-aid kit: while the use key is down, keeps the raise animation and reports the held
 * slot to the server every five ticks. The server performs the heal and spends one use.
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class MedkitClientTick
{
	private MedkitClientTick() { }

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event)
	{
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null || mc.screen != null || mc.options == null) return;
		if(!mc.options.keyUse.isDown()) return;
		var stack = mc.player.getMainHandItem();
		if(!(InfoType.getType(stack) instanceof ToolType tool) || !Tools.isEmergencyKit(tool)) return;
		if(mc.player.getHealth() >= mc.player.getMaxHealth()) return;
		if(!mc.player.isUsingItem()) mc.player.startUsingItem(InteractionHand.MAIN_HAND);
		if(mc.player.tickCount % 5 == 0)
			PacketDistributor.sendToServer(new MedkitUsePayload(mc.player.getInventory().selected));
	}
}
