package com.flansmod.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import com.flansmod.common.types.ToolType;

/**
 * Tool behaviour, ported from 1.7.10's {@code ItemTool}.
 *
 * <p>1.7.10's tools were {@code ItemFood} subclasses that did four different jobs in one
 * {@code onItemRightClick}: eat, open a parachute, detonate remote explosives, or repair what the
 * player was looking at. The port has no driveables, parachutes or remote explosives yet, so this
 * class covers the part the current content can actually use — healing players, eating, and tool
 * wear — and leaves the rest to be added with those systems.
 *
 * <p>The old tool spent its vanilla damage value as its life counter. That cannot come back
 * unchanged: the port's shared {@code ItemFlan} item is registered once per pack definition, so it
 * can only have one durability value for every kind of content, and a vintage pack's
 * {@code ToolLife} has to work without a per-definition item class. The remaining life is
 * therefore carried in the stack's {@code custom_data} instead, which is the modern place for
 * per-stack state and needs no item subclass.
 */
public final class Tools
{
	/** The custom-data key holding a stack's remaining tool life. */
	public static final String TOOL_LIFE_KEY = "flansmod:tool_life";

	private Tools() { }

	/**
	 * One right-click of a tool.
	 *
	 * <p>Side handling follows the usual split for an effect that is an inventory/health change:
	 * the client answers {@code SUCCESS} so the click is acknowledged, and the server answers
	 * {@code CONSUME} after doing the work. The client is never allowed to change health, hunger or
	 * tool life, or a desync would hand out free repairs.
	 *
	 * @param level  the level the click happened in
	 * @param player the player using the tool
	 * @param stack  the held tool stack
	 * @param type   the definition behind the stack
	 * @return {@code PASS} when this kind of tool has no ported behaviour yet
	 */
	public static InteractionResult use(Level level, Player player, ItemStack stack, ToolType type)
	{
		if(type == null || stack.isEmpty())
			return InteractionResult.PASS;

		// Only these two behaviours exist in the port so far; a key/parachute/remote tool is left
		// as a plain item instead of pretending to work and silently losing durability.
		boolean hasEffect = type.healPlayers || type.foodness > 0;
		if(!hasEffect)
			return InteractionResult.PASS;

		if(level.isClientSide)
			return InteractionResult.SUCCESS;

		if(type.healPlayers)
			player.heal(healFor(type));

		// Port-owned medical states. Painkillers suppress the fracture slowdown, splints cure the
		// fracture itself; both still apply the authored HealAmount and consume one use.
		if(isPainkiller(type))
		{
			com.flansmod.common.Fracture.painkiller(player,painkillerTicks(type));
			// The packs' rounds apply Slowness on hit; the dose clears an existing one as well.
			player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
		}
		if(isSplint(type))
			com.flansmod.common.Fracture.cure(player);

		// Foodness is the original's ItemFood nutrition value. The old item ate through vanilla's
		// eating state machine; without an ItemFood subclass the port applies the food directly,
		// which feeds the player without an animation. A food pack item has no ToolLife, so the
		// normal tool-life path would never consume it; vanilla's one-item-per-eat rule is applied
		// here instead (creative does not consume, matching the tool path).
		if(type.foodness > 0)
		{
			player.getFoodData().eat(type.foodness, 1F);
			if(type.toolLife <= 0 && !player.getAbilities().instabuild)
			{
				stack.shrink(1);
				return InteractionResult.CONSUME;
			}
		}

		spendUse(player, stack, type);
		return InteractionResult.CONSUME;
	}

	// ---------------------------------------------------------------- pure tool-life rules
	//
	// These are split out of the stack/NBT handling so the wear rules can be checked without a
	// level, a player or an item stack.

