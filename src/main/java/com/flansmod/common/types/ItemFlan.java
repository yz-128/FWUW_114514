package com.flansmod.common.types;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

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

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity)
	{
		if(!(type instanceof ToolType tool)) return 0;
		if(com.flansmod.common.Tools.isEmergencyKit(tool)) return 72000;
		return com.flansmod.common.Tools.isConsumable(tool) ? 32 : 0;
	}

	@Override
	public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration)
	{
		if(type instanceof ToolType tool)
			com.flansmod.common.Tools.continuousTick(level, living, stack, tool, remainingUseDuration);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack)
	{
		return type instanceof ToolType tool && com.flansmod.common.Tools.isDrink(tool)
				? UseAnim.DRINK : UseAnim.EAT;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living)
	{
		if(type instanceof ToolType tool)
		{
			if(com.flansmod.common.Tools.isEmergencyKit(tool)) return stack;
			// Food/drink is finished by Tools.serverFoodUse from the held-slot channel; letting
			// vanilla finish as well would eat/consume twice.
			if(com.flansmod.common.Tools.isConsumable(tool)) return stack;
		}
		return super.finishUsingItem(stack, level, living);
	}

	/**
	 * Right-click behaviour for the kinds that have one.
	 *
	 * <p>Guns are driven from the client tick (see {@code GunAnimationDriver}), so only the thrown
	 * and tool definitions need vanilla's use path. Everything else stays a plain item, which is
	 * the same "no behaviour yet" shape the port started with.
	 */
	@Override
	public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
			net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand)
	{
		ItemStack stack = player.getItemInHand(hand);
		net.minecraft.world.InteractionResult result = net.minecraft.world.InteractionResult.PASS;
		if(type instanceof GrenadeType grenade)
			result = com.flansmod.common.Grenades.throwGrenade(level, player, stack, grenade);
		else if(type instanceof ToolType tool && hand == net.minecraft.world.InteractionHand.MAIN_HAND)
		{
			// Food, drink and first-aid kits use vanilla's hold-to-use state so the arm raises and
			// the effect is applied continuously (kits) or at the end (food/drink).
			if(com.flansmod.common.Tools.isEmergencyKit(tool))
			{
				// The held slot is driven by MedkitClientTick/MedkitUsePayload; return CONSUME so
				// the client raises the arm and blocks the click, without the shared Item class
				// entering a lifecycle whose onUseTick may not fire for every content item.
				return net.minecraft.world.InteractionResultHolder.consume(stack);
			}
			if(com.flansmod.common.Tools.isConsumable(tool))
				return net.minecraft.world.InteractionResultHolder.consume(stack);
			result = com.flansmod.common.Tools.use(level, player, stack, tool);
		}
		if(result == net.minecraft.world.InteractionResult.PASS)
			return net.minecraft.world.InteractionResultHolder.pass(stack);
		if(result == net.minecraft.world.InteractionResult.SUCCESS)
			return net.minecraft.world.InteractionResultHolder.success(stack);
		return net.minecraft.world.InteractionResultHolder.consume(stack);
	}

	/**
	 * The gun's movement-speed penalty, as 1.7.10 put on {@code ItemGun}.
	 *
	 * <p>The old item added a {@code MOVEMENT_SPEED} modifier of
	 * {@code gun.getMoveSpeed(stack) - 1}, where the gun's {@code MoveSpeedModifier} was multiplied
	 * by every mounted part's {@code MoveSpeedMultiplier}. Only the held stack's own tree counts,
	 * so a lighter build walks faster than a loaded one.
	 */
	@Override
	public net.minecraft.world.item.component.ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack)
	{
		var base = super.getDefaultAttributeModifiers(stack);
		if(!(type instanceof GunType gun))
			return base;
		float modifier = gun.moveSpeedModifier * com.flansmod.common.GunStats.moveSpeedMultiplier(gun, stack) - 1F;
		if(Math.abs(modifier) < 1e-4F)
			return base;
		var builder = net.minecraft.world.item.component.ItemAttributeModifiers.builder();
		for(var entry : base.modifiers())
			builder.add(entry.attribute(), entry.modifier(), entry.slot());
		builder.add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
				new net.minecraft.world.entity.ai.attributes.AttributeModifier(
						net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
								FlansMod.MODID, "gun_movement_speed"),
						modifier,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND);
		return builder.build();
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

	@Override
	public boolean isBarVisible(ItemStack stack)
	{
		return type instanceof ToolType tool && tool.toolLife > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack)
	{
		if(!(type instanceof ToolType tool) || tool.toolLife <= 0) return 0;
		int remaining = com.flansmod.common.Tools.remainingToolLife(stack, tool);
		return Math.max(0, Math.min(13, Math.round(13F * remaining / tool.toolLife)));
	}

	@Override
	public int getBarColor(ItemStack stack)
	{
		if(!(type instanceof ToolType tool) || tool.toolLife <= 0) return 0xFFFFFF;
		float left = (float)com.flansmod.common.Tools.remainingToolLife(stack, tool) / tool.toolLife;
		return left <= .15F ? 0xFF3030 : left <= .35F ? 0xFFAA30 : 0x55FF55;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags)
	{
		super.appendHoverText(stack, context, lines, flags);
		// 1.7.10's ItemAttachment/ItemGun both opened with the pack name in italics and then the
		// definition's Description, split on '_' into one tooltip line per segment.
		if(type.contentPack != null && !type.contentPack.isBlank())
			lines.add(Component.literal(type.contentPack)
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
		if(type.description != null && !type.description.isBlank())
			for(String line : type.description.split("_"))
				if(!line.isBlank())
					lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
		if(type instanceof ToolType tool && tool.toolLife > 0)
			lines.add(Component.translatable("tool.flansmod.uses",
					com.flansmod.common.Tools.remainingToolLife(stack, tool), tool.toolLife)
					.withStyle(ChatFormatting.GRAY));
		if(type instanceof AttachmentType attachment)
		{
			lines.add(Component.translatable("attachment.flansmod.interface", attachment.attachmentType)
					.withStyle(ChatFormatting.DARK_GRAY));
			// Weapons that actually accept this part. The previous code listed attachment parents,
			// which read as nonsense ("muzzle brake fits onto a dust cover") because a part can sit
			// on another part; the line's label promises weapons, so it lists weapons.
			var guns = com.flansmod.common.GunAttachments.gunsAccepting(attachment);
			if(!guns.isEmpty())
			{
				var names = Component.empty();
				for(int i=0;i<Math.min(3,guns.size());i++)
				{
					if(i>0) names.append(" / ");
					var gun = guns.get(i);
					names.append(gun.item == null ? Component.literal(gun.displayName())
							: gun.item.getName(new ItemStack(gun.item)));
				}
				if(guns.size()>3) names.append(" ").append(Component.translatable("attachment.flansmod.more",guns.size()-3));
				lines.add(Component.translatable("attachment.flansmod.parents",names).withStyle(ChatFormatting.GRAY));
			}
		}
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
