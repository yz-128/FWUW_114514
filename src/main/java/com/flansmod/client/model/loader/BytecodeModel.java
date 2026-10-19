package com.flansmod.client.model.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.flansmod.client.model.loader.ModelGeometry.Box;
import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.client.model.loader.ModelGeometry.Prism;
import com.flansmod.client.model.loader.ModelGeometry.Part;

/**
 * Reads a 1.7.10 {@code ModelXxx.class} and returns the geometry it builds.
 *
 * <p>Content packs ship their models as compiled classes for a Minecraft that no longer exists,
 * so they cannot be loaded — but they do not need to be. A model constructor is a straight list
 * of geometry calls whose arguments are all compile-time constants, so the class is
 * <em>interpreted</em> rather than executed: the same technique the offline converter uses,
 * running in the game instead of ahead of time. That is what lets a pack work unmodified.
 *
 * <p>Reading the bytecode is done with ASM, which Minecraft itself provides (ModLauncher and
 * Mixin both need it), so there is no bundled copy to drift out of date.
 *
 * <p>The layout of the emitted geometry follows {@code RootModelRenderer}/{@code ModelGun}:
 * parts are named after the field they were assigned to ({@code gunModel}, {@code ammoModel},
 * {@code turretModel}, …), and each part carries its own texture offset, sheet size and rotation
 * point, because the old renderer applied those per array rather than per box.
 */
public final class BytecodeModel
{
	/** ModelRendererTurbo's public direction constants. */
	private static final int MR_FRONT = 0, MR_BACK = 1, MR_LEFT = 2, MR_RIGHT = 3, MR_TOP = 4, MR_BOTTOM = 5;

	private BytecodeModel() { }

	// ---------------------------------------------------------------- entry points

	public static Model fromFile(Path classFile) throws IOException
	{
		try(InputStream in = Files.newInputStream(classFile))
		{
			return fromStream(in);
		}
	}

	public static Model fromStream(InputStream in) throws IOException
	{
		ClassNode cn = new ClassNode();
		new ClassReader(in).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return fromClassNode(cn);
	}

	// ---------------------------------------------------------------- extraction

	private static Model fromClassNode(ClassNode cn)
	{
		Model model = new Model();
		model.className = cn.name.replace('/', '.');
		model.simpleName = simpleNameOf(cn.name);
		model.superName = cn.superName == null ? "" : cn.superName.replace('/', '.');

		Set<String> arrays = rendererArrays(cn);
		for(MethodNode method : cn.methods) {
			captureFloatArrays(model, method);
			captureVectorArrays(model, method);
		}

		// The sheet size lives in the class's own textureX/textureY fields, and those values are
		// also what the ModelRendererTurbo constructor reads — so they are resolved first and
		// substituted whenever the constructor appears. They may be assigned in any method, not
		// only the constructor, so every method is inspected.
		int sheetX = 128, sheetY = 128;
		for(MethodNode m : cn.methods)
		{
			if(m.instructions == null)
				continue;
			for(AbstractInsnNode insn : m.instructions)
				if(insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
						&& ("textureX".equals(fi.name) || "textureY".equals(fi.name)))
				{
					Integer value = intOf(fi.getPrevious());
					if(value != null)
					{
						if("textureX".equals(fi.name)) sheetX = value;
						else sheetY = value;
					}
				}
		}
		model.sheetX = sheetX;
		model.sheetY = sheetY;

		// Every method is scanned, not just the constructor: two generations of content packs
		// coexist and they place their geometry differently. The older classes build everything
		// inline in the constructor; the newer ones split it into `initbodyModel_1()`,
		// `initturretModel_1()` and so on, which the constructor merely calls. Scanning only the
		// constructor silently loses every vehicle and plane from the newer packs — which is
		// exactly the difference between 210 and 257 models in the reference set.
		MethodNode init = constructor(cn);
		if(init != null)
			scan(init, cn, model, arrays, sheetX, sheetY);
		for(MethodNode m : cn.methods)
			if(m != init && m.instructions != null && m.instructions.size() > 0)
				scan(m, cn, model, arrays, sheetX, sheetY);

		// A pivot is a property of the renderer object, and the class sets it in a separate
		// statement from the geometry — sometimes before, sometimes after. Now that every method
		// has been read, each element can be handed the pivot of the renderer it belongs to.
		model.applyPivots();

		return model;
	}

