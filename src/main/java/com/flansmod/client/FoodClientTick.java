package com.flansmod.client;

import com.flansmod.common.FlansMod;
import com.flansmod.common.Tools;
import com.flansmod.common.network.FoodUsePayload;
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
 * Held food/drink: keeps the raise animation and reports the slot, so the server can finish the
 * 1.6-second eat even though the shared Item class cannot rely on vanilla's use lifecycle.
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class FoodClientTick
{
	private FoodClientTick() { }

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event)
	{
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null || mc.screen != null || mc.options == null) return;
		if(!mc.options.keyUse.isDown()) return;
		var stack = mc.player.getMainHandItem();
		if(!(InfoType.getType(stack) instanceof ToolType tool) || !Tools.isConsumable(tool)
				|| Tools.isEmergencyKit(tool)) return;
		if(!mc.player.isUsingItem()) mc.player.startUsingItem(InteractionHand.MAIN_HAND);
		if(mc.player.tickCount % 5 == 0)
			PacketDistributor.sendToServer(new FoodUsePayload(mc.player.getInventory().selected));
	}
}
