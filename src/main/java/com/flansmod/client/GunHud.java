package com.flansmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.flansmod.common.FlansMod;
import com.flansmod.common.GunMagazine;
import com.flansmod.common.types.BulletType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ItemFlan;

/** Ammo beside the hotbar: actual magazine contents and the independent chamber round. */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class GunHud
{
	private GunHud() { }

	@SubscribeEvent
	public static void onRenderLayer(RenderGuiLayerEvent.Pre event)
	{
		if(!VanillaGuiLayers.CROSSHAIR.equals(event.getName()))
			return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.player != null && (isFirearm(mc.player.getMainHandItem()) || isFirearm(mc.player.getOffhandItem())))
			event.setCanceled(true);
	}

	private static boolean isFirearm(ItemStack stack)
	{
		return InfoType.getType(stack) instanceof GunType gun && gun.itsType != GunType.GunItsType.MAG;
	}

	@SubscribeEvent
	public static void onRegisterLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event)
	{
		event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR,
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "gun_ammo"),
				GunHud::render);
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta)
	{
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null || mc.options.hideGui || mc.screen != null)
			return;

		ItemStack held = mc.player.getMainHandItem();
		if(!(held.getItem() instanceof ItemFlan flan) || !(flan.type instanceof GunType gun))
			return;

		int sw = graphics.guiWidth(), sh = graphics.guiHeight();

		// The fire mode, under the crosshair on the right, in the same colours the original used:
		// grey when it cannot be switched, the lighter grey-white when it can. Read from the stack,
		// not the definition, because the selection is per gun: switching one rifle to semi does
		// not switch the identical rifle in the next slot.
		String mode = modeName(gun.modeFor(held));
		int modeColour = gun.canSwitchMode() ? 0xAAAAFF : 0x888888;
		int modeX = sw / 2 + 96;
		int modeY = sh - 31;
		graphics.drawString(mc.font, mode, modeX + 1, modeY + 1, 0x000000, false);
		graphics.drawString(mc.font, mode, modeX, modeY, modeColour, false);

		var entries=new java.util.ArrayList<AmmoEntry>();
		if(gun.itsType==GunType.GunItsType.MAG) {
			ItemStack top=GunMagazine.chamberedBullet(held);
			if(!top.isEmpty()) entries.add(new AmmoEntry(top,"x"+GunMagazine.roundsRemaining(held)));
		}
		for(int chamber = 0; chamber < gun.numAmmoItemsInGun; chamber++)
		{
			if(gun.itsType==GunType.GunItsType.MAG) break;
			ItemStack round = GunMagazine.get(held, gun, chamber);
			if(round.isEmpty())
				continue;

			int per = GunMagazine.roundsPerItem(round);
			int remaining = GunMagazine.roundsRemaining(round);
			if(remaining <= 0 && !(InfoType.getType(round) instanceof GunType magazine && magazine.itsType==GunType.GunItsType.MAG))
				continue;
			String text = per > 1 ? remaining + "/" + per : "x" + remaining;
			if(per > 1 && round.getCount() > 1)
				text += " x" + round.getCount();
			entries.add(new AmmoEntry(round,text));
		}
		ItemStack chambered=gun.itsType==GunType.GunItsType.MAG?ItemStack.EMPTY:GunMagazine.getChamberedRound(held);
		if(!chambered.isEmpty()) entries.add(new AmmoEntry(chambered,"+1"));
		// Keep both the magazine and +1 visible at narrow window widths / large GUI scales.
		int width=entries.stream().mapToInt(e->16+mc.font.width(e.text())+4).sum();
		int ammoX=Math.max(2,Math.min(modeX,sw-width-4));
		for(var entry:entries) {
			int iconX = ammoX;
			int iconY = sh - 19;
			graphics.renderItem(entry.stack(), iconX, iconY);
			graphics.renderItemDecorations(mc.font, entry.stack(), iconX, iconY);

			int textX = iconX + 16 + 2;
			int textY = sh - 12;
			graphics.drawString(mc.font, entry.text(), textX + 1, textY + 1, 0x000000, false);
			graphics.drawString(mc.font, entry.text(), textX, textY, 0xFFFFFF, false);
			ammoX += 16 + mc.font.width(entry.text()) + 4;
		}

		// A small reserve count on the left: how many spare rounds the player is carrying of the
		// first ammunition type this gun takes. Always shown for guns with magazines, because the
		// rounds live in the gun rather than in the inventory count.
		BulletType firstAmmo = gun.ammo.isEmpty() ? null : gun.ammo.get(0);
		if(firstAmmo != null && firstAmmo.roundsPerItem > 1)
		{
			int reserve = countReserve(mc, gun);
			String text = Component.translatable("hud.flansmod.reserve", reserve).getString();
			graphics.drawString(mc.font, text, sw / 2 - 176, sh - 19, 0xFFFFFF, false);
		}
	}
	private record AmmoEntry(ItemStack stack,String text) { }

	/** Spare rounds carried, counted as rounds rather than as items. */
	private static int countReserve(Minecraft mc, GunType gun)
	{
		int total = 0;
		for(int i = 0; i < mc.player.getInventory().getContainerSize(); i++)
		{
			ItemStack stack = mc.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof ItemFlan flan))
				continue;
			if(!(flan.type instanceof BulletType bullet) || !gun.ammo.contains(bullet))
				continue;
			int per = GunMagazine.roundsPerItem(stack);
			total += per > 1
					? (stack.getCount() - 1) * per + GunMagazine.roundsRemaining(stack)
					: stack.getCount();
		}
		return total;
	}

	/**
	 * The fire-mode name shown on the HUD. Read through the language system so it follows the
	 * player's language — the keys are the ones {@code GunDetails} already uses for the tooltip's
	 * mode list — with the enum's English label as the fallback.
	 */
	private static String modeName(GunType.EnumFireMode mode)
	{
		return Component.translatableWithFallback(
				"stats.flansmod.mode." + mode.name().toLowerCase(java.util.Locale.ROOT),
				mode.getDisplayName()).getString();
	}
}
