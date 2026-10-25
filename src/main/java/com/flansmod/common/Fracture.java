package com.flansmod.common;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A small port-owned fracture state: a fall injury slows movement until a splint cures it, while a
 * painkiller temporarily suppresses the slowdown without curing the fracture.
 *
 * <p>1.7.10 had no fracture system; the EFT medical tools only carry {@code Heal} data. This state
 * exists so the packs' painkiller and splint items have something meaningful to act on. It is
 * stored on the player's persistent data, so it survives relogging, and the slowdown is a real
 * attribute modifier that the server adds/removes rather than a hidden vanilla potion.
 */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class Fracture
{
	private static final String UNTIL_KEY = "flansmodFractureUntil";
	private static final String PAINKILLER_KEY = "flansmodPainkillerUntil";
	private static final ResourceLocation SLOW_ID =
			ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "fracture_slow");
	/** 40% slower while fractured. */
	private static final double SLOW_AMOUNT = -0.40D;

	private Fracture() { }

	public static boolean fractured(Player player)
	{
		return player.getPersistentData().getLong(UNTIL_KEY) > player.level().getGameTime();
	}

	public static boolean painkiller(Player player)
	{
		return player.getPersistentData().getLong(PAINKILLER_KEY) > player.level().getGameTime();
	}

	public static void fracture(Player player, int ticks)
	{
		if(!MechanicsSettings.fractureEnabled()) return;
		player.getPersistentData().putLong(UNTIL_KEY, player.level().getGameTime() + Math.max(1, ticks));
		update(player);
	}

	public static void cure(Player player)
	{
		player.getPersistentData().remove(UNTIL_KEY);
		update(player);
	}

	/** Suppresses the slowdown for the duration; the fracture itself remains. */
	public static void painkiller(Player player, int ticks)
	{
		player.getPersistentData().putLong(PAINKILLER_KEY, player.level().getGameTime() + Math.max(1, ticks));
		update(player);
	}

	/** Adds or removes the movement modifier so the slowdown follows fracture+painkiller state. */
	public static void update(Player player)
	{
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if(speed == null) return;
		boolean slow = MechanicsSettings.fractureEnabled() && fractured(player) && !painkiller(player);
		boolean present = speed.getModifier(SLOW_ID) != null;
		if(slow && !present)
			speed.addTransientModifier(new AttributeModifier(SLOW_ID, SLOW_AMOUNT,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		else if(!slow && present)
			speed.removeModifier(SLOW_ID);
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event)
	{
		if(event.getEntity().level().isClientSide) return;
		update(event.getEntity());
	}

	/** Falls are the only port-owned fracture source; bullet limbs do not exist in this port. */
	@SubscribeEvent
	public static void onFall(LivingIncomingDamageEvent event)
	{
		if(!(event.getEntity() instanceof ServerPlayer player)) return;
		if(!MechanicsSettings.fractureEnabled() || !"fall".equals(event.getSource().getMsgId())) return;
		if(event.getAmount() < 5F || fractured(player)) return;
		fracture(player,20*60*2);
		player.displayClientMessage(Component.translatable("message.flansmod.fracture"),false);
	}
}
