package com.flansmod.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.flansmod.common.types.ItemFlan;

/**
 * A development-only harness for verifying the port on a machine that cannot be driven by hand.
 *
 * <p>It is completely inert unless the JVM is started with {@code -Dflansmod.debugCapture=true},
 * which only the {@code runClientAuto} debug run does. When active it hands the joining player a
 * StG-44 with ammunition, then fires the gun for a short burst and lets go — all through the mod's
 * own API ({@link GunFiring}), never through synthetic input — so the resulting screenshots show
 * the real texture, pose, firing animation and ammo HUD state.
 */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class DebugCapture
{
	public static final boolean ENABLED = Boolean.getBoolean("flansmod.debugCapture");
	/**
	 * Ticks after the player joins at which each step happens. The reload is started first and the
	 * trigger pulled well after it finishes, so the shots fired here are real ones out of a full
	 * magazine.
	 *
	 * <p>The numbers fit the current default kit: the EFT AK-74M's stepped reload sums to 70 ticks,
	 * so a reload started at tick 70 is done by 140 and the trigger at 200 fires into a full gun.
	 */
	public static final int KIT_FROM_TICK = 5, KIT_TO_TICK = 60, RELOAD_AT_TICK = 70,
			FIRE_AT_TICK = 200, RELEASE_AT_TICK = 240, REPORT_AT_TICK = 280;

	/**
	 * Tick at which a stationary round is parked in front of the camera.
	 *
	 * <p>A fired round crosses five blocks a tick, so a screenshot of a real shot catches either
	 * the muzzle flash or nothing. This spawns the same entity at zero speed three blocks ahead —
	 * far enough to be in frame, close enough to read — so the geometry and the texture can
	 * actually be judged. It is a rendering check, not a physics one.
	 */
	public static final int DISPLAY_AT_TICK = 205;

	/**
	 * Tick at which the camera is pushed back so the third-person pose is on screen.
	 *
	 * <p>Third person is a camera mode, and the harness is not allowed to press the key that
	 * switches it — that would be synthetic input. What it can do is put a spectator camera behind
	 * the player through the server, which shows the same thing: the player model, the arm and the
	 * gun as another player sees them.
	 */
	public static final int THIRD_PERSON_AT_TICK = 260;

	/**
	 * Tick at which the compatibility round throws a real grenade.
	 *
	 * <p>Only used with {@code flansmod.compatScenes}: the scene exercises the entity renderer,
	 * the ballistics and the explosion in the same run as the optimisation mods, through the same
	 * API the grenade item uses. Nothing is thrown by an ordinary self-verification run.
	 */
	public static final int GRENADE_AT_TICK = 500;

	/**
	 * Tick at which the compatibility round fits a scope, so the ADS scene goes through the
	 * independent scope camera ({@code ScopeWorldTexture}) and not just the iron-sight pose.
	 */
	public static final int OPTIC_AT_TICK = 430;
	/** After the last captured frame; restores the player's inventory. */
	public static final int COMPAT_END_TICK = 600;
	private static final boolean COMPAT_SCENES = Boolean.getBoolean("flansmod.compatScenes");

	/**
	 * Tick at which every gun's model is baked and reported.
	 *
	 * <p>Baking is what reads the model class and, with it, checks the model's declared texture size
	 * against the skin the pack actually ships. Doing that for the whole pack once turns "some guns
	 * sample the wrong part of their texture" — a defect that is otherwise only visible by eye —
	 * into a list, and it exercises the UV correction for every weapon rather than only the one the
	 * kit hands out.
	 */
	public static final int MODEL_AUDIT_AT_TICK = 275;

	/**
	 * The gun and its magazine the kit hands out.
	 *
	 * <p>Defaults to the EFT pack's AK-74M and one of its 5.45 magazines, because that pack is the
	 * one this port is being verified against and its guns are assembled from attachments — the
	 * old default (a StG-44 from Warfare44) has no attachment tree, so a run with it could not show
	 * whether the parts mount. Override with {@code -Dflansmod.debugGun=<shortName>
	 * -Dflansmod.debugAmmo=<shortName>}.
	 */
	private static final String DEBUG_GUN = System.getProperty("flansmod.debugGun", "AK74M");
	private static final String DEBUG_AMMO = System.getProperty("flansmod.debugAmmo", "6L20_545x39_30Rounds");

	private DebugCapture() { }

	/** Which tick of the joined session this player is on, and whether the kit was handed out. */
	private static final java.util.Map<java.util.UUID, Integer> TICKS = new java.util.HashMap<>();
	private static final java.util.Set<java.util.UUID> KITTED = new java.util.HashSet<>();

	/**
	 * The player's real inventory, taken before the kit replaces it and put back afterwards.
	 *
	 * <p>The harness has to clear the inventory — the verification world is a copy of a played save
	 * and its own hotbar would otherwise leave the player holding something else. But the same
	 * world is the one being played, and without this the save would quietly lose whatever was in
	 * it. Capturing here and restoring at the end of the run makes the harness non-destructive.
	 */
	private static final java.util.Map<java.util.UUID, java.util.List<ItemStack>> SAVED_INVENTORY =
			new java.util.HashMap<>();
	private static final java.util.Map<java.util.UUID, Integer> SAVED_SLOT = new java.util.HashMap<>();

	@SubscribeEvent
	public static void onJoin(PlayerEvent.PlayerLoggedInEvent event)
	{
		if(!ENABLED || !(event.getEntity() instanceof ServerPlayer player))
			return;
		TICKS.put(player.getUUID(), 0);
		KITTED.add(player.getUUID());
		takeInventorySnapshot(player);
		FlansMod.LOGGER.info("[DebugCapture] Joined; kit will be handed out on the first tick.");

		// Daylight, for the screenshots. The verification world is a copy of a played save and the
		// last session left it at night, which makes every frame a black silhouette and the gun
		// impossible to judge. This sets the server's own clock — no input is involved, and the
		// weather is cleared for the same reason.
		player.serverLevel().setDayTime(6000L);
		player.serverLevel().setWeatherParameters(6000, 0, false, false);
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event)
	{
		if(!ENABLED || !(event.getEntity() instanceof ServerPlayer player))
			return;
		java.util.UUID id = player.getUUID();
		if(!KITTED.contains(id))
			return;
		int tick = TICKS.merge(id, 1, Integer::sum);

		// The world's own saved inventory is applied to the player during the first seconds of the
		// join and lands after the first tick — so the kit is written on every tick early in the
		// session, and whichever write is last is guaranteed to be ours.
		if(tick >= KIT_FROM_TICK && tick <= KIT_TO_TICK)
		{
			giveKit(player);
			if(tick == KIT_TO_TICK)
				FlansMod.LOGGER.info("[DebugCapture] Kit locked in: " + describeMagazine(player));
			return;
		}
		if(tick == RELOAD_AT_TICK)
		{
			giveKit(player);
			GunFiring.requestReload(player, false);
			FlansMod.LOGGER.info("[DebugCapture] Reload started. Chambers: " + describeMagazine(player));
			return;
		}
		if(tick == FIRE_AT_TICK)
		{
			ItemStack held = player.getMainHandItem();
			if(held.getItem() instanceof com.flansmod.common.types.ItemFlan)
			{
				placeTarget(player);
				FlansMod.LOGGER.info("[DebugCapture] Trigger down. Chambers before: "
						+ describeMagazine(player));
				GunFiring.serverTrigger(player, true, true);
			}
			return;
		}
		if(tick == RELEASE_AT_TICK)
		{
			GunFiring.serverTrigger(player, true, false);
			FlansMod.LOGGER.info("[DebugCapture] Trigger up. Chambers after: "
					+ describeMagazine(player));
			return;
		}
		if(tick == REPORT_AT_TICK)
		{
			FlansMod.LOGGER.info("[DebugCapture] Final. Held=" + player.getMainHandItem().getItem()
					+ " chambers=" + describeMagazine(player));
			reportTarget(player);
			// The compatibility scenes run after this report and need both the tick clock and the
			// kit, so the inventory is restored at the end of the compat run instead. An ordinary
			// run stops here exactly as before.
			if(!COMPAT_SCENES)
			{
				restoreInventorySnapshot(player);
				KITTED.remove(id);          // one report per session
			}
			return;
		}
		if(tick == DISPLAY_AT_TICK)
		{
			spawnDisplayRound(player);
			return;
		}
		if(COMPAT_SCENES && tick == OPTIC_AT_TICK)
		{
			compatOptic(player);
			return;
		}
		if(COMPAT_SCENES && tick == GRENADE_AT_TICK)
		{
			compatGrenade(player);
			return;
		}
		if(COMPAT_SCENES && tick == COMPAT_END_TICK)
		{
			// The scenes are over: put the player's own inventory back, exactly what an ordinary
			// run does at its report, and stop the clock.
			restoreInventorySnapshot(player);
			KITTED.remove(id);
			FlansMod.LOGGER.info("[DebugCompat] compat session finished, inventory restored");
			return;
		}
		if(tick == THIRD_PERSON_AT_TICK)
		{
			// The third-person pose cannot be photographed from inside this client: it is what
			// OTHER players see, and switching this one's camera needs a key press. Rather than
			// fake that, the run reports the numbers the pose is built from, and the picture is
			// taken offline by the preview renderer — which drives the same transform chain.
			reportThirdPersonPose(player);
			return;
		}

		// Rounds in flight are counted for a while after the trigger, so the log says whether the
		// shots that were fired actually produced entities — firing and spawning are separate
		// steps and a screenshot alone cannot tell an invisible round from one that never existed.
		if(tick > FIRE_AT_TICK && tick <= RELEASE_AT_TICK && tick % 10 == 0)
			FlansMod.LOGGER.info("[DebugCapture] Rounds in flight at tick " + tick + ": " + countRounds(player));
	}

	/**
	 * Reports what the third-person pose is built from.
	 *
	 * <p>Worth logging because every input to it is data: the model's own offset comes from the
	 * pack, and the rest is fixed. A wrong offset shows up here as a number that differs from the
	 * reference rather than only as a gun floating beside a hand in someone else's screenshot.
	 */
	private static void reportThirdPersonPose(ServerPlayer player)
	{
		ItemStack held = player.getMainHandItem();
		if(!(held.getItem() instanceof com.flansmod.common.types.ItemFlan flan)
				|| !(flan.type instanceof com.flansmod.common.types.GunType gun))
		{
			FlansMod.LOGGER.info("[DebugCapture] Third-person: no gun held.");
			return;
		}
		var rig = com.flansmod.client.model.loader.ModelCache.rigFor(gun, gun.modelScale);
		FlansMod.LOGGER.info("[DebugCapture] Third-person pose for " + gun.shortName
				+ ": R_y(90) · T(-0.08, -0.12, 0) · T("
				+ (rig == null ? "?" : rig.thirdPersonOffsetX + ", " + rig.thirdPersonOffsetY
						+ ", " + rig.thirdPersonOffsetZ)
				+ ")  [modelScale=" + gun.modelScale + "]");
	}

	/** A dummy standing where the player is looking, so the shots have something to hit. */
	private static com.flansmod.common.DebugCapture.Target TARGET = null;

	/** Where the dummy was spawned and what it had for health, for the report at the end. */
	public record Target(net.minecraft.world.entity.LivingEntity entity, float initialHealth) { }

	/**
	 * Spawns a dummy eight blocks along the player's view.
	 *
	 * <p>Existence checks need a target, and the harness is not allowed to move the player or the
	 * camera. Putting the dummy where the player already points is the one placement that works
	 * without touching input: the shots then travel through it on their own.
	 */
	private static void placeTarget(ServerPlayer player)
	{
		var level = player.serverLevel();
		var look = player.getLookAngle();
		var at = player.getEyePosition().add(look.scale(8D));
		// An armour stand is the wrong dummy on purpose-built grounds: modern Minecraft only lets a
		// damage source flagged CAN_BREAK_ARMOR_STAND touch one, and a projectile is not, so shots
		// would pass through it no matter how correct the bullet code is. A zombie takes ordinary
		// damage from anything, which is what makes it a usable witness. It is made immobile and
		// silenced so it cannot walk out of the firing line or bite the player between screenshots.
		var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
		if(zombie == null)
		{
			FlansMod.LOGGER.info("[DebugCapture] Could not create the target.");
			return;
		}
		zombie.setPos(at.x, at.y - 1.5D, at.z);
		zombie.setNoAi(true);
		zombie.setSilent(true);
		zombie.setPersistenceRequired();
		zombie.setCustomName(net.minecraft.network.chat.Component.literal("Flan's Mod test dummy"));
		level.addFreshEntity(zombie);
		TARGET = new Target(zombie, zombie.getHealth());
		FlansMod.LOGGER.info("[DebugCapture] Target spawned at " + at + " health=" + zombie.getHealth());
	}

	/** Reports whether the shots reached the dummy, and by how much its health moved. */
	private static void reportTarget(ServerPlayer player)
	{
		if(TARGET == null)
		{
			FlansMod.LOGGER.info("[DebugCapture] No target was placed.");
			return;
		}
		var stand = TARGET.entity();
		FlansMod.LOGGER.info("[DebugCapture] Target result: alive=" + stand.isAlive()
				+ " health=" + stand.getHealth() + " (started " + TARGET.initialHealth() + ")"
				+ " dead=" + stand.isRemoved());
	}

	/** How many of our bullet entities are near the player; the check that firing really spawned them. */
	private static int countRounds(ServerPlayer player)
	{
		return player.serverLevel().getEntitiesOfClass(com.flansmod.common.entity.EntityBullet.class,
				player.getBoundingBox().inflate(64D)).size();
	}

	/**
	 * Puts one round of the held gun's ammunition three blocks ahead, motionless, facing the camera.
	 *
	 * <p>Only ever runs under the debug flag, and only on the server: the client picks it up through
	 * the normal spawn packet, which is the same path a fired round takes.
	 */
	/**
	 * Fits the first compatible scope for the ADS scene.
	 *
	 * <p>The scene has to reach {@code ScopeWorldTexture}'s independent camera, which only runs for a
	 * mounted sight with a scope, so the harness installs one through the same attachment
	 * transaction the gunsmith uses. Reason strings are logged either way: if nothing fits, the ADS
	 * frames are still a valid visual check, just without the extra camera pass.
	 */
	private static void compatOptic(ServerPlayer player)
	{
		ItemStack gunStack = player.getMainHandItem();
		var gun = com.flansmod.common.GunActions.firearm(gunStack);
		if(gun == null)
		{
			FlansMod.LOGGER.info("[DebugCompat] optic scene: no gun in hand");
			return;
		}
		// A short name can appear in more than one pack, and the item registry resolves to one of
		// them; the transaction compares the item's own type, so resolve it the same way here.
		var seen = new java.util.HashSet<String>();
		for(com.flansmod.common.types.InfoType type : com.flansmod.common.types.InfoType.infoTypes)
		{
			if(!(type instanceof com.flansmod.common.types.AttachmentType candidate))
				continue;
			if(!candidate.isAimingSight()
					|| (candidate.zoomLevel <= 1F && candidate.scopeGlassModel == null))
				continue;
			if(!seen.add(candidate.shortName))
				continue;
			ItemStack optic = itemFor(registryPath(candidate.shortName), 1);
			if(optic.isEmpty() || !(com.flansmod.common.types.InfoType.getType(optic)
					instanceof com.flansmod.common.types.AttachmentType part))
				continue;
			int slot = -1;
			for(int s = 0; s < gun.slots.length; s++)
				if(com.flansmod.common.GunAttachments.allowed(gun, s, part)) { slot = s; break; }
			if(slot < 0)
				continue;
			int inventorySlot = -1;
			for(int i = 9; i < 36; i++)
				if(player.getInventory().getItem(i).isEmpty()) { inventorySlot = i; break; }
			if(inventorySlot < 0)
			{
				FlansMod.LOGGER.info("[DebugCompat] optic scene: no free inventory slot");
				return;
			}
			player.getInventory().setItem(inventorySlot, optic);
			String reason = com.flansmod.common.GunAttachmentTransaction.apply(player.getInventory(),
					player.getInventory().selected, com.flansmod.common.GunAttachments.revision(gunStack),
					com.flansmod.common.network.GunModificationPayload.INSTALL, new int[0], slot, inventorySlot,
					0, 0, player.registryAccess());
			FlansMod.LOGGER.info("[DebugCompat] optic scene: " + part.shortName + " on slot " + slot
					+ " -> " + (reason == null ? "installed" : reason));
			if(reason == null)
				return;
			player.getInventory().setItem(inventorySlot, ItemStack.EMPTY);
		}
		FlansMod.LOGGER.info("[DebugCompat] optic scene: no compatible sight could be installed");
	}

	/**
	 * Throws the first throwable pack grenade for the compatibility scene.
	 *
	 * <p>Server-side by design: the entity, its ballistics and the explosion are what the scene is
	 * checking, and {@code Grenades.throwGrenade} is the same entry point the grenade item calls.
	 */
	private static void compatGrenade(ServerPlayer player)
	{
		for(com.flansmod.common.types.InfoType type : com.flansmod.common.types.InfoType.infoTypes)
		{
			if(!(type instanceof com.flansmod.common.types.GrenadeType grenade) || !grenade.canThrow)
				continue;
			ItemStack stack = itemFor(registryPath(grenade.shortName), 1);
			if(stack.isEmpty())
				continue;
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
			var result = com.flansmod.common.Grenades.throwGrenade(player.level(), player, stack, grenade);
			FlansMod.LOGGER.info("[DebugCompat] grenade scene: " + grenade.shortName + " -> " + result);
			return;
		}
		FlansMod.LOGGER.info("[DebugCompat] grenade scene: no throwable grenade in the loaded packs");
	}

	private static void spawnDisplayRound(ServerPlayer player)
	{
		ItemStack held = player.getMainHandItem();
		if(!(held.getItem() instanceof com.flansmod.common.types.ItemFlan flan)
				|| !(flan.type instanceof com.flansmod.common.types.GunType gun) || gun.ammo.isEmpty())
		{
			FlansMod.LOGGER.info("[DebugCapture] No gun or no ammunition; no display round.");
			return;
		}

		var level = player.serverLevel();
		var look = player.getLookAngle();
		var origin = player.getEyePosition().add(look.scale(3D));
		var bullet = new com.flansmod.common.entity.EntityBullet(level, origin,
				player.getLookAngle(), player, 0F, gun.damage, gun.ammo.get(0), 0F, gun);

		// Zero speed would still be dragged downward by the tick, so the round is held still and
		// its own physics is left to run — over the seconds the screenshots take it would sag.
		// Pinning it is the point: the shape and the texture are what is being looked at.
		bullet.setNoGravity(true);
		bullet.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		level.addFreshEntity(bullet);
		FlansMod.LOGGER.info("[DebugCapture] Display round spawned: type="
				+ gun.ammo.get(0).shortName + " texture=" + gun.ammo.get(0).texture
				+ " model=" + gun.ammo.get(0).modelString + " at " + origin);

		// A second one turned side-on, so the model reads as a shape rather than a dot.
		var sideways = new com.flansmod.common.entity.EntityBullet(level,
				player.getEyePosition().add(look.scale(3D)).add(0.6D, 0D, 0D),
				new net.minecraft.world.phys.Vec3(1D, 0D, 0D), player, 0F, gun.damage,
				gun.ammo.get(0), 0F, gun);
		sideways.setNoGravity(true);
		sideways.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		level.addFreshEntity(sideways);
	}

	/**
	 * The gun plus a magazine, straight into the hotbar selection, so the hand renders it.
	 *
	 * <p>The whole inventory is cleared first: the verification world is a copy of a played save,
	 * and its own hotbar would otherwise leave the player holding whatever was there before —
	 * which is exactly what happened the first time this ran.
	 */
	private static void giveKit(ServerPlayer player)
	{
		player.getInventory().clearContent();
		ItemStack gun = itemFor(registryPath(DEBUG_GUN), 1);

		// The ammunition has to be a ROUND item (an item whose definition is a BulletType): that
		// is what the chamber stores and what the shot is built from. A pack's magazine item is a
		// separate definition that holds rounds, and the magazine-as-item flow is not ported yet —
		// handing one out would load the gun and then fire nothing. So the kit gives loose rounds,
		// chosen from the gun's own resolved ammunition list, which is the same list the reload
		// searches. The configured name is honoured when it resolves to a round; otherwise the
		// gun's first accepted round is used.
		ItemStack ammo = itemFor(registryPath(DEBUG_AMMO), 1);
		if(!isRound(ammo) && gun.getItem() instanceof ItemFlan gunFlan
				&& gunFlan.type instanceof com.flansmod.common.types.GunType gunType
				&& !gunType.ammo.isEmpty())
		{
			com.flansmod.common.types.BulletType round = gunType.ammo.get(0);
			ammo = itemFor(registryPath(round.shortName), 1);
		}
		if(!ammo.isEmpty())
			ammo.setCount(Math.min(ammo.getMaxStackSize(), 64));

		// The gun goes into whichever hotbar slot the CLIENT has selected. The selection is owned
		// by the client (it tells the server, not the other way round), so moving the server's
		// `selected` field here would leave the player looking at an empty hand while the shotgun
		// sat one slot over — which is exactly what the first attempt did.
		int slot = Math.max(0, Math.min(8, player.getInventory().selected));
		if(!gun.isEmpty())
			player.getInventory().setItem(slot, gun);
		int ammoSlot = (slot + 1) % 9;
		if(!ammo.isEmpty())
			player.getInventory().setItem(ammoSlot, ammo);
		FlansMod.LOGGER.info("[DebugCapture] Kit: gun=" + DEBUG_GUN + "(" + !gun.isEmpty() + ") ammo="
				+ ammo.getItem() + " x" + ammo.getCount() + " slot=" + slot);
	}

	/** True when the stack is an ammunition ROUND — the only kind a chamber can hold. */
	private static boolean isRound(ItemStack stack)
	{
		return !stack.isEmpty() && stack.getItem() instanceof ItemFlan flan
				&& flan.type instanceof com.flansmod.common.types.BulletType;
	}

	/**
	 * A definition's ShortName folded into its registry path.
	 *
	 * <p>The registry name is the lower-cased ShortName with anything outside {@code [a-z0-9_./-]}
	 * replaced by an underscore — the same rule {@code ContentRegistry.registryPath} applies when
	 * the item is registered. The kit names guns by their pack ShortName because that is what a
	 * person reads in the definition file.
	 */
	private static String registryPath(String shortName)
	{
		return shortName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
	}

	/** Copies the whole inventory aside, with the selected slot, before the kit replaces it. */
	private static void takeInventorySnapshot(ServerPlayer player)
	{
		java.util.List<ItemStack> saved = new java.util.ArrayList<>();
		var inventory = player.getInventory();
		for(int i = 0; i < inventory.getContainerSize(); i++)
			saved.add(inventory.getItem(i).copy());
		SAVED_INVENTORY.put(player.getUUID(), saved);
		SAVED_SLOT.put(player.getUUID(), inventory.selected);
		FlansMod.LOGGER.info("[DebugCapture] Saved the existing inventory (" + saved.size() + " slots).");
	}

	/** Puts the player's own items back, so a debug run does not eat the save's contents. */
	private static void restoreInventorySnapshot(ServerPlayer player)
	{
		java.util.List<ItemStack> saved = SAVED_INVENTORY.remove(player.getUUID());
		if(saved == null)
			return;
		var inventory = player.getInventory();
		inventory.clearContent();
		for(int i = 0; i < saved.size() && i < inventory.getContainerSize(); i++)
			inventory.setItem(i, saved.get(i));
		Integer slot = SAVED_SLOT.remove(player.getUUID());
		if(slot != null)
			inventory.selected = slot;
		player.containerMenu.broadcastChanges();
		FlansMod.LOGGER.info("[DebugCapture] Restored the player's own inventory.");
	}

	private static ItemStack itemFor(String path, int count)
	{
		net.minecraft.resources.ResourceLocation id =
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, path);
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
		return item == null || item == net.minecraft.world.item.Items.AIR
				? ItemStack.EMPTY : new ItemStack(item, count);
	}

	/** The rounds currently held, for the log line that proves the shot was spent. */
	private static String describeMagazine(ServerPlayer player)
	{
		ItemStack held = player.getMainHandItem();
		if(!(held.getItem() instanceof com.flansmod.common.types.ItemFlan flan)
				|| !(flan.type instanceof com.flansmod.common.types.GunType gun))
			return "(no gun)";
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < gun.numAmmoItemsInGun; i++)
		{
			ItemStack round = GunMagazine.get(held, gun, i);
			sb.append(round.isEmpty() ? "-"
					: GunMagazine.roundsRemaining(round) + "/"
							+ GunMagazine.roundsPerItem(round)).append(' ');
		}
		return sb.toString();
	}
}
