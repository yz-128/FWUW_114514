package com.flansmod.client.model;

/**
 * The gun animation state the renderer reads, ported from 1.7.10's class of the same name.
 *
 * <p>This is the bridge between the 20 Hz game state and the much faster frame rate. Every
 * quantity that moves is stored twice — {@code thing} and {@code lastThing} — so the renderer can
 * draw at {@code last + (now - last) * partialTick} instead of at the last whole tick. That is why
 * {@code RenderGun} is full of a {@code smoothing} variable; this is where it comes from.
 *
 * <p>It is also a small state machine of its own, but a purely cosmetic one: {@code pumping},
 * {@code reloading} and the melee counter advance on their own schedule and mean nothing to the
 * server. 1.7.10 kept one instance per player per hand, so the two hands animated independently.
 */
public class GunAnimations
{
	public final FmumShotEffects effects=new FmumShotEffects();
	/** A shared do-nothing instance, used for entities that have no animation state of their own. */
	public static final GunAnimations DEFAULTS = new GunAnimations();
	public final FmumRecoil fmumRecoil = new FmumRecoil();

	// ---------------------------------------------------------------- slide / recoil

	/** Recoil slide, 1 on firing and decaying afterwards. */
	public float gunSlide = 0F, lastGunSlide = 0F;
	private boolean fmumSlide;
	private float slideVelocity,slideAcceleration,slideProportional;

	/**
	 * The whole-gun kick: how far the weapon has travelled back, 0 at rest.
	 *
	 * Compatibility state for the renderer's non-FMUM fallback. EFT uses {@link #fmumRecoil}.
	 */
	public float gunRecoil = 0F, lastGunRecoil = 0F;
	/** How much one shot adds to {@link #gunRecoil}. */
	public float recoilAmount = 0.33F;

	// ---------------------------------------------------------------- pumping

	/** Ticks to wait before the pump starts, and how long it then takes. */
	public int timeUntilPump = 0, timeToPumpFor = 0;
	/** Pump position: -1 at rest, 0 mid-stroke, rising to 1 as it returns. */
	public float pumped = -1F, lastPumped = -1F;
	public boolean pumping = false;

	// ---------------------------------------------------------------- reloading

	public boolean reloading = false;
	/** Duration of the reload animation in ticks. */
	public float reloadAnimationTime = 0;
	/** Reload animation progress, 0 to 1, with its previous-tick value for interpolation. */
	public float reloadAnimationProgress = 0F, lastReloadAnimationProgress = 0F;

	// ---------------------------------------------------------------- minigun

	public float minigunBarrelRotation = 0F;
	public float minigunBarrelRotationSpeed = 0F;

	// ---------------------------------------------------------------- melee

	/** Current frame of a melee swing and how many it lasts, in ticks. */
	public int meleeAnimationProgress = 0, meleeAnimationLength = 0;

	/**
	 * Advances FMUM's recoil retention and accelerated slide once per tick. The non-FMUM
	 * fallback retains its existing slide/minigun animation.
	 */
	public void update()
	{
		effects.tick();
		fmumRecoil.tick();
		lastPumped = pumped;

		if(timeUntilPump > 0)
		{
			timeUntilPump--;
			if(timeUntilPump == 0)
			{
				// Fire the delayed action — a pump, or the clip being worked loose.
				pumping = true;
				lastPumped = pumped = -1F;
			}
		}

		if(pumping)
		{
			pumped += 2F / timeToPumpFor;
			if(pumped >= 0.999F)
				pumping = false;
		}

		lastGunSlide = gunSlide;
		if(fmumSlide && (gunSlide>0F || slideVelocity>0F)) {
			slideVelocity+=slideAcceleration+slideProportional*gunSlide;
			gunSlide=Math.max(0F,gunSlide-slideVelocity);
			if(gunSlide==0F) slideVelocity=0F;
		} else if(gunSlide > 0F) gunSlide *= .4F;

		// The whole-gun kick. The 0.7 decay is the 1.20.1 build's, and it is deliberately slower
		// than the slide's 0.4: the slide snaps back, the kick settles. Using one factor for both
		// makes a rifle feel like a pistol.
		lastGunRecoil = gunRecoil;
		if(gunRecoil > 0F)
			gunRecoil *= 0.7F;

		lastReloadAnimationProgress = reloadAnimationProgress;
		if(reloading)
			reloadAnimationProgress += 1F / reloadAnimationTime;
		if(reloading && reloadAnimationProgress >= 1F)
			reloading = false;

		minigunBarrelRotation += minigunBarrelRotationSpeed;
		minigunBarrelRotationSpeed *= 0.9F;

		if(meleeAnimationLength > 0)
		{
			meleeAnimationProgress++;
			if(meleeAnimationProgress == meleeAnimationLength)
				meleeAnimationProgress = meleeAnimationLength = 0;
		}
	}

	/** A shot was fired. */
	public void doShoot(int pumpDelay, int pumpTime)
	{
		fmumSlide=false;
		minigunBarrelRotationSpeed += 2F;
		lastGunSlide = gunSlide = 1F;

		// Accumulating rather than assigning is the whole reason automatic fire climbs: each round
		// adds to what is left of the last kick, so the gun walks back while the trigger is held
		// and settles when it is released.
		lastGunRecoil = gunRecoil = gunRecoil + recoilAmount;

		timeUntilPump = pumpDelay;
		timeToPumpFor = pumpTime;
	}
	public void doShoot(com.flansmod.common.GunStats.Shot shot,com.flansmod.client.model.loader.ModelGeometry.Model model,int pumpDelay,int pumpTime) {
		doShoot(pumpDelay,pumpTime);
		fmumRecoil.shoot(shot,model);
		fmumSlide=true;slideVelocity=0F;
		slideAcceleration=shot.gun().slideAcceleration[0];slideProportional=shot.gun().slideAcceleration[1];
	}

	/** A reload started. */
	public void doReload(int reloadTime, int pumpDelay, int pumpTime)
	{
		reloading = true;
		lastReloadAnimationProgress = reloadAnimationProgress = 0F;
		reloadAnimationTime = reloadTime;
		timeUntilPump = pumpDelay;
		timeToPumpFor = pumpTime;
	}

	/** A melee swing started. */
	public void doMelee(int meleeTime)
	{
		meleeAnimationLength = meleeTime;
	}

	/** Back to rest, for when the gun is put away. */
	public void reset()
	{
		effects.reset();
		fmumRecoil.reset();
		fmumSlide=false;slideVelocity=0F;
		gunSlide = lastGunSlide = 0F;
		gunRecoil = lastGunRecoil = 0F;
		pumped = lastPumped = -1F;
		pumping = false;
		reloading = false;
		reloadAnimationProgress = lastReloadAnimationProgress = 0F;
		meleeAnimationProgress = meleeAnimationLength = 0;
	}
}
