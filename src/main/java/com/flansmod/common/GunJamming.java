package com.flansmod.common;

import com.flansmod.common.types.GunType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Overheat jams. When the barrel crosses the overheat line the gun stores a jam on its own item
 * data, which the trigger checks and the HUD reads on both sides; the charge key (G by default)
 * racks the bolt and clears it.
 *
 * <p>Storing the jam on the stack rather than in transient player state survives hotbar swaps and
 * client/server sync: the server's item data replication carries it, so the HUD and the trigger
 * never disagree.
 */
public final class GunJamming
{
	/** Item-data key holding the short name of the gun that jammed, or absent. */
	public static final String KEY = "flansmodJam";
	/** Heat left in the barrel after a successful clear: the gun fires again but warms quickly. */
	public static final float CLEARED_HEAT = 0.35F;

	private GunJamming() { }

	/** The short name of the gun this stack is jammed for, or {@code ""}. */
	public static String jam(ItemStack stack)
	{
		if(stack == null || stack.isEmpty())
			return "";
		var data = stack.get(DataComponents.CUSTOM_DATA);
		if(data == null)
			return "";
		var tag = data.copyTag();
		return tag.contains(KEY, Tag.TAG_STRING) ? tag.getString(KEY) : "";
	}

	/** Whether this stack is jammed for exactly this gun definition. */
	public static boolean jammed(ItemStack stack, GunType gun)
	{
		return gun != null && gun.shortName != null && gun.shortName.equals(jam(stack));
	}

	public static void setJam(ItemStack stack, String gunShortName)
	{
		var data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		if(gunShortName == null || gunShortName.isEmpty())
			tag.remove(KEY);
		else
			tag.putString(KEY, gunShortName);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static void clear(ItemStack stack)
	{
		setJam(stack, "");
	}

	/** Heat after a clear: lowered, but not cold — the barrel is still hot. */
	public static float clearedHeat(float heat)
	{
		return Math.min(Math.max(0F, heat), CLEARED_HEAT);
	}
}
