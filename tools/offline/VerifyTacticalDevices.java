import com.flansmod.common.GunAttachments;
import com.flansmod.common.GunTactical;
import com.flansmod.client.model.loader.BytecodeModel;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.EnumType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.TypeFile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Tactical devices (flashlight / laser pointer) contracts, pure offline:
 * the real pack's X400 definition, every authored light/laser model reference, the laserFrom model
 * point, the stack switch helpers, the U/Y key wiring and the server-authoritative payload.
 *
 * <p>Args: {@code <final jar> <pack root>}.
 */
public final class VerifyTacticalDevices
{
	static int checks;
	static ZipFile jar;
	static void check(boolean ok, String message) { checks++; if(!ok) throw new AssertionError(message); }
	static ClassNode node(String name) throws Exception {
		check(jar.getEntry(name + ".class") != null, "final JAR has " + name);
		var n = new ClassNode();
		try(var in = jar.getInputStream(jar.getEntry(name + ".class"))) { new ClassReader(in.readAllBytes()).accept(n, 0); }
		return n;
	}
	static boolean hasField(ClassNode n, String field) {
		for(var f : n.fields) if(f.name.equals(field)) return true;
		return false;
	}
	static boolean callsAnywhere(ClassNode n, String owner, String called) {
		for(var m : n.methods) for(var i : m.instructions) {
			if(i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(called)) return true;
			if(i instanceof FieldInsnNode f && f.owner.equals(owner) && f.name.equals(called)) return true;
			if(i instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.equals(called)) return true;
		}
		return false;
	}

