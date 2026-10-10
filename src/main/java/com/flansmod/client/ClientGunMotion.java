package com.flansmod.client;

import com.flansmod.client.render.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.common.GunActions;
import com.flansmod.common.types.GunType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Reads the player's actual motion; no generated keyboard, mouse, or desktop input. */
@EventBusSubscriber(modid=com.flansmod.common.FlansMod.MODID,value=Dist.CLIENT)
public final class ClientGunMotion {
	private static final LegacyGunMotion MOTION=new LegacyGunMotion();
	private static LocalPlayer previousPlayer;
	private static GunType previousGun;
	private static float previousYaw,previousPitch,previousVelocity;
	private static double previousY;
	private static Boolean savedBobbing;
	private ClientGunMotion() { }
	public static void tick(LocalPlayer player,GunType gun,boolean aiming) {
		Minecraft mc=Minecraft.getInstance();
		if(gun==null) { reset(); return; }
		BakedRig rig=ModelCache.rigFor(gun,gun.modelScale);
		if(rig==null || !FmumFirstPersonPose.hasPose(rig.animation)) { reset(); return; }
		if(savedBobbing==null) { savedBobbing=mc.options.bobView().get(); mc.options.bobView().set(false); }
		if(previousPlayer!=player || previousGun!=gun) {
			MOTION.reset();previousYaw=player.getYRot();previousPitch=player.getXRot();
			previousY=player.getY();previousVelocity=0F;previousPlayer=player;previousGun=gun;
		}
		float velocity=(float)(player.getY()-previousY);
		MOTION.tick(rig.animation,Mth.wrapDegrees(player.getYRot()-previousYaw),player.getXRot()-previousPitch,
				previousVelocity-velocity,player.walkDist,player.bob,aiming);
		// Old FlansModClient applied these differences to the player's camera each tick.
		float ads=AimState.blend(1F),sprint=SprintStance.blend(1F);
		player.setYRot(player.getYRot()+MOTION.cameraYaw(rig.animation,ads,sprint));
		player.setXRot(Mth.clamp(player.getXRot()+MOTION.cameraPitch(rig.animation,ads,sprint),-90F,90F));
		previousYaw=player.getYRot();previousPitch=player.getXRot();previousY=player.getY();previousVelocity=velocity;
		var data=com.flansmod.common.PlayerHandler.getPlayerData(player);
		if(data.gunAction.operation==com.flansmod.common.GunActionState.Operation.NONE && SprintStance.blend(1F)==0F)
			data.gunWallRay=FmumCollisionRay.local(rig.animation,gun,ads,MOTION.frame(rig.animation,1,ads,0));
	}
	public static LegacyGunMotion.Frame frame(ModelGeometry.Model model,float partial,float ads,float sprint) {
		return previousGun==null?LegacyGunMotion.Frame.ZERO:MOTION.frame(model,partial,ads,sprint);
	}
	public static void reset() {
		MOTION.reset();previousPlayer=null;previousGun=null;
		if(savedBobbing!=null) { Minecraft.getInstance().options.bobView().set(savedBobbing);savedBobbing=null; }
	}
	@SubscribeEvent public static void onTick(ClientTickEvent.Post event) {
		var player=Minecraft.getInstance().player;
		if(player==null || GunActions.firearm(player.getMainHandItem())==null) reset();
	}
}
