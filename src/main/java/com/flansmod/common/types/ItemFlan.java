package com.flansmod.common.types;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.flansmod.common.FlansMod;

/**
 * The item that represents one content-pack definition.
 *
 * <p>1.7.10 had a subclass per kind ({@code ItemGun}, {@code ItemPart}, {@code ItemVehicle}, …)
 * because each carried its own behaviour. The port starts from one shared class: the defining
 * data is already held by the {@link InfoType}, and behaviour that is genuinely per-kind
 * (firing, driving) lives in components added later, so a class per kind would only add
 * dispatch. Keeping one class also means a pack that uses a key the port has not modelled
 * yet still produces a working, named, textured item instead of vanishing.
 */
public class ItemFlan extends Item
{
	public final InfoType type;

	public ItemFlan(InfoType type, Properties properties)
	{
		super(properties);
		this.type = type;
		type.item = this;
	}

	/** The registry name this item is registered under — the pack's {@code ShortName}. */
	public String registryName()
	{
		return type.shortName;
	}

	/** Iris adds its light-provider interface to Item; these signatures override its defaults
	 * at runtime without making Iris a required dependency, including on dedicated servers. */
	public int getLightEmission(net.minecraft.world.entity.player.Player player, ItemStack stack)
	{
		if(player == null || !player.level().isClientSide || !(type instanceof GunType gun))
			return 0;
		return com.flansmod.client.render.MuzzleLighting.emission(player, stack, gun);
	}

	public org.joml.Vector3f getLightColor(net.minecraft.world.entity.player.Player player, ItemStack stack)
	{
		return new org.joml.Vector3f(1F, .72F, .32F);
	}

	/**
	 * The cube-camera name.
	 *
	 * <p>1.7.10 got this from the content pack's own language files: the item was registered under
	 * its {@code ShortName}, so {@code I18n} resolved {@code item.<ShortName>.name} and showed
	 * English or Chinese depending on the player's language setting. The port keeps that scheme —
	 * {@link #getDescriptionId()} returns the same key and the generated resource pack serves the
	 * packs' entries under it (see {@code PackLang}, {@code GeneratedAssets}) — with the
	 * definition's {@code Name} line as the fallback, so an item can never print a raw key.
	 */
	@Override
	public net.minecraft.network.chat.Component getName(ItemStack stack)
	{
		return net.minecraft.network.chat.Component.translatableWithFallback(
				getDescriptionId(), type.displayName());
	}

	@Override
	public String getDescriptionId()
	{
		return com.flansmod.common.ItemAssets.packTranslationKey(type, registryName());
	}

	/** Ammo, chamber and mode updates must not stack vanilla's equip dip onto FMUM actions. */
	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged)
	{
		if(type instanceof GunType && !slotChanged && ItemStack.isSameItem(oldStack, newStack))
			return false;
		return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
	}

	// ---------------------------------------------------------------- firing
	//
	// 1.7.10 fired the main gun on right-click (and the 1.20.1 port put the trigger on the attack
	// key, which is where modern shooters expect it). The port follows the later convention:
	// left-click fires, and right-click stays free for the world. Nothing about firing goes through
	// vanilla's use() path — the client tick reads the attack key directly and drives GunFiring —
	// which is also what keeps the gun out of the "in use" state and therefore out of vanilla's
	// eat/drink arm pose.

	/**
	 * One tick of the trigger being held.
	 *
	 * <p>Called from the client tick rather than from {@code onUseTick}: the trigger has to be
	 * followed from a tick to keep the gun out of the "in use" state, and left-click has no
	 * vanilla per-tick entry point at all.
	 */
	public void tickHeld(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
						 ItemStack stack, boolean mainHand)
	{
		if(type instanceof com.flansmod.common.types.GunType gun)
			com.flansmod.common.GunFiring.tickHeld(level, player, stack, gun, mainHand);
	}

	/**
	 * Left-click must not mine. A gun has no block-breaking behaviour, and letting the click fall
	 * through to vanilla would carve blocks out of the world with every shot.
	 *
	 * <p>{@code isCorrectToolForDrops} returning false is what makes the click a miss rather than a
	 * swing; the port keeps that instead of the 1.7.10 route of cancelling the block event, because
	 * it is the narrower answer — it stops the swing without claiming the attack button globally.
	 */
	@Override
	public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,
								  net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
								  net.minecraft.world.entity.player.Player player)
	{
		return !(type instanceof com.flansmod.common.types.GunType);
	}

	@Override
	public boolean isCorrectToolForDrops(ItemStack stack, net.minecraft.world.level.block.state.BlockState state)
	{
		return !(type instanceof com.flansmod.common.types.GunType);
	}
}
