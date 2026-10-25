package com.flansmod.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.flansmod.common.FlansMod;

/**
 * A gun lying on the ground, ported from 1.7.10's {@code EntityGunItem}.
 *
 * <p>The original was an {@code EntityItem} subclass, so it inherited the whole item-drop physics
 * and the merged-stack behaviour, and then spent most of its own code on carrying a gun plus a
 * magazine and on swapping that bundle with a held gun. The port only needs the dropped item part
 * so far: the stack travels in synched data (the client has no other way to learn what was
 * dropped), and the entity is a plain {@link Entity} with the original's fall rules spelled out
 * below — gravity, ground friction, and a ten-minute life — so the pickup paths stay contained in
 * one small class instead of coming with vanilla's item-merge and hopper machinery.
 *
 * <p>What is deliberately not here yet: the 1.7.10 ammo bundle carried alongside the gun, the
 * "right-click with a held gun to swap" behaviour, and the Teams loot hooks. Those belong with the
 * loot/teams work; this is the part throwing a gun off the hotbar needs.
 */
public class EntityGunItem extends Entity
{
	/** How long a dropped gun waits before it is discarded, in ticks; the original's ten minutes. */
	public static final int LIFETIME = 6000;

	/** The original's per-tick fall acceleration ({@code motionY -= 0.04}). */
	private static final double GRAVITY = 0.04D;

	/** Air drag, and the faster rub the ground applies so a slid gun settles quickly. */
	private static final double AIR_FRICTION = 0.98D;
	private static final double GROUND_FRICTION = 0.6D;

	/** Below this squared speed a gun on the ground is treated as stopped. */
	private static final double REST_SPEED_SQR = 1.0E-4D;

	private static final String TAG_ITEM = "Item";
	private static final String TAG_AGE = "Age";
	private static final String TAG_PICKUP_DELAY = "PickupDelay";

	/**
	 * The dropped stack, synced to the client.
	 *
	 * <p>The spawn packet carries only the entity type, and a content pack's definitions are data
	 * the client cannot derive from that, so the stack has to travel. 1.7.10's {@code EntityItem}
	 * did the same thing with its data watcher item slot; modern Minecraft names that slot
	 * {@link EntityDataSerializers#ITEM_STACK}.
	 */
	private static final EntityDataAccessor<ItemStack> DATA_STACK =
			SynchedEntityData.defineId(EntityGunItem.class, EntityDataSerializers.ITEM_STACK);

	/** Ticks this gun has existed, the original's {@code age}. Persisted so a reload does not reset it. */
	private int age;

	/**
	 * Ticks before a thrown gun can be collected again.
	 *
	 * <p>A tossed item spawns inside the thrower's own hitbox, so without this the same player
	 * would pick it straight back up on the next tick and the gun could never be dropped. Vanilla's
	 * {@code ItemEntity} uses the same two-second delay.
	 */
	private int pickupDelay;

	public EntityGunItem(EntityType<? extends EntityGunItem> entityType, Level level)
	{
		super(entityType, level);
		// The original's throw gave an item a random heading, so two guns dropped together do not
		// lie at exactly the same angle.
		setYRot(random.nextFloat() * 360F);
		yRotO = getYRot();
	}

	/**
	 * The throwing constructor: where the gun leaves the player and what it is.
	 *
	 * <p>The small random velocity is the original {@code EntityItem} constructor's; the thrower
	 * normally overrides it immediately with the player's own throw vector.
	 */
	public EntityGunItem(Level level, double x, double y, double z, ItemStack stack)
	{
		this(FlansMod.GUN_ITEM.get(), level);
		setPos(x, y, z);
		setDeltaMovement(level.random.nextDouble() * 0.2D - 0.1D, 0.2D,
				level.random.nextDouble() * 0.2D - 0.1D);
		setItemStack(stack);
		setPickupDelay(40);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder)
	{
		builder.define(DATA_STACK, ItemStack.EMPTY);
	}

	/** The original's gravity; {@link Entity#applyGravity()} reads it every moving tick. */
	@Override
	protected double getDefaultGravity()
	{
		return GRAVITY;
	}

	/** The gun this entity stands for, or {@link ItemStack#EMPTY}. */
	public ItemStack getItemStack()
	{
		return entityData.get(DATA_STACK);
	}

	/** Sets the displayed stack. The entity keeps its own copy, so the caller's stack stays its own. */
	public void setItemStack(ItemStack stack)
	{
		entityData.set(DATA_STACK, stack == null ? ItemStack.EMPTY : stack.copy());
	}