	/** The fields declared as {@code ModelRendererTurbo[]}. */
	private static Set<String> rendererArrays(ClassNode cn)
	{
		Set<String> names = new HashSet<>();
		for(FieldNode f : cn.fields)
			if(f.desc != null && f.desc.startsWith("[L") && f.desc.contains("ModelRendererTurbo"))
				names.add(f.name);
		return names;
	}

	private static MethodNode constructor(ClassNode cn)
	{
		for(MethodNode m : cn.methods)
			if("<init>".equals(m.name))
				return m;
		return null;
	}

	/**
	 * Walks the constructor, turning each geometry call into a {@link Box} or {@link Prism}.
	 *
	 * <p>The compiler emits a call as: receiver, then the arguments as pushes, then the call
	 * instruction. So every constant pushed since the previous call is that call's argument list
	 * — which is exactly how the shapes are recovered. A {@code AALOAD} between the field read
	 * and the call supplies the array index, and a {@code DUP}/{@code AASTORE} pair is what
	 * stores a freshly built renderer into the array.
	 */
	private static void scan(MethodNode mn, ClassNode cn, Model model, Set<String> arrays,
							 int sheetX, int sheetY)
	{
		List<Object> consts = new ArrayList<>();
		List<float[]> capturedCoords = new ArrayList<>();
		Set<Integer> localArrays = new HashSet<>();

		String currentField = null;
		String effectVector = null;
		int currentIndex = 0;
		int pendingDup = -1;

		// The shapes added since the last renderer object was constructed. ModelRendererTurbo
		// instances are built one at a time and stored into the array, and each carries its own
		// pivot, so this is the set a setRotationPoint call applies to.
		List<Object> elementShapes = new ArrayList<>();

		for(AbstractInsnNode insn : mn.instructions)
		{
			int op = insn.getOpcode();

			// ---- a local array of renderers, as build helpers use
			if(op == Opcodes.ASTORE && insn instanceof org.objectweb.asm.tree.VarInsnNode vi)
			{
				if(isRendererArrayStore(mn, insn, arrays))
					localArrays.add(vi.var);
				continue;
			}
			if(op == Opcodes.ALOAD && insn instanceof org.objectweb.asm.tree.VarInsnNode vi
					&& localArrays.contains(vi.var))
			{
				currentField = "local" + vi.var;
				consts.clear();
				continue;
			}

			if(op == Opcodes.AALOAD)
			{
				if(!consts.isEmpty() && isInt(consts.get(consts.size() - 1)))
					currentIndex = (int) num(consts.remove(consts.size() - 1));
				consts.clear();
				continue;
			}
			if(op == Opcodes.DUP)
			{
				pendingDup = (!consts.isEmpty() && isInt(consts.get(consts.size() - 1)))
						? (int) num(consts.get(consts.size() - 1)) : -1;
				continue;
			}
			if(op == Opcodes.AASTORE)
			{
				if(pendingDup >= 0)
					currentIndex = pendingDup;
				consts.clear();
				continue;
			}
			if(op == Opcodes.PUTFIELD)
			{
				// rotateAngleX/Y/Z assignments set the part's rotation; the port records them on
				// the part so the rig keeps the pose the class asked for.
				if(effectVector!=null && consts.size()>=1 && insn instanceof FieldInsnNode fi
						&& fi.owner.endsWith("Vector3f") && fi.desc.equals("F") && List.of("x","y","z").contains(fi.name)) {
					float[] point=model.fmumPose.computeIfAbsent(effectVector,k->new float[3]);
					point[fi.name.equals("x")?0:fi.name.equals("y")?1:2]=num(consts.get(consts.size()-1));
					effectVector=null;
				}
				else if(consts.size() >= 1 && insn instanceof FieldInsnNode fi
						&& (fi.name.equals("field_78795_f") || fi.name.equals("field_78796_g") || fi.name.equals("field_78808_h")))
				{
					// EFTArm uses the 1.7.10 SRG names for rotateAngleX/Y/Z.
					String renderer = (currentField == null ? "gunModel" : currentField) + "#" + currentIndex;
					float[] angles = model.rendererRotations.computeIfAbsent(renderer, k -> new float[3]);
					int axis = fi.name.equals("field_78795_f") ? 0 : fi.name.equals("field_78796_g") ? 1 : 2;
					angles[axis] = (float)Math.toDegrees(num(consts.get(consts.size() - 1)));
				}
				else if(consts.size() >= 1 && insn instanceof FieldInsnNode fi
						&& fi.name.startsWith("rotateAngle"))
				{
					Part part = model.part(currentField == null ? "gunModel" : currentField);
					float radians = num(consts.get(consts.size() - 1));
					float degrees = (float) Math.toDegrees(radians);
					switch(fi.name)
					{
						case "rotateAngleX" -> part.rotationAngleX = degrees;
						case "rotateAngleY" -> part.rotationAngleY = degrees;
						case "rotateAngleZ" -> part.rotationAngleZ = degrees;
					}
				}
				else if(consts.size() >= 3 && insn instanceof FieldInsnNode fi && fi.desc.startsWith("L") && fi.desc.endsWith("Vector3f;"))
				{
					// Model-level Vector3f settings, stored on the model rather than on geometry.
					// The three floats are what the Vector3f constructor was handed; the constructor
					// case below deliberately leaves them in the list for this to pick up.
					// The descriptor goes along so an unknown name is only captured when the field
					// really is a Vector3f — a scalar field that happens to be preceded by three
					// constants must not be misread as one.
					int b = consts.size() - 3;
					vectorField(model, fi.name, fi.desc, num(consts.get(b)), num(consts.get(b + 1)), num(consts.get(b + 2)));
				}
				else if(consts.size() >= 1 && insn instanceof FieldInsnNode fi)
				{
					// Scalar model settings: the animation distances and timings RenderGun reads.
					// Booleans arrive as the ints 0 and 1 from iconst, which the same path reads.
					float value = num(consts.get(consts.size() - 1));
					if(!scalarField(model, fi.name, value))
					{
						// Not one we model — drop it rather than leaving it to be misread as an
						// argument to whatever call comes next.
						consts.clear();
						continue;
					}
				}
				consts.clear();
				continue;
			}

			// ---- constants
			Object pushed = push(insn);
			if(pushed != null)
			{
				consts.add(pushed);
				continue;
			}

			if(insn instanceof FieldInsnNode fi)
			{
				if(fi.getOpcode() == Opcodes.GETFIELD)
				{
					if(fi.desc.endsWith("Vector3f;") && (List.of("muzzleFlashPoint","barrelOrigin","chamberSmokeOri","caseOrigin",
							"pupilRefCenter","scopeGlassModelScale","scopeObjectiveLensCenter","normalVecOfDotBorder").contains(fi.name) || fi.name.startsWith("caseEject"))) {
						effectVector=fi.name;consts.clear();continue;
					}
					if(arrays.contains(fi.name) || "[Lcom/flansmod/client/tmt/ModelRendererTurbo;".equals(fi.desc))
					{
						currentField = fi.name;
						continue;
					}
					if("textureX".equals(fi.name)) { consts.add(sheetX); continue; }
					if("textureY".equals(fi.name)) { consts.add(sheetY); continue; }
				}
				else if(fi.getOpcode() == Opcodes.GETSTATIC && fi.desc.contains("EnumAnimationType"))
				{
					// `animationType = EnumAnimationType.BOTTOM_CLIP` compiles to GETSTATIC then
					// PUTFIELD, so this is the only chance to see which one was chosen. The enum
					// name is the field name.
					model.animationType = fi.name;
					continue;
				}
				continue;
			}

			if(insn instanceof MethodInsnNode mi)
			{
				// Coord2D(double x, double y, int u, int v) — the polygon of an addShape3D.
				if(mi.owner.endsWith("Coord2D") && "<init>".equals(mi.name) && consts.size() >= 4)
				{
					int b = consts.size() - 4;
					capturedCoords.add(new float[] {
							num(consts.get(b)), num(consts.get(b + 1)),
							num(consts.get(b + 2)), num(consts.get(b + 3)) });
					consts.clear();
					continue;
				}

				if(effectVector!=null && mi.owner.endsWith("Vector3f") && mi.name.equals("set")
						&& (mi.desc.equals("(FFF)V") || mi.desc.equals("(F)V"))) {
					int count=mi.desc.equals("(F)V")?1:3;
					if(consts.size()>=count) {
						int b=consts.size()-count;
						float x=num(consts.get(b)),y=count==1?x:num(consts.get(b+1)),z=count==1?x:num(consts.get(b+2));
						model.fmumPose.put(effectVector,new float[]{x,y,z});
					}
					effectVector=null;
				}
				else if(mi.owner.endsWith("ModelRendererTurbo"))
					handle(mi, consts, capturedCoords, model, currentField, currentIndex,
							pendingDup, sheetX, sheetY);
				else if("flipAll".equals(mi.name))
				{
					// flipAll is called on the model, not on a renderer, so it never reaches
					// handle(). Compose it with earlier translations in the constructor's order.
					model.flipAll();
				}
				else if("translateAll".equals(mi.name) && consts.size() >= 3)
				{
					// ModelGun/ModelDriveable.translateAll(x,y,z): shifts every renderer's rotation
					// point, which is how packs move a model that was built around its own origin
					// into the space the game draws it in. Recorded and applied after the whole
					// class has been read; see ModelGeometry.translateAll.
					int b = consts.size() - 3;
					model.translateAll(num(consts.get(b)), num(consts.get(b + 1)), num(consts.get(b + 2)));
				}
				else if(mi.owner.endsWith("Vector3f") && "<init>".equals(mi.name))
				{
					// new Vector3f(x, y, z) — the arguments are already the last three constants, and
					// the putfield that follows is what knows which field they belong to. Deliberately
					// do NOT clear: the assignment reads them from this same list.
					continue;
				}

				consts.clear();
			}
		}
	}

