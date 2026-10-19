package com.flansmod.common.types;

import java.util.EnumMap;
import java.util.List;

import com.flansmod.common.FlansMod;
import com.flansmod.common.ItemAssets;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;

/** A content-pack armour definition that participates in vanilla equipment handling. */
public final class ItemFlanArmour extends ArmorItem
{
	public final ArmourType armour;

	public ItemFlanArmour(ArmourType armour, Properties properties)
	{
		super(materialFor(armour), slotType(armour.type), propertiesFor(armour, properties));
		this.armour = armour;
		armour.item = this;
	}

	private static Properties propertiesFor(ArmourType armour, Properties properties)
	{
		properties.stacksTo(1);
		// Cosmetic equipment without a Durability line stays unbreakable, as in the pack.
		return armour.durability > 0 ? properties.durability(armour.durability) : properties;
	}

	private static ArmorItem.Type slotType(int slot)
	{
		return switch(slot)
		{
			case 0 -> ArmorItem.Type.HELMET;
			case 2 -> ArmorItem.Type.LEGGINGS;
			case 3 -> ArmorItem.Type.BOOTS;
			default -> ArmorItem.Type.CHESTPLATE;
		};
	}

	/** The generated resource pack aliases the original armour skins to this material layer. */
	public static ResourceLocation layerId(ArmourType armour)
	{
		return ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,
				"legacy/" + ItemAssets.registryPathOf(armour));
	}

	private static Holder<ArmorMaterial> materialFor(ArmourType armour)
	{
		var defense = new EnumMap<ArmorItem.Type, Integer>(ArmorItem.Type.class);
		for(var slot : ArmorItem.Type.values())
			defense.put(slot, 0);
		// Defence is a fraction in the original packs, not a vanilla armour-point count.
		// ArmourProtection adds it during the armour reduction stage instead.
		return Holder.direct(new ArmorMaterial(defense, 0, SoundEvents.ARMOR_EQUIP_LEATHER,
				() -> Ingredient.EMPTY, List.of(new ArmorMaterial.Layer(layerId(armour))), 0F, 0F));
	}

	@Override
	public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack)
	{
		var base = super.getDefaultAttributeModifiers(stack);
		var modifiers = ItemAttributeModifiers.builder();
		for(var entry : base.modifiers())
			modifiers.add(entry.attribute(), entry.modifier(), entry.slot());
		EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(getEquipmentSlot());
		String suffix = getEquipmentSlot().getName();
		if(Float.isFinite(armour.moveSpeedModifier) && Math.abs(armour.moveSpeedModifier - 1F) > 1E-6F)
			modifiers.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(
					ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "armour_movement_" + suffix),
					Math.max(0F, armour.moveSpeedModifier) - 1D,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot);
		if(Float.isFinite(armour.knockbackModifier) && armour.knockbackModifier != 0F)
			modifiers.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(
					ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "armour_knockback_" + suffix),
					armour.knockbackModifier, AttributeModifier.Operation.ADD_VALUE), slot);
		return modifiers.build();
	}

	@Override
	public Component getName(ItemStack stack)
	{
		return Component.translatableWithFallback(getDescriptionId(), armour.displayName());
	}

	@Override
	public String getDescriptionId()
	{
		return ItemAssets.packTranslationKey(armour, ItemAssets.registryPathOf(armour));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> lines,
			net.minecraft.world.item.TooltipFlag flags)
	{
		super.appendHoverText(stack, context, lines, flags);
		lines.add(Component.translatable("item.modifiers." + getEquipmentSlot().getName())
				.withStyle(net.minecraft.ChatFormatting.GRAY));
		if(armour.defence > 0D)
			lines.add(Component.translatable("armour.flansmod.defence",
					String.format(java.util.Locale.ROOT,"%.0f",armour.defence * 100D))
					.withStyle(net.minecraft.ChatFormatting.BLUE));
		if(stack.isDamageableItem())
			lines.add(Component.translatable("armour.flansmod.durability",stack.getMaxDamage()-stack.getDamageValue(),stack.getMaxDamage())
					.withStyle(net.minecraft.ChatFormatting.GRAY));
		lines.add(Component.translatable("armour.flansmod.equip").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
	}
}
