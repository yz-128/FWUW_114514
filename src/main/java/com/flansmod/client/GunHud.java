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
		if(com.flansmod.common.MechanicsSettings.ammoHudEnabled())
		{
			graphics.drawString(mc.font, mode, modeX + 1, modeY + 1, 0x000000, false);
			graphics.drawString(mc.font, mode, modeX, modeY, modeColour, false);
		}

		if(com.flansmod.common.MechanicsSettings.ammoHudEnabled())
		{
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
		}

		// Barrel heat: a thin bar above the hotbar, only once the barrel is warming up, plus a red
		// OVERHEAT word while the trigger is locked. The client predicts the same heat the server
		// does (both run fireRound), so the bar matches the lock in single player.
		var data = com.flansmod.common.PlayerHandler.getPlayerData(mc.player);
		if(data != null && com.flansmod.common.BarrelHeat.enabled()
				&& com.flansmod.common.MechanicsSettings.heatBarEnabled())
		{
			float heat = data.heatOf(gun.shortName);
			boolean locked = data.lockOf(gun.shortName) > 0;
			if(heat > 0.02F || locked)
			{
				int barWidth = 90, barHeight = 3;
				int barX = sw / 2 - barWidth / 2, barY = sh - 54;
				graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x90000000);
				int colour = locked ? 0xFFFF3030 : heat > 0.6F ? 0xFFFF9A30 : 0xFFFFE066;
				graphics.fill(barX, barY, barX + (int)(barWidth * Math.min(1F, heat)), barY + barHeight, colour);
				if(locked)
				{
					String overheat = Component.translatable("hud.flansmod.overheat").getString();
					int textX = sw / 2 - mc.font.width(overheat) / 2;
					graphics.drawString(mc.font, overheat, textX + 1, barY - 11, 0x000000, false);
					graphics.drawString(mc.font, overheat, textX, barY - 12, 0xFF6060, false);
				}
			}
		}

		renderAmmoType(graphics, mc, sw, sh);

		// Overheat jam: the gun will not fire until G racks the bolt, so say so plainly above the
		// heat bar (which may have cooled below the visible threshold while still jammed).
		String jammedFor = com.flansmod.common.GunJamming.jam(held);
		if(!jammedFor.isEmpty() && jammedFor.equals(gun.shortName))
		{
			String jam = Component.translatable("hud.flansmod.jammed").getString()
					+ " · " + Component.translatable("hud.flansmod.jammed_hint").getString();
			int jamX = sw / 2 - mc.font.width(jam) / 2;
			int jamY = sh - 70;
			graphics.drawString(mc.font, jam, jamX + 1, jamY + 1, 0x000000, false);
			graphics.drawString(mc.font, jam, jamX, jamY, 0xFF6060, false);
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

	/** The transient bottom-right line shown after a magazine inspection. */
	private static String ammoTypeText;
	private static long ammoTypeUntil;
	private static final long AMMO_TYPE_MS = 4000L;

	/** Reads the loaded round's authored name for the inspection readout. */
	public static void showAmmoType(ItemStack stack)
	{
		if(!(InfoType.getType(stack) instanceof GunType gun)) return;
		ItemStack round = GunMagazine.getChamberedRound(stack);
		if(round.isEmpty()) round = GunMagazine.get(stack, gun, 0);
		if(round.isEmpty() || GunMagazine.roundsRemaining(round) <= 0) return;
		InfoType type = InfoType.getType(round);
		if(type == null) return;
		ammoTypeText = Component.translatable("hud.flansmod.ammo_type", type.displayName()).getString();
		ammoTypeUntil = System.currentTimeMillis() + AMMO_TYPE_MS;
	}

	private static void renderAmmoType(GuiGraphics graphics, Minecraft mc, int sw, int sh)
	{
		if(ammoTypeText == null || System.currentTimeMillis() > ammoTypeUntil) return;
		int textX = sw - mc.font.width(ammoTypeText) - 8;
		int textY = sh - 45;
		graphics.drawString(mc.font, ammoTypeText, textX + 1, textY + 1, 0x000000, false);
		graphics.drawString(mc.font, ammoTypeText, textX, textY, 0xFFFFFF, false);
	}

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
	public static String modeName(GunType.EnumFireMode mode)
	{
		return Component.translatableWithFallback(
				"stats.flansmod.mode." + mode.name().toLowerCase(java.util.Locale.ROOT),
				mode.getDisplayName()).getString();
	}
}