	private static void handle(MethodInsnNode mi, List<Object> consts, List<float[]> capturedCoords,
							   Model model, String field, int index, int pendingDup,
							   int sheetX, int sheetY)
	{
		// Which renderer object this call is on: `gunModel[3]` and `turretModel[0]` are different
		// objects with different pivots, and the geometry each carries has to stay attached to it.
		String renderer = rendererKey(field, index);

		switch(mi.name)
		{
			case "<init>" -> {
				// (ModelBase, offsetX, offsetY, sheetW, sheetH)
				if(consts.size() < 4)
					return;
				int b = consts.size() - 4;
				Part part = partFor(model, field);
				part.textureOffsetX = (int) num(consts.get(b));
				part.textureOffsetY = (int) num(consts.get(b + 1));
				part.sheetWidth = (int) num(consts.get(b + 2));
				part.sheetHeight = (int) num(consts.get(b + 3));
				model.rendererTextures.put(rendererKey(field, pendingDup >= 0 ? pendingDup : index),
						new int[] { part.textureOffsetX, part.textureOffsetY, part.sheetWidth, part.sheetHeight });
			}
			case "setTextureOffset" -> {
				if(consts.size() < 2)
					return;
				int b = consts.size() - 2;
				Part part = partFor(model, field);
				part.textureOffsetX = (int) num(consts.get(b));
				part.textureOffsetY = (int) num(consts.get(b + 1));
				model.rendererTextures.put(renderer, new int[] {
						part.textureOffsetX, part.textureOffsetY, part.sheetWidth, part.sheetHeight });
			}
			case "addShapeBox" -> {
				// (float x,y,z, int w,h,d, float scale, then eight corner offsets)
				if(consts.size() < 31)
					return;
				int b = consts.size() - 31;
				Box box = new Box();
				box.x = num(consts.get(b));
				box.y = num(consts.get(b + 1));
				box.z = num(consts.get(b + 2));
				box.w = (int) num(consts.get(b + 3));
				box.h = (int) num(consts.get(b + 4));
				box.d = (int) num(consts.get(b + 5));
				box.scale = num(consts.get(b + 6));
				for(int i = 0; i < 24; i++)
					box.offsets[i] = num(consts.get(b + 7 + i));
				box.renderer = renderer;
				partFor(model, field).boxes.add(box);
			}
			case "addBox", "func_78790_a" -> {
				// (float x,y,z, int w,h,d, float expansion)
				if(consts.size() < 7)
					return;
				int b = consts.size() - 7;
				Box box = new Box();
				box.x = num(consts.get(b));
				box.y = num(consts.get(b + 1));
				box.z = num(consts.get(b + 2));
				box.w = (int) num(consts.get(b + 3));
				box.h = (int) num(consts.get(b + 4));
				box.d = (int) num(consts.get(b + 5));
				box.scale = num(consts.get(b + 6));
				box.renderer = renderer;
				partFor(model, field).boxes.add(box);
			}
			case "addTrapezoid" -> {
				// (FFFIII F F I): x,y,z,w,h,d,scale,bottomScale,dir
				if(consts.size() < 9)
					return;
				int b = consts.size() - 9;
				Box box = new Box();
				box.x = num(consts.get(b));
				box.y = num(consts.get(b + 1));
				box.z = num(consts.get(b + 2));
				box.w = (int) num(consts.get(b + 3));
				box.h = (int) num(consts.get(b + 4));
				box.d = (int) num(consts.get(b + 5));
				box.scale = num(consts.get(b + 6));
				applyTrapezoid(box, num(consts.get(b + 7)), (int) num(consts.get(b + 8)));
				box.renderer = renderer;
				partFor(model, field).boxes.add(box);
			}
			case "setRotationPoint", "func_78793_a" -> {
				// Stored against the renderer, not the part: it is applied after every method has
				// been read, because the class may set it in a different method from the geometry.
				if(consts.size() < 3)
					return;
				int b = consts.size() - 3;
				model.pivots.put(renderer, new float[] {
						num(consts.get(b)), num(consts.get(b + 1)), num(consts.get(b + 2)) });
			}
			case "addShape3D" -> {
				if(consts.size() >= 8 && !capturedCoords.isEmpty())
				{
					int b = consts.size() - 8;
					Prism prism = new Prism();
					prism.x = num(consts.get(b));
					prism.y = num(consts.get(b + 1));
					prism.z = num(consts.get(b + 2));
					prism.depth = num(consts.get(b + 3));
					prism.shapeTexW = (int) num(consts.get(b + 4));
					prism.shapeTexH = (int) num(consts.get(b + 5));
					prism.sideTexW = (int) num(consts.get(b + 6));
					prism.sideTexH = (int) num(consts.get(b + 7));
					prism.direction = consts.size() >= 9 ? (int) num(consts.get(consts.size() - 1)) : MR_BACK;
					prism.coords.addAll(capturedCoords);
					prism.renderer = renderer;
					partFor(model, field).prisms.add(prism);
				}
				capturedCoords.clear();
			}
			case "flipAll" -> model.flipAll();
			default -> { /* not geometry */ }
		}
	}

