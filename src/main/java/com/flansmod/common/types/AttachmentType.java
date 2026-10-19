package com.flansmod.common.types;

import java.util.ArrayList;
import java.util.List;

import com.flansmod.common.FlansMod;

/**
 * An attachment, from {@code attachments/*.txt} and {@code attachments/*.df}. Ported from
 * 1.7.10's {@code AttachmentType} and extended with the FMUM keys the EFT packs are written for.
 *
 * <p>The multiplier fields stack between attachments on the same gun, which is how the old
 * packs balanced scopes and barrels, so they are all kept.
 */
public class AttachmentType extends ModifiableType
{
	public static final List<AttachmentType> attachments = new ArrayList<>();

	/** Generic / Barrel / Scope / Stock / Grip. */
	public String attachmentType = "generic";

	/**
	 * What this part is, in FMUM's vocabulary — the value matching a slot's whitelist uses this
	 * rather than {@link #attachmentType}, which is the older and coarser tag.
	 */
	public String itsType = "";

	/**
	 * Parts this attachment requires to be mounted before it fits — the "Need:AK74 AK74N" note a
	 * pack puts in the description, made machine-readable.
	 */
	public final List<String> attachmentTypesNeeded = new ArrayList<>();

	public boolean silencer = false;
	public boolean flashlight = false;
	public float flashlightRange = 10F;
	public int flashlightStrength = 12;

	public float spreadMultiplier = 1F;
	public float recoilMultiplier = 1F;
	public float recoilYawMultiplier = 1F;
	/** Z/Y rotation, X translation by pitch/yaw, FOV recoil. */
	public final float[] modelRecoilMultipliers = { 1F, 1F, 1F, 1F, 1F };
	public float damageMultiplier = 1F;
	public float meleeDamageMultiplier = 1F;
	public float bulletSpeedMultiplier = 1F;
	public float reloadTimeMultiplier = 1F;

	/** Fire mode this attachment forces, or null to leave the gun's own mode alone. */
	public GunType.EnumFireMode modeOverride = null;

	/** Hard-point offsets along the attachment's own model, in blocks. */
	public float[] attachOffsets = new float[0];

	/** The attachment's shadow and how it switches the gun's state when toggled. */
	public String shadowType = "generic";
	public boolean shadowUpdatesGunStates = false;
	public boolean hideInCreativeTab = false;
	/** Fourth ShadowType argument: generic, toggle or cosight. */
	public String shadowMode = "none";
	public boolean isCoSight() { return "cosight".equals(shadowMode); }
	public boolean isToggle() { return "toggle".equals(shadowMode); }
	/** EFT marks the hidden half of a reciprocal folding pair as its folded variant. */
	public boolean isFoldedVariant() {
		if(!hideInCreativeTab || (!isCoSight() && !isToggle())) return false;
		var other = getAttachment(shadowType);
		return other != null && !other.hideInCreativeTab && other.shadowType.equalsIgnoreCase(shortName);
	}

	/** Secondary-fire behaviour: this part can fire, and with what. */
	public boolean secondaryFire = false;
	public final List<String> secondaryAmmoNames = new ArrayList<>();
	public float secondaryDamage = 1F;
	public float secondarySpread = 0F, secondaryShotGunSpread = 0F;

	/** Whether the sight's dot/light model draws, and its skin. */
	public String lightStuffModel, lightStuffTexture;
	public int lightTextureCount = 1;

	/** The armour and hardness this attachment contributes to the gun's hitbox. */
	public float volumeWhenAttached = 1F;

	/** The laser pointer's models (first-person and world) when the part is one. */
	public String laserModelFP, laserModel;

	/**
	 * The hand pose this part puts the supporting hand into.
	 *
	 * <p>Each pose is a name plus a priority: when several attachments on one gun both want the
	 * left hand, the higher priority wins. Storing both is what makes that decidable later.
	 */
	public String leftHandPose, rightHandPose;
	public int leftHandPriority = -128, rightHandPriority = -128;

	/**
	 * How much of the gun's space the part occupies, per slot.
	 *
	 * <p>Only ever authored by the content packs — neither the 1.7.10 core nor FMU 2.0 reads this
	 * key at all, so it has no runtime meaning. Kept so the line parses without error.
	 */
	public final List<Integer> spaceTaken = new ArrayList<>();

	// ---------------------------------------------------------------- FMUM effects

