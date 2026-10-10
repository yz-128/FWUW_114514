package com.flansmod.common.entity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.flansmod.common.FlansMod;
import com.flansmod.common.types.BulletType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;

/**
 * A fired round, ported from 1.7.10's {@code EntityBullet}.
 *
 * <p>The original was a plain {@code Entity} with its own sweeper: each tick it advanced by its
 * motion, tested that segment against the world, then against players and entities, and carried a
 * "penetrating power" budget it spent on whatever it hit. The port keeps the movement exactly —
 * the same drag and gravity, the same spread formula at launch, the same 600-tick life — and hands
 * the segment tests to vanilla's ray tracer, which is also what the 1.20.1 build does.
 *
 * <p>What is deliberately not here yet: driveable hitboxes (there are no driveables to hit),
 * lag-compensated player snapshots, lock-on homing, explosions and flak. Those belong with the
 * vehicle and damage work; this is the part a gun needs to shoot something.
 */
public class EntityBullet extends Projectile
{
	/** How long a round exists before it is discarded, in ticks; the original's 30 seconds. */
	public static final int BULLET_LIFE = 600;

	/**
	 * The definition's {@code ShortName}, synced to the client.
	 *
	 * <p>The spawn packet carries only the entity type id, and a pack's definitions are data the
	 * client cannot derive from that, so the name has to travel. 1.7.10 sent it in
	 * {@code writeSpawnData}; a synced data value is the modern way of saying the same thing.
	 */
	private static final EntityDataAccessor<String> DATA_TYPE_NAME =
			SynchedEntityData.defineId(EntityBullet.class, EntityDataSerializers.STRING);

	/** The definition this round came from, for damage, gravity, the fuse and the renderer. */
	private BulletType type;

	/** The gun definition that fired it; null for rounds placed by anything else. */
	private InfoType firedFrom;

	/** Damage this round carries, already scaled by the gun. */
	private float damage;

	/** Rounds left it can pass through; the original's {@code penetratingPower}. */
	private float penetratingPower = 1F;

	/** Ticks since launch, which gates the shooter's own grace period. */
	private int ticksInAir;

	/** Set once the round has spent its penetration and should stop on the next tick. */
	private boolean detonated;

	public EntityBullet(EntityType<? extends EntityBullet> entityType, Level level)
	{
		super(entityType, level);
	}

	/**
	 * The launch constructor: where the round starts, where it goes, and what the gun lent it.
	 *
	 * <p>1.7.10 had four constructors — handheld, machine gun, plane bomb, generic vector — and
	 * they all funnelled into one private partial constructor. The port keeps a single entry point
	 * and takes a direction vector, which is what all four computed anyway.
	 */
	public EntityBullet(Level level, Vec3 origin, Vec3 direction, LivingEntity shooter,
						float spread, float damage, BulletType type, float speed, InfoType firedFrom)
	{
		super(FlansMod.BULLET.get(), level);
		this.type = type;
		this.firedFrom = firedFrom;
		this.damage = damage;
		this.penetratingPower = type == null ? 1F : type.penetratingPower;
		entityData.set(DATA_TYPE_NAME, type == null || type.shortName == null ? "" : type.shortName);
		setOwner(shooter);
		setPos(origin.x, origin.y, origin.z);
		setArrowHeading(direction, spread, speed);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder)
	{
		builder.define(DATA_TYPE_NAME, "");
	}

	/**
	 * Aims the round down {@code direction} at {@code speed}, with the original's spread.
	 *
	 * <p>1.7.10 normalised the direction, scaled it to the speed, then added one Gaussian per axis
	 * scaled by {@code 0.005 · spread · speed}. The Gaussian is what makes spread feel like a cone
	 * rather than a grid, and both constants are the original's — this is a wider distribution than
	 * the 1.20.1 build uses, and 1.7.10 is the target.
	 */
	public void setArrowHeading(Vec3 direction, float spread, float speed)
	{
		Vec3 dir = direction.normalize().scale(speed);
		double sigma = 0.005D * spread * speed;
		setDeltaMovement(dir.x + random.nextGaussian() * sigma,
				dir.y + random.nextGaussian() * sigma,
				dir.z + random.nextGaussian() * sigma);
		orientToMotion();

		// The original also set prevYaw/prevPitch here, so the round already faces its direction
		// on the frame the client first sees it rather than swinging round from the shooter's yaw.
		xRotO = getXRot();
		yRotO = getYRot();
	}