	/** The key a renderer object is filed under: its array field and the index into it. */
	private static String rendererKey(String field, int index)
	{
		return (field == null ? "gunModel" : field) + "#" + index;
	}

	/**
	 * Stores a Vector3f-valued model field into the right float triple.
	 *
	 * <p>Written as a switch over the field name rather than a lookup table so the compiler
	 * checks that every parameter the renderer reads has somewhere to go.
	 */
	private static boolean vectorField(Model model, String name, String desc, float x, float y, float z)
	{
		switch(name)
		{
			case "thirdPersonOffset" ->
			{
				model.thirdPersonOffsetX = x;
				model.thirdPersonOffsetY = y;
				model.thirdPersonOffsetZ = z;
			}
			case "itemFrameOffset" ->
			{
				model.itemFrameOffsetX = x;
				model.itemFrameOffsetY = y;
				model.itemFrameOffsetZ = z;
			}
			case "stanceRotate" ->
			{
				model.stanceRotateX = x;
				model.stanceRotateY = y;
				model.stanceRotateZ = z;
			}
			case "stanceTranslate" ->
			{
				model.stanceTranslateX = x;
				model.stanceTranslateY = y;
				model.stanceTranslateZ = z;
			}
			case "revolverFlipPoint" -> set(model.revolverFlipPoint, x, y, z);
			case "barrelBreakPoint" -> set(model.barrelBreakPoint, x, y, z);
			case "minigunBarrelOrigin" -> set(model.minigunBarrelOrigin, x, y, z);
			case "translateGun" -> set(model.translateGun, x, y, z);
			case "translateClip" -> set(model.translateClip, x, y, z);
			case "spinPoint" -> set(model.spinPoint, x, y, z);
			default ->
			{
				// Any other Vector3f field: the FMUM family's pose vocabulary — holding*,
				// aiming*, sprint*, takeOut*, rest*, gunModify*, reload*, check*… — plus placement
				// points this port does not read yet (muzzleFlashPoint and friends).
				//
				// Store verbatim for FMUM's first-person pose. The descriptor check prevents a
				// scalar preceded by three constants from being captured as a vector.
				if(desc == null || !desc.endsWith("Vector3f;"))
					return false;
				model.fmumPose.put(name, new float[] { x, y, z });
			}
		}
		return true;
	}