	/** Recoil adjustments this part makes, and the decrease that follows them. */
	public float recoil = 0F, recoilYaw = 0F;
	public float decreaseRecoil = 0F, decreaseRecoilYaw = 0F;
	/** Multiplier on the model's recoil, and the constant subtracted from it. */
	public float modelRecoilMultiplier = 1F;
	public float antiModelRecoilConst = -1F;

	/** Aiming and handling multipliers. */
	public float aimTimeMult = 1F, sightSwitchMult = 1F, takeOutTimeMult = 1F,
			runPoseTimeMult = 1F, moveSpeedMultiplier = 1F;
	/**
	 * The same three, applied only by the sight the gun is actually aiming through. 1.7.10 wrote
	 * them as a second number on the same line: {@code AimTimeMult <all> <in-use>}.
	 */
	public float onUseAimTimeMult = 1F, onUseSightSwitchMult = 1F, aimingMoveSpeedMultiplier = 1F;

	/** How far the part sits along the gun's length, in blocks. */
	public float physicalLength = 0F;

	/** Sound adjustments: which shoot sound plays, how far it carries. */
	public int enableShootSoundIndex = -1;
	public float soundRangeMultiplier = 1F;

	/** The alternate muzzle effects this part can swap in, when it changes the flash. */
	public GunType.Smoke muzzleSmoke = new GunType.Smoke();

	/** Duration of the flash this part adds, when it has one. */
	public float muzzleFlashTime = 0F;

	/**
	 * The pack's {@code ZoomLevel} weight is ten times the optic's nominal magnification: PSO1
	 * writes {@code 40 0.6} and is a 4x scope, NF7x writes {@code 70 0.3} and is 7x, and Octagon's
	 * variable scope writes {@code 87.75 ... 39} for 8.8x down to 3.9x. Dividing by this gives the
	 * magnification the author named the part after.
	 *
	 * <p>1.7.10 instead divided by {@code raw * pupilDistance} because it re-rendered the world from
	 * the ocular; that is the same order of magnitude for a direct-mount PSO1 but degenerates for
	 * parts whose pupil reference sits far from the camera (NF7x fell below 1x and was clamped, and
	 * a 48.75/0.63 red dot became 30x). The named magnification is the stable, asset-derived value.
	 */
	public static final float ZOOM_LEVEL_SCALE = 0.1F;

	/**
	 * Legacy fixed reticle approximation. Live rendering reconstructs the dynamic pupil point
	 * instead of using this value; retained only for compatibility with old offline tools.
	 */
	@Deprecated
	public static final float RETICLE_PUPIL_DISTANCE = 0.4F;

	/** Scope glass and black-mask models, by reference; null when the part has neither. */
	public String scopeGlassModel, blackMaskModel;
	/** One entry per magnification step; empty when this part does not magnify. */
	public float[] fovZoomLevels = new float[0];
	/** Mouse sensitivity multiplier that applies at the same step index. */
	public float[] mouseSensitivityMult = new float[0];
	/** Legacy definition default only. Runtime code must pass the mounted node's zoomStep. */
	@Deprecated
	public int zoomStep = 0;

	/** Kept for the scalar {@code FOVZoomLevel} spelling some packs use. */
	public float zoomLevel = 1F;
	/** Scalar frame-FOV divisor, when the pack wrote {@code FOVZoomLevel} instead of {@code ZoomLevel}. */
	public float FOVZoomLevel = 1F;
	public String zoomOverlay;
	public boolean hasScopeOverlay = false;

	/**
	 * Optical magnification for the selected step; 1 means no magnification. This is the value the
	 * world camera is divided by, so it is also the perceptible zoom through the ocular.
	 */
	public float zoomDivisor()
	{
		return zoomDivisor(zoomStep);
	}
	public float zoomDivisor(int selectedStep)
	{
		if(fovZoomLevels.length == 0)
			return FOVZoomLevel > 1F ? Math.min(FOVZoomLevel, 20F) : 1F;
		int step = Math.max(0, Math.min(selectedStep, fovZoomLevels.length - 1));
		float divisor = fovZoomLevels[step] * ZOOM_LEVEL_SCALE;
		return divisor > 1F ? Math.min(divisor, 20F) : 1F;
	}