	@Override
	public void tick()
	{
		// Projectile's own tick runs first: it handles the owner's grace period and the projectile
		// game event, and it advances xo/yo/zo, so the swept test below covers exactly one tick.
		super.tick();

		if(level().isClientSide)
		{
			clientTick();
			return;
		}

		if(detonated)
		{
			discard();
			return;
		}
		// Synced-name updates and NBT loading invalidate the cached definition. Resolve it
		// on the server too, before using the ammo's fuse, hitbox, damage and penetration.
		bulletType();

		ticksInAir++;

		// The fuse: a shell that has flown for its fuse time is done. Rounds with no fuse (the
		// default) skip this entirely, as in the original.
		if(type != null && type.fuse > 0 && ticksInAir > type.fuse)
		{
			discard();
			return;
		}
		if(tickCount > BULLET_LIFE)
		{
			discard();
			return;
		}

		Vec3 motion = getDeltaMovement();
		Vec3 start = position();
		Vec3 end = start.add(motion);

		// Blocks first, then entities — the order the original sorted its hits in, so a round
		// stopped by a wall never reaches whatever stood behind it.
		// Soft-block shapes are skipped within the SAME sweep, so a target behind wood can
		// still be hit this tick while the next stone/metal wall continues to bound entity hits.
		BlockHitResult blockHit = BulletBlockTrace.clip(level(), start, end, this);
		if(blockHit.getType() != HitResult.Type.MISS)
			end = blockHit.getLocation();

		EntityHitResult entityHit = findEntityHit(start, end);
		// Cover AFTER a target must not reduce that target's damage. Only the distance actually
		// flown is integrated; partial blocks continue naturally across subsequent ticks.
		damage = SoftBlockBallistics.remainingDamage(level(), start,
				entityHit == null ? end : entityHit.getLocation(), this, type, damage);
		if(entityHit != null)
		{
			// Stop on the target whether or not the round survives it: 1.7.10 repositioned the
			// bullet at the impact point, and a round that keeps flying from inside a body is how
			// you get hits on the entity behind it.
			setPos(entityHit.getLocation().x, entityHit.getLocation().y, entityHit.getLocation().z);
			hitEntity(entityHit);
			if(!detonated)
				detonated = true;                       // a round that hit a body never flies on in 1.7.10
			return;
		}

		if(blockHit.getType() != HitResult.Type.MISS)
		{
			setPos(blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z);
			hitBlock(blockHit.getBlockPos());
			return;
		}

		setPos(end.x, end.y, end.z);

		// Movement dampening: the original's 0.99 drag (0.8 in water) and its gravity scaled by
		// the definition's FallSpeed. These numbers decide the drop over a distance, so they are
		// kept exactly as written — including water slowing a round down.
		double drag = isInWater() ? 0.8D : 0.99D;
		double gravity = 0.02D * (type == null ? 1F : type.fallSpeed);
		setDeltaMovement(motion.x * drag, motion.y * drag - gravity, motion.z * drag);

		orientToMotion();
	}

	/** The client's share: the trail the definition asks for, and nothing else. */
	private void clientTick()
	{
		if(type != null && type.trailParticles && ticksInAir > 1)
		{
			// The names the packs use; anything else falls back to a plain smoke puff rather than
			// drawing nothing, which is what an unmapped name did in the original too.
			net.minecraft.core.particles.SimpleParticleType particle = switch(
					type.trailParticleType == null ? "" : type.trailParticleType.toLowerCase(java.util.Locale.ROOT))
			{
				case "flame" -> ParticleTypes.FLAME;
				case "bubble" -> ParticleTypes.BUBBLE;
				case "largesmoke" -> ParticleTypes.LARGE_SMOKE;
				default -> ParticleTypes.SMOKE;
			};
			Vec3 back = getDeltaMovement().scale(-0.1D);
			level().addParticle(particle, getX() + back.x, getY() + back.y, getZ() + back.z, 0D, 0D, 0D);
		}
		ticksInAir++;
	}