	/**
	 * The tool-life value a stack carries after one use.
	 *
	 * <p>{@code remaining} is what the stack holds now; a full tool is {@code type.toolLife} and a
	 * use costs one point. Reaching zero destroys the tool when {@code destroyOnEmpty} is set;
	 * otherwise the tool starts again from {@code type.toolLife} — the port's version of the
	 * original's tool that wore down but was never removed.
	 *
	 * <p>A tool with no life ({@code toolLife <= 0}) is unlimited and keeps whatever value it had.
	 */
	public static int nextToolLife(ToolType type, int remaining)
	{
		if(type == null || type.toolLife <= 0)
			return remaining;

		int life = Math.max(0, remaining - 1);
		if(life > 0)
			return life;
		return type.destroyOnEmpty ? 0 : type.toolLife;
	}

	/** Whether a stack with this much life left has to be destroyed (or reset) after the use. */
	public static boolean shouldDestroy(ToolType type, int remaining)
	{
		return type != null && type.toolLife > 0 && type.destroyOnEmpty && remaining <= 0;
	}

	/** How much health one use of this tool restores; negative pack values are treated as zero. */
	public static float healFor(ToolType type)
	{
		return type == null ? 0F : Math.max(0F, type.healAmount);
	}

	/** Food packs eat with EAT; the EFT water bottle is a drink with the same hold-to-use timer. */
	public static boolean isDrink(ToolType type)
	{
		if(type == null || type.shortName == null || type.foodness > 0) return false;
		String name = type.shortName.toLowerCase(java.util.Locale.ROOT);
		return type.healPlayers && (name.contains("water") || name.contains("drink")
				|| name.contains("juice") || name.contains("soda") || name.contains("cola"));
	}

	/** Items that use vanilla's hold-to-consume animation instead of an instant right-click. */
	public static boolean isConsumable(ToolType type)
	{
		return type != null && (type.foodness > 0 || isDrink(type));
	}

	/** Applies a finished eat/drink on the server and spends the item, mirroring ItemFood. */
	public static net.minecraft.world.item.ItemStack finishConsumable(Level level, net.minecraft.world.entity.LivingEntity living,
			ItemStack stack, ToolType type)
	{
		if(level.isClientSide) return stack;
		if(type.healPlayers) living.heal(healFor(type));
		if(type.foodness > 0 && living instanceof Player player)
			player.getFoodData().eat(type.foodness, 1F);
		if(type.toolLife > 0 && living instanceof Player player)
			spendUse(player, stack, type);
		else if(!(living instanceof Player player) || !player.getAbilities().instabuild)
			stack.shrink(1);
		return stack;
	}

	// ---------------------------------------------------------------- stack wiring

	/**
	 * The tool life left in {@code stack}.
	 *
	 * <p>A stack that has never been used carries no custom data, which means a full tool; that
	 * keeps stacks created by the content generator, by crafting or by the creative menu working
	 * without an initialisation pass. Unlimited tools report zero: there is no life to track.
	 */
	public static int remainingToolLife(ItemStack stack, ToolType type)
	{
		if(type == null || type.toolLife <= 0)
			return 0;

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if(data != null)
		{
			CompoundTag tag = data.copyTag();
			if(tag.contains(TOOL_LIFE_KEY))
				return Math.max(0, tag.getInt(TOOL_LIFE_KEY));
		}
		return type.toolLife;
	}

	/** Names that the packs use for the fracture-related medical tools. */
	public static boolean isPainkiller(ToolType type)
	{
		String name=name(type);
		return name.contains("painkiller") || name.contains("morphine");
	}

	/**
	 * Suppression time per dose. The EFT tool files carry no duration field, so these are the
	 * port's balance values: a single pill, a pack of mild painkillers, and morphine.
	 */
	public static int painkillerTicks(ToolType type)
	{
		String name=name(type);
		if(name.contains("morphine")) return 20*60*10;
		if(name.contains("painkillers")) return 20*60*3;
		return 20*60*5;
	}

	public static boolean isSplint(ToolType type)
	{
		return name(type).contains("splint");
	}

