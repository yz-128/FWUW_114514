package com.flansmod.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.flansmod.common.FlansMod;
import com.flansmod.common.FmumSounds;
import com.flansmod.common.LegacyPotion;
import com.flansmod.common.types.GrenadeType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ItemFlanArmour;

/**
 * A thrown grenade, ported from 1.7.10's {@code EntityGrenade}.
 *
 * <p>The original was a plain {@code Entity} that moved by hand: every tick it advanced by its
 * motion, ray-traced that step against the world, and either detonated, broke glass, bounced or
 * carried on; the definition's fuse and its own smoke cloud drove the rest. The port keeps that
 * whole order — the fuse is still checked before the move, gravity is still applied after it, and
 * the bounce is still the original's reflect-and-rescale — because its numbers are what the packs
 * were balanced against. The segment test itself is handed to vanilla's ray tracer, which is also
 * what {@link EntityBullet} does.
 *
 * <p>What is deliberately not here yet, and stays for the vehicle/utility work: proximity fuses
 * ({@code livingProximityTrigger}/{@code driveableProximityTrigger}/{@code damageToTriggerer}),
 * sticking to throwers and entities ({@code stickToThrower}/{@code stickToEntity}/
 * {@code stickToDriveable}/{@code stickToEntityAfter}), deployable bags ({@code deployableBag}
 * with {@code healAmount}/{@code numUses}/{@code numClips}), motion sensors, remote detonation
 * ({@code remote}), the damage a grenade deals while it flies ({@code hitEntityDamage}/
 * {@code damageVsLiving}), impact fire ({@code fireRadius}), the item dropped on the throw
 * ({@code dropItemOnThrow}), the single-token legacy form of {@code smokeEffect}, and
 * {@code detonateWhenShot}. The definition loader already reads those fields; nothing in this
 * class consumes them yet.
 */
public class EntityGrenade extends Projectile
{
	/**
	 * The definition's {@code ShortName}, synced to the client.
	 *
	 * <p>The spawn packet carries only the entity type id and a pack's definitions are data the
	 * client cannot derive from that, so the name travels with the entity — the same arrangement
	 * {@link EntityBullet} uses. Resolving the name into a {@link GrenadeType} is lazy because on
	 * the client it only arrives after the constructor has run.
	 */
	private static final EntityDataAccessor<String> DATA_TYPE_NAME =
			SynchedEntityData.defineId(EntityGrenade.class, EntityDataSerializers.STRING);

	/**
	 * Whether this grenade has already detonated and is now the smoke cloud.
	 *
	 * <p>1.7.10 kept the entity alive after the bang when the definition had a {@code SmokeTime},
	 * and the client has to know that too: it draws the cloud's particles and stops drawing the
	 * grenade's model. A synced boolean is the smallest way to say it.
	 */
	private static final EntityDataAccessor<Boolean> DATA_SMOKING =
			SynchedEntityData.defineId(EntityGrenade.class, EntityDataSerializers.BOOLEAN);

	/** The definition, resolved from the synced short name on first use. */
	private GrenadeType type;

	/** Ticks since the throw, the original's {@code ticksExisted} for the fuse and PrimeDelay. */
	private int age;

	/** Set once the charge has gone off; stops repeat detonations. */
	private boolean detonated;

	/** Ticks of smoke left after detonation; the entity is discarded when it reaches zero. */
	private int smokeTicks;

	/** Sticky grenades stop dead on the surface they hit; a stuck one does not move. */
	private boolean stuck;
	private BlockPos stuckTo;

	public EntityGrenade(EntityType<? extends EntityGrenade> entityType, Level level)
	{
		super(entityType, level);
	}

