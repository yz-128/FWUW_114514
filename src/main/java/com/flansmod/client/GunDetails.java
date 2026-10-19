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
		var base=GunStats.base(gun);var stats=GunStats.effective(gun,stack);
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
		net.minecraft.world.item.ItemStack held=mc.player.getMainHandItem();
		if(!(InfoType.getType(held) instanceof GunType gun))return;
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
		double zoom=1;
		if(event.usedConfiguredFov() && !PlayerHandler.getPlayerData(mc.player).sightFolding(mc.player)) {
			var view=GunAttachments.view(gun,held);
			var scope=GunSightState.scopeSight(view);
			// Magnify the world pass itself, the way TACZ does, instead of sampling a crop of the
			// finished frame. The frame buffer holds every pixel the camera can see; cropping a
			// fifth of it and stretching it over the ocular is what made the image soft.
			if(scope!=null)
				zoom=com.flansmod.client.render.ScopeMagnification.zoom(scope.type(),scope.node().zoomStep);
			else {
				var sight=GunSightState.selectedSight(view);
				if(sight!=null && !sight.type().isFoldedVariant())zoom=Math.max(1,sight.type().FOVZoomLevel);
			}
			// The hand pass keeps its own FOV, so the gun model does not grow with the world.
			zoom=1+(zoom-1)*AimState.blend((float)event.getPartialTick());
		}
		if(event.usedConfiguredFov())com.flansmod.client.render.ScopeMagnification.frame(base*(1F+kick),zoom);
		event.setFOV(base*(1F+kick)/zoom);
	}
	@SubscribeEvent public static void sensitivity(net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent event) {
		var mc=Minecraft.getInstance();
		if(mc.player==null || mc.screen!=null || !mc.options.getCameraType().isFirstPerson())return;
		if(PlayerHandler.getPlayerData(mc.player).sightFolding(mc.player))return;
		var stack=mc.player.getMainHandItem();if(!(InfoType.getType(stack) instanceof GunType gun))return;
		var group=GunSightState.selectedGroup(GunAttachments.view(gun,stack));if(group==null)return;
		double multiplier=1;
		for(var sight:group.sights())if(!sight.type().isFoldedVariant())
			multiplier*=sight.type().sensitivityMultiplier(sight.node().zoomStep);
		float aim=AimState.blend(mc.getTimer().getGameTimeDeltaPartialTick(true));
		event.setMouseSensitivity(com.flansmod.client.render.FmumSightPicture.sensitivity(event.getMouseSensitivity(),1+(multiplier-1)*aim));
	}
}
