package com.flansmod.client;

import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Visual equivalent of FMUM's third-person setItemInUse(stack, 100), without using an item. */
public final class LegacyGunArmPose
{
	private LegacyGunArmPose() { }

	public static HumanoidModel.ArmPose forItem(LivingEntity entity, InteractionHand hand, ItemStack stack)
	{
		if(hand != InteractionHand.MAIN_HAND || !(InfoType.getType(stack) instanceof GunType gun)
				|| gun.itsType == GunType.GunItsType.MAG)
			return null;
		return forType(gun, entity.isSprinting());
	}

	public static HumanoidModel.ArmPose forType(GunType gun, boolean sprinting)
	{
		if(gun.itsType == GunType.GunItsType.MAG) return null;
		if(sprinting) return HumanoidModel.ArmPose.ITEM;
		String action = gun.itemUseAction == null ? "bow" : gun.itemUseAction.toLowerCase(java.util.Locale.ROOT);
		return switch(action)
		{
			case "bow" -> HumanoidModel.ArmPose.BOW_AND_ARROW;
			case "block" -> HumanoidModel.ArmPose.BLOCK;
			default -> HumanoidModel.ArmPose.ITEM;
		};
	}
}