	/**
	 * The throw constructor: leaves the thrower's eyes along their look vector.
	 *
	 * <p>1.7.10 :73-99 faced the grenade the way the thrower looked, gave it
	 * {@code 0.5 · ThrowSpeed} along that vector, and recorded the thrower as the owner — the
	 * handle every later rule (explosion attribution, proximity triggers, no-self-hit) reads. The
	 * eye position is what the original wrote by hand as {@code posY + getEyeHeight()}.
	 */
	public EntityGrenade(Level level, GrenadeType type, LivingEntity thrower)
	{
		super(FlansMod.GRENADE.get(), level);
		this.type = type;
		entityData.set(DATA_TYPE_NAME, type == null || type.shortName == null ? "" : type.shortName);
		setOwner(thrower);

		if(thrower != null)
		{
			// 1.7.10 :76 — the grenade starts at the thrower's eye height.
			Vec3 eye = thrower.getEyePosition();
			setPos(eye.x, eye.y, eye.z);

			// 1.7.10 :87-90 — the facing the thrower had. The tumble itself is the renderer's:
			// the original spun a RotatedAxes here, which the port draws from the entity tick.
			setYRot(thrower.getYRot());
			setXRot(thrower.getXRot());
			yRotO = getYRot();
			xRotO = getXRot();

			// 1.7.10 :92-95 — half the definition's ThrowSpeed along the look vector.
			float speed = 0.5F * (type == null ? 1F : type.throwSpeed);
			setDeltaMovement(thrower.getViewVector(1F).scale((double)speed));
		}

		// The definition's HitBoxSize is the original's setSize; the entity type's registered box
		// only applies until the synced name is known.
		refreshDimensions();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder)
	{
		builder.define(DATA_TYPE_NAME, "");
		builder.define(DATA_SMOKING, false);
	}

	// ---------------------------------------------------------------- lookups the renderer and the world need

	/**
	 * The definition, resolved lazily from the synced short name.
	 *
	 * <p>A blank name is how a saved entity survives the pack that defined it being removed, and
	 * the literal "Ashes" marker some content uses for a burnt-out definition is treated the same
	 * way; both mean "nothing to throw". A non-grenade definition with the same short name is
	 * rejected too, so a bullet or a gun can never be mistaken for the throwable that spawned this
	 * entity.
	 */
	public GrenadeType grenadeType()
	{
		if(type == null)
		{
			String name = entityData.get(DATA_TYPE_NAME);
			if(name != null && !name.isBlank() && !name.equalsIgnoreCase("Ashes"))
			{
				InfoType definition = InfoType.getType(name);
				if(definition instanceof GrenadeType grenade)
					type = grenade;
			}
		}
		return type;
	}

	/** Ticks since the throw; HUD/debug and the remote-detonation queue will want it. */
	public int age()
	{
		return age;
	}

	/** True once this grenade has gone off, whether it left a smoke cloud or not. */
	public boolean isDetonated()
	{
		return detonated;
	}

	/** True while the grenade is the smoke cloud rather than a projectile. */
	public boolean smoking()
	{
		return entityData.get(DATA_SMOKING);
	}

	/** True while a sticky grenade is fixed to a block. */
	public boolean isStuck()
	{
		return stuck;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick()
	{
		// Projectile's own tick runs first: it handles the owner's grace period and the projectile
		// game event, and it advances xo/yo/zo so the sweep below covers exactly one tick.
		super.tick();

		if(level().isClientSide)
		{
			clientTick();
			return;
		}

		GrenadeType type = grenadeType();
		if(type == null)
		{
			// A definition that is no longer loaded has nothing to detonate — 1.7.10 did the same.
			discard();
			return;
		}

		age++;

		// 1.7.10 :143-149 — a grenade that outlives its DespawnTime is quietly removed.
		if(type.despawnTime > 0 && age > type.despawnTime)
		{
			discard();
			return;
		}

		// 1.7.10 :171-205 — once detonated, the entity is the smoke cloud: it stops moving, keeps
		// applying its effects and is discarded when the smoke runs out.
		if(smoking())
		{
			applySmoke(type);
			if(--smokeTicks <= 0)
				discard();
			return;
		}

		// 1.7.10 :210-211 — the fuse. detonate() itself enforces PrimeDelay.
		if(GrenadeBallistics.detonatesByFuse(age, type.fuse))
		{
			detonate();
			if(detonated || smoking())
				return;
		}

		// 1.7.10 :245-248 — a sticky grenade drops off when the block it stuck to is gone.
		if(stuck && stuckTo != null && level().isEmptyBlock(stuckTo))
			stuck = false;

		// 1.7.10 :249-370 — the physics, and 1.7.10 :393-398 the held-against-the-thrower variant.
		if(!stuck && !type.stickToThrower)
			moveGrenade(type);
		else if(type.stickToThrower)
			holdOnThrower();

		// 1.7.10 :505-506 — gravity is applied after the move, so it takes effect on the next tick.
		setDeltaMovement(GrenadeBallistics.applyGravity(getDeltaMovement(), type.fallSpeed));
	}

	/** The client's share: the smoke cloud, and nothing else — the server owns the physics. */
	private void clientTick()
	{
		GrenadeType type = grenadeType();
		if(type == null || !smoking())
			return;

		float radius = Math.max(0.5F, type.smokeRadius);
		int count = Math.max(1, Mth.ceil(radius * 0.8F));
		for(int i = 0; i < count; i++)
		{
			double x = getX() + (random.nextDouble() - 0.5D) * 2D * (double)radius;
			double y = getY() + random.nextDouble() * 1.5D;
			double z = getZ() + (random.nextDouble() - 0.5D) * 2D * (double)radius;
			level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0D, 0.02D, 0D);
		}
	}