	private static void set(float[] target, float x, float y, float z)
	{
		target[0] = x;
		target[1] = y;
		target[2] = z;
	}

	/**
	 * Stores a scalar model field by name.
	 *
	 * <p>The vectors above and these scalars exist because the renderer, not the geometry, reads
	 * them: they say how far a slide travels or how long a reload takes, and a model that sets one
	 * animates differently from one that leaves the default.
	 *
	 * @return true if the name is one of them, so the caller knows whether to consume the constant
	 */
	private static boolean scalarField(Model model, String name, float value)
	{
		model.fmumScalars.put(name, value);
		switch(name)
		{
			case "gunSlideDistance" -> model.gunSlideDistance = value;
			// The recoil trio. The packs that declare these spell them with a leading capital —
			// they come from the ModelGun generation that made the fields public — while every
			// other scalar here is lower-cased, so both spellings are accepted. A model that
			// leaves them alone keeps the standard kick rather than having none.
			case "RecoilSlideDistance", "recoilSlideDistance" -> model.recoilSlideDistance = value;
			case "RotateSlideDistance", "rotateSlideDistance" -> model.rotateSlideDistance = value;
			case "ShakeDistance", "shakeDistance" -> model.shakeDistance = value;
			case "tiltGunTime" -> model.tiltGunTime = value;
			case "unloadClipTime" -> model.unloadClipTime = value;
			case "loadClipTime" -> model.loadClipTime = value;
			case "untiltGunTime" -> model.untiltGunTime = value;
			case "pumpDelay" -> model.pumpDelay = (int) value;
			case "pumpDelayAfterReload" -> model.pumpDelayAfterReload = (int) value;
			case "pumpTime" -> model.pumpTime = (int) value;
			case "pumpHandleDistance" -> model.pumpHandleDistance = value;
			case "endLoadedAmmoDistance" -> model.endLoadedAmmoDistance = value;
			case "revolverFlipAngle" -> model.revolverFlipAngle = value;
			case "breakAngle" -> model.breakAngle = value;
			case "rotateGunVertical" -> model.rotateGunVertical = value;
			case "rotateGunHorizontal" -> model.rotateGunHorizontal = value;
			case "tiltGun" -> model.tiltGun = value;
			case "rotateClipVertical" -> model.rotateClipVertical = value;
			case "rotateClipHorizontal" -> model.rotateClipHorizontal = value;
			case "tiltClip" -> model.tiltClip = value;
			case "numBulletsInReloadAnimation" -> model.numBulletsInReloadAnimation = value;
			case "scopeIsOnSlide" -> model.scopeIsOnSlide = value != 0F;
			case "scopeIsOnBreakAction" -> model.scopeIsOnBreakAction = value != 0F;
			case "gripIsOnPump" -> model.gripIsOnPump = value != 0F;
			case "spinningCocking" -> model.spinningCocking = value != 0F;
			case "fancyStance" -> model.fancyStance = value != 0F;
			// The sight's reticle plate is scaled by this times the pupil distance. It is a
			// plain field rather than a named setting, so it only lives in the scalar map that
			// FmumActionPose reads; the renderer falls back to 0.4 when a model omits it.
			case "reticleScale" -> { }
			default -> { return false; }
		}
		return true;
	}

