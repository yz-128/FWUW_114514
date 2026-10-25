package com.flansmod.client;

import com.flansmod.common.FlansMod;
import com.flansmod.common.GunFiring;
import com.flansmod.common.network.FireModePayload;
import com.flansmod.common.network.ReloadPayload;
import com.flansmod.common.ReloadKeyState;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

/** Observes real bindings: tap R reloads, double R uses FMUM advanced reload, held R compresses. */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class ReloadKeyHandler
{
	private ReloadKeyHandler() { }
	private static final ReloadKeyState RELOAD_INPUT=new ReloadKeyState();
	private static int observedReloadPresses;
	@SubscribeEvent
	public static void onKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
		Minecraft mc=Minecraft.getInstance();
		if(event.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS && mc.player!=null && mc.screen==null
				&& RELOAD_KEY.matches(event.getKey(),event.getScanCode()) && RELOAD_KEY.isConflictContextAndModifierActive())
			observedReloadPresses++;
	}

	/** R, in a category of this mod's own so it shows up in the controls screen. */
	public static final KeyMapping RELOAD_KEY = new KeyMapping(
			"key.flansmod.reload", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.flansmod");
	public static final KeyMapping CHARGE_KEY=new KeyMapping("key.flansmod.charge",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,InputConstants.KEY_G,"key.categories.flansmod");
	public static final KeyMapping RELEASE_BOLT_KEY=new KeyMapping("key.flansmod.release_bolt",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,InputConstants.KEY_H,"key.categories.flansmod");

	/**
	 * B, the firing-mode key — the same default the 1.20.1 build uses.
	 *
	 * <p>It lives here rather than in a class of its own because it is the other half of the same
	 * idea: both keys change what the gun will do next, both are sent to the server as a request,
	 * and both do nothing when no gun is held.
	 */
	public static final KeyMapping FIRE_MODE_KEY = new KeyMapping(
			"key.flansmod.fire_mode", InputConstants.Type.KEYSYM, InputConstants.KEY_B, "key.categories.flansmod");
	/** T is the tactical-device key: one press toggles every mounted flashlight/laser together. */
	public static final KeyMapping TACTICAL_KEY = new KeyMapping("key.flansmod.tactical", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_T, "key.categories.flansmod");
	public static final KeyMapping CHECK_AMMO_KEY = new KeyMapping("key.flansmod.check_ammo", KeyConflictContext.IN_GAME, KeyModifier.ALT, InputConstants.Type.KEYSYM, InputConstants.KEY_T, "key.categories.flansmod");
	public static final KeyMapping CHECK_CHAMBER_KEY = new KeyMapping("key.flansmod.check_chamber", KeyConflictContext.IN_GAME, KeyModifier.ALT, InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.flansmod");
	/** Alt+B reads the current firing mode into the action bar, separate from plain B's switch. */
	public static final KeyMapping CHECK_FIRE_MODE_KEY = new KeyMapping("key.flansmod.check_fire_mode", KeyConflictContext.IN_GAME, KeyModifier.ALT, InputConstants.Type.KEYSYM, InputConstants.KEY_B, "key.categories.flansmod");
	public static final KeyMapping REST_KEY = new KeyMapping("key.flansmod.rest", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_O, "key.categories.flansmod");
	public static final KeyMapping ALT_REST_KEY = new KeyMapping("key.flansmod.co_rest", KeyConflictContext.IN_GAME, KeyModifier.ALT, InputConstants.Type.KEYSYM, InputConstants.KEY_V, "key.categories.flansmod");

	@SubscribeEvent
	public static void onRegisterKeys(RegisterKeyMappingsEvent event)
	{
		event.register(RELOAD_KEY);
		event.register(CHARGE_KEY);event.register(RELEASE_BOLT_KEY);
		event.register(TACTICAL_KEY);
		event.register(FIRE_MODE_KEY);
		event.register(CHECK_AMMO_KEY); event.register(CHECK_CHAMBER_KEY); event.register(CHECK_FIRE_MODE_KEY);
		event.register(REST_KEY); event.register(ALT_REST_KEY);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event)
	{
		Minecraft mc = Minecraft.getInstance();
		while(RELOAD_KEY.consumeClick()) { } // OS key-repeat cannot become a second R press
		boolean valid=mc.player!=null && mc.screen==null;
		GunType held=valid && InfoType.getType(mc.player.getMainHandItem()) instanceof GunType gun?gun:null;
		int commands=RELOAD_INPUT.update(valid && RELOAD_KEY.isDown() && RELOAD_KEY.isConflictContextAndModifierActive(),held!=null && held.itsType==GunType.GunItsType.MAG,
				valid?mc.player.getInventory().selected:-1,held==null?null:mc.player.getMainHandItem().getItem(),valid?observedReloadPresses:0);
		observedReloadPresses=0;
		if((commands&ReloadKeyState.RELEASE)!=0) reload(mc,ReloadPayload.Action.COMPRESS_STOP);
		if((commands&ReloadKeyState.HOLD)!=0) reload(mc,ReloadPayload.Action.COMPRESS_START);
		if((commands&ReloadKeyState.NORMAL)!=0) reload(mc,ReloadPayload.Action.NORMAL);
		if((commands&ReloadKeyState.SPECIAL)!=0) reload(mc,ReloadPayload.Action.SPECIAL);
		while(CHARGE_KEY.consumeClick())
		{
			if(!valid) continue;
			// G on a magazine pulls one round out instead of starting a gun charge.
			if(InfoType.getType(mc.player.getMainHandItem()) instanceof GunType heldGun
					&& heldGun.itsType == GunType.GunItsType.MAG)
			{
				PacketDistributor.sendToServer(new com.flansmod.common.network.UnloadMagazinePayload(
						mc.player.getInventory().selected));
			}
			else reload(mc,ReloadPayload.Action.CHARGE);
		}
		// Hold G on a magazine: keep pulling rounds out, four times a second.
		if(valid && CHARGE_KEY.isDown() && mc.player.tickCount % 4 == 0
				&& InfoType.getType(mc.player.getMainHandItem()) instanceof GunType heldMag
				&& heldMag.itsType == GunType.GunItsType.MAG)
			PacketDistributor.sendToServer(new com.flansmod.common.network.UnloadMagazinePayload(
					mc.player.getInventory().selected));
		while(RELEASE_BOLT_KEY.consumeClick()) if(valid) reload(mc,ReloadPayload.Action.RELEASE_BOLT);
		// Tactical devices use T (one key, like the user asked): it turns every mounted device on,
		// or all of them off when any is already on. Predict locally so the light changes on the
		// key press, then let the server confirm on the stack it owns.
		while(TACTICAL_KEY.consumeClick()) tactical(mc, com.flansmod.common.GunTactical.ALL);

		// NeoForge resolves modified bindings before plain bindings: Alt+R checks the chamber
		// without also consuming the ordinary R reload binding.
		while(CHECK_AMMO_KEY.consumeClick())
		{
			if(mc.player != null) GunHud.showAmmoType(mc.player.getMainHandItem());
			action(mc, com.flansmod.common.GunActionState.Operation.CHECK_AMMO);
		}
		while(CHECK_CHAMBER_KEY.consumeClick())
			action(mc, com.flansmod.common.GunActionState.Operation.CHECK_CHAMBER);
		while(CHECK_FIRE_MODE_KEY.consumeClick())
			showFireMode(mc);
		while(REST_KEY.consumeClick())
			action(mc, com.flansmod.common.GunActionState.Operation.REST);
		while(ALT_REST_KEY.consumeClick())
			action(mc, com.flansmod.common.GunActionState.Operation.REST);
		while(FIRE_MODE_KEY.consumeClick())
		{
			if(mc.player == null || mc.screen != null)
				continue;

			// The mode is switched locally as well, so the HUD changes on the key press instead of
			// a round trip later. The server runs the identical switch on the stack it owns; if the
			// client's copy had drifted, the server's answer is the one that survives.
			GunFiring.serverCycleFireMode(mc.player, false);
			PacketDistributor.sendToServer(new FireModePayload(false));
		}
	}
	private static void showFireMode(Minecraft mc) {
		if(mc.player == null || mc.screen != null) return;
		var stack = mc.player.getMainHandItem();
		if(!(InfoType.getType(stack) instanceof GunType gun) || gun.itsType == GunType.GunItsType.MAG) return;
		mc.player.displayClientMessage(Component.translatable("hud.flansmod.fire_mode",
				com.flansmod.client.GunHud.modeName(gun.modeFor(stack))), true);
	}

	private static void reload(Minecraft mc,ReloadPayload.Action action) {
		if(mc.player!=null) PacketDistributor.sendToServer(new ReloadPayload(false,action,mc.player.getInventory().selected));
	}
	private static void tactical(Minecraft mc, byte device) {
		if(mc.player == null || mc.screen != null) return;
		var stack = mc.player.getMainHandItem();
		if(!(com.flansmod.common.types.InfoType.getType(stack) instanceof com.flansmod.common.types.GunType gun)) return;
		if(!com.flansmod.common.GunTactical.hasDevice(gun, stack, device)) return;
		com.flansmod.common.GunTactical.toggle(mc.player, stack, device);
		net.neoforged.neoforge.network.PacketDistributor.sendToServer(
				new com.flansmod.common.network.GunTacticalPayload(device));
	}

	private static void action(Minecraft mc, com.flansmod.common.GunActionState.Operation operation) {
		if(mc.player != null && mc.screen == null)
			PacketDistributor.sendToServer(new com.flansmod.common.network.GunActionPayload(operation.ordinal(), mc.player.getInventory().selected));
	}
}