	/**
	 * One tick of movement, transcribed from 1.7.10 :249-370.
	 *
	 * <p>The original ray-traced from the current position to the next, and on a block hit took
	 * exactly one branch: detonate on impact, break glass, bounce, or (for a definition that
	 * penetrates blocks) carry on. The port keeps that branch order, because a grenade that both
	 * explodes on impact and breaks glass must still explode.
	 */
	private void moveGrenade(GrenadeType type)
	{
		Vec3 motion = getDeltaMovement();
		Vec3 from = position();
		Vec3 next = from.add(motion);

		// 1.7.10 :263 — the swept test for this tick's step.
		BlockHitResult hit = level().clip(new ClipContext(from, next,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if(hit.getType() == HitResult.Type.MISS)
		{
			// 1.7.10 :360-366 — nothing in the way, so move the whole step.
			setPos(next.x, next.y, next.z);
			return;
		}

		BlockPos pos = hit.getBlockPos();

		// 1.7.10 :271-273 — an impact grenade detonates where it touched the world.
		if(type.explodeOnImpact)
		{
			detonate();
			return;
		}

		// 1.7.10 :275-280 — break the glass and stop for this tick; the next tick flies through
		// the hole. The original also checked a teams rule for breaking glass, which the port
		// does not carry a setting for.
		BlockState state = level().getBlockState(pos);
		if(type.breaksGlass && isGlass(state))
		{
			level().destroyBlock(pos, false);
			level().playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1F, 1F);
			return;
		}

		// A definition that names PenetratesBlocks goes through what it meets. 1.7.10 left the
		// position untouched on the hit tick, which would stop it on the same block forever; the
		// port advances the step instead, which is what the field asks for.
		if(type.penetratesBlocks)
		{
			setPos(next.x, next.y, next.z);
			return;
		}

		// 1.7.10 :286-317 — reflect the untravelled part of the step on the face's axis, take the
		// definition's Bounciness out of it and restore the original velocity scale.
		Vec3 bouncedPosition = GrenadeBallistics.bouncePosition(from, hit.getLocation(), motion,
				hit.getDirection(), type.bounciness);
		Vec3 bouncedMotion = GrenadeBallistics.bounce(motion, from, hit.getLocation(),
				hit.getDirection(), type.bounciness);
		setPos(bouncedPosition.x, bouncedPosition.y, bouncedPosition.z);
		setDeltaMovement(bouncedMotion);

		// 1.7.10 :319-327 — the contact gives it a tumble, and a bounce that still carries speed
		// makes the definition's bounce sound.
		if(bouncedMotion.lengthSqr() > 0.01D)
			playGrenadeSound(type.bounceSound, 0F, 1F,
					1.2F / (random.nextFloat() * 0.2F + 0.9F));

		// 1.7.10 :329-357 — a sticky grenade stops dead on the surface it hit.
		if(type.sticky)
		{
			setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
			setDeltaMovement(Vec3.ZERO);
			stuck = true;
			stuckTo = pos.immutable();
		}
	}

	/** 1.7.10 :393-398 — a grenade the definition keeps against its thrower follows them. */
	private void holdOnThrower()
	{
		LivingEntity thrower = getOwner() instanceof LivingEntity living ? living : null;
		if(thrower == null || !thrower.isAlive())
		{
			discard();
			return;
		}
		setPos(thrower.getX(), thrower.getY(), thrower.getZ());
	}

	/** 1.7.10 treated glass as a material; the modern equivalent is the same test bullets use. */
	private static boolean isGlass(BlockState state)
	{
		return state.is(BlockTags.IMPERMEABLE) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE);
	}

	// ---------------------------------------------------------------- detonation