	/** Includes writes into arrays allocated by the legacy superclass (notably reticleAt). */
	private static void captureVectorArrays(Model model, MethodNode method)
	{
		if(method.instructions == null) return;
		for(AbstractInsnNode instruction : method.instructions) {
			if(instruction instanceof FieldInsnNode field && field.getOpcode()==Opcodes.PUTFIELD
					&& field.desc.startsWith("[L") && field.desc.endsWith("Vector3f;")) {
				var array=field.getPrevious();
				if(array instanceof org.objectweb.asm.tree.TypeInsnNode && array.getOpcode()==Opcodes.ANEWARRAY) {
					Integer count=intOf(array.getPrevious());
					if(count!=null && count>=0 && count<=256) model.fmumVectorArrays.put(field.name,new float[count][3]);
				}
			}
			if(instruction.getOpcode()!=Opcodes.AASTORE) continue;
			AbstractInsnNode constructor=instruction.getPrevious();
			if(!(constructor instanceof MethodInsnNode call) || !call.owner.endsWith("Vector3f") || !call.name.equals("<init>")) continue;
			java.util.List<Float> constants=new java.util.ArrayList<>();
			AbstractInsnNode cursor=constructor.getPrevious();
			for(int guard=0;cursor!=null && guard++<24;cursor=cursor.getPrevious()) {
				if(cursor instanceof org.objectweb.asm.tree.TypeInsnNode create && create.getOpcode()==Opcodes.NEW) {
					Integer index=intOf(cursor.getPrevious());
					var access=cursor.getPrevious()==null?null:cursor.getPrevious().getPrevious();
					if(access instanceof FieldInsnNode field && field.getOpcode()==Opcodes.GETFIELD && constants.size()==3 && index!=null) {
						float[][] values=model.fmumVectorArrays.get(field.name);
						if(index>=0 && index<256) {
							if(values==null) values=new float[index+1][3];
							else if(index>=values.length) values=java.util.Arrays.copyOf(values,index+1);
							values[index]=new float[]{constants.get(2),constants.get(1),constants.get(0)};
							model.fmumVectorArrays.put(field.name,values);
						}
					}
					break;
				}
				Object value=push(cursor);
				if(value instanceof Number number) constants.add(number.floatValue());
			}
		}
		captureArrayElementSetters(model,method);
	}

