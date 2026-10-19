package com.flansmod.client.model.loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The geometry a model class describes, in the same terms the old renderer used.
 *
 * <p>Kept deliberately close to 1.7.10's vocabulary — a box with a position, a size and a
 * texture offset, grouped into arrays named after the field they were assigned to
 * ({@code gunModel}, {@code turretModel}, …). The names matter: they are what the port maps
 * onto attachment slots and animation parts.
 *
 * <p>Everything is in the model's own units. How those units reach the screen — the 1/16 scale,
 * the flip on the Y axis — is applied later, at bake time, so this stays a faithful record of
 * what the class said.
 */
public final class ModelGeometry
{
	/** One {@code addShapeBox} / {@code addBox} / {@code addTrapezoid} call. */
	public static final class Box
	{
		public float x, y, z;
		public int w, h, d;
		/** The {@code expansion}/{@code scale} argument; expands the box on every side. */
		public float scale;
		/**
		 * The eight shape-box corner offsets as {@code x0,y0,z0, x1,y1,z1, …}.
		 * All zero for a plain box.
		 */
		public final float[] offsets = new float[24];

		/**
		 * The renderer object this element was added to, as {@code field#index}.
		 *
		 * <p>Resolved to a pivot by {@link Model#applyPivots()} once the whole class has been
		 * read, because a class may add geometry in one statement and set the rotation point in
		 * another — including in a different method entirely.
		 */
		public String renderer;

		/**
		 * Where this element rotates around, in model units, filled in from
		 * {@link Model#pivots} by {@link Model#applyPivots()}.
		 *
		 * <p>This belongs to the element, not to the part. {@code ModelRendererTurbo} is one
		 * object per {@code setRotationPoint} call, and a part is an array of them, so a gun with
		 * fifty boxes has fifty pivots. Hoisting one pivot to the part and applying it to every
		 * box moves the boxes that never asked for it — which is what collapsed whole models onto
		 * a single point.
		 */
		public float pivotX, pivotY, pivotZ;
		public boolean hasPivot;
		/** Renderer-local SRG rotations, in degrees (same Y/Z/X order as TMT.render). */
		public float rotationX, rotationY, rotationZ;
		/** Each ModelRendererTurbo owns a UV offset and sheet; arrays may mix many offsets. */
		public int textureOffsetX, textureOffsetY, sheetWidth, sheetHeight;
		public boolean hasTexture;

		public boolean isPlainBox()
		{
			for(float v : offsets)
				if(v != 0F)
					return false;
			return true;
		}
	}

	/** An {@code addShape3D} prism: a 2D polygon extruded along the facing axis. */
	public static final class Prism
	{
		public float x, y, z;
		public float depth;
		public int shapeTexW = 1, shapeTexH = 1, sideTexW = 1, sideTexH = 1;
		/** ModelRendererTurbo.MR_* — which face the polygon is drawn on. */
		public int direction = 1;
		/** Polygon vertices as {@code x, y, u, v}. */
		public final List<float[]> coords = new ArrayList<>();

		/** See {@link Box#renderer}. */
		public String renderer;
		/** See {@link Box#pivotX}: the pivot of the renderer this prism belongs to. */
		public float pivotX, pivotY, pivotZ;
		public boolean hasPivot;
	}

	/** One model part: everything assigned to a single {@code ModelRendererTurbo[]} field. */
	public static final class Part
	{
		/** The field name, e.g. {@code gunModel} or {@code turretModel}. */
		public String name;
		/** Texture offset this part's pixels start at. */
		public int textureOffsetX, textureOffsetY;
		/** The texture page size the offsets are relative to. */
		public int sheetWidth = 256, sheetHeight = 256;
		/**
		 * The part's fixed pose ({@code rotateAngleX/Y/Z} in the class), in degrees.
		 * The class writes radians; this holds degrees because that is what the rig format and
		 * the renderer both use.
		 *
		 * <p>This one really is per part: a {@code rotateAngle} assignment targets a field of the
		 * renderer array rather than a single element, so it applies to the whole array. (1.7.10
		 * models never set it; 1.12.2 sources do.)
		 */
		public float rotationAngleX, rotationAngleY, rotationAngleZ;