	/** Sensitivity multiplier for the selected step; 1 when the pack declared none. */
	public float sensitivityMultiplier()
	{
		return sensitivityMultiplier(zoomStep);
	}
	public float sensitivityMultiplier(int selectedStep)
	{
		if(mouseSensitivityMult.length == 0)
			return 1F;
		int step = Math.max(0, Math.min(selectedStep, mouseSensitivityMult.length - 1));
		float value = mouseSensitivityMult[step];
		return value > 0F ? value : 1F;
	}

	public int maxStackSize = 1;

	public AttachmentType(TypeFile file)
	{
		super(file);
		attachments.add(this);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{
			switch(split[0])
			{
				case "AttachmentType" -> attachmentType = split[1].toLowerCase(java.util.Locale.ROOT);
				case "ItsType" -> itsType = split[1].toLowerCase(java.util.Locale.ROOT);
				case "AttachmentTypesNeeded", "TypesNeeded" -> {
					for(int i = 1; i < split.length; i++)
						attachmentTypesNeeded.add(split[i]);
				}
				case "Silencer" -> silencer = toBool(split[1]);
				case "Flashlight" -> {
					flashlight = toBool(split[1]);
					if(split.length > 2)
						flashlightRange = toFloat(split[2]);
					if(split.length > 3)
						flashlightStrength = toInt(split[3]);
				}
				case "SpreadMultiplier" -> spreadMultiplier = toFloat(split[1]);
				case "RecoilMultiplier" -> {
					recoilMultiplier = toFloat(split[1]);
					if(split.length > 2) recoilYawMultiplier = toFloat(split[2]);
				}
				case "DamageMultiplier" -> damageMultiplier = toFloat(split[1]);
				case "MeleeDamageMultiplier" -> meleeDamageMultiplier = toFloat(split[1]);
				case "BulletSpeedMultiplier" -> bulletSpeedMultiplier = toFloat(split[1]);
				case "ReloadTimeMultiplier" -> reloadTimeMultiplier = toFloat(split[1]);
				// 1.7.10 writes a second number on these three lines for the "in use" value —
				// the multiplier that applies only through the sight actually being aimed.
				case "MoveSpeedMultiplier" -> {
					moveSpeedMultiplier = toFloat(split[1]);
					if(split.length > 2) aimingMoveSpeedMultiplier = toFloat(split[2]);
				}
				case "AimTimeMult", "AimTimeMultiplier" -> {
					aimTimeMult = toFloat(split[1]);
					if(split.length > 2) onUseAimTimeMult = toFloat(split[2]);
				}
				case "SightSwitchMult" -> {
					sightSwitchMult = toFloat(split[1]);
					if(split.length > 2) onUseSightSwitchMult = toFloat(split[2]);
				}
				case "ModeOverride" -> modeOverride = GunType.EnumFireMode.getFireMode(split[1]);
				// 1.7.10: ZoomLevel <glass model> <mask model> <level> <sensitivity> [<level> <sensitivity> ...]
				// The first two tokens are models, not numbers — reading them as a float silently
				// produced 0 and threw away the whole magnification list.
				case "ZoomLevel" -> {
					if(split.length == 2) {
						try {
							float scalar = Float.parseFloat(split[1]);
							if(Float.isFinite(scalar) && scalar > 0F) zoomLevel = FOVZoomLevel = scalar;
						} catch(NumberFormatException modelOnly) {
							if(!split[1].equalsIgnoreCase("None")) scopeGlassModel = split[1];
						}
						break;
					}
					if(!split[1].equalsIgnoreCase("None"))
						scopeGlassModel = split[1];
					if(split.length > 2 && !split[2].equalsIgnoreCase("None"))
						blackMaskModel = split[2];
					int steps = (split.length - 3) / 2;
					if(steps > 0)
					{
						fovZoomLevels = new float[steps];
						mouseSensitivityMult = new float[steps];
						for(int i = 0; i < steps; i++)
						{
							fovZoomLevels[i] = toFloat(split[i * 2 + 3]);
							mouseSensitivityMult[i] = i * 2 + 4 < split.length ? toFloat(split[i * 2 + 4]) : 1F;
						}
						hasScopeOverlay = true;
					}
				}
				case "FOVZoomLevel" -> FOVZoomLevel = toFloat(split[1]);
				// The packs call this "Scope" on attachments, matching the gun field.
				case "Scope" -> {
					hasScopeOverlay = !split[1].equals("None");
					if(hasScopeOverlay) zoomOverlay = split[1];
				}
				case "ZoomOverlay" -> { zoomOverlay = split[1]; hasScopeOverlay = true; }

				// ------------------------------------------------------------ FMUM keys

				// Hard-point offsets: a run of numbers in blocks, along the part's own axis.
				case "AttachOffsets" -> {
					attachOffsets = new float[split.length - 1];
					for(int i = 0; i < attachOffsets.length; i++)
						attachOffsets[i] = toFloat(split[i + 1]) / 16F;
				}

				case "ShadowType" -> {
					// FMUM: ShadowType <name> [hideCreative] [updateGunStates] [mode].
					shadowType = split[1]; shadowMode = "generic";
					if(split.length > 2) hideInCreativeTab = toBool(split[2]);
					if(split.length > 3) shadowUpdatesGunStates = toBool(split[3]);
					if(split.length > 4) shadowMode = split[4].toLowerCase(java.util.Locale.ROOT);
				}
				case "HideInCreativeTab" -> hideInCreativeTab = toBool(split[1]);

				// The attachment's own model. Same multi-token shape as everywhere else in this
				// family: class, skin, scale, and (attachments only) a coated-glass model.
				case "Model" -> {
					// The base class already read the class name, skin and scale; the extra
					// token this class knows about is the coated glass.
					if(split.length > 4)
						coatedGlassModel = split[4];
				}
				case "DotModel" -> {
					if(!split[1].equals("None"))
						lightStuffModel = split[1];
					if(split.length > 2)
						lightStuffTexture = split[2];
					if(split.length > 3)
						lightTextureCount = toInt(split[3]);
				}
				case "MuzzleSmokeModel" -> {
					// The attachment-side smoke line: <at> <model> <texture> <scale> <num>.
					muzzleSmoke.at = toFloat(split[1]) / 16F;
					if(split.length > 2 && !split[2].equals("None"))
						muzzleSmoke.model = split[2];
					if(split.length > 3 && !split[3].equals("None"))
						muzzleSmoke.texture = split[3];
					if(split.length > 4)
						muzzleSmoke.scale = toFloat(split[4]);
					if(split.length > 5)
						muzzleSmoke.numTexture = toInt(split[5]);
				}
				case "MuzzleFlashTime" -> muzzleFlashTime = toFloat(split[1]);
				case "SmokeVanishConst" -> muzzleSmoke.vanishConst = toFloat(split[1]);

				// The attachment-side flash line keeps the gun-side shape, leading with the fade
				// constant; the model, skin, scale and texture count follow it.
				case "FlashModel" -> {
					muzzleFlashVanishConst = toFloat(split[1]);
					if(split.length > 2 && !split[2].equals("None"))
						flashModelName = split[2];
					if(split.length > 3 && !split[3].equals("None"))
						flashTextureName = split[3];
					if(split.length > 4)
						muzzleFlashScale = toFloat(split[4]);
					if(split.length > 5)
						numFlashTexture = toInt(split[5]);
				}

				case "SecFire" -> secondaryFire = toBool(split[1]);
				case "Recoil" -> recoil = toFloat(split[1]);
				case "RecoilYaw" -> recoilYaw = toFloat(split[1]);
				case "DecreaseRecoil" -> decreaseRecoil = toFloat(split[1]);
				case "DecreaseRecoilYaw" -> decreaseRecoilYaw = toFloat(split[1]);
				case "ModelRecoilMultiplier" -> {
					modelRecoilMultiplier = toFloat(split[1]);
					for(int i=1;i<split.length && i<=modelRecoilMultipliers.length;i++)
						modelRecoilMultipliers[i-1]=toFloat(split[i]);
				}
				case "AntiModelRecoilConst" -> antiModelRecoilConst = toFloat(split[1]);

				case "Ammo" -> secondaryAmmoNames.add(split[1]);
				case "Damage" -> secondaryDamage = toFloat(split[1]);
				case "Spread" -> {
					secondarySpread = toFloat(split[1]);
					if(split.length > 2)
						secondaryShotGunSpread = toFloat(split[2]);
				}
				case "ShootDelay" -> shootDelay = toFloat(split[1]);
				case "ReloadTime" -> reloadTime = toInt(split[1]);
				case "LoadIntoGun" -> loadIntoGun = split[1];

				// The stepped reload, in the FMUM attachment's own (shorter) form.
				case "ReloadSteps" -> {
					for(int i = 1; i < split.length; i++)
						reloadSteps.add(split[i]);
				}
				case "ReloadTimes" -> {
					for(int i = 1; i < split.length; i++)
						reloadTimes.add(toInt(split[i]));
				}
				case "ReloadSounds" -> {
					for(int i = 1; i < split.length; i++)
						reloadSounds.add(split[i]);
				}

				case "SecondaryShootSound" -> {
					secondaryShootSound = split[1];
					if(split.length > 2)
						secondaryShootSoundRange = toInt(split[2]);
				}
				case "EmptyClickSound" -> {
					emptyClickSound = split[1];
					if(split.length > 2)
						emptyClickSoundRange = toInt(split[2]);
				}
				case "ReloadSound" -> reloadSound = split[1];
				case "EnableShootSoundNum" -> enableShootSoundIndex = toInt(split[1]);
				case "SoundRangeMultiplier" -> soundRangeMultiplier = toFloat(split[1]);

				case "TakeOutTimeMult" -> takeOutTimeMult = toFloat(split[1]);
				case "RunPoseTimeMult" -> runPoseTimeMult = toFloat(split[1]);

				case "PhysicalLength" -> physicalLength = toFloat(split[1]);
				case "AttachedVolume" -> volumeWhenAttached = toFloat(split[1]);

				case "LaserPointer" -> {
					laserPointer = toBool(split[1]);
					if(split.length > 2 && laserPointer)
						laserModelFP = split[2];
					if(split.length > 3)
						laserModel = split[3];
				}
				case "LeftHandPose" -> {
					leftHandPose = split[1];
					if(split.length > 2)
						leftHandPriority = toInt(split[2]);
				}
				case "RightHandPose" -> {
					rightHandPose = split[1];
					if(split.length > 2)
						rightHandPriority = toInt(split[2]);
				}
				// EFT's FMUM 1.1 core only changes priority in Left/RightHandPose's third
				// token. Standalone HandPriority lines in this pack were ignored there.

				case "SpaceTaken" -> {
					for(int i = 1; i < split.length; i++)
						spaceTaken.add(toInt(split[i]));
				}

				// Paintjob support on attachments: which part styles are available.
				case "Paintjob" -> { /* accepted; the paintjob table is a later port */ }
				case "ExPaintjob" -> { /* accepted; extended paintjobs are a later port */ }
			}
		}
		catch(Exception e)
		{
			FlansMod.log("Reading attachment file failed : " + shortName + " (" + split[0] + ")");
			e.printStackTrace();
		}
	}