	/**
	 * Captures {@code array[i].set(x,y,z)}, which is how most pack models write a reticle point.
	 *
	 * <p>Some classes allocate the array in a base class and then set an element in place — the CIC
	 * N|D Optics ships {@code reticleAt[0].set(4096F, 0.028125F, 0F)} — instead of constructing a
	 * new vector and storing it. Without this those sights have no aiming ray at all, so the
	 * reticle can never find an aperture to land on.
	 */
	private static void captureArrayElementSetters(Model model,MethodNode method)
	{
		if(method.instructions==null) return;
		for(AbstractInsnNode instruction : method.instructions) {
			if(!(instruction instanceof MethodInsnNode call) || !"set".equals(call.name)
					|| !call.owner.endsWith("Vector3f") || !"(FFF)V".equals(call.desc)) continue;
			java.util.List<Float> constants=new java.util.ArrayList<>();
			// Collect the three float args immediately before the call, in stack order.
			AbstractInsnNode cursor=call.getPrevious();
			for(int guard=0;cursor!=null && guard++<12;cursor=cursor.getPrevious()) {
				Object value=push(cursor);
				if(value instanceof Number number) constants.add(number.floatValue());
			}
			if(constants.size()<3) continue;
			int index=-1;
			String arrayName=null;
			for(AbstractInsnNode node=call.getPrevious();node!=null;node=node.getPrevious()) {
				if(node.getOpcode()==Opcodes.AALOAD) {
					Integer value=intOf(node.getPrevious());
					var access=node.getPrevious()==null?null:node.getPrevious().getPrevious();
					if(access instanceof FieldInsnNode field && field.getOpcode()==Opcodes.GETFIELD) {
						index=value==null?-1:value;
						arrayName=field.name;
					}
					break;
				}
			}
			if(arrayName==null || index<0 || index>=256) continue;
			float[][] values=model.fmumVectorArrays.get(arrayName);
			if(values==null) values=new float[index+1][3];
			else if(index>=values.length) values=java.util.Arrays.copyOf(values,index+1);
			values[index]=new float[]{constants.get(2),constants.get(1),constants.get(0)};
			model.fmumVectorArrays.put(arrayName,values);
		}
	}

	/** Literal float arrays retain independent entries, rather than being mistaken for vectors. */
	private static void captureFloatArrays(Model model, MethodNode method)
	{
		if(method.instructions == null) return;
		for(AbstractInsnNode instruction : method.instructions)
		{
			if(!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTFIELD
					|| !"[F".equals(field.desc)) continue;
			java.util.Map<Integer, Float> entries = new java.util.HashMap<>();
			AbstractInsnNode cursor = instruction.getPrevious();
			for(int guard = 0; cursor != null && guard++ < 120; cursor = cursor.getPrevious())
			{
				if(cursor.getOpcode() == Opcodes.FASTORE)
				{
					Object value = push(cursor.getPrevious());
					Integer index = intOf(cursor.getPrevious().getPrevious());
					if(value instanceof Number number && index != null) entries.put(index, number.floatValue());
				}
				if(cursor instanceof IntInsnNode array && array.getOpcode() == Opcodes.NEWARRAY && array.operand == Opcodes.T_FLOAT)
				{
					Integer count = intOf(cursor.getPrevious());
					if(count != null && count >= 0 && count <= 128)
					{
						float[] values = new float[count];
						entries.forEach((index, value) -> { if(index >= 0 && index < values.length) values[index] = value; });
						model.fmumArrays.put(field.name, values);
					}
					break;
				}
				if(cursor.getOpcode() == Opcodes.PUTFIELD) break;
			}
		}
	}