	/**
	 * Detonates the charge: sound, explosion, flash, dropped item, and the smoke that follows.
	 *
	 * <p>1.7.10 :522-626. The early return on {@code PrimeDelay} is the original's "do not
	 * detonate before the grenade is primed"; the {@code detonated} flag is its repeat guard. The
	 * damaging effects are server-only, which is also how the original split them ({@code
	 * !worldObj.isRemote}); the sound is broadcast by the server through {@code Level.playSound}.
	 */
	public void detonate()
	{
		GrenadeType type = grenadeType();
		if(type == null || detonated)
			return;
		if(!GrenadeBallistics.armed(age, type.primeDelay))
			return;
		detonated = true;

		// 1.7.10 :533-534 — the bang. 1.7.10 sent it with PacketPlaySound; playSound on the
		// server reaches every tracking player on its own.
		playGrenadeSound(type.detonateSound, 0F, 4F, 1F);

		// The client must not run the explosion, the hurt effects or the item drop.
		if(!level().isClientSide)
		{
			// 1.7.10 :536-550 — the explosion. The original's custom FlansModExplosion split the
			// damage per target kind; vanilla's explosion is what the port's other content uses.
			//
			// ExplosionBreaksBlocks decides whether the blast may remove blocks, so it belongs in
			// the interaction kind, not in the boolean argument (that one is "start fires"). Passing
			// TNT unconditionally made every grenade — including RGD5/ExplosionBreaksBlocks false —
			// tear through wooden structures.
			if(type.explosionRadius > 0.1F)
				level().explode(this, getX(), getY(), getZ(), type.explosionRadius, false,
						type.explosionBreaksBlocks ? Level.ExplosionInteraction.BLOCK
								: Level.ExplosionInteraction.NONE);

			applyFlash(type);

			// 1.7.10 :579-591 — whatever the definition leaves behind after the bang.
			if(type.dropItemOnDetonate != null && !type.dropItemOnDetonate.isBlank())
				dropDetonationItem(type.dropItemOnDetonate);
		}

		// 1.7.10 :593-602 — a smoke grenade becomes the cloud; anything else is spent at once.
		if(type.smokeTime > 0)
		{
			entityData.set(DATA_SMOKING, true);
			smokeTicks = type.smokeTime;
		}
		else
			discard();
	}

