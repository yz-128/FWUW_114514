package com.flansmod.client.model.loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.flansmod.common.FlansMod;
import com.flansmod.common.ContentPackIndex;
import com.flansmod.common.types.InfoType;
import dev.packarms.core.PackIndex;
import dev.packarms.core.PackEntry;
import dev.packarms.core.IndexedPack;
import dev.packarms.core.LazyAssetCache;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

/**
 * Finds, extracts and caches the 3D model for a content-pack definition.
 *
 * <p>A definition names its model as {@code Package.ModelName} — for example
 * {@code W44.Tiger1H} or {@code NewAge.AK47}. Resolving that means finding
 * {@code com/flansmod/client/model/<Package>/Model<Name>.class} inside one of the loaded packs,
 * reading its geometry, and baking it once. All three steps are cached, because the result is
 * immutable and item rendering asks for it every frame.
 *
 * <p>A definition with no usable model is not an error: many pack entries are bullets, parts or
 * armour that were only ever shown as a flat icon, and those keep the generated icon model.
 */
public final class ModelCache
{
	private static final java.util.concurrent.ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
		Thread thread = new Thread(task, "flansmod-model-reader"); thread.setDaemon(true); return thread;
	});
	private record RigKey(String reference, float scale, net.minecraft.resources.ResourceLocation texture,
			boolean skinFallback, boolean clientUv, boolean armour) { }
	private static final class State {
		final PackIndex index;
		final Set<String> missing = ConcurrentHashMap.newKeySet();
		final LazyAssetCache<String, ModelGeometry.Model> geometry;
		final Map<RigKey, BakedRig> rigs = new ConcurrentHashMap<>();
		final Set<RigKey> queued = ConcurrentHashMap.newKeySet();
		final ConcurrentLinkedQueue<RigKey> baking = new ConcurrentLinkedQueue<>();
		final ConcurrentLinkedQueue<RigKey> effectsBaking = new ConcurrentLinkedQueue<>();
		State(PackIndex index) {
			this.index = index;
			geometry = new LazyAssetCache<>(reference -> {
				try {
					var model = read(index, reference);
					if(model == null) missing.add(reference);
					return model;
				} catch(Exception failure) { missing.add(reference); throw failure; }
			}, WORKERS);
		}
	}
	private static volatile State state;
	private static State currentState() {
		var index = ContentPackIndex.current();
		var value = state;
		if(value != null && value.index.generation() == index.generation()) return value;
		synchronized(ModelCache.class) {
			if(state == null || state.index.generation() != index.generation()) {
				var previous = state; state = new State(index);
				if(previous != null) previous.geometry.close();
			}
			return state;
		}
	}
	/** A corrected player skin is queried every frame; report each mismatch only once. */
	private static final java.util.Set<String> UV_WARNINGS = ConcurrentHashMap.newKeySet();

	private ModelCache()
	{
	}

	/** Everything currently loaded, for diagnostics. */
	public static Map<String, BakedRig> loaded()
	{
		Map<String,BakedRig> out = new LinkedHashMap<>();
		currentState().rigs.forEach((key,rig) -> out.put(key.toString(),rig));
		return out;
	}

	/**
	 * Whether the code is running in a client, tolerating environments where NeoForge's loader is
	 * not present at all (the offline bake and preview tools, which run this class directly).
	 */
	private static boolean isClientEnvironment()
	{
		try
		{
			return net.neoforged.fml.loading.FMLEnvironment.dist.isClient();
		}
		catch(Throwable notRunningUnderNeoForge)
		{
			return false;
		}
	}

	/** Live rendering never waits for an asset future. Offline tools retain synchronous access. */
	private static boolean liveClient() {
		if(!isClientEnvironment()) return false;
		var mc = net.minecraft.client.Minecraft.getInstance();
		return mc != null && mc.level != null;
	}
	private static boolean usable(String reference) {
		return reference != null && !reference.isBlank() && !reference.equalsIgnoreCase("none")
				&& !reference.equalsIgnoreCase("null");
	}

	/**
	 * The baked model for a bare class reference, with no definition behind it.
	 *
	 * <p>The arm models a pack attaches to a gun are named on the gun's {@code ArmModel} line
	 * rather than by a definition of their own, so there is no {@link InfoType} to read a texture
	 * or a {@code ModelScale} from. Neither is needed: the arm is drawn at its authored size.
	 *
	 * <p>The UV correction DOES apply, though, and this is where it matters most. {@code
	 * ModelEFTArm} declares a 64×32 sheet — the pre-1.8 player-skin layout — and its coordinates
	 * therefore land at v 0.5..1.0. A modern skin is 64×64, where those same coordinates are the
	 * (usually empty) jacket layer, so an uncorrected arm draws invisible. Correcting the one
	 * differing axis (v × 32/64) puts the coordinates back on the arm at (40,16)-(52,32) — the
	 * same single-axis rule the model baker already applies everywhere else.
	 *
	 * @param texture the skin the arm will actually be drawn with, for that correction; may be null
	 */
	public static BakedRig rigForReference(String reference, float modelScale,
                                           net.minecraft.resources.ResourceLocation texture)
    {
        return rig(reference, modelScale, texture, true);
    }

    private static BakedRig rig(String reference, float scale,
            net.minecraft.resources.ResourceLocation texture, boolean skinFallback) {
        return rig(reference, scale, texture, skinFallback, false);
    }

    private static BakedRig rig(String reference, float scale,
            net.minecraft.resources.ResourceLocation texture, boolean skinFallback, boolean armour) {
        if(!usable(reference)) return null;
        var current = currentState();
        var key = new RigKey(reference, scale, texture, skinFallback, isClientEnvironment(), armour);
        var cached = current.rigs.get(key);
        if(cached != null) return cached;
        if(liveClient()) {
            schedule(current, key);
            return null;
        }
        ModelGeometry.Model geometry;
        try { geometry = current.geometry.get(reference); }
        catch(CompletionException | IllegalStateException failure) { return null; }
        return geometry == null ? null : bake(current, key, geometry);
    }

    private static void schedule(State current, RigKey key) { schedule(current,key,true); }
    private static void schedule(State current, RigKey key, boolean primary) {
        if(!current.geometry.isResolved(key.reference())) current.geometry.request(key.reference());
        if(current.rigs.containsKey(key) || current.missing.contains(key.reference())) return;
        if(current.queued.add(key)) (primary ? current.baking : current.effectsBaking).offer(key);
        else if(primary && current.effectsBaking.remove(key)) current.baking.offer(key);
    }

    private static BakedRig bake(State current, RigKey key, ModelGeometry.Model geometry) {
        float[] uv = key.skinFallback() ? uvScaleFor(geometry, key.texture()) : textureUv(geometry,key.texture());
        var rig = key.armour() ? ArmourGeometry.bake(geometry, key.scale(), uv[0], uv[1])
                : BakedRig.bake(geometry, key.scale(), uv[0], uv[1]);
        // Workers and derived caches are bound to one state; reload never imports their results.
        if(current == state) current.rigs.putIfAbsent(key, rig);
        return rig;
    }

    private static float[] textureUv(ModelGeometry.Model geometry, net.minecraft.resources.ResourceLocation texture) {
        if(texture == null || !isClientEnvironment()) return new float[] {1F,1F};
        int[] actual = TextureSizes.of(texture);
        if(actual == null) return new float[] {1F,1F};
        return uvScaleForDimensions(geometry,actual[0],actual[1]);
    }

    /** Client tick only: a bounded number of derived rigs, never a blocking future. */
    public static int bakePending(int limit) {
        var current = currentState();
        int attempts = Math.min(32,current.baking.size()+current.effectsBaking.size()), baked = 0;
        while(attempts-- > 0 && baked < limit) {
            var key = current.baking.poll();
            boolean primary = key != null;
            if(key == null) key = current.effectsBaking.poll();
            if(key == null) break;
            var model = current.geometry.peek(key.reference());
            if(model.isPresent()) {
                bake(current,key,model.get()); current.queued.remove(key); baked++;
            } else if(current.geometry.isResolved(key.reference())) {
                current.queued.remove(key);
            } else (primary ? current.baking : current.effectsBaking).offer(key);
        }
        return baked;
    }

    public static void prewarmReference(String reference, float scale,
            net.minecraft.resources.ResourceLocation texture, boolean skinFallback) {
        if(!usable(reference)) return;
        schedule(currentState(), new RigKey(reference,scale,texture,skinFallback,isClientEnvironment(),false));
    }
    public static void prewarm(InfoType type) {
        if(type != null) prewarmReference(type.modelString,type.modelScale,
                com.flansmod.client.render.Textures.forType(type),false);
    }

    public static void prewarmEffectReference(String reference, float scale,
            net.minecraft.resources.ResourceLocation texture) {
        if(usable(reference)) schedule(currentState(),new RigKey(reference,scale,texture,true,isClientEnvironment(),false),false);
    }

    public static BakedRig rigForArmour(com.flansmod.common.types.ArmourType type,
            net.minecraft.resources.ResourceLocation texture) {
        return rig(type.modelString, type.modelScale, texture, false, true);
    }

    public static void prewarmArmour(com.flansmod.common.types.ArmourType type,
            net.minecraft.resources.ResourceLocation texture) {
        if(usable(type.modelString)) schedule(currentState(),new RigKey(type.modelString,type.modelScale,
                texture,false,isClientEnvironment(),true));
    }

    public static LazyAssetCache.Stats geometryStats() { return currentState().geometry.stats(); }
    public static long indexGeneration() { return currentState().index.generation(); }
    public static boolean geometryResolved(String reference) { return currentState().geometry.isResolved(reference); }

	/** {@link #rigForReference} without a texture, so no UV correction is attempted. */
	public static BakedRig rigForReference(String reference, float modelScale)
	{
		return rigForReference(reference, modelScale, null);
	}

	/** The single-axis UV correction for a texture, or {@code {1,1}} when nothing applies. */
	private static float[] uvScaleFor(ModelGeometry.Model geometry, net.minecraft.resources.ResourceLocation texture)
	{
		float[] none = { 1F, 1F };
		if(texture == null || !isClientEnvironment())
			return none;

		int[] actual = TextureSizes.of(texture);
		if(actual == null)
		{
			// A player skin is served by the skin service rather than the resource manager, so
			// its size cannot be read this way. Every modern skin is 64x64 — Mojang normalises
			// the legacy 64x32 layout into the top half of a 64x64 image — so that is what an
			// unreadable skin is taken to be. This is the case the correction exists for: an arm
			// model authored against 64x32 would otherwise sample the modern skin's jacket layer,
			// which is empty, and the arm would draw as nothing at all.
			actual = new int[] { 64, 64 };
		}

		int declaredW = 0, declaredH = 0;
		for(ModelGeometry.Part part : geometry.parts.values())
		{
			declaredW = Math.max(declaredW, part.sheetWidth);
			declaredH = Math.max(declaredH, part.sheetHeight);
		}
		if(declaredW <= 0 || declaredH <= 0)
		{
			declaredW = geometry.sheetX;
			declaredH = geometry.sheetY;
		}
		if(declaredW <= 0 || declaredH <= 0)
			return none;

		boolean widthOk = declaredW == actual[0];
		boolean heightOk = declaredH == actual[1];
		if(widthOk && heightOk)
			return none;
		if(!widthOk && !heightOk)
			return none;                      // genuinely different art, not a mistyped axis
		warnUvCorrection(geometry, texture, declaredW, declaredH, actual);
		return uvScaleForDimensions(geometry, actual[0], actual[1]);
	}

	/** Pure form of the existing single-axis rule, shared by runtime baking and offline audits. */
	public static float[] uvScaleForDimensions(ModelGeometry.Model geometry, int width, int height)
	{
		int declaredW = 0, declaredH = 0;
		for(ModelGeometry.Part part : geometry.parts.values())
		{
			declaredW = Math.max(declaredW, part.sheetWidth);
			declaredH = Math.max(declaredH, part.sheetHeight);
		}
		if(declaredW <= 0 || declaredH <= 0) { declaredW = geometry.sheetX; declaredH = geometry.sheetY; }
		if(declaredW <= 0 || declaredH <= 0 || width <= 0 || height <= 0
				|| (declaredW != width && declaredH != height)) return new float[] { 1F, 1F };
		return new float[] { (float)declaredW / width, (float)declaredH / height };
	}

	/**
	 * The baked model for a definition, or null when the pack ships no 3D model for it.
	 *
	 * @param type       the definition, whose {@code modelString} names the class
	 * @param modelScale the definition's {@code ModelScale}, baked into the vertices
	 */
	public static BakedRig rigFor(InfoType type, float modelScale)
    {
        if(type == null) return null;
        var texture = isClientEnvironment() ? com.flansmod.client.render.Textures.forType(type) : null;
        return rig(type.modelString,modelScale,texture,false);
    }

	private static void warnUvCorrection(ModelGeometry.Model geometry,
			net.minecraft.resources.ResourceLocation texture, int width, int height, int[] actual)
	{
		String key = geometry.simpleName + "#" + texture + "#" + width + "x" + height
				+ "->" + actual[0] + "x" + actual[1];
		if(UV_WARNINGS.add(key))
			FlansMod.LOGGER.warn("Model {} declares a {}x{} texture but {} is {}x{}; rescaling its UVs.",
					geometry.simpleName, width, height, texture, actual[0], actual[1]);
	}

    /** All callers share successful, missing and failed geometry results. */
    public static ModelGeometry.Model load(InfoType type) {
        return type == null ? null : load(type.modelString);
    }
    public static ModelGeometry.Model load(String reference) {
        if(!usable(reference)) return null;
        var current = currentState();
        try {
            if(liveClient()) {
                if(!current.geometry.isResolved(reference)) current.geometry.request(reference);
                return current.geometry.peek(reference).orElse(null);
            }
            return current.geometry.get(reference);
        } catch(CompletionException | IllegalStateException failure) { return null; }
    }

    /** Class presence only: JSON routing and startup diagnostics do not parse the whole pack. */
    public static boolean hasReference(String reference) {
        return usable(reference) && (STOCK_BULLET_CLASS.equals(classNameFor(reference)) || sourcePack(reference).isPresent());
    }

    /** The same ordered class lookup used by the geometry reader, without parsing a class. */
    public static Optional<IndexedPack> sourcePack(String reference) {
        if(!usable(reference)) return Optional.empty();
        String path = classNameFor(reference);
        if(path == null) return Optional.empty();
        for(var pack : ContentPackIndex.current().packs()) {
            if(classEntry(pack,path).isPresent()) return Optional.of(pack);
            if(reference.substring(reference.lastIndexOf('.')+1).startsWith("Model")) {
                int slash = path.lastIndexOf('/')+1;
                if(classEntry(pack,path.substring(0,slash)+"Model"+path.substring(slash)).isPresent()) return Optional.of(pack);
            }
        }
        return Optional.empty();
    }

    private static Optional<PackEntry> classEntry(IndexedPack pack,String path) {
        var exact = pack.exact(path);
        if(exact.isPresent()) return exact;
        if(!pack.archive()) return pack.folded(path);
        // The legacy archive class suffix fallback is case-sensitive, unlike texture lookup.
        return pack.suffix(path).filter(entry -> entry.sourcePath().endsWith(path));
    }

    private static ModelGeometry.Model read(PackIndex index,String reference) throws Exception {
        String path = classNameFor(reference);
        if(path == null) return null;
        Exception lastFailure = null;
        for(var pack : index.packs()) {
            try {
                var entry = classEntry(pack,path);
                if(entry.isEmpty() && reference.substring(reference.lastIndexOf('.')+1).startsWith("Model")) {
                    int slash = path.lastIndexOf('/')+1;
                    entry = classEntry(pack,path.substring(0,slash)+"Model"+path.substring(slash));
                }
                if(entry.isPresent()) try(var input = entry.get().open()) { return BytecodeModel.fromStream(input); }
            } catch(Exception failure) {
                lastFailure = failure;
                FlansMod.LOGGER.warn("Could not read model {} from {}",reference,pack.name(),failure);
            }
        }
        if(path.equals(STOCK_BULLET_CLASS)) return stockBullet();
        if(lastFailure != null) throw lastFailure;
        return null;
    }

	/** The mod's own class path for {@code ModelBullet}, used to recognise a request for it. */
	private static final String STOCK_BULLET_CLASS = "com/flansmod/client/model/ModelBullet.class";

	/**
	 * {@code ModelBullet} as 1.7.10 declared it: one 1×3×1 box, halved at draw time.
	 *
	 * <p>Recreated here rather than compiled as a class because the extractor reads model
	 * geometry out of {@code .class} bytes and this one is ours — writing the same numbers
	 * straight into the geometry avoids teaching the loader to parse a class it already knows.
	 */
	private static ModelGeometry.Model stockBullet()
	{
		ModelGeometry.Model model = new ModelGeometry.Model();
		model.className = "com.flansmod.client.model.ModelBullet";
		model.simpleName = "Bullet";
		model.sheetX = 64;
		model.sheetY = 32;
		// The class's render() opened with GL11.glScalef(0.5F, 0.5F, 0.5F) before drawing, so the
		// drawn round is half these units.
		model.renderScale = 0.5F;

		ModelGeometry.Part part = new ModelGeometry.Part();
		part.name = "bulletModel";
		part.sheetWidth = 64;
		part.sheetHeight = 32;
		part.textureOffsetX = 0;
		part.textureOffsetY = 0;

		ModelGeometry.Box box = new ModelGeometry.Box();
		box.x = -0.5F;
		box.y = -1.5F;
		box.z = -0.5F;
		box.w = 1;
		box.h = 3;
		box.d = 1;
		part.boxes.add(box);

		model.parts.put(part.name, part);
		return model;
	}

	/**
	 * {@code W44.Tiger1H} -> {@code com/flansmod/client/model/W44/ModelTiger1H.class}.
	 *
	 * <p>The path shape comes from 1.7.10's own layout: a definition's {@code Model} field names
	 * the package and class without the {@code Model} prefix, and the loader prepended both.
	 */
	static String classNameFor(String reference)
	{
		String trimmed = reference.trim();
		if(trimmed.isEmpty())
			return null;

		int dot = trimmed.lastIndexOf('.');
		String pkg = dot > 0 ? trimmed.substring(0, dot) : "";
		String simple = dot > 0 ? trimmed.substring(dot + 1) : trimmed;
		if(simple.isEmpty())
			return null;

		StringBuilder path = new StringBuilder("com/flansmod/client/model/");
		if(!pkg.isEmpty())
			path.append(pkg.replace('.', '/')).append('/');
		path.append(simple.startsWith("Model") ? simple : "Model" + simple);
		path.append(".class");
		return path.toString();
	}

    /** Invalidates geometry and rigs; retired workers only complete retired cache futures. */
    public static synchronized void clear() {
        var previous = state;
        state = new State(ContentPackIndex.current());
        if(previous != null) previous.geometry.close();
        UV_WARNINGS.clear();
    }

	/** A one-line summary for the log. */
	public static String summary()
	{
		var current = currentState(); return current.rigs.size() + " model(s) baked, " + current.missing.size() + " without a 3D model; geometry=" + current.geometry.stats();
	}

	/**
	 * The {@code ModelResourceLocation} a standalone model is stored under.
	 *
	 * <p>Models registered through {@code ModelEvent.RegisterAdditional} are only retrievable by
	 * the exact location they were registered with, which is the standalone variant of their id.
	 * Keeping the construction in one place stops the register and the lookup drifting apart —
	 * a mismatch is silent and shows as a blank icon.
	 */
	public static net.minecraft.client.resources.model.ModelResourceLocation iconModelLocation(
			net.minecraft.resources.ResourceLocation id)
	{
		return net.minecraft.client.resources.model.ModelResourceLocation.standalone(id);
	}

	/** Models that were looked for and not found, for diagnosing a pack. */
	public static List<String> missingReferences()
	{
		return new ArrayList<>(currentState().missing);
	}
}