	/** The first entity whose box the swept path crosses. */
	private EntityHitResult findEntityHit(Vec3 start, Vec3 end)
	{
		double reach = type == null ? 0.5D : type.hitBoxSize;
		List<Entity> candidates = level().getEntities(this, new AABB(start, end).inflate(reach));
		EntityHitResult best = null;
		double bestDistance = Double.MAX_VALUE;

		for(Entity candidate : candidates)
		{
			// A shooter never hits themselves — 1.7.10 skipped the owner outright for players and
			// for a grace period for driveables. The driveable half has nothing to act on yet, so
			// the rule kept here is the one that matters: never hit the owner, never hit what the
			// owner is riding for the first second.
			if(candidate == getOwner())
				continue;
			if(ticksInAir < 20 && getOwner() != null && candidate == getOwner().getVehicle())
				continue;
			if(candidate instanceof Player player && (player.isSpectator() || player.isCreative()))
				continue;
			if(!candidate.isAlive() || candidate.isRemoved() || !candidate.canBeHitByProjectile())
				continue;

			var clip = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
			if(clip.isPresent())
			{
				double distance = start.distanceToSqr(clip.get());
				if(distance < bestDistance)
				{
					bestDistance = distance;
					best = new EntityHitResult(candidate, clip.get());
				}
			}
		}
		return best;
	}

	/**
	 * Applies the hit and either carries on through the target or stops.
	 *
	 * <p>This is the original's penetration loop in miniature. 1.7.10 spent one point of a budget
	 * per thing hit and kept flying while any was left, which is what makes a rifle round pass
	 * through a Nerf target but not through armour. Only the entity half matters so far.
	 */
	private void hitEntity(EntityHitResult hit)
	{
		Entity target = hit.getEntity();
		Entity owner = getOwner();
		if(target == owner || !target.isAlive())
			return;

		playHitSound();

		// The round's damage is the gun's Damage multiplied by the bullet definition's
		// DamageVsLiving — 1.7.10 wrote "damage * type.damageVsLiving" here, and the split is what
		// lets one gun fire a weak practice round and a strong live one with the same Damage value.
		float hitDamage = damage * (type == null ? 1F : type.damageVsLiving);
		if(owner instanceof LivingEntity livingOwner)
			target.hurt(damageSources().mobProjectile(this, livingOwner), hitDamage);
		else
			target.hurt(damageSources().thrown(this, owner), hitDamage);

		if(target instanceof LivingEntity living)
		{
			// 1.7.10 cancelled most of the target's invulnerability window here, because a fast
			// gun's second round otherwise lands while the first is still being absorbed and the
			// gun feels like it is firing blanks.
			living.invulnerableTime = living.invulnerableDuration / 2;

			if(type != null)
			{
				for(BulletType.HitEffect effect : type.hitEffects)
					living.addEffect(new MobEffectInstance(effect.effect(), effect.duration(), effect.amplifier()));
				if(type.setEntitiesOnFire)
					living.setRemainingFireTicks(20 * 5);
			}
		}

		penetratingPower -= 1F;
		if(penetratingPower <= 0F)
			detonated = true;
	}