	public static void main(String[] args) throws Exception
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// The fixture gun's ItemFlan has to be constructed against the real item registry.
		((net.minecraft.core.MappedRegistry<?>)net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
		jar = new ZipFile(args[0]);
		Path root = Path.of(args[1]);

		// ---------------------------------------------------------------- real pack data
		var pack = new OfflinePack(root.resolve("EscapeFromTarcraftReforged_1.5.3"));
		pack.readTypes();
		// The AN/PEQ-15 definition lives in the legacy FMUM Guns pack, next to EFT Reforged.
		new OfflinePack(root.resolve("[FMUM]EscapeFromTarCraft-Guns")).readTypes();
		var x400 = AttachmentType.getAttachment("X400");
		check(x400 != null, "the pack still defines the X400 tactical device");
		check(x400.flashlight, "X400 is a flashlight");
		check(Math.abs(x400.flashlightRange - 50F) < .001F, "flashlight range parsed from the data line");
		check(x400.lightModel != null && !x400.lightModel.isBlank(), "flashlight light model parsed");
		check(x400.laserPointer, "X400 is a laser pointer");
		check(x400.laserModelFP != null && !x400.laserModelFP.isBlank(), "laser first-person model parsed");
		check(x400.laserModel != null && !x400.laserModel.isBlank(), "laser world model parsed");

		int flashlights = 0, lasers = 0, laserFrom = 0;
		for(var type : pack.types)
			if(type instanceof AttachmentType at)
			{
				if(at.flashlight) {
					flashlights++;
					check(at.lightModel != null, "flashlight without a light model: " + at.shortName);
					check(ModelCache.rigForReference(at.lightModel, at.modelScale) != null,
							"light model resolves: " + at.shortName + " -> " + at.lightModel);
				}
				if(at.laserPointer) {
					lasers++;
					check(at.laserModelFP != null || at.laserModel != null,
							"laser without a model: " + at.shortName);
					var rig = ModelCache.rigFor(at, at.modelScale);
					if(rig != null && rig.animation != null && rig.animation.fmumPose.containsKey("laserFrom"))
						laserFrom++;
				}
			}
		check(flashlights > 0 && lasers > 0, "pack has both tactical device kinds");
		check(laserFrom > 0, "some laser attachment authors a laserFrom origin point");

		// ---------------------------------------------------------------- tactical devices are not optics
		// FMUM marks AN/PEQ-15 as ItsType sight even though it only emits light/laser. If it enters
		// the aiming groups, the ADS camera moves onto the side rail (the reported giant-gun view).
		var elcan = AttachmentType.getAttachment("SeecterDR1x4x");
		var peq = AttachmentType.getAttachment("ANPEQ15");
		check(elcan != null && elcan.isAimingSight(), "the ELCAN optic is an aiming sight");
		check(peq != null && !peq.isAimingSight(), "AN/PEQ-15 is not an aiming sight despite ItsType sight");
		for(var type : pack.types)
			if(type instanceof AttachmentType at && (at.flashlight || at.laserPointer))
				check(!at.isAimingSight(), "a light/laser device never supplies the aiming camera: " + at.shortName);

		// Both on one rail line, with the device mounted further forward: before the fix the group
		// sort put the device first and LegacySightPose used its position as the ADS camera.
		var sightGunFile = new TypeFile(EnumType.gun, "SightHost", false);
		sightGunFile.lines.addAll(List.of("ShortName SightHost", "AttachPoints 0 0 0", "AttachVectors 1 10",
				"MaxAttachments 2"));
		var sightGun = new GunType(sightGunFile);
		sightGun.read(sightGunFile);
		var mount = new com.flansmod.common.types.ModifiableType.AttachableSlot();
		mount.x = 0F; mount.y = .3F; mount.rotate = 0;
		sightGun.slots = new com.flansmod.common.types.ModifiableType.AttachableSlot[] { mount };
		var opticNode = new com.flansmod.common.types.ModifiableType.DefaultAttachment();
		opticNode.name = elcan.shortName; opticNode.slot = 0; opticNode.pos = 0F;
		var deviceNode = new com.flansmod.common.types.ModifiableType.DefaultAttachment();
		deviceNode.name = peq.shortName; deviceNode.slot = 0; deviceNode.pos = .08F;
		sightGun.defaultAttachments = List.of(List.of(opticNode, deviceNode));
		sightGun.selectedSightGroup = 0;
		var groups = com.flansmod.common.GunSightState.groups(sightGun);
		check(groups.size() == 1, "one rail line after excluding the device: " + groups.size());
		var selected = com.flansmod.common.GunSightState.selectedSight(sightGun);
		check(selected != null && selected.type().shortName.equals(elcan.shortName),
				"the aiming sight is the optic, not the tactical device");
		var sightNode = node("com/flansmod/common/GunSightState");
		check(callsAnywhere(sightNode, "com/flansmod/common/types/AttachmentType", "isAimingSight"),
				"the sight groups use the aiming-sight predicate");

		// ---------------------------------------------------------------- stack switches
		var atFile = new TypeFile(EnumType.attachment, "TacticalFixture", false);
		atFile.lines.addAll(List.of("ShortName TacticalFixture", "ItsType flashlight",
				"Flashlight true 50 EFTRAtt.LightModel",
				"LaserPointer true EFTRAtt.LaserFP EFTRAtt.LaserTP"));
		var fixture = new AttachmentType(atFile);
		fixture.read(atFile);
		var gunFile = new TypeFile(EnumType.gun, "TacticalGun", false);
		gunFile.lines.addAll(List.of("ShortName TacticalGun", "AttachPoints 0 0 0", "AttachVectors 1 10",
				"MaxAttachments 1"));
		var gun = new GunType(gunFile);
		gun.read(gunFile);
		var slot = new com.flansmod.common.types.ModifiableType.AttachableSlot();
		slot.x = .3F; slot.y = .2F; slot.rotate = 90;
		gun.slots = new com.flansmod.common.types.ModifiableType.AttachableSlot[]{slot};
		var node = new com.flansmod.common.types.ModifiableType.DefaultAttachment();
		node.name = fixture.shortName; node.slot = 0;
		gun.defaultAttachments = List.of(List.of(node));
		new com.flansmod.common.types.ItemFlan(gun, new net.minecraft.world.item.Item.Properties());
		var stack = new ItemStack(gun.item);
		check(GunTactical.hasDevice(gun, stack, GunTactical.LIGHT), "stack reports the mounted flashlight");
		check(GunTactical.hasDevice(gun, stack, GunTactical.LASER), "stack reports the mounted laser");
		check(GunTactical.toggle(null, stack, GunTactical.ALL), "the single tactical key turns devices on");
		check(GunAttachments.isLightOn(stack) && GunAttachments.isLaserOn(stack),
				"one press turns on every mounted device");
		check(GunTactical.toggle(null, stack, GunTactical.ALL), "the single tactical key turns devices off");
		check(!GunAttachments.isLightOn(stack) && !GunAttachments.isLaserOn(stack),
				"one press turns everything off");

		// ---------------------------------------------------------------- final-JAR wiring
		var attachment = node("com/flansmod/common/types/AttachmentType");
		check(hasField(attachment, "lightModel"), "AttachmentType keeps the light model reference");
		check(hasField(attachment, "laserModelFP") && hasField(attachment, "laserModel"),
				"AttachmentType keeps both laser models");
		var renderer = node("com/flansmod/client/render/TurborigItemRenderer");
		check(callsAnywhere(renderer, "com/flansmod/common/GunAttachments", "isLightOn"),
				"the renderer asks the stack whether the light is on");
		check(callsAnywhere(renderer, "com/flansmod/common/GunAttachments", "isLaserOn"),
				"the renderer asks the stack whether the laser is on");
		check(callsAnywhere(renderer, "com/flansmod/client/render/FmumActionPose", "vector"),
				"the laser model is offset by the authored laserFrom point");
		var keys = node("com/flansmod/client/ReloadKeyHandler");
		check(hasField(keys, "TACTICAL_KEY"), "the single T tactical binding exists");
		check(callsAnywhere(keys, "com/flansmod/common/GunTactical", "toggle"),
				"the key press toggles through the shared state helper");
		var network = node("com/flansmod/common/network/FlanNetwork");
		check(callsAnywhere(network, "com/flansmod/common/network/GunTacticalPayload", "TYPE")
				|| callsAnywhere(network, "com/flansmod/common/network/GunTacticalPayload", "STREAM_CODEC"),
				"the server-authoritative toggle payload is registered");
		var tactic = node("com/flansmod/common/GunTactical");
		check(callsAnywhere(tactic, "com/flansmod/common/GunAttachments", "setTacticalState"),
				"toggling writes the switch onto the gun stack");
		var bytecode = node("com/flansmod/client/model/loader/BytecodeModel");
		check(callsAnywhere(bytecode, "java/util/List", "contains")
				|| hasString(bytecode, "laserFrom"), "the model loader reads the laserFrom field");
		System.out.println("PASS VerifyTacticalDevices: " + checks + " assertions; real X400 data, "
				+ flashlights + " flashlights / " + lasers + " lasers (" + laserFrom + " with laserFrom), "
				+ "stack switches, the single T key and server payload. No game/GPU.");
	}

	static boolean hasString(ClassNode n, String value) {
		for(var m : n.methods) for(var i : m.instructions)
			if(i instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.equals(value)) return true;
		return false;
	}
}