		public final List<Box> boxes = new ArrayList<>();
		public final List<Prism> prisms = new ArrayList<>();

		public int elementCount()
		{
			return boxes.size() + prisms.size();
		}
	}

	/** A whole model. */
	public static final class Model
	{
		/** The class name, e.g. {@code com.flansmod.client.model.mw.ModelAK47}. */
		public String className = "";
		/** The name without package or the {@code Model} prefix, for matching against a definition. */
		public String simpleName = "";
		/** The direct superclass, which says what kind of thing this is. */
		public String superName = "";
		/** The model class's own {@code textureX} / {@code textureY} fields. */
		public int sheetX = 128, sheetY = 128;
		public final Map<String, Part> parts = new LinkedHashMap<>();
		/** Whether the composed constructor transform flips Y and Z (an odd number of flipAll calls). */
		public boolean flipped;

		/**
		 * A scale the class applies inside its own {@code render} method, multiplied into the
		 * baked vertices.
		 *
		 * <p>1.7.10 called {@code GL11.glScalef} at the top of a renderer rather than baking it
		 * into the boxes, so the extractor cannot see it. Only one model needs it so far —
		 * {@code ModelBullet}, which halves itself before drawing its box, and which the packs
		 * name as {@code Model Bullet} rather than shipping.</p>
		 */
		public float renderScale = 1F;

		/**
		 * Rotation points, keyed by {@code field#index}.
		 *
		 * <p>Filled while the class is scanned, then pushed onto the elements by
		 * {@link #applyPivots()} — the geometry and the pivot are separate statements, and either
		 * may come first, so neither can be resolved on the spot.
		 */
		public final Map<String, float[]> pivots = new LinkedHashMap<>();

		/**
		 * {@code ModelGun.thirdPersonOffset} — where the gun sits in third person.
		 *
		 * <p>This is per-model data, not a constant: packs use it to pull a weapon in or out
		 * relative to the standard hand position, and a model that sets it renders visibly off
		 * without it. Zero for models that do not.
		 */
		public float thirdPersonOffsetX, thirdPersonOffsetY, thirdPersonOffsetZ;
		/** {@code ModelGun.itemFrameOffset} — the same idea for dropped items. */
		public float itemFrameOffsetX, itemFrameOffsetY, itemFrameOffsetZ;
		/**
		 * {@code ModelGun.stanceRotate} — an extra rotation for the first-person pose.
		 *
		 * <p>Only newer packs set this; 1.7.10's own {@code RenderGun} has no such field, so a
		 * pack built against 1.7.10 leaves it zero and nothing changes.
		 */
		public float stanceRotateX, stanceRotateY, stanceRotateZ;
		/**
		 * {@code ModelGun.stanceTranslate} — where the gun sits while the stance is active, and
		 * {@code ModelGun.fancyStance} — whether the gun uses a stance at all.
		 *
		 * <p>Together with {@code stanceRotate} these are the pack's "carry" pose: the FMUM build
		 * of the game blends the resting pose toward this one while the player is running, which
		 * is the pose most players see most of the time. A model that never sets them keeps a zero
		 * offset and renders exactly as plain 1.7.10 did.
		 */
		public float stanceTranslateX, stanceTranslateY, stanceTranslateZ;
		public boolean fancyStance;

