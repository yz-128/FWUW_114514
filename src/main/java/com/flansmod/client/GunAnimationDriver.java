package com.flansmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.model.GunAnimations;
import com.flansmod.common.FlansMod;
import com.flansmod.common.PlayerData;
import com.flansmod.common.PlayerHandler;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ItemFlan;

/**
 * Turns the shooting state machine into animation, once per tick.
 *
 * <p>Successful local shots queue their sampled recoil before spending ammunition. Reloads and
 * identical consecutive cooldown values cannot fake or suppress a recoil impulse.
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class GunAnimationDriver
{
	private GunAnimationDriver() { }

	/** Detect the reload transition for models without the stepped FMUM reload. */
	private static boolean lastReloadingRight;
	/** Last tick's attack key, so the press edge that starts semi/burst fire can be told. */
	private static boolean lastTriggerDown;
	private static final com.flansmod.client.model.FmumRecoil.Camera CAMERA=new com.flansmod.client.model.FmumRecoil.Camera();

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event)
	{
		Player player = event.getEntity();
		if(!(player instanceof LocalPlayer))
			return;

		PlayerData data = PlayerHandler.getPlayerData(player);
		if(data == null)
			return;

		ItemStack held = player.getMainHandItem();
		InfoType type = InfoType.getType(held);
		boolean aiming=Minecraft.getInstance().screen==null && Minecraft.getInstance().options.keyUse.isDown();
		if(data.gunAimDown!=aiming) {
			data.gunAimDown=aiming;
			net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.flansmod.common.network.GunAimPayload(aiming,player.getInventory().selected));
		}
		com.flansmod.common.GunActions.tick(player,data);

		// The trigger, read from the attack key. Left-click is where the 1.20.1 port put the
		// primary fire input, and it is what a modern player reaches for — the vanilla use key
		// stays free for interactions.
		//
		// The press edge starts the shot for the modes that fire once per click; the held state
		// carries automatic fire. Releasing clears the trigger, which is also what ends a burst.
		// Reading it from a tick rather than from onUseTick is what keeps the gun out of the
		// "in use" state, and therefore out of vanilla's eat/drink arm pose.
		boolean trigger = Minecraft.getInstance().screen==null && Minecraft.getInstance().options.keyAttack.isDown();
		boolean wasTrigger = lastTriggerDown;
		if(held.getItem() instanceof ItemFlan flan)
		{
			if(trigger && !wasTrigger)
				com.flansmod.common.GunFiring.onTriggerPull(player.level(), player, held, true);

			if(!trigger)
				data.isShootingRight = data.isShootingLeft = data.pendingGunTrigger = false;
			flan.tickHeld(player.level(), player, held, true);

			// The server is the side that owns the rounds, so it has to be told about every trigger
			// edge and release. The local calls above are only the animation prediction; without
			// this packet the authoritative copy never fires and the saved gun never spends a
			// round. The edge is what is sent, not the held state: the server's own tick walks a
			// held trigger from there (PlayerHandler.onPlayerTick), matching how 1.7.10's
			// PacketHeldTrigger worked.
			if(trigger != wasTrigger)
				net.neoforged.neoforge.network.PacketDistributor.sendToServer(
						new com.flansmod.common.network.TriggerPayload(true, trigger));
		}
		lastTriggerDown = trigger;

		// Both are smoothed here rather than in the renderer, so they advance once per tick like
		// every other animation value. Aiming is the vanilla use button, which is the modern
		// convention the packs' ADS data was made for.
		AimState.tick(com.flansmod.common.GunActions.firearm(held)!=null && !data.gunAction.manualRest
				&& data.gunAction.takeOut==0F
				&& aiming);

		// FMUM's sprint pose is declared by holding/sprint vectors, not FMU2's fancyStance flag.
		boolean fancyStance = type instanceof GunType gun
				&& gun.modelScale > 0F
				&& ModelCache.rigFor(gun, gun.modelScale) != null
				&& com.flansmod.client.render.FmumFirstPersonPose.hasPose(ModelCache.rigFor(gun, gun.modelScale).animation);
		SprintStance.tick(fancyStance && !data.isReloading() && SprintStance.carrying(),
				type instanceof GunType gun && gun.runPoseTime > 0 ? gun.runPoseTime : 6F);

		com.flansmod.client.render.MuzzleEffectsRenderer.bindLocal(type instanceof GunType gun?gun:null,player.getInventory().selected);
		// Only the reload transition uses flags; shots use the successful-fire queue.
		boolean reloadStarted = data.reloadingRight && !lastReloadingRight;

		if(type instanceof GunType gun && held.getItem() instanceof ItemFlan)
		{
			GunAnimations animation = GunAnimationRegistry.right(player);
			// The pump and slide parameters are properties of the model class, not the definition,
			// so they come from the baked rig — a gun whose model declares no pump simply has zero
			// here and the pump step does nothing.
			BakedRig rig = ModelCache.rigFor(gun, gun.modelScale);
			int pumpDelay = rig == null ? 0 : rig.animation.pumpDelay;
			int pumpTime = rig == null || rig.animation.pumpTime <= 0 ? 1 : rig.animation.pumpTime;

			while(!data.recoilShots.isEmpty()) {
				var shot=data.recoilShots.removeFirst();
				if(shot.gun()!=gun || gun.itsType==GunType.GunItsType.MAG) continue;
				animation.doShoot(shot,rig==null?null:rig.animation,pumpDelay,pumpTime);
				CAMERA.shoot(shot.pitch(),shot.yaw());
				// A shot also holds the carry stance off for a moment, so a burst fired while
				// running does not have the stance snapping back in between rounds.
				SprintStance.onShot();
			}
			if(com.flansmod.common.GunReloading.supports(gun)) animation.reloading=false;
			else if(reloadStarted)
				animation.doReload(Math.round(gun.effectiveReloadTime()),
						rig == null ? 0 : rig.animation.pumpDelayAfterReload, pumpTime);
		}
		else data.recoilShots.clear();
		if(player.isAlive()) {
			var kick=CAMERA.tick();
			player.setXRot(Math.clamp(player.getXRot()+kick.pitch(),-90F,90F));
			player.setYRot(player.getYRot()+kick.yaw());
		} else CAMERA.reset();
		ClientGunMotion.tick((LocalPlayer)player,com.flansmod.common.GunActions.firearm(held),Minecraft.getInstance().options.keyUse.isDown());

		lastReloadingRight = data.reloadingRight;
	}

	/** A new world must not inherit camera recoil or a held trigger. */
	public static void reset()
	{
		CAMERA.reset();lastTriggerDown=false;
		lastReloadingRight = false;
	}
}
