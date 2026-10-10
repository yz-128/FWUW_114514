package com.flansmod.client.model;

import com.flansmod.client.render.FmumMuzzlePose;
import com.flansmod.common.types.GunType;

/** Shot-only visual state; mirrors EFT GunAnimations' alpha *= muzzleFlashVanishConst. */
public final class FmumShotEffects {
	public GunType gun;
	public float alpha,lastAlpha,fade,roll;
	public int variant;
	private int pendingSmoke,age;
	public record Ejection(GunType gun,com.flansmod.common.types.BulletType round,boolean manual,long seed,int expires) { }
	private final java.util.Deque<Ejection> pendingCases=new java.util.ArrayDeque<>();
	private int caseClock;
	public void requestCase(GunType gun,com.flansmod.common.types.BulletType round,boolean manual,long seed) {
		if(gun.itsType==GunType.GunItsType.MAG || round.bulletModel==null || round.bulletTexture==null)return;
		if(pendingCases.size()>=32)pendingCases.removeFirst();
		pendingCases.addLast(new Ejection(gun,round,manual,seed,caseClock+4));
	}
	public java.util.List<Ejection> takeCases(GunType gun) {
		var result=new java.util.ArrayList<Ejection>();
		pendingCases.removeIf(e->{if(e.gun==gun && e.expires>=caseClock)result.add(e);return true;});
		return result;
	}
	public void shoot(GunType type,FmumMuzzlePose.Flash flash,int texture,float rotation,boolean smoke) {
		if(type.itsType==GunType.GunItsType.MAG)return;
		if(gun!=type)reset();
		gun=type;age=0;fade=flash==null?0F:flash.fade();variant=texture;roll=rotation;
		// A confirmed shot flashes immediately, including frames before the next client tick.
		// Interpolating from the previous dark frame made the first frame fade in instead.
		alpha=lastAlpha=fade>0?1F:0F;
		if(smoke)pendingSmoke=Math.min(32,pendingSmoke+1);
	}
	public void tick() {
		caseClock++;pendingCases.removeIf(e->e.expires<caseClock);
		lastAlpha=alpha;alpha*=fade;
		if(++age>4)pendingSmoke=0;
	}
	public float alpha(float partial) { return Math.max(0F,Math.min(1F,lastAlpha+(alpha-lastAlpha)*partial)); }
	public int takeSmoke(GunType type) {
		if(type!=gun)return 0;
		int count=pendingSmoke;pendingSmoke=0;return count;
	}
	public void reset() { gun=null;alpha=lastAlpha=fade=roll=0F;variant=pendingSmoke=age=caseClock=0;pendingCases.clear(); }
}
