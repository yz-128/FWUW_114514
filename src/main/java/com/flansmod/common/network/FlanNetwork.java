package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import com.flansmod.common.GunFiring;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Gun requests and server-accepted action/reload animations.
 */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class FlanNetwork
{
	/**
	 * A version string that must match on both sides. It exists so a mismatch is a clear error
	 * rather than a silently dropped packet; bump it whenever a payload's fields change.
	 */
	// Per-stack optical controls carry both structure and sight-state revisions. 0.4.14 raises
	// it for the stow and landing-sound behaviour change: no payload field moved, but a mixed
	// pair would disagree about whether aiming un-stows the gun, so it has to fail loudly.
	private static final String VERSION = "19";

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event)
	{
		event.registrar(VERSION).playToClient(BlockImpactPayload.TYPE,BlockImpactPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->com.flansmod.client.render.BulletImpactRenderer.accept(payload)));
		event.registrar(VERSION).playToServer(ArmourMaskPayload.TYPE,ArmourMaskPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->payload.apply(context.player())));
		event.registrar(VERSION).playToClient(GunSightAnimationPayload.TYPE,GunSightAnimationPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->com.flansmod.client.SightFoldAnimations.accept(payload)));
		event.registrar(VERSION).playToServer(GunSightPayload.TYPE,GunSightPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->com.flansmod.common.GunSightState.request(context.player(),payload)));
		event.registrar(VERSION).playToServer(GunModificationPayload.TYPE,GunModificationPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->{
					if(!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
					if(payload.operation()==GunModificationPayload.OPEN) com.flansmod.common.inventory.GunModificationMenu.open(player);
					else if(player.containerMenu instanceof com.flansmod.common.inventory.GunModificationMenu menu) menu.modify(player,payload);
				}));
		event.registrar(VERSION).playToClient(GunEjectionPayload.TYPE,GunEjectionPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->com.flansmod.client.render.MuzzleEffectsRenderer.ejection(payload)));
		event.registrar(VERSION).playToClient(GunShotPayload.TYPE,GunShotPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->com.flansmod.client.render.MuzzleEffectsRenderer.remote(payload)));
		event.registrar(VERSION).playToClient(ReloadAnimationPayload.TYPE,ReloadAnimationPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->com.flansmod.common.GunReloading.accept(context.player(),payload)));
		event.registrar(VERSION).playToServer(GunAimPayload.TYPE,GunAimPayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->{
					if(payload.slot()==context.player().getInventory().selected)
						com.flansmod.common.PlayerHandler.getPlayerData(context.player()).gunAimDown=payload.aiming();
				}));
		event.registrar(VERSION).playBidirectional(GunActionPayload.TYPE, GunActionPayload.STREAM_CODEC,
				(payload, context) -> context.enqueueWork(() -> {
					var player = context.player();
					if(payload.slot() != player.getInventory().selected) return;
					var operations = com.flansmod.common.GunActionState.Operation.values();
					if(payload.operation() < 0 || payload.operation() >= operations.length) return;
					var operation = operations[payload.operation()];
					if(com.flansmod.common.GunActions.request(player, operation) && !player.level().isClientSide)
						context.reply(new GunActionPayload(operation==com.flansmod.common.GunActionState.Operation.REST?operation.ordinal()
								:com.flansmod.common.PlayerHandler.getPlayerData(player).gunAction.queued.ordinal(),payload.slot()));
				}));
		event.registrar(VERSION).playToServer(ReloadPayload.TYPE, ReloadPayload.STREAM_CODEC,
				(payload, context) -> context.enqueueWork(() ->
						com.flansmod.common.GunReloading.handleRequest(context.player(),payload)));

		event.registrar(VERSION).playToServer(TriggerPayload.TYPE, TriggerPayload.STREAM_CODEC,
				(payload, context) -> context.enqueueWork(() ->
						GunFiring.serverTrigger(context.player(), payload.mainHand(), payload.down())));

		// Tactical device switches (U flashlight, Y laser). The server toggles the gun it owns.
		event.registrar(VERSION).playToServer(GunTacticalPayload.TYPE, GunTacticalPayload.STREAM_CODEC,
				(payload, context) -> context.enqueueWork(() ->
						com.flansmod.common.GunTactical.toggle(context.player(), context.player().getMainHandItem(),
								payload.device())));

		// Switching firing mode. The server performs the switch and writes it onto the stack, so
		// both sides agree on the result and the choice is saved with the gun.
		event.registrar(VERSION).playToServer(FireModePayload.TYPE, FireModePayload.STREAM_CODEC,
				(payload, context) -> context.enqueueWork(() ->
						GunFiring.serverCycleFireMode(context.player(), payload.offHand())));

		// Content-pack handshake: the server sends its manifest on login, the client answers with
		// its own, and the server disconnects a client whose packs do not match.
		event.registrar(VERSION).playBidirectional(ContentPackManifestPayload.TYPE,
				ContentPackManifestPayload.STREAM_CODEC,(payload,context)->context.enqueueWork(() ->
						com.flansmod.common.ContentPackSync.accept(payload,context.player())));

		// Held first-aid kit: the client reports the slot and the server owns healing/uses.
		event.registrar(VERSION).playToServer(MedkitUsePayload.TYPE,MedkitUsePayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->{
					if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
						com.flansmod.common.Tools.serverMedkitUse(player,payload.slot());
				}));

		event.registrar(VERSION).playToServer(FoodUsePayload.TYPE,FoodUsePayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->{
					if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
						com.flansmod.common.Tools.serverFoodUse(player,payload.slot());
				}));

		event.registrar(VERSION).playToServer(UnloadMagazinePayload.TYPE,UnloadMagazinePayload.STREAM_CODEC,
				(payload,context)->context.enqueueWork(()->{
					if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
					{
						var stack = player.getMainHandItem();
						if(player.getInventory().selected != payload.slot()) return;
						// Server-side rate limit: holding G sends every four client ticks, but a
						// laggy client must not drain a magazine faster than one round per 4 ticks.
						long now = player.level().getGameTime();
						long last = player.getPersistentData().getLong("flansmodMagEjectTick");
						if(now - last < 4) return;
						player.getPersistentData().putLong("flansmodMagEjectTick", now);
						if(com.flansmod.common.types.InfoType.getType(stack)
								instanceof com.flansmod.common.types.GunType mag)
							com.flansmod.common.GunMagazine.ejectOneRound(player, stack, mag);
					}
				}));
	}

	private FlanNetwork() { }
}