	/** An obstructing block was hit after the requested soft-block trace filter. */
	private void hitBlock(BlockPos pos)
	{
		if(type != null && type.breaksGlass)
		{
			var state = level().getBlockState(pos);
			// 1.7.10 tested the block's material for glass. The tag is the modern equivalent and
			// covers stained glass and panes, which carry the same material.
			if(state.is(BlockTags.IMPERMEABLE) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE))
			{
				level().destroyBlock(pos, false);
				level().playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1F, 1F);
			}
		}
		detonated = true;
	}

	/** Plays the impact sound the definition names, if it names one. */
	private void playHitSound()
	{
		SoundEvent sound = impactSound();
		if(sound != null && type.hitSoundEnable)
			level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL,
					10F, 1F/(getRandom().nextFloat()*.4F+.8F));
	}

	/** Points the entity along its current motion, as the original recalculated its angles each tick. */
	private void orientToMotion()
	{
		Vec3 motion = getDeltaMovement();
		double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		float yaw = (float)Math.toDegrees(Mth.atan2(motion.x, motion.z));
		float pitch = (float)Math.toDegrees(Mth.atan2(motion.y, horizontal));

		// The original eased these in (0.2 per tick) to square the smoothing with the client's
		// interpolation. That easing is what the renderer relies on, so it is kept.
		setYRot(yRotO + Mth.wrapDegrees(yaw - yRotO) * 0.2F);
		setXRot(xRotO + Mth.wrapDegrees(pitch - xRotO) * 0.2F);
	}

	// ---------------------------------------------------------------- lookups the renderer and HUD need

	/**
	 * The definition, resolved lazily from the synced short name.
	 *
	 * <p>Lazy because on the client the name arrives with the spawn packet, after the constructor
	 * has run; resolving in the constructor would leave every remote round without a type and
	 * therefore without a model.
	 */
	public BulletType bulletType()
	{
		if(type == null)
		{
			String name = entityData.get(DATA_TYPE_NAME);
			if(!name.isEmpty())
				type = BulletType.getBullet(name);
		}
		return type;
	}

	public InfoType firedFrom()
	{
		return firedFrom;
	}

	/** The round's remaining damage after cover attenuation, for the debug harness. */
	public float damage()
	{
		return damage;
	}

	/** True when the definition wants a full-bright render, which is what tracers set. */
	public boolean hasLight()
	{
		return bulletType() != null && bulletType().hasLight;
	}

	/** The impact sound, or null when the definition names none (or names one no pack ships). */
	public SoundEvent impactSound()
	{
		BulletType resolved = bulletType();
		if(resolved == null || resolved.hitSound == null || resolved.hitSound.isBlank())
			return null;
		return com.flansmod.common.FmumSounds.event(resolved.hitSound,resolved.hitSoundRange);
	}

	/** The texture {@code RenderBullet} bound: the definition's skin, defaulting to the stock one. */
	public ResourceLocation textureLocation()
	{
		BulletType resolved = bulletType();
		String texture = resolved == null ? null : resolved.texture;
		if(texture == null || texture.isBlank())
			texture = "defaultBullet";
		return ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,
				("skins/" + texture + ".png").toLowerCase(java.util.Locale.ROOT));
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
	{
		super.onSyncedDataUpdated(accessor);
		// The name may land after the client-side instance was built, so drop the cached type and
		// let the next lookup re-resolve it.
		if(DATA_TYPE_NAME.equals(accessor))
			type = null;
	}

	// ---------------------------------------------------------------- save / misc

	@Override
	protected void readAdditionalSaveData(CompoundTag tag)
	{
		super.readAdditionalSaveData(tag);
		ticksInAir = tag.getInt("ticksInAir");
		damage = tag.getFloat("damage");
		penetratingPower = tag.getFloat("penetratingPower");
		entityData.set(DATA_TYPE_NAME, tag.getString("bulletType"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag)
	{
		super.addAdditionalSaveData(tag);
		tag.putInt("ticksInAir", ticksInAir);
		tag.putFloat("damage", damage);
		tag.putFloat("penetratingPower", penetratingPower);
		tag.putString("bulletType", entityData.get(DATA_TYPE_NAME));
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

	/** Rounds do not burn; the original had to override this for the same reason. */
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

	/**
	 * The fire radius: air pockets around the impact become fire.
	 *
	 * <p>Rounds with a fire radius are incendiaries, and 1.7.10 lit the ground they landed on in
	 * {@code setDead}. Only the server does this, and only when the round is actually destroyed.
	 */
	@Override
	public void remove(RemovalReason reason)
	{
		super.remove(reason);
		if(reason.shouldDestroy() && level() instanceof ServerLevel && type != null && type.fireRadius > 0F)
		{
			int r = Mth.ceil(type.fireRadius);
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for(int x = -r; x <= r; x++)
				for(int z = -r; z <= r; z++)
				{
					cursor.set(Mth.floor(getX()) + x, Mth.floor(getY()), Mth.floor(getZ()) + z);
					if(level().isEmptyBlock(cursor) && level().getBlockState(cursor.below()).isSolid())
						level().setBlockAndUpdate(cursor, Blocks.FIRE.defaultBlockState());
				}
		}
	}

	/**
	 * Where a round leaves a shooter: their eye position.
	 *
	 * <p>1.7.10 wrote {@code posY + getEyeHeight()} by hand. Modern Minecraft's no-argument
	 * {@code getEyeHeight()} is deprecated precisely because it no longer tracks the entity's pose
	 * or its eye-height attribute, so the port asks for {@link LivingEntity#getEyePosition()},
	 * which is what vanilla projectiles use and what the pose-dependent value actually is.
	 */
	public static Vec3 muzzleOf(LivingEntity shooter)
	{
		return shooter.getEyePosition();
	}

	/** The gun's damage for this round; both sides read the definition, so they agree. */
	public static float damageFor(GunType gun, InfoType gunType)
	{
		return gun.damage;
	}
}
