package com.flansmod.common;

import com.flansmod.common.types.*;
import net.minecraft.world.item.ItemStack;

/** FMUM's effective states, using the same default attachment tree the port renders. */
public final class GunStats {
	private GunStats() { }
	public record Values(float damage, float speed, float hipSpread, float adsSpread,float shotHipSpread,float shotAdsSpread,
			float pitch, float randomPitch, float yaw, float randomYaw, float delay,
			float rotZ, float rotY, float transXPitch, float transXYaw, float camera) {
		public float rpm() { return delay>0F?1200F/delay:0F; }
	}
	public static Values base(GunType g) {
		return new Values(g.damage,g.bulletSpeed,g.bulletSpread,g.bulletAimingSpread,g.shotGunSpread,g.shotGunAimingSpread,
				g.recoilPitch,g.rndRecoilPitchRange,g.recoilYaw,g.rndRecoilYawRange,g.shootDelay,
				g.modelRecoil[0],g.modelRecoil[1],g.modelRecoil[3],g.modelRecoil[4],g.modelRecoil[5]);
	}
	public static Values effective(GunType g) {
		Values v=base(g);
		for(var group:g.defaultAttachments) for(var node:group) v=attachment(v,node);
		return v;
	}
	private static Values attachment(Values v,ModifiableType.DefaultAttachment node) {
		AttachmentType a=AttachmentType.getAttachment(node.name);
		if(a!=null) v=new Values(v.damage*a.damageMultiplier,v.speed*a.bulletSpeedMultiplier,
				v.hipSpread*a.spreadMultiplier,v.adsSpread*a.spreadMultiplier,
				v.shotHipSpread*a.spreadMultiplier,v.shotAdsSpread*a.spreadMultiplier,
				v.pitch*a.recoilMultiplier,v.randomPitch*a.recoilMultiplier,
				v.yaw*a.recoilYawMultiplier,v.randomYaw*a.recoilYawMultiplier,v.delay,
				v.rotZ*a.modelRecoilMultipliers[0],v.rotY*a.modelRecoilMultipliers[1],
				v.transXPitch*a.modelRecoilMultipliers[2],v.transXYaw*a.modelRecoilMultipliers[3],v.camera*a.modelRecoilMultipliers[4]);
		for(var child:node.children) v=attachment(v,child);
		return v;
	}
	/** Core tryShoot selects pellet spread for multi-projectile ammunition, then ammo's multiplier. */
	public static float firingSpread(Values v,BulletType bullet,boolean aiming) {
		float spread=bullet.numBullets>1?(aiming?v.shotAdsSpread:v.shotHipSpread):(aiming?v.adsSpread:v.hipSpread);
		return spread*(aiming?bullet.aimingBulletSpreadMultiplier:bullet.bulletSpreadMultiplier)/2F;
	}
	public record Shot(GunType gun,Values stats,float pitch,float yaw,boolean crouching) { }
	/** Samples before spending the round, so the shot keeps the seated magazine's modifiers. */
	public static Shot shot(GunType g,ItemStack stack,boolean crouching,float randomPitch,float randomYaw) {
		Values v=effective(g);
		float p=v.pitch+(randomPitch-.5F)*v.randomPitch,y=v.yaw+(randomYaw-.5F)*v.randomYaw;
		if(crouching) { p*=.8F; y*=.8F; }
		GunType mag=GunMagazine.magazineType(stack,g);
		if(mag!=null) { p*=mag.recoilPitch; y*=mag.rndRecoilYawRange; }
		return new Shot(g,v,p,y,crouching);
	}
}