	/**
	 * The original's fall, rub and rest, all on the server.
	 *
	 * <p>The client is not allowed to move this entity: the server owns the position (the stack is
	 * synched data, not a client-side prediction), and a client that simulated the fall would
	 * disagree with it and be corrected every time a packet landed. The position updates are forced
	 * while moving ({@code hasImpulse}) because the registered tracking interval is long enough
	 * that a falling gun would otherwise visibly stutter.
	 */
	@Override
	public void tick()
	{
		super.tick();

		if(level().isClientSide)
			return;

		if(getItemStack().isEmpty())
		{
			discard();
			return;
		}

		if(++age >= LIFETIME)
		{
			discard();
			return;
		}

		if(pickupDelay > 0)
			pickupDelay--;

		// A gun at rest stays exactly where it landed, but the ground is re-checked periodically:
		// that is what lets a gun fall again when the block under it is broken, and it is the same
		// periodic move the original inherited from EntityItem.
		boolean atRest = onGround() && getDeltaMovement().equals(Vec3.ZERO);
		boolean groundCheck = atRest && (tickCount + getId()) % 4 == 0;
		if(atRest && !groundCheck)
			return;

		double startX = getX(), startY = getY(), startZ = getZ();

		applyGravity();
		move(MoverType.SELF, getDeltaMovement());

		Vec3 motion = getDeltaMovement();
		double friction = onGround() ? GROUND_FRICTION : AIR_FRICTION;
		double dx = motion.x * friction;
		double dy = motion.y * AIR_FRICTION;
		double dz = motion.z * friction;
		if(onGround())
		{
			// The original bounced the vertical motion (-0.5) here so items hopped to a stop. A
			// dropped gun should just lie there, so the drop is cancelled instead.
			dy = 0;
			if(dx * dx + dz * dz < REST_SPEED_SQR)
			{
				dx = 0;
				dz = 0;
			}
		}
		setDeltaMovement(dx, dy, dz);

		if(getX() != startX || getY() != startY || getZ() != startZ)
			hasImpulse = true;
	}

	/**
	 * Walk-up pickup.
	 *
	 * <p>Called every tick a player overlaps the hitbox, so it must be safe to call repeatedly
	 * while the inventory is full: nothing is consumed or discarded until something actually fits.
	 */
	@Override
	public void playerTouch(Player player)
	{
		if(level().isClientSide)
			return;
		pickUp(player);
	}

	/**
	 * Right-click pickup.
	 *
	 * <p>The client answers {@code SUCCESS} so the click is acknowledged and the hand swings; the
	 * server does the real work and answers {@code CONSUME}, which is the standard split for an
	 * interaction whose effect is a server-side inventory change.
	 */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand)
	{
		if(level().isClientSide)
			return InteractionResult.SUCCESS;
		return pickUp(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
	}

	/**
	 * Hands the stack to {@code player}, dropping whatever does not fit.
	 *
	 * <p>{@code Inventory#add} writes the leftover count back into the stack it is given, so the
	 * remainder after the call is exactly what has to be dropped. If nothing at all fitted the gun
	 * stays on the ground rather than being dropped from the player's feet — dropping it there
	 * would spawn a fresh entity every tick the player stood on the spot and never pick anything up.
	 *
	 * @return true when at least part of the stack moved into the inventory
	 */
	private boolean pickUp(Player player)
	{
		if(pickupDelay > 0)
			return false;

		ItemStack stack = getItemStack();
		if(stack.isEmpty())
		{
			discard();
			return false;
		}

		int count = stack.getCount();
		ItemStack remaining = stack.copy();
		player.getInventory().add(remaining);
		if(remaining.getCount() == count)
			return false;

		if(!remaining.isEmpty())
			player.drop(remaining, false);

		discard();
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
				0.2F, (random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F);
		return true;
	}

	/** Ticks left before the gun can be picked up; 0 means it can be collected now. */
	public int getPickupDelay()
	{
		return pickupDelay;
	}

	/** How long the gun stays uncollectable, so a thrower does not catch their own drop. */
	public void setPickupDelay(int ticks)
	{
		pickupDelay = ticks;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag)
	{
		if(tag.contains(TAG_ITEM))
			setItemStack(ItemStack.parseOptional(registryAccess(), tag.getCompound(TAG_ITEM)));
		age = tag.getInt(TAG_AGE);
		pickupDelay = tag.getInt(TAG_PICKUP_DELAY);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag)
	{
		// An empty stack has no encoding: only a gun with a real item is written, exactly as
		// vanilla's ItemEntity guards its own item tag.
		if(!getItemStack().isEmpty())
			tag.put(TAG_ITEM, getItemStack().save(registryAccess()));
		tag.putInt(TAG_AGE, age);
		tag.putInt(TAG_PICKUP_DELAY, pickupDelay);
	}

	/** The drop is the pickup target: without this the client never ray-traces it for {@code interact}. */
	@Override
	public boolean isPickable()
	{
		return true;
	}

	/** Hitting a dropped gun should not swing at it; it is picked up, not fought. */
	@Override
	public boolean isAttackable()
	{
		return false;
	}

	@Override
	public boolean isPushable()
	{
		return false;
	}

	/** 1.7.10's dropped guns never showed the burning overlay; they cannot be destroyed by fire. */
	@Override
	public boolean fireImmune()
	{
		return true;
	}
}