	/**
	 * The smoke cloud's per-tick effects: the definition's potions for everything breathing it.
	 *
	 * <p>1.7.10 :177-200 took every living entity within {@code SmokeRadius}, skipped anyone whose
	 * armour had smoke protection (a gas mask), and applied the definition's {@code SmokeEffect}
	 * entries. The port reads the same {@code SmokeEffect} lines, resolved from the old numeric
	 * ids through {@link LegacyPotion}; the {@code potionEffects} list is honoured too, because
	 * the loader accepts both spellings.
	 */
	private void applySmoke(GrenadeType type)
	{
		if(type.smokeRadius <= 0F)
			return;

		double radiusSqr = (double)type.smokeRadius * (double)type.smokeRadius;
		for(LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
				getBoundingBox().inflate(type.smokeRadius)))
		{
			if(!target.isAlive() || target.isSpectator() || distanceToSqr(target) > radiusSqr
					|| smokeProtected(target))
				continue;

			for(GrenadeType.SmokePotion potion : type.smokePotions)
			{
				Holder<MobEffect> effect = LegacyPotion.byId(potion.id());
				if(effect != null && potion.duration() > 0)
					target.addEffect(new MobEffectInstance(effect, potion.duration(), potion.amplifier()));
			}
			for(var effect : type.potionEffects)
				if(effect.duration() > 0)
					target.addEffect(new MobEffectInstance(effect.effect(), effect.duration(), effect.amplifier()));
		}
	}

	/** 1.7.10 :185-195 — a gas mask anywhere on the body keeps the smoke effects out. */
	private static boolean smokeProtected(LivingEntity target)
	{
		for(EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET})
		{
			ItemStack worn = target.getItemBySlot(slot);
			if(worn.getItem() instanceof ItemFlanArmour armour && armour.armour.smokeProtection)
				return true;
		}
		return false;
	}

	/**
	 * Flashbangs, from 1.7.10 :604-625.
	 *
	 * <p>The original's flash block was commented out, and its three-token {@code FlashEffects}
	 * line was never wired into the type, so the port applies the two effects a flashbang is for
	 * with a {@link MobEffectInstance} and uses the pack's own range, time, damage and sound.
	 */
	private void applyFlash(GrenadeType type)
	{
		if(type.flashBang && type.flashRange > 0F && type.flashTime > 0)
		{
			double rangeSqr = (double)type.flashRange * (double)type.flashRange;
			for(LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
					getBoundingBox().inflate(type.flashRange)))
			{
				if(!target.isAlive() || target.isSpectator() || distanceToSqr(target) > rangeSqr)
					continue;
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, type.flashTime, 0));
				target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, type.flashTime, 0));
				if(type.flashDamageEnable && type.flashDamage > 0)
					target.hurt(level().damageSources().explosion(this, getOwner()), type.flashDamage);
			}
		}

		if(type.flashSoundEnable)
			playGrenadeSound(type.flashSound, type.flashSoundRange, 4F, 1F);
	}

	/**
	 * Drops the definition's {@code DropItemOnDetonate}, e.g. a spent casing or a marker.
	 *
	 * <p>1.7.10 allowed {@code <item>.<damage>} in the line in the same way the rest of its item
	 * references did; the port keeps that parsing and spawns the modern {@link ItemEntity}.
	 */
	private void dropDetonationItem(String name)
	{
		String itemName = name;
		int damage = 0;
		int dot = itemName.indexOf('.');
		if(dot > 0)
		{
			try
			{
				damage = Integer.parseInt(itemName.substring(dot + 1));
			}
			catch(NumberFormatException ignored)
			{
				damage = 0;
			}
			itemName = itemName.substring(0, dot);
		}

		InfoType definition = InfoType.getType(itemName);
		if(definition == null || definition.item == null)
			return;

		ItemStack stack = new ItemStack(definition.item);
		if(damage > 0 && stack.isDamageableItem())
			stack.setDamageValue(Math.min(damage, stack.getMaxDamage() - 1));
		ItemEntity drop = new ItemEntity(level(), getX(), getY() + 0.2D, getZ(), stack);
		drop.setDefaultPickUpDelay();
		level().addFreshEntity(drop);
	}

	/** Plays one of the definition's sounds, if it names one that a pack actually ships. */
	private void playGrenadeSound(String name, float range, float volume, float pitch)
	{
		if(name == null || name.isBlank())
			return;
		SoundEvent event = FmumSounds.event(name, range);
		if(event != null)
			level().playSound(null, getX(), getY(), getZ(), event, SoundSource.PLAYERS, volume, pitch);
	}

	// ---------------------------------------------------------------- save / sync

	@Override
	protected void readAdditionalSaveData(CompoundTag tag)
	{
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_TYPE_NAME, tag.getString("grenadeType"));
		entityData.set(DATA_SMOKING, tag.getBoolean("smoking"));
		age = Math.max(0, tag.getInt("age"));
		detonated = tag.getBoolean("detonated");
		stuck = tag.getBoolean("stuck");
		stuckTo = tag.contains("stuckTo") ? BlockPos.of(tag.getLong("stuckTo")) : null;
		smokeTicks = Math.max(0, tag.getInt("smokeTicks"));
		refreshDimensions();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag)
	{
		super.addAdditionalSaveData(tag);
		tag.putString("grenadeType", entityData.get(DATA_TYPE_NAME));
		tag.putBoolean("smoking", entityData.get(DATA_SMOKING));
		tag.putInt("age", age);
		tag.putBoolean("detonated", detonated);
		tag.putBoolean("stuck", stuck);
		if(stuckTo != null)
			tag.putLong("stuckTo", stuckTo.asLong());
		tag.putInt("smokeTicks", smokeTicks);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
	{
		super.onSyncedDataUpdated(accessor);
		// The name may land after the client-side instance was built, and the box depends on it.
		if(DATA_TYPE_NAME.equals(accessor))
		{
			type = null;
			refreshDimensions();
		}
	}

	// ---------------------------------------------------------------- size / misc

	/** The definition's HitBoxSize is the original's {@code setSize}; 0.35 is the type's default. */
	@Override
	public EntityDimensions getDimensions(Pose pose)
	{
		GrenadeType type = grenadeType();
		float size = type == null ? 0.35F : Mth.clamp(type.hitBoxSize, 0.1F, 2F);
		return EntityDimensions.scalable(size, size);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distanceSq)
	{
		return distanceSq < 128.0D * 128.0D;
	}

	@Override
	public boolean isPickable()
	{
		return false;
	}

	@Override
	public boolean isAttackable()
	{
		return false;
	}

	/** Grenades do not burn; the original overrode this for the same reason. */
	@Override
	public boolean fireImmune()
	{
		return true;
	}

	@Override
	public boolean isPushable()
	{
		return false;
	}

	@Override
	public boolean isPushedByFluid()
	{
		return false;
	}
}
