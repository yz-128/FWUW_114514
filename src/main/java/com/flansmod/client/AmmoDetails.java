package com.flansmod.client;

import com.flansmod.common.types.BulletType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Locale;

/** EFT ammo's parsed base values; no inventory, weapon state or tooltip side effects. */
public final class AmmoDetails {
	private AmmoDetails() { }
	public static void append(List<Component> lines,BulletType bullet,boolean expanded) {
		if(bullet.description!=null && !bullet.description.isBlank())
			for(String text:bullet.description.split("_")) lines.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
		if(bullet.isCase) { lines.add(Component.translatable("stats.flansmod.ammo.case").withStyle(ChatFormatting.GRAY));return; }
		lines.add(Component.translatable("stats.flansmod.ammo.heading").withStyle(ChatFormatting.GOLD));
		if(bullet.ammoType!=null && !bullet.ammoType.isBlank()) line(lines,"caliber",caliber(bullet.ammoType));
		line(lines,"damage",GunDetails.number(bullet.damageVsLiving),bullet.numBullets);
		if(bullet.maxPenetrationLevel>0F || bullet.minPenetrationLevel>0F)
			line(lines,"penetration",GunDetails.number(bullet.minPenetrationLevel),GunDetails.number(bullet.maxPenetrationLevel));
		if(bullet.armorBreakPower>0F) line(lines,"armor",GunDetails.number(bullet.armorBreakPower));
		if(!expanded) { lines.add(Component.translatable("stats.flansmod.ammo.shift").withStyle(ChatFormatting.YELLOW));return; }
		line(lines,"targets",GunDetails.number(bullet.damageVsPlayer),GunDetails.number(bullet.damageVsLiving));
		line(lines,"vehicles",GunDetails.number(bullet.damageVsVehicles),GunDetails.number(bullet.damageVsPlanes));
		line(lines,"spread",GunDetails.number(bullet.bulletSpreadMultiplier),GunDetails.number(bullet.aimingBulletSpreadMultiplier));
		line(lines,"blunt",GunDetails.number(bullet.bluntDamageMult*100F));
		line(lines,"through",GunDetails.number(bullet.penetratingPower));
		line(lines,"stack",bullet.maxStackSize);
		lines.add(Component.translatable("stats.flansmod.ammo.damage_note").withStyle(ChatFormatting.DARK_GRAY));
	}
	private static void line(List<Component> lines,String key,Object... values) {
		lines.add(Component.translatable("stats.flansmod.ammo."+key,values).withStyle(ChatFormatting.GRAY));
	}
	public static Component caliber(String type) {
		String label=switch(type.toLowerCase(Locale.ROOT)) {
			case "545x39" -> "5.45×39 mm";
			case "556x45" -> "5.56×45 mm";
			case "762x39" -> "7.62×39 mm";
			case "762x51" -> "7.62×51 mm";
			case "762x54" -> "7.62×54 mm";
			case "58x42" -> "5.8×42 mm";
			case "57x28" -> "5.7×28 mm";
			case "46x30" -> "4.6×30 mm";
			case "127x55" -> "12.7×55 mm";
			case "9x19" -> "9×19 mm";
			case "9x39" -> "9×39 mm";
			case "45acp" -> ".45 ACP";
			case "338" -> ".338";
			default -> type;
		};
		return type.equalsIgnoreCase("12g")?Component.translatable("stats.flansmod.ammo.caliber.12g"):Component.literal(label);
	}
}
