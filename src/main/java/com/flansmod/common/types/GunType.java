package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import com.flansmod.common.FlansMod;

/**
 * A gun definition, parsed from a pack's {@code guns/*.txt}.
 *
 * <p>Ported key-for-key from 1.7.10's {@code com.flansmod.common.guns.GunType}. Every field
 * the old packs can set is kept, including the ones the port does not act on yet — dropping
 * them would make a re-save of a pack lossy and hide what still has to be wired up.
 *
 * <p>Where the old field name differed between pack eras the loader accepts both spellings,
 * exactly as 1.7.10 did (for instance {@code Accuracy} and {@code Spread}).
 */
public class GunType extends ModifiableType implements Cloneable
{
	private GunType originalDefinition;
	/** Runtime assembly view only; the registered definition always starts at group zero. */
	public int selectedSightGroup;
	/** A read-only assembly view; never runs a constructor or alters the registered definition. */
	public GunType withAttachments(java.util.List<java.util.List<DefaultAttachment>> tree) {
		try {
			GunType view = (GunType)super.clone();
			view.originalDefinition = definition();
			view.defaultAttachments = tree;
			return view;
		} catch(CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
	}
	public GunType definition() { return originalDefinition == null ? this : originalDefinition; }
	/** Ammo this gun accepts, by {@code ShortName}. Resolved to BulletType after all packs load. */
	public final List<String> ammoNames = new ArrayList<>();
	public final List<BulletType> ammo = new ArrayList<>();

	// ---------------------------------------------------------------- the FMUM family
	//
	// The keys below come from the FMUM lineage, which the EFT packs are written for. They are
	// parsed and stored verbatim; the systems that consume them (the magazine item, the stepped
	// reload, the model recoil) are ported on top of the data, not inside the parser.

	/**
	 * What this {@code guns/} file really defines.
	 *
	 * <p>FMUM put magazines in the same folder as guns and distinguished them with
	 * {@code ItsType}. A mag declares its own {@code Ammo}, {@code AmmoCapacity} and
	 * {@code ReloadTimes}, and a gun refers to it by name from its {@code Mag} line — the round
	 * lives in the magazine item, not in the gun. Reading the folder alone would register every
	 * magazine as a firearm.
	 */
	public enum GunItsType { AUTO_GUN, BOLT_GUN, MAG }

	public GunItsType itsType = GunItsType.AUTO_GUN;

	/** Magazine types this gun takes, by ShortName; the gun loads rounds out of them. */
	public final List<String> magazineNames = new ArrayList<>();
	/** Port option: direct loose-round loading is opt-in; magazines still accept loose rounds. */
	public boolean allowDirectAmmoLoading = false;
	/** A magazine's capacity, in rounds. */
	public int ammoCapacity;

	/** The stepped reload: which of FMUM's operations run, in order. */
	public final List<String> reloadSteps = new ArrayList<>();
	/** How long each step takes, in ticks. */
	public final List<Integer> reloadTimes = new ArrayList<>();

	/**
	 * How long a reload actually takes, in ticks.
	 *
	 * <p>Two eras write this differently: the older packs set {@code ReloadTime} directly, while
	 * the FMUM family lists one duration per step of its stepped reload and never sets the single
	 * value. The stepped list's sum is the same quantity — the time the gun is busy — and without
	 * this fallback every FMUM weapon reloads in a single tick, which reads as the animation being
	 * skipped.
	 */
	public float effectiveReloadTime()
	{
		if(reloadTime > 0)
			return reloadTime;
		int total = 0;
		for(int t : reloadTimes)
			total += t;
		return total > 0 ? total : 1F;
	}
	/** The sound each step plays, and how far it carries. */
	public final List<String> reloadSounds = new ArrayList<>();
	public final List<Integer> reloadSoundRanges = new ArrayList<>();
	/** When the push-mag and push-bullet sounds play, as fractions of the step. */
	public float pushMagSoundTime = 0.25F, pushBulletSoundTime = 0F, pullBulletSoundTime = 0F;

	/** A five-number model-recoil variant some EFT packs write; kept as written. */
	public float[] modelRecoilConst;

	/** Whether the pack lets each bullet type override the round count. */
	public boolean allowNumBulletsByBulletType = false;

	/** The ammo-check animation: its length, its two sounds, and what the HUD says at each level. */
	public float checkAmmoTime = 0F;
	public final String[] checkAmmoSound = new String[2];
	public final float[] checkAmmoSoundTime = new float[2];
	public float checkAmmoSoundRange = 8F;
	public final List<AmmoState> checkAmmoStates = new ArrayList<>();

	/** One {@code CheckAmmoState min max text}: what the inspection reads out. */
	public record AmmoState(int min, int max, String text) { }

	/** The chamber check: its two times (normal and bolt-catch), sounds and ranges. */
	public final int[] checkChamberTime = new int[2];
	public final String[] checkChamberSound = new String[4];
	public final float[] checkChamberSoundTime = new float[4];
	public float checkChamberSoundRange = 8F;

	/** Releasing the bolt after a reload: its time, sounds and ranges (index 1 = bolt catch). */
	public int releaseSlideTime = 0;
	public final String[] releaseBoltSound = new String[2];
	public final int[] releaseBoltSoundR = new int[2];

	/** Charging the handle: its time, up to five sounds with ranges, and the delay before it. */
	public int chargeTime = 0, chargeDelay = 7;
	public final String[] chargeSound = new String[6];
	public final int[] chargeSoundR = {8,8,8,8,8,8};

	/** Pose timings: drawing the gun, the run pose, resting and coming out of rest. */
	public int takeOutTime = 16, runPoseTime = 6, restTime = 12, unrestTime = 6;

	/** Folding stock geometry: how far it travels, over how many steps, and the speed while folded. */
	public float foldStockDistance = 0F, stockDistance = 0F, stockFoldedSpeedModifier = 1F;
	public int stockSteps = 0;

	/** Magazine audio volume, and how far the muzzle sits beyond the model origin. */
	public float magVolume = 1F, gunLengthOffset = 0F;

	/** How fast the slide accelerates back and forward, as a fraction per tick. */
	public final float[] slideAcceleration = { .1F, .1F };

	/** Recoil as the packs express it: a pitch, a yaw, and their random ranges. */
	public float recoilPitch = 1F, rndRecoilPitchRange = 1F, recoilYaw = 0F, rndRecoilYawRange = 1F;

	/** The model's own recoil, eight constants; see FMUM's ModelRecoil line. */
	public final float[] modelRecoil = { .45F, 1F, 1.8F, .015F, .01F, .3F, 0F, -.01F };
	/** Per-tick retention: Z/Y/X rotation, X/Y/Z translation (EFT core defaults). */
	public final float[] antiModelRecoil = { .7F, .7F, .7F, .7F, .7F, .7F };

	/** Spread values the FMUM packs set beside the plain one. */
	public float bulletSpreadWLP = 0F, shotGunSpread = 1F;
	public float bulletAimingSpread = 0F, shotGunAimingSpread = 1F, shotGunSpreadWLP = 1F;

	/** Time to aim down the sights, in seconds (FMUM divides it into the pose blend). */
	public float aimTimeConst = 0F, sightSwitchConst = 0F;

	/** The secondary fire's own sound set, when the pack gives one. */
	public String secondaryShootSound;
	public int secondaryShootSoundRange = -1;
	public String lockSlideSound, lockOnSound;
	public int lockSlideSoundRange = 5;

	/** The sound the fire-mode switch makes, and how far it carries. */
	public String modeSwitchSound;
	public int modeSwitchSoundRange = 5;
	public int meleeSoundRange = 5, emptyClickSoundRange = 5;
	public String safeEmptyClickSound;

	/** Behaviours the FMUM packs toggle per gun. */
	public boolean showCrosshair = true, canSetPosition = false, allowRearm = false;
	public String itemUseAction, secondaryFunction;
	public int maxRangeLockOn = 0, canLockOnAngle = 10, lockOnSoundTime = 0;

	/** Custom melee: its length, the hand's path through it, and where damage lands. */
	public boolean useCustomMelee = false, useCustomMeleeWhenShoot = false;
	public int meleeTime = 0;
	public final List<float[]> meleePathNodes = new ArrayList<>();
	public final List<float[]> meleePathAngles = new ArrayList<>();
	public final List<float[]> meleeDamagePoints = new ArrayList<>();

	/** Speed multiplier while aiming, derived from the second token of MoveSpeedModifier. */
	public float aimingMoveSpeed = 1F;

	/** The arm models the gun draws with, and their skins, when the pack ships its own. */
	public String leftArmModel, leftArmTexture, rightArmModel, rightArmTexture;
	/** The magazine follower model, and its skin. */
	public String magFollowerModel, magFollowerTexture;
	public float magFollowerScale = 1F;
	/** The muzzle and chamber smoke models, with FMUM's vanish/scale tuning. */
	public Smoke muzzleSmoke = new Smoke(), chamberSmoke = new Smoke();
	/** The arrow/pointer model the packs ship for aiming. */
	public String arrowModel;
	/** How long the muzzle flash lives, and the skin it draws. */
	public float muzzleFlashVanishConst = 0.2F, muzzleFlashScale = 1F;
	public int numFlashTexture = 1;

	/** One smoke effect's tuning, mirroring FMUM's MuzzleSmokeModel/ChamberSmokeModel line. */
	public static class Smoke
	{
		public float vanishConst, at, scale, scaleEx, freeRotate;
		public int numTexture = 1;
		public String model, texture;
	}

	/**
	 * The gun's own smoke line, whose layout FMUM fixed token by token.
	 *
	 * <p>{@code MuzzleSmokeModel <vanish> <at> <model> <texture> <scale> <scaleEx> <num> <free>}
	 * and {@code ChamberSmokeModel <vanish> <model> <texture> <scale> <scaleEx> <num> <free>} —
	 * they differ only in whether the second number is the distance along the barrel. FMUM read
	 * them from the end, so a short line simply omits the trailing values; that is what the
	 * bounds here repeat.
	 */
	private static void parseSmoke(String[] split, Smoke smoke, boolean muzzle)
	{
		if(split.length < 2)
			return;
		smoke.vanishConst = toFloat(split[1]);
		int i = 2;
		if(muzzle && split.length > i)
			smoke.at = toFloat(split[i++]) / 16F;
		if(split.length > i)
		{
			if(!split[i].equals("None"))
				smoke.model = split[i];
			i++;
		}
		if(split.length > i)
		{
			if(!split[i].equals("None"))
				smoke.texture = split[i];
			i++;
		}
		if(split.length > i)
			smoke.scale = toFloat(split[i++]);
		if(split.length > i)
			smoke.scaleEx = toFloat(split[i++]);
		if(split.length > i)
			smoke.numTexture = toInt(split[i++]);
		if(split.length > i)
			smoke.freeRotate = toBool(split[i]) ? 1F : 0F;
	}

	/** The surface a shot impact uses, when the pack ships one. */
	public String hitTexture;

	public boolean canForceReload = true;
	public int reloadTime;
	public int recoil;
	public float bulletSpread;
	public float damage = 0;
	public float meleeDamage = 1;
	public float bulletSpeed = 5.0F;
	public int numBullets = 1;
	/**
	 * Ticks between rounds.
	 *
	 * <p>A float because packs express the fire rate two ways: 1.7.10's {@code ShootDelay} is in
	 * ticks, but 1.12.2-era packs give {@code RoundsPerMin}, and 1200/RPM rarely lands on a whole
	 * tick. The timer that consumes this is a float for the same reason.
	 */
	public float shootDelay = 0;
	public int numAmmoItemsInGun = 1;	public EnumFireMode mode = EnumFireMode.FULLAUTO;
	/**
	 * Every firing mode the pack declares; {@code Mode FullAuto SemiAuto} lists two.
	 *
	 * <p>The active one is {@link #mode}, which starts as the first listed — 1.7.10 let the player
	 * cycle them with a key, and the HUD shows whether there is anything to cycle. Not modelled
	 * yet beyond that: the port fires in the first mode only.
	 */
	public final List<EnumFireMode> modes = new ArrayList<>();
	public int numBurstRounds = 3;
	public float minigunStartSpeed = 15F;
	public boolean canShootUnderwater = true;
	public float knockback = 0F;
	public boolean oneHanded = false;
	public boolean consumeGunUponUse = false;
	public String dropItemOnShoot = null;

	//Sounds
	public String shootSound;
	public final List<String> shootSounds = new ArrayList<>();
	public int shootSoundLength;
	public boolean distortSound = true;
	public String reloadSound;
	public String meleeSound;
	public String idleSound;
	public int idleSoundLength;
	public boolean useLoopingSounds = false;
	public String warmupSound;
	public int warmupSoundLength = 20;
	public String loopedSound;
	public int loopedSoundLength = 20;
	public String cooldownSound;

	//Scope / zoom
	public String defaultScopeTexture;
	public boolean hasScopeOverlay = false;
	public float zoomLevel = 1.0F;
	public float FOVFactor = 1.5F;

	public boolean deployable = false;
	public String deployableTexture;

	// ---------------------------------------------------------------- the keys the packs set that
	// ---------------------------------------------------------------- the port had been dropping
	//
	// Every field below is read by a pack in the wild and was previously discarded, so the gun
	// behaved as if the line were absent. They are grouped here rather than beside their neighbours
	// so a future audit can see at a glance which ones were added for compatibility.

	/** Sound played when the trigger is pulled on an empty gun. */
	public String emptyClickSound, emptyClickSoundRepeated;

	/** How far the gun's firing sound carries, in blocks; -1 for the mod-wide default. */
	public int gunSoundRange = 50;
	/** How far the reload sound carries, in blocks; -1 for the mod-wide default. */
	public int reloadSoundRange = -1;

	/** The separator between the four attachment slots the port models and the gadget slot. */
	public boolean allowGadgetAttachments = false;

	/** Half-range of the random vertical recoil wobble, in degrees. */
	public float randomRecoilRange = 0.5F;
	/** Half-range of the random horizontal recoil wobble, in degrees. */
	public float randomRecoilYawRange = 0.3F;
	/** Degrees a shot pulls the view down afterwards, and the yaw half of the same settling. */
	public float decreaseRecoil = 0F, decreaseRecoilYaw = 0F;

	/** Spread multiplier while aiming; 0 means the pack did not set one. */
	public float aimingSpreadConstant = 0F;

	/** Whether the HUD shows the accuracy and reload readouts for this gun. */
	public boolean showAccuracy = true, showReloadTime = true;

	/** Model classes the pack ships for the muzzle flash and the ejected casing. */
	public String flashModel, casingModel, deployedModel;
	/** The skins those models draw with, under the pack's {@code skins/} folder. */
	public String flashTexture, casingTexture;
	public float standBackDist = 1.5F, topViewLimit = -60F, bottomViewLimit = 30F, sideViewLimit = 45F, pivotHeight = 0.375F;

	//Attachments
	public boolean allowAllAttachments = false;
	public final List<String> allowedAttachmentNames = new ArrayList<>();
	public boolean allowBarrelAttachments = false, allowScopeAttachments = false,
			allowStockAttachments = false, allowGripAttachments = false;
	public int numGenericAttachmentSlots = 0;

	public float moveSpeedModifier = 1F;
	public float knockbackModifier = 0F;

	public GunType(TypeFile file)
	{
		super(file);
		muzzleSmoke.scale=chamberSmoke.scale=1F;
		muzzleSmoke.vanishConst=chamberSmoke.vanishConst=0.2F;
		muzzleSmoke.freeRotate=1F;
	}

	@Override
	protected void postRead(TypeFile file) {
		super.postRead(file);
		gunLengthOffset*=modelScale;
		muzzleSmoke.at*=modelScale;
		// EFT core interprets the two sizes as initial and final, then GunSmoke uses
		// final + (initial - final) * alpha. Keep that conversion out of the renderer.
		for(Smoke smoke:new Smoke[]{muzzleSmoke,chamberSmoke}) {
			float difference=smoke.scale-smoke.scaleEx;
			smoke.scale=smoke.scaleEx;smoke.scaleEx=difference;
		}
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				case "Damage" -> damage = toFloat(split[1]);
				case "MeleeDamage" -> meleeDamage = toFloat(split[1]);
				case "CanForceReload" -> canForceReload = toBool(split[1]);
				case "AllowDirectAmmoLoading" -> allowDirectAmmoLoading = toBool(split[1]);
				case "ReloadTime" -> reloadTime = toInt(split[1]);
				case "Recoil" -> recoil = toInt(split[1]);
				case "Knockback" -> knockback = toFloat(split[1]);
				case "Accuracy", "Spread" -> {
					bulletSpread = toFloat(split[1]);
					// Shotguns write a second value: the spread while aiming. FMUM keeps both;
					// without it the pump gun's aimed pattern would read as its hip-fire one.
					if(split.length > 2)
						shotGunSpread = toFloat(split[2]);
				}
				case "AimingSpread" -> {
					bulletAimingSpread = toFloat(split[1]);
					if(split.length > 2)
						shotGunAimingSpread = toFloat(split[2]);
				}
				case "SpreadWLP" -> {
					bulletSpreadWLP = toFloat(split[1]);
					if(split.length > 2)
						shotGunSpreadWLP = toFloat(split[2]);
				}
				case "MagRunPosTime" -> bulletSpread = toFloat(split[1]);
				case "LoadMagTime" -> {
					shotGunSpread = toFloat(split[1]);
					if(split.length > 2)
						shotGunAimingSpread = toFloat(split[2]);
				}
				case "NumBullets" -> numBullets = toInt(split[1]);
				case "ConsumeGunOnUse" -> consumeGunUponUse = toBool(split[1]);
				case "DropItemOnShoot" -> dropItemOnShoot = split[1];
				case "NumBurstRounds" -> numBurstRounds = toInt(split[1]);
				case "MinigunStartSpeed" -> minigunStartSpeed = toFloat(split[1]);

				case "ShootDelay" -> shootDelay = toFloat(split[1]);
				// Both spellings of the fire rate are in the wild: 1.7.10 packs give a delay in
				// ticks, while 1.12.2-era packs give rounds per minute. 1200/RPM converts one to
				// the other, which is what the 1.12.2 source does — and it is why this is a float,
				// since 1200/900 is not a whole number of ticks.
				case "RoundsPerMin" -> shootDelay = split.length > 1 && toFloat(split[1]) > 0F
						? 1200F / toFloat(split[1]) : 0F;
				case "SoundLength" -> shootSoundLength = toInt(split[1]);
				case "DistortSound" -> distortSound = toBool(split[1]);
				case "ShootSound" -> { shootSound=split[1];shootSounds.clear();for(int i=1;i<split.length;i++)shootSounds.add(split[i]); }
				case "ReloadSound" -> reloadSound = split[1];
				case "IdleSound" -> idleSound = split[1];
				case "IdleSoundLength" -> idleSoundLength = toInt(split[1]);
				case "MeleeSound" -> meleeSound = split[1];
				case "WarmupSound" -> warmupSound = split[1];
				case "WarmupSoundLength" -> warmupSoundLength = toInt(split[1]);
				case "LoopedSound", "SpinSound" -> { loopedSound = split[1]; useLoopingSounds = true; }
				case "LoopedSoundLength", "SpinSoundLength" -> loopedSoundLength = toInt(split[1]);
				case "CooldownSound" -> cooldownSound = split[1];
				// The click a dry gun makes. Without it an empty weapon is silent, which reads as
				// the input having been dropped rather than as the magazine being empty.
				case "EmptyClickSound" -> emptyClickSound = split[1];
				case "EmptyClickSoundRepeated" -> emptyClickSoundRepeated = split[1];

				// Sound ranges. -1 means "use the mod-wide default", which is what the packs
				// expect when they leave these out; a gun that sets one overrides it.
				case "GunSoundRange" -> gunSoundRange = toInt(split[1]);
				case "ReloadSoundRange" -> reloadSoundRange = toInt(split[1]);

				case "Mode" -> {
					// A mode line may list several, space separated. The first becomes the active
					// mode, which is what the port fires with until mode switching is ported.
					for(int i = 1; i < split.length; i++)
					{
						EnumFireMode parsed = EnumFireMode.getFireMode(split[i]);
						modes.add(parsed);
					}
					if(!modes.isEmpty())
						mode = modes.get(0);
				}
				case "Scope" -> {
					hasScopeOverlay = !split[1].equals("None");
					if(hasScopeOverlay) defaultScopeTexture = split[1];
				}
				case "ZoomLevel" -> zoomLevel = toFloat(split[1]);
				case "FOVZoomLevel" -> FOVFactor = toFloat(split[1]);
				case "Deployable" -> deployable = split[1].equals("True");
				case "DeployedTexture" -> deployableTexture = split[1];
				case "StandBackDistance" -> standBackDist = toFloat(split[1]);
				case "TopViewLimit" -> topViewLimit = -toFloat(split[1]);
				case "BottomViewLimit" -> bottomViewLimit = toFloat(split[1]);
				case "SideViewLimit" -> sideViewLimit = toFloat(split[1]);
				case "PivotHeight" -> pivotHeight = toFloat(split[1]);

				// Ammo is resolved by name after every pack has been read: a gun may list
				// bullets that live in a different pack, and zip packs are read in arbitrary order.
				case "Ammo" -> {
					for(int i=1;i<split.length;i++)
						if(!split[i].equalsIgnoreCase("next")) ammoNames.add(split[i]);
				}
				case "NumAmmoSlots", "NumAmmoItemsInGun", "LoadIntoGun" -> numAmmoItemsInGun = toInt(split[1]);
				case "BulletSpeed" -> bulletSpeed = toFloat(split[1]);
				case "CanShootUnderwater" -> canShootUnderwater = toBool(split[1]);
				case "OneHanded" -> oneHanded = toBool(split[1]);
				case "UsableByPlayers", "UsableByMechas" -> { /* accepted, not modelled yet */ }

				case "MoveSpeedModifier", "Slowness" -> moveSpeedModifier = toFloat(split[1]);
				case "KnockbackReduction", "KnockbackModifier" -> knockbackModifier = toFloat(split[1]);

				case "AllowAllAttachments" -> allowAllAttachments = toBool(split[1]);
				case "AllowAttachments" -> {
					for(int i = 1; i < split.length; i++)
						allowedAttachmentNames.add(split[i]);
				}
				case "AllowBarrelAttachments" -> allowBarrelAttachments = toBool(split[1]);
				case "AllowScopeAttachments" -> allowScopeAttachments = toBool(split[1]);
				case "AllowStockAttachments" -> allowStockAttachments = toBool(split[1]);
				case "AllowGripAttachments" -> allowGripAttachments = toBool(split[1]);
				case "NumGenericAttachmentSlots" -> numGenericAttachmentSlots = toInt(split[1]);
				// The gadget slot is separate from the four the port models; accepted so its lines
				// are not mistaken for a value belonging to the key above them.
				case "AllowGadgetAttachments" -> allowGadgetAttachments = toBool(split[1]);

				// Recoil shape. The two Random* values are the half-range of the per-shot wobble in
				// degrees, and the two Decrease* values are how many degrees a shot pulls the view
				// back down — the pitch and yaw halves of the same climb-and-settle.
				case "RandomRecoilRange" -> randomRecoilRange = toFloat(split[1]);
				case "RandomRecoilYawRange" -> randomRecoilYawRange = toFloat(split[1]);
				case "DecreaseRecoil" -> decreaseRecoil = toFloat(split[1]);
				case "DecreaseRecoilYaw" -> decreaseRecoilYaw = toFloat(split[1]);

				// Spread while aiming, as a multiplier of the hip-fire spread.
				case "AimingSpreadConstant" -> aimingSpreadConstant = toFloat(split[1]);

				// HUD switches; both default to showing.
				case "ShowAccuracy" -> showAccuracy = toBool(split[1]);
				case "ShowReloadTime" -> showReloadTime = toBool(split[1]);

				// Model classes for the muzzle flash and the ejected casing. They name classes the
				// pack ships, exactly like the gun's own Model line, and are resolved through the
				// same loader — so a pack that has them gets the effects and one that does not
				// simply has none.
				//
				// The two pack eras write this line differently: 1.7.10 gives the model's name
				// alone, FMUM gives "vanish model texture scale textures" with the model third. A
				// second token that parses as a number is the FMUM form; anything else is a class
				// name. No pack ships a model called "0.25", so the test cannot misfire.
				case "FlashModel" -> {
					if(split.length == 2 && !isNumeric(split[1]))
					{
						flashModel = split[1];
					}
					else if(split.length >= 2)
					{
						muzzleFlashVanishConst = toFloat(split[1]);
						if(split.length > 2)
							flashModel = split[2];
						if(split.length > 3 && !split[3].equals("None"))
							flashTexture = split[3];
						if(split.length > 4)
							muzzleFlashScale = toFloat(split[4]);
						if(split.length > 5)
							numFlashTexture = toInt(split[5]);
					}
				}
				case "CasingModel" -> casingModel = split[1];
				case "DeployedModel" -> deployedModel = split[1];
				// The textures those two draw with; bare names under the pack's skins/ folder.
				case "FlashTexture" -> flashTexture = split[1];
				case "CasingTexture" -> casingTexture = split[1];

				// ------------------------------------------------------------ FMUM keys
				// Everything below comes from the FMUM lineage and is what the EFT packs write.
				// It is all stored; the behaviour (magazines, stepped reloads, model recoil) is
				// ported separately so a parse bug can never look like a gameplay bug.

				case "ItsType" -> itsType = switch(split[1].toLowerCase(java.util.Locale.ROOT))
				{
					case "mag" -> GunItsType.MAG;
					case "boltgun" -> GunItsType.BOLT_GUN;
					default -> GunItsType.AUTO_GUN;
				};

				// Mag / MagType: the magazines this gun takes, possibly a chain ("Mag A next B").
				// "next" separates alternative magazine types, not slots.
				case "Mag", "MagType" -> {
					for(int i = 1; i < split.length; i++)
						if(!split[i].equals("next"))
							magazineNames.add(split[i]);
				}
				case "AmmoCapacity" -> ammoCapacity = toInt(split[1]);

				// The stepped reload. The step names are FMUM's own vocabulary; storing them as
				// strings keeps the order and the exact step, and the reload system maps them.
				case "ReloadSteps" -> {
					for(int i = 1; i < split.length; i++)
						reloadSteps.add(split[i].toLowerCase(java.util.Locale.ROOT));
				}
				case "ReloadTimes" -> {
					for(int i = 1; i < split.length; i++)
						reloadTimes.add(toInt(split[i]));
				}
				case "ReloadSounds" -> {
					for(int i = 1; i < split.length; i++)
						reloadSounds.add(split[i]);
				}
				case "ReloadSoundRanges", "ReloadSoundsRange" -> {
					for(int i = 1; i < split.length; i++)
						reloadSoundRanges.add(toInt(split[i]));
				}
				case "PushMag" -> {
					// The EFT packs write this where the old core wrote a push-mag sound time;
					// both spellings denote the same value, so the later one wins if both appear.
					// FMUM's own core ignores the key, so this is a pack-side alias.
					if(split.length > 1)
						pushMagSoundTime = toFloat(split[1]);
				}
				case "PushMagTime" -> pushMagSoundTime = toFloat(split[1]);
				case "ReleaseSlideTime" -> releaseSlideTime = toInt(split[1]);
				case "ModelRecoilConst", "AntiModelRecoilConst" -> {
					// A five-number variant the EFT packs write; no reference core reads it, so it
					// is kept verbatim rather than mapped onto a field it does not match.
					modelRecoilConst = new float[Math.min(5, split.length - 1)];
					for(int i = 0; i < modelRecoilConst.length; i++)
						modelRecoilConst[i] = toFloat(split[i + 1]);
				}
				case "AllowNumBulletsByBulletType" -> allowNumBulletsByBulletType = toBool(split[1]);
				case "AllowSpreadEffectedByBullet" -> { /* accepted; ignored by every reference core */ }
				case "MagSoundTime" -> pushMagSoundTime = toFloat(split[1]);
				case "BulletSoundTime" -> {
					pushBulletSoundTime = toFloat(split[1]);
					if(split.length > 2)
						pullBulletSoundTime = toFloat(split[2]);
				}

				case "CheckAmmo" -> {
					checkAmmoTime = toFloat(split[1]);
					if(split.length > 2)
						checkAmmoSound[0] = split[2];
					if(split.length > 3)
						checkAmmoSoundTime[0] = toFloat(split[3]);
					if(split.length > 4)
						checkAmmoSound[1] = split[4];
					if(split.length > 5)
						checkAmmoSoundTime[1] = toFloat(split[5]);
					if(split.length > 6)
						checkAmmoSoundRange = toFloat(split[6]);
				}
				case "CheckChamber" -> {
					checkChamberTime[0] = toInt(split[1]);
					if(split.length > 2)
						checkChamberSound[0] = split[2];
					if(split.length > 3)
						checkChamberSoundTime[0] = toFloat(split[3]);
					if(split.length > 4)
						checkChamberSound[1] = split[4];
					if(split.length > 5)
						checkChamberSoundTime[1] = toFloat(split[5]);
					if(split.length > 6)
						checkChamberSoundRange = toFloat(split[6]);
				}
				case "CheckChamberBC" -> {
					checkChamberTime[1] = toInt(split[1]);
					if(split.length > 2)
						checkChamberSound[2] = split[2];
					if(split.length > 3)
						checkChamberSoundTime[2] = toFloat(split[3]);
					if(split.length > 4)
						checkChamberSound[3] = split[4];
					if(split.length > 5)
						checkChamberSoundTime[3] = toFloat(split[5]);
				}
				case "CheckAmmoState" -> {
					if(split.length < 4)
						break;
					StringBuilder text = new StringBuilder(split[3]);
					for(int i = 4; i < split.length; i++)
						text.append(' ').append(split[i]);
					int a = toInt(split[1]), b = toInt(split[2]);
					checkAmmoStates.add(new AmmoState(Math.min(a, b), Math.max(a, b), text.toString()));
				}
				case "ReleaseSlide" -> {
					releaseSlideTime = toInt(split[1]);
					if(split.length > 2)
						releaseBoltSound[0] = split[2];
					if(split.length > 3)
						releaseBoltSoundR[0] = toInt(split[3]);
					if(split.length > 4)
						releaseBoltSound[1] = split[4];
					if(split.length > 5)
						releaseBoltSoundR[1] = toInt(split[5]);
				}
				case "ChargeTime" -> {
					chargeTime = toInt(split[1]);
					int pair = 0;
					for(int i = 2; i < split.length && pair < chargeSound.length; i += 2, pair++)
					{
						chargeSound[pair] = split[i];
						if(i+1<split.length)chargeSoundR[pair] = toInt(split[i + 1]);
					}
				}
				case "ChargeDelay" -> chargeDelay = toInt(split[1]);

				case "TakeOutTime" -> takeOutTime = toInt(split[1]);
				case "RunPoseTime" -> runPoseTime = toInt(split[1]);
				case "RestTime" -> {
					restTime = toInt(split[1]);
					if(split.length > 2)
						unrestTime = toInt(split[2]);
				}
				case "StockSteps" -> {
					stockSteps = Math.max(0, toInt(split[1]) - 1);
					if(split.length > 2)
						stockDistance = toFloat(split[2]);
				}
				case "FoldStockDistance" -> foldStockDistance = toFloat(split[1]);
				case "StockFoldedSpeedModifier" -> stockFoldedSpeedModifier = toFloat(split[1]);
				case "MagVolume" -> magVolume = toFloat(split[1]);
				case "GunLengthOffset" -> gunLengthOffset = toFloat(split[1]) / 16F;
				case "SlideAcceleration" -> {
					slideAcceleration[0] = toFloat(split[1]);
					slideAcceleration[1] = split.length > 2 ? toFloat(split[2]) : slideAcceleration[0];
				}

				// Recoil, FMUM's shape: RecoilPitch pitch [range], RecoilYaw range [yaw].
				case "RecoilPitch" -> {
					recoilPitch = toFloat(split[1]);
					if(split.length > 2)
						rndRecoilPitchRange = toFloat(split[2]) * 2F;
				}
				case "RecoilYaw" -> {
					rndRecoilYawRange = toFloat(split[1]);
					if(split.length > 2)
						recoilYaw = toFloat(split[2]);
				}
				case "MagRecoil" -> {
					recoilPitch = toFloat(split[1]);
					if(split.length > 2)
						rndRecoilYawRange = toFloat(split[2]);
				}
				case "ModelRecoil" -> {
					for(int i = 1; i < split.length && i <= modelRecoil.length; i++)
						modelRecoil[i - 1] = toFloat(split[i]);
				}
				// Only the exact key affects retention in the supplied EFT core.
				case "AntiModelRecoil" -> {
					for(int i = 1; i < split.length && i <= antiModelRecoil.length; i++)
						antiModelRecoil[i - 1] = toFloat(split[i]);
				}
				// The supplied 1.7.10 core ignores this pack typo; do not activate its values.
				case "AntiModelRecoi" -> { }

				case "AimTime", "AimTimeConstant" -> aimTimeConst = toFloat(split[1]);
				case "SightSwitch" -> sightSwitchConst = toFloat(split[1]);

				case "ModeSwitchSound" -> {
					modeSwitchSound = split[1];
					if(split.length > 2)
						modeSwitchSoundRange = toInt(split[2]);
				}
				case "ShootSoundRange" -> gunSoundRange = toInt(split[1]);
				case "MeleeSoundRange" -> meleeSoundRange = toInt(split[1]);
				case "LockSlideSound" -> {
					lockSlideSound = split[1];
					if(split.length > 2)
						lockSlideSoundRange = toInt(split[2]);
				}
				case "LockOnSound" -> lockOnSound = split[1];
				case "LockOnSoundTime" -> lockOnSoundTime = toInt(split[1]);
				case "CanLockAngle" -> canLockOnAngle = toInt(split[1]);
				case "MaxRangeLockOn" -> maxRangeLockOn = toInt(split[1]);

				case "CanSetPosition" -> canSetPosition = toBool(split[1]);
				case "AllowRearm" -> allowRearm = toBool(split[1]);
				case "ShowCrosshair" -> showCrosshair = toBool(split[1]);
				case "ItemUseAction" -> itemUseAction = split[1];
				case "SecondaryFunction" -> secondaryFunction = split[1];
				case "SecondaryShootSound" -> {
					secondaryShootSound = split[1];
					if(split.length > 2)
						secondaryShootSoundRange = toInt(split[2]);
				}

				case "UseCustomMelee" -> useCustomMelee = toBool(split[1]);
				case "UseCustomMeleeWhenShoot" -> useCustomMeleeWhenShoot = toBool(split[1]);
				case "MeleeTime" -> meleeTime = toInt(split[1]);
				case "AddNode" -> {
					if(split.length >= 7)
					{
						meleePathNodes.add(new float[] { toFloat(split[1]) / 16F, toFloat(split[2]) / 16F,
								toFloat(split[3]) / 16F });
						meleePathAngles.add(new float[] { toFloat(split[4]), toFloat(split[5]), toFloat(split[6]) });
					}
				}
				case "MeleeDamagePoint", "MeleeDamageOffset" -> {
					if(split.length >= 4)
						meleeDamagePoints.add(new float[] { toFloat(split[1]) / 16F, toFloat(split[2]) / 16F,
								toFloat(split[3]) / 16F });
				}

				case "ArmModel" -> {
					// Left arm, then right arm, each optionally followed by its texture; "None"
					// means the pack keeps the default arm.
					if(split.length > 1 && !split[1].equals("None"))
						leftArmModel = split[1];
					if(split.length > 2 && !split[2].equals("None"))
						rightArmModel = split[2];
					if(split.length > 3 && !split[3].equals("None"))
						leftArmTexture = split[3];
					if(split.length > 4 && !split[4].equals("None"))
						rightArmTexture = split[4];
				}
				case "MagFollowerModel" -> {
					magFollowerModel = split[1];
					if(split.length > 2)
						magFollowerTexture = split[2].equalsIgnoreCase("None") ? null : split[2];
					if(split.length > 3)
						magFollowerScale = toFloat(split[3]);
				}
				case "MuzzleSmokeModel" -> parseSmoke(split, muzzleSmoke, true);
				case "ChamberSmokeModel" -> parseSmoke(split, chamberSmoke, false);
				case "ArrowModel" -> arrowModel = split[1];
				case "HitTexture" -> hitTexture = split[1];
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading gun file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	// ---------------------------------------------------------------- the active firing mode
	//
	// A gun with several modes remembers which one is selected ON THE STACK, not on the
	// definition: two of the same rifle in one inventory can be set differently, and a mode chosen
	// in the world survives a save. That is the 1.20.1 build's arrangement (its GunType stores the
	// value under "gun_mode" in the item's own tag) and it is also what 1.7.10's per-stack NBT did.

	/** The item-data key holding the selected mode's name. */
	public static final String MODE_KEY = "gun_mode";

	/** The mode this stack is set to, falling back to the definition's first listed mode. */
	public EnumFireMode modeFor(ItemStack stack)
	{
		if(stack != null && !stack.isEmpty())
		{
			var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
			if(data != null)
			{
				var tag = data.copyTag();
				if(tag.contains(MODE_KEY))
				{
					EnumFireMode stored = EnumFireMode.getFireMode(tag.getString(MODE_KEY));
					// Only a mode the pack actually lists is honoured: a stack saved by an older
					// build, or by a pack that has since dropped a mode, must not fire in a mode it
					// no longer has.
					if(modes.isEmpty() || modes.contains(stored))
						return stored;
				}
			}
		}
		return modes.isEmpty() ? mode : modes.get(0);
	}

	/** Writes the selected mode onto the stack. */
	public void setModeFor(ItemStack stack, EnumFireMode chosen)
	{
		if(stack == null || stack.isEmpty())
			return;
		var existing = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		var tag = existing == null ? new net.minecraft.nbt.CompoundTag() : existing.copyTag();
		tag.putString(MODE_KEY, chosen.name());
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
				net.minecraft.world.item.component.CustomData.of(tag));
	}

	/** True when there is more than one mode to switch between. */
	public boolean canSwitchMode()
	{
		return modes.size() > 1;
	}

	/** The next mode in the pack's own order, wrapping round; the current one if there is only one. */
	public EnumFireMode nextMode(EnumFireMode current)
	{
		if(modes.size() <= 1)
			return modes.isEmpty() ? mode : modes.get(0);
		int at = modes.indexOf(current);
		return modes.get((at + 1) % modes.size());
	}

	/**
	 * Firing modes.
	 *
	 * <p>The names are the 1.20.1 lineage's ({@code SEMIAUTO}, not {@code SEMI}) because that is
	 * what the pack files spell: they write {@code SemiAuto} and {@code FullAuto}, and 1.7.10's
	 * older {@code SEMI} spelling does not appear in any pack in the wild. Both are accepted here,
	 * so a file written either way loads.
	 *
	 * <p>Matching strips {@code -}, {@code _} and spaces and ignores case, which is what the
	 * 1.20.1 build's {@code normalize} does — a pack that writes {@code Semi-Auto} or
	 * {@code semi_auto} still lands on the right mode.
	 *
	 * <p>An unrecognised name falls back to {@link #SEMIAUTO} rather than {@link #FULLAUTO}: a
	 * typo or a mode this port does not model must not silently turn a rifle into a machine gun.
	 */
	public enum EnumFireMode
	{
		SEMIAUTO, FULLAUTO, MINIGUN, BURST, MELEE, SAFE;

		/** 1.7.10's spelling of {@link #SEMIAUTO}, kept so older packs still load. */
		public static final EnumFireMode SEMI = SEMIAUTO;

		public static EnumFireMode getFireMode(String s)
		{
			String wanted = normalize(s);
			for(EnumFireMode mode : values())
				if(mode != SEMI && normalize(mode.name()).equals(wanted))
					return mode;
			// "Semi" as well as the enum's own "SemiAuto", since both spellings are in use.
			if(wanted.equals("semi") || wanted.equals("sem") || wanted.equals("single"))
				return SEMIAUTO;
			return SEMIAUTO;
		}

		/** Case- and punctuation-insensitive, as the 1.20.1 build's own mode matching is. */
		private static String normalize(String s)
		{
			return s == null ? ""
					: s.replace("-", "").replace("_", "").replace(" ", "")
							.toLowerCase(java.util.Locale.ROOT);
		}

		public boolean isAutomaticFire()
		{
			return this == FULLAUTO || this == MINIGUN;
		}

		/** The name the HUD shows, matching the reference builds' strings. */
		public String getDisplayName()
		{
			return switch(this)
			{
				case SEMIAUTO -> "Semi-Auto";
				case FULLAUTO -> "Full-Auto";
				case BURST -> "Burst";
				case MINIGUN -> "Minigun";
				case MELEE -> "Melee";
				case SAFE -> "Safe";
			};
		}
	}
}