	/** First-aid kits that are held down and heal continuously until full health. */
	public static boolean isEmergencyKit(ToolType type)
	{
		String name=name(type);
		return name.contains("firstaidkit") || name.contains("ai2") || name.contains("salewa")
				|| name.contains("ifak") || name.contains("grizzly") || name.contains("medkit")
				|| name.equals("tool_car");
	}

	private static String name(ToolType type)
	{
		return type == null || type.shortName == null ? "" : type.shortName.toLowerCase(java.util.Locale.ROOT);
	}

	/** Kept for callers that want one explicit use; the held-kit path is server tick driven. */
	public static void startEmergencyKit(Level level, Player player, ItemStack stack, ToolType type)
	{
		if(level.isClientSide || !isEmergencyKit(type)) return;
		if(player.getHealth() >= player.getMaxHealth() || remainingToolLife(stack,type) <= 0) return;
		player.heal(healFor(type));
		consumeUse(player,stack,type);
	}

	/**
	 * Server side of the held-kit channel. The client reports the held slot every five ticks while
	 * the use key is down; this heals once per report, so it does not depend on the shared
	 * {@code Item} class entering vanilla's use lifecycle.
	 */
	public static void serverMedkitUse(net.minecraft.server.level.ServerPlayer player, int slot)
	{
		if(slot != player.getInventory().selected) return;
		ItemStack stack = player.getMainHandItem();
		if(!(com.flansmod.common.types.InfoType.getType(stack) instanceof ToolType type) || !isEmergencyKit(type))
			return;
		long now = player.level().getGameTime();
		long last = player.getPersistentData().getLong("flansmodMedkitTick");
		if(now - last < 4) return;
		player.getPersistentData().putLong("flansmodMedkitTick", now);
		if(player.getHealth() >= player.getMaxHealth() || remainingToolLife(stack,type) <= 0) return;
		player.heal(healFor(type));
		consumeUse(player,stack,type);
	}

	/** Server side of held food/drink; the eat completes after 32 reported ticks (1.6 seconds). */
	public static void serverFoodUse(net.minecraft.server.level.ServerPlayer player, int slot)
	{
		if(slot != player.getInventory().selected) return;
		ItemStack stack = player.getMainHandItem();
		if(!(com.flansmod.common.types.InfoType.getType(stack) instanceof ToolType type)
				|| !isConsumable(type) || isEmergencyKit(type)) return;
		long now = player.level().getGameTime();
		var tag = player.getPersistentData();
		long last = tag.getLong("flansmodFoodPacket");
		if(now - last > 5) tag.putLong("flansmodFoodStart", now);
		tag.putLong("flansmodFoodPacket", now);
		long start = tag.getLong("flansmodFoodStart");
		if(start <= 0) { tag.putLong("flansmodFoodStart", now); start = now; }
		if(now - start < 32) return;
		tag.remove("flansmodFoodStart");
		finishConsumable(player.level(), player, stack, type);
		player.stopUsingItem();
	}

	/** One tick of a held first-aid kit: heal four times a second until full or uses run out. */
	public static void continuousTick(Level level, net.minecraft.world.entity.LivingEntity living,
			ItemStack stack, ToolType type, int remainingUseDuration)
	{
		// Held kits are driven by MedkitUsePayload; vanilla's onUseTick must not heal a second time.
	}

	/** Spends one use of the tool, shrinking the stack when the last use destroys it. */
	public static void consumeUse(Player player, ItemStack stack, ToolType type)
	{
		spendUse(player,stack,type);
	}

	private static void spendUse(Player player, ItemStack stack, ToolType type)
	{
		if(type.toolLife <= 0)
			return;
		// 1.7.10 did not wear a tool in creative mode.
		if(player.getAbilities().instabuild)
			return;

		int remaining = nextToolLife(type, remainingToolLife(stack, type));
		if(shouldDestroy(type, remaining))
		{
			stack.shrink(1);
			return;
		}

		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TOOL_LIFE_KEY, remaining));
	}
}