	// ---------------------------------------------------------------- fields the base class
	// ---------------------------------------------------------------- fills but this class names
	//
	// These live here rather than in the base only because a gun's flash/casing/dot names have
	// their own semantics; the attachment writes them from different lines.

	/** The flash model this part swaps in, when it does. */
	public String flashModelName, flashTextureName;
	/** How long the flash lives, how big it is, and how many skins it has. */
	public float muzzleFlashVanishConst = 1F, muzzleFlashScale = 1F;
	public int numFlashTexture = 0;

	@Override
	protected void postRead(TypeFile file) {
		super.postRead(file);
		for(int i=0;i<attachOffsets.length;i++) attachOffsets[i]*=modelScale<=0F?1F:modelScale;
		muzzleSmoke.at*=modelScale;
	}
	/** The coated-glass overlay model a sight draws its lens with. */
	public String coatedGlassModel;
	/** The gun-state operation a togglable part performs when switched. */
	public String loadIntoGun;
	public boolean laserPointer = false;

	/** Secondary shooting's sound and its range, when the part has its own. */
	public String secondaryShootSound;
	public int secondaryShootSoundRange = -1;
	public String emptyClickSound;
	public int emptyClickSoundRange = -1;
	public String reloadSound;

	/** Ticks between the secondary fire's rounds, and its reload length. */
	public float shootDelay = 0F;
	public int reloadTime = 0;

	/** The part's own stepped reload, mirroring the gun's. */
	public final List<String> reloadSteps = new ArrayList<>();
	public final List<Integer> reloadTimes = new ArrayList<>();
	public final List<String> reloadSounds = new ArrayList<>();

	public static AttachmentType getAttachment(String shortName)
	{
		for(AttachmentType a : attachments)
			if(a.shortName != null && a.shortName.equalsIgnoreCase(shortName))
				return a;
		return null;
	}
}
