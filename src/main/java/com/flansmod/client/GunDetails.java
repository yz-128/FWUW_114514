package com.flansmod.client;

import com.flansmod.common.*;
import com.flansmod.common.types.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.util.List;

/** FMUM's Shift-expanded item information and recoil FOV, without changing user options. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class GunDetails {
	private GunDetails() { }
	@SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
		append(event.getToolTip(),InfoType.getType(event.getItemStack()),event.getItemStack(),Screen.hasShiftDown());
	}
	public static void append(List<Component> lines,InfoType type,net.minecraft.world.item.ItemStack stack,boolean expanded) {
		if(type instanceof GunType gun) append(lines,gun,stack,expanded);
		else if(type instanceof BulletType bullet) AmmoDetails.append(lines,bullet,expanded);
	}
	public static void append(List<Component> lines,GunType gun,net.minecraft.world.item.ItemStack stack,boolean expanded) {
		if(gun.description!=null && !gun.description.isBlank())
			for(String text:gun.description.split("_")) lines.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
		if(gun.itsType==GunType.GunItsType.MAG) {
			lines.add(Component.translatable("stats.flansmod.capacity",GunMagazine.roundsRemaining(stack),gun.ammoCapacity).withStyle(ChatFormatting.GRAY));
			return;
		}
		if(gun.shootDelay<=0F) return; // EFT's melee item is also in guns/.
		if(!expanded) { lines.add(Component.translatable("stats.flansmod.shift").withStyle(ChatFormatting.YELLOW));return; }
		var base=GunStats.base(gun);var stats=GunStats.effective(gun);
		lines.add(Component.translatable("stats.flansmod.heading").withStyle(ChatFormatting.GOLD));
		line(lines,"damage",number(stats.damage()));
		line(lines,"rpm",number(stats.rpm()));
		var modes=Component.empty();var available=gun.modes.isEmpty()?List.of(gun.mode):gun.modes;
		for(var mode:available) { if(!modes.getSiblings().isEmpty()) modes.append(" / ");modes.append(Component.translatable("stats.flansmod.mode."+mode.name().toLowerCase(java.util.Locale.ROOT))); }
		lines.add(Component.translatable("stats.flansmod.modes",modes).withStyle(ChatFormatting.GRAY));
		line(lines,"pitch",number(stats.pitch()),number(stats.randomPitch()/2F));
		line(lines,"yaw",number(stats.yaw()),number(stats.randomYaw()/2F));
		line(lines,"spread",number(stats.hipSpread()),number(stats.adsSpread()));
		line(lines,"speed",number(stats.speed()));
		line(lines,"reload",number(gun.effectiveReloadTime()/20F));
		lines.add(Component.translatable("stats.flansmod.base",number(base.pitch()),number(base.hipSpread()),number(base.adsSpread())).withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("stats.flansmod.damage_note").withStyle(ChatFormatting.DARK_GRAY));
	}
	private static void line(List<Component> lines,String key,Object... values) { lines.add(Component.translatable("stats.flansmod."+key,values).withStyle(ChatFormatting.GRAY)); }
	public static String number(float value) { return java.math.BigDecimal.valueOf(value).setScale(3,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
	@SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
		var mc=Minecraft.getInstance();if(mc.player==null || !mc.options.getCameraType().isFirstPerson()) return;
		if(!(InfoType.getType(mc.player.getMainHandItem()) instanceof GunType gun))return;
		float kick=gun.itsType==GunType.GunItsType.MAG?0F:GunAnimationRegistry.right(mc.player).fmumRecoil.fov((float)event.getPartialTick());
		double base=event.getFOV();
		if(!event.usedConfiguredFov()) {
			var rig=com.flansmod.client.model.loader.ModelCache.rigFor(gun,gun.modelScale);
			if(rig==null || !com.flansmod.client.render.FmumFirstPersonPose.hasPose(rig.animation))return;
			// The original RenderGun reset the hand projection to RenderFlan's configured
			// FOV (water: *60/70), rather than vanilla's fixed 70-degree hand projection.
			base=mc.options.fov().get();
			if(event.getCamera().getFluidInCamera()==net.minecraft.world.level.material.FogType.WATER)base*=60D/70D;
		}
		event.setFOV(base*(1F+kick));
	}
}
