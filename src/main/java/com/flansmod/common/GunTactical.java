package com.flansmod.common;

import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ModifiableType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Tactical devices (flashlight / laser pointer): they are toggles stored on the gun stack, like
 * the selected sight group, so they survive swapping and hotbar moves and are replicated by the
 * item data instead of a client-only flag.
 *
 * <p>1.7.10 toggled them with U (light) and Y (laser) and drew the part's own glow model while on;
 * this class is the shared state side of that behaviour.
 */
public final class GunTactical
{
	public static final byte LIGHT = 0, LASER = 1, ALL = 2;

	private GunTactical() { }

	/** Whether the currently mounted attachment tree carries at least one such device. */
	public static boolean hasDevice(GunType gun, ItemStack stack, byte device)
	{
		if(gun == null || stack == null || stack.isEmpty())
			return false;
		return contains(GunAttachments.read(gun, stack), device);
	}

	private static boolean contains(List<List<ModifiableType.DefaultAttachment>> groups, byte device)
	{
		for(var group : groups)
			for(var node : group)
			{
				var at = AttachmentType.getAttachment(node.name);
				if(at != null && (device == LIGHT ? at.flashlight : at.laserPointer))
					return true;
				if(!node.children.isEmpty() && contains(List.of(node.children), device))
					return true;
			}
		return false;
	}

	/** Toggles one device on the stack; returns false when that kind of device is not mounted. */
	public static boolean toggle(Player player, ItemStack stack, byte device)
	{
		if(!(InfoType.getType(stack) instanceof GunType gun))
			return false;
		if(device == ALL)
			return toggleAll(stack, gun);
		if(!hasDevice(gun, stack, device))
			return false;
		boolean light = GunAttachments.isLightOn(stack);
		boolean laser = GunAttachments.isLaserOn(stack);
		if(device == LIGHT)
			light = !light;
		else
			laser = !laser;
		GunAttachments.setTacticalState(stack, light, laser);
		return true;
	}

	/**
	 * The single tactical key (T): when any mounted device is on, turning it switches everything
	 * off; when all are off it turns on every device the gun actually carries.
	 */
	private static boolean toggleAll(ItemStack stack, GunType gun)
	{
		boolean hasLight = hasDevice(gun, stack, LIGHT);
		boolean hasLaser = hasDevice(gun, stack, LASER);
		if(!hasLight && !hasLaser)
			return false;
		boolean on = GunAttachments.isLightOn(stack) || GunAttachments.isLaserOn(stack);
		GunAttachments.setTacticalState(stack, !on && hasLight, !on && hasLaser);
		return true;
	}
}