	/** A trapezoid is a shape box whose top or bottom has been narrowed. */
	private static void applyTrapezoid(Box box, float bottomScale, int dir)
	{
		// The four corners of the narrowed face move inwards; which face depends on the
		// direction, matching the old renderer.
		float w = box.w, h = box.h, d = box.d;
		float insetW = (w - w * bottomScale) / 2F;
		float insetD = (d - d * bottomScale) / 2F;
		switch(dir)
		{
			case MR_BOTTOM -> {
				box.offsets[0] += insetW; box.offsets[2] += insetD;
				box.offsets[3] -= insetW; box.offsets[5] += insetD;
				box.offsets[6] += insetW; box.offsets[8] -= insetD;
				box.offsets[9] -= insetW; box.offsets[11] -= insetD;
			}
			case MR_TOP -> {
				box.offsets[12] += insetW; box.offsets[14] += insetD;
				box.offsets[15] -= insetW; box.offsets[17] += insetD;
				box.offsets[18] += insetW; box.offsets[20] -= insetD;
				box.offsets[21] -= insetW; box.offsets[23] -= insetD;
			}
			default -> { }
		}
	}

	private static Part partFor(Model model, String field)
	{
		return model.part(field == null ? "gunModel" : field);
	}

	/** True when this store is putting a renderer array into a local variable. */
	private static boolean isRendererArrayStore(MethodNode mn, AbstractInsnNode store, Set<String> arrays)
	{
		// Walk back to the instruction that produced the value.
		AbstractInsnNode prev = store.getPrevious();
		int guard = 0;
		while(prev != null && guard++ < 40)
		{
			if(prev instanceof MethodInsnNode mi && "<init>".equals(mi.name)
					&& mi.owner.contains("ModelRendererTurbo"))
				return true;
			if(prev instanceof org.objectweb.asm.tree.TypeInsnNode ti && ti.getOpcode() == Opcodes.ANEWARRAY)
				return true;
			if(prev instanceof FieldInsnNode fi && (arrays.contains(fi.name) || fi.desc.startsWith("[L")))
				return true;
			prev = prev.getPrevious();
		}
		return false;
	}

	// ---------------------------------------------------------------- constant decoding

	/** The compile-time value an instruction pushes, or null if it is not a constant. */
	private static Object push(AbstractInsnNode insn)
	{
		int op = insn.getOpcode();
		if(op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5)
			return op - Opcodes.ICONST_0;
		if(op >= Opcodes.FCONST_0 && op <= Opcodes.FCONST_2)
			return (float) (op - Opcodes.FCONST_0);
		if(op >= Opcodes.LCONST_0 && op <= Opcodes.LCONST_1)
			return (long) (op - Opcodes.LCONST_0);
		if(op >= Opcodes.DCONST_0 && op <= Opcodes.DCONST_1)
			return (double) (op - Opcodes.DCONST_0);

		if(insn instanceof IntInsnNode ii)
			return ii.operand;
		if(insn instanceof LdcInsnNode ldc)
		{
			Object cst = ldc.cst;
			if(cst instanceof Integer || cst instanceof Float || cst instanceof Long || cst instanceof Double)
				return cst;
			return null;
		}
		if(insn instanceof InsnNode && op == Opcodes.ACONST_NULL)
			return null;
		return null;
	}

	private static boolean isInt(Object o)
	{
		return o instanceof Integer;
	}

	/** Numeric value of a decoded constant, or 0. */
	private static float num(Object o)
	{
		if(o instanceof Integer i) return i;
		if(o instanceof Float f) return f;
		if(o instanceof Long l) return l;
		if(o instanceof Double d) return d.floatValue();
		return 0F;
	}

	private static Integer intOf(AbstractInsnNode insn)
	{
		Object pushed = push(insn);
		return pushed instanceof Integer i ? i : null;
	}

	/** {@code com/flansmod/client/model/mw/ModelAK47} -> {@code AK47}. */
	static String simpleNameOf(String internalName)
	{
		String simple = internalName.substring(internalName.lastIndexOf('/') + 1);
		if(simple.startsWith("Model"))
			simple = simple.substring(5);
		return simple;
	}
}