		/**
		 * Pose vectors from the FMUM family, consumed by the legacy first-person pose and arms.
		 *
		 * <p>"Flan's Mod Ultimate 1.1 Modified" and its relatives get their first-person pose from
		 * per-gun vectors such as {@code holdingTranslate}, {@code sprintRotate} or
		 * {@code takeOutTranslate} — names plain 1.7.10 and the 1.51-era build never assign. The
		 * renderer selects the FMUM pose only when these fields are present; older guns retain
		 * the native 1.7.10 fallback. Values are {@code {x, y, z}} exactly as the class assigned them.
		 */
		public final Map<String, float[]> fmumPose = new LinkedHashMap<>();
		/** Numeric FMUM settings, including inherited constructor defaults supplied by the pose code. */
		public final Map<String, Float> fmumScalars = new LinkedHashMap<>();
		public final Map<String, float[]> fmumArrays = new LinkedHashMap<>();
		/** Bullet seating/follower positions are arrays of vectors, not one pose vector. */
		public final Map<String, float[][]> fmumVectorArrays = new LinkedHashMap<>();
		/** SRG-named rotateAngle assignments, keyed by renderer rather than the whole array. */
		public final Map<String, float[]> rendererRotations = new LinkedHashMap<>();
		public final Map<String, int[]> rendererTextures = new LinkedHashMap<>();

		// ------------------------------------------------------------ gun animation parameters
		//
		// These are what RenderGun reads to animate a gun, and they live on the model class, not on
		// the definition — two guns sharing a definition but shipping different model classes can
		// animate differently. Defaults are the ones ModelGun declares.

		/** How far the slide travels on firing, in model units. */
		public float gunSlideDistance = 1F / 4F;

		/**
		 * How far the whole gun kicks back when it fires, in blocks, and how far it rotates.
		 *
		 * <p>These are the model class's {@code RecoilSlideDistance} and
		 * {@code RotateSlideDistance}. 1.7.10's own {@code ModelGun} had neither — the kick is part
		 * of the later lineage the packs were authored against, and every Warfare44 gun sets it —
		 * so a model that leaves the defaults gets the standard kick rather than none.
		 */
		public float recoilSlideDistance = 0.125F;
		public float rotateSlideDistance = -3F;
		/** Random corner-to-corner wobble added while the gun is kicking. Zero disables it. */
		public float shakeDistance = 0F;
		/** Which of the 17 clip-loading animations this gun uses; {@code EnumAnimationType} name. */
		public String animationType = "NONE";
		/** Reload phases, as fractions of the whole reload: tilt, unload, load, untilt. */
		public float tiltGunTime = 0.25F, unloadClipTime = 0.25F, loadClipTime = 0.25F, untiltGunTime = 0.25F;
		/** Delayed pump-action timing, in ticks. */
		public int pumpDelay = 0, pumpDelayAfterReload = 0, pumpTime = 1;
		/** How far the pump handle travels. */
		public float pumpHandleDistance = 4F / 16F;
		/** Travel of a shell in a tube-loading shotgun. */
		public float endLoadedAmmoDistance = 1F;
		/** Revolver cylinder flip, and its pivot. */
		public float revolverFlipAngle = 15F;
		public float[] revolverFlipPoint = new float[3];
		/** How far a break-action barrel opens, and its pivot. */
		public float breakAngle = 45F;
		public float[] barrelBreakPoint = new float[3];
		/** Where a minigun's barrels spin. */
		public float[] minigunBarrelOrigin = new float[3];
		/** The gun's own reload pose: rotate the whole gun by these while reloading. */
		public float rotateGunVertical, rotateGunHorizontal, tiltGun;
		public float[] translateGun = new float[3];
		/** The clip's own reload pose. */
		public float rotateClipVertical, rotateClipHorizontal, tiltClip;
		public float[] translateClip = new float[3];
		/** Where the cocking handle spins, and how many rounds a reload animation shows. */
		public float[] spinPoint = new float[3];
		public float numBulletsInReloadAnimation = 1;
		/** Which part binds to which animation. */
		public boolean scopeIsOnSlide, scopeIsOnBreakAction, gripIsOnPump, spinningCocking;

		/**
		 * Applies a {@code translateAll(x,y,z)} call, which the model classes use to shift a
		 * whole model into place after building it around its own origin.
		 *
		 * <p>{@code ModelGun.translateAll} adds the offset to every renderer's rotation point —
		 * including renderers that never had one set explicitly, which is why entries are created
		 * for keys that have no pivot yet. The values are often large: {@code ModelSten} shifts by
		 * {@code (-20, 14.75, 0.055)}, so ignoring this leaves the gun a block away from the hand.
		 *
		 * <p>Accumulated rather than applied on the spot: a class may build its geometry in an
		 * {@code initXxxModel()} method and call {@code translateAll} in another, so the call can
		 * be scanned before the pivots it has to move exist. The composed translation is applied
		 * at the end; a subsequent flipAll must also flip the translation accumulated so far.
		 */
		public void translateAll(float x, float y, float z)
		{
			translateAllX += x;
			translateAllY += y;
			translateAllZ += z;
		}

		public float translateAllX, translateAllY, translateAllZ;

		/** Compose the original flip with previous translations; translation and flip do not commute. */
		public void flipAll()
		{
			flipped = !flipped;
			translateAllY = -translateAllY;
			translateAllZ = -translateAllZ;
		}

		/** Gives a pivot entry to every renderer that has geometry but no explicit rotation point. */
		public void ensurePivots()
		{
			for(Part part : parts.values())
			{
				for(Box box : part.boxes)
					pivots.putIfAbsent(box.renderer, new float[] { 0F, 0F, 0F });
				for(Prism prism : part.prisms)
					pivots.putIfAbsent(prism.renderer, new float[] { 0F, 0F, 0F });
			}
		}

		/** Copies each renderer's pivot onto the elements that renderer owns. */
		public void applyPivots()
		{
			// Every renderer that carries geometry needs a pivot, even if the class never set one
			// on it, because translateAll moves all of them.
			ensurePivots();

			// The constructor calls have already been composed into a flip and translation.
			// Applying that final affine transform here retains their original order, including
			// translate-then-flip and repeated flips. Mirroring Y and Z preserves winding.
			for(float[] pivot : pivots.values())
			{
				if(flipped)
				{
					pivot[1] = -pivot[1];
					pivot[2] = -pivot[2];
				}
				pivot[0] += translateAllX;
				pivot[1] += translateAllY;
				pivot[2] += translateAllZ;
			}

			for(Part part : parts.values())
			{
				for(Box box : part.boxes)
					applyPivot(box, box.renderer);
				for(Prism prism : part.prisms)
					applyPivot(prism, prism.renderer);
			}
		}

		private void applyPivot(Box box, String renderer)
		{
			int[] texture = rendererTextures.get(renderer);
			if(texture != null)
			{
				box.textureOffsetX = texture[0]; box.textureOffsetY = texture[1];
				box.sheetWidth = texture[2]; box.sheetHeight = texture[3]; box.hasTexture = true;
			}
			float[] rotation = rendererRotations.get(renderer);
			if(rotation != null)
			{
				box.rotationX = rotation[0]; box.rotationY = rotation[1]; box.rotationZ = rotation[2];
			}
			float[] pivot = pivots.get(renderer);
			if(pivot == null)
				return;
			box.pivotX = pivot[0];
			box.pivotY = pivot[1];
			box.pivotZ = pivot[2];
			box.hasPivot = true;
		}

		private void applyPivot(Prism prism, String renderer)
		{
			float[] pivot = pivots.get(renderer);
			if(pivot == null)
				return;
			prism.pivotX = pivot[0];
			prism.pivotY = pivot[1];
			prism.pivotZ = pivot[2];
			prism.hasPivot = true;
		}

		public Part part(String name)
		{
			return parts.computeIfAbsent(name, n ->
			{
				Part p = new Part();
				p.name = n;
				return p;
			});
		}

		public int elementCount()
		{
			int n = 0;
			for(Part p : parts.values())
				n += p.elementCount();
			return n;
		}

		/** The part names in declaration order, for the log and for slot mapping. */
		public List<String> partNames()
		{
			return new ArrayList<>(parts.keySet());
		}

		@Override
		public String toString()
		{
			return simpleName + "[" + parts.size() + " parts, " + elementCount() + " elements]";
		}
	}
}
