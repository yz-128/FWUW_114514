import com.flansmod.common.BarrelHeat;
import com.flansmod.common.GunJamming;
import com.flansmod.common.types.EnumType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.TypeFile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.zip.ZipFile;

/**
 * Barrel heat: pure-function behaviour, the shipped defaults and the final-JAR wiring.
 *
 * <p>Runs without a client, a world or a game launch: the mechanic is a set of pure functions plus
 * data fields, so every rule can be asserted here before anyone sees it in game. Args:
 * {@code <final jar>}.
 */
public final class VerifyBarrelHeat
{
	static int checks;
	static ZipFile jar;
	static void check(boolean ok, String message) { checks++; if(!ok) throw new AssertionError(message); }
	static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .00001F, message + ": " + actual + " / " + expected); }
	static GunType fixture(String name, String... lines) {
		var file = new TypeFile(EnumType.gun, name, false);
		file.contentPack = "verification";
		file.lines.add("ShortName " + name);
		file.lines.addAll(List.of("AttachPoints 0 0 0", "AttachVectors 1 10", "MaxAttachments 64"));
		file.lines.addAll(List.of(lines));
		var gun = new GunType(file);
		gun.read(file);
		return gun;
	}
	static ClassNode node(String name) throws Exception {
		check(jar.getEntry(name + ".class") != null, "final JAR has " + name);
		var n = new ClassNode();
		try(var in = jar.getInputStream(jar.getEntry(name + ".class"))) { new ClassReader(in.readAllBytes()).accept(n, 0); }
		return n;
	}
	static boolean calls(ClassNode n, String method, String owner, String called) {
		for(var m : n.methods) if(m.name.equals(method)) for(var i : m.instructions)
			if(i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(called)) return true;
		return false;
	}
	static boolean callsAnywhere(ClassNode n, String owner, String called) {
		for(var m : n.methods) for(var i : m.instructions)
			if(i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(called)) return true;
		return false;
	}
	static boolean hasString(ClassNode n, String fragment) {
		for(var m : n.methods) for(var i : m.instructions)
			if(i instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.contains(fragment)) return true;
		return false;
	}
	static boolean hasField(ClassNode n, String field) {
		for(var f : n.fields) if(f.name.equals(field)) return true;
		return false;
	}
	static boolean hasMethod(ClassNode n, String method) {
		for(var m : n.methods) if(m.name.equals(method)) return true;
		return false;
	}

	public static void main(String[] args) throws Exception
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		jar = new ZipFile(args[0]);

		// ---------------------------------------------------------------- default on, switch off
		check(BarrelHeat.enabled(), "the mechanic is on by default via the mechanics settings");

		// ---------------------------------------------------------------- pure heat rules
		near(BarrelHeat.afterShot(0F, .012F), .012F, "one shot adds heatPerShot");
		near(BarrelHeat.afterShot(.99F, .05F), 1F, "heat clamps at a full barrel");
		near(BarrelHeat.afterShot(1F, .012F), 1F, "a full barrel cannot take more heat");
		near(BarrelHeat.afterShot(.5F, 0F), .5F, "a zero heat-per-shot gun never heats up");
		near(BarrelHeat.cool(.5F, .0035F, 10), .465F, "cooling removes rate per tick");
		near(BarrelHeat.cool(.001F, .0035F, 1), 0F, "cooling floors at zero");
		near(BarrelHeat.cool(.5F, 0F, 10), .5F, "a zero cooling rate never cools");

		check(BarrelHeat.overheated(1F), "full heat is overheat");
		check(!BarrelHeat.overheated(.99F), "99% is not overheat");
		check(!BarrelHeat.blocked(0F, 0), "a cold, unlocked barrel fires");
		check(BarrelHeat.blocked(0F, 5), "a running lock blocks even a cold barrel");
		check(BarrelHeat.blocked(1F, 0), "full heat blocks without a lock");

		check(BarrelHeat.lockOnOverheat(1F, 0, 40) == 40, "overheat arms the pack's lock length");
		check(BarrelHeat.lockOnOverheat(1F, 10, 40) == 40, "a longer fresh lock wins over a short running one");
		check(BarrelHeat.lockOnOverheat(1F, 60, 40) == 60, "an existing lock is not shortened");
		check(BarrelHeat.lockOnOverheat(.5F, 7, 40) == 7, "below the line leaves the lock alone");
		check(BarrelHeat.lockOnOverheat(.5F, 0, 40) == 0, "a normal shot arms nothing (the first-shot overheat fix)");
		check(BarrelHeat.lockOnOverheat(.012F, 0, 40) == 0, "one default shot does not lock the trigger");

		// ---------------------------------------------------------------- fire rate, the only penalty
		near(BarrelHeat.rateMultiplier(0F, 1.1F), 1F, "a cold barrel keeps its fire rate");
		near(BarrelHeat.rateMultiplier(1F, 1.1F), 1.1F, "a full barrel fires 10% slower by default");
		near(BarrelHeat.rateMultiplier(.5F, 1.1F), 1.05F, "the rate penalty is linear in heat");
		near(BarrelHeat.rateMultiplier(1F, .9F), 1F, "a pack cannot make heat speed the gun up");

		// Spread and recoil are deliberately not part of this mechanic; a hot barrel only slows fire.
		var heat = node("com/flansmod/common/BarrelHeat");
		check(!hasMethod(heat, "spreadMultiplier"), "no spread multiplier on the heat mechanic");
		check(!hasMethod(heat, "recoilMultiplier"), "no recoil multiplier on the heat mechanic");
		check(hasMethod(heat, "rateMultiplier"), "the rate multiplier is the only shot-shaping rule");

		// ---------------------------------------------------------------- shipped defaults
		var defaults = fixture("heat_defaults");
		check(defaults.heatPerShot > 0F, "heat is on by default (per-shot heat)");
		check(defaults.heatPerShot <= .02F, "default per-shot heat stays gentle");
		check(defaults.heatCoolRate > 0F, "default cooling exists");
		check(defaults.heatRateMult > 1F && defaults.heatRateMult <= 1.2F,
				"default fire-rate penalty is small (" + defaults.heatRateMult + ")");
		check(defaults.heatLockTicks >= 20, "default lock is long enough to be felt");
		check(defaults.heatPerShot * 30F < 1F, "a 30-round magazine does not overheat a default gun");
		check((int)Math.ceil(1F / defaults.heatCoolRate) <= 300, "a full barrel cools in under 15 seconds");

		// ---------------------------------------------------------------- pack overrides
		var tuned = fixture("heat_tuned", "HeatPerShot 0.02", "HeatCoolRate 0.01",
				"HeatRateMult 1.3", "HeatLockTicks 25");
		near(tuned.heatPerShot, .02F, "HeatPerShot parsed");
		near(tuned.heatCoolRate, .01F, "HeatCoolRate parsed");
		near(tuned.heatRateMult, 1.3F, "HeatRateMult parsed");
		check(tuned.heatLockTicks == 25, "HeatLockTicks parsed");

		// ---------------------------------------------------------------- final-JAR wiring
		var firing = node("com/flansmod/common/GunFiring");
		check(callsAnywhere(firing, "com/flansmod/common/BarrelHeat", "blocked"),
				"the trigger lock is consulted before firing");
		check(callsAnywhere(firing, "com/flansmod/common/BarrelHeat", "afterShot"),
				"each shot adds heat");
		check(callsAnywhere(firing, "com/flansmod/common/BarrelHeat", "lockOnOverheat"),
				"overheat arms the trigger lock");
		check(callsAnywhere(firing, "com/flansmod/common/BarrelHeat", "rateMultiplier"),
				"heat slows the shot delay");
		check(!callsAnywhere(firing, "com/flansmod/common/BarrelHeat", "spreadMultiplier"),
				"the shot spread is not heat-dependent");
		var playerData = node("com/flansmod/common/PlayerData");
		check(hasField(playerData, "heatByGun"), "heat is stored per gun");
		check(callsAnywhere(playerData, "com/flansmod/common/BarrelHeat", "cool"),
				"the player tick cools the barrels");
		// The map behaviour is pure enough to run without a world: two guns must not share heat.
		var state = new com.flansmod.common.PlayerData(java.util.UUID.randomUUID());
		state.setHeatOf("gun_a", .8F, .01F);
		state.setHeatOf("gun_b", .1F, .02F);
		near(state.heatOf("gun_a"), .8F, "gun A keeps its own heat");
		near(state.heatOf("gun_b"), .1F, "gun B keeps its own heat");
		near(state.heatOf("gun_c"), 0F, "an unused gun starts cold");
		state.setLockOf("gun_a", 7);
		check(state.lockOf("gun_a") == 7 && state.lockOf("gun_b") == 0, "locks are per gun");
		state.clearHeat("gun_a");
		near(state.heatOf("gun_a"), 0F, "clearing one gun drops only its heat");
		near(state.heatOf("gun_b"), .1F, "the other gun stays hot");
		check(callsAnywhere(firing, "com/flansmod/common/PlayerData", "heatOf"),
				"the trigger reads the gun's own heat");
		check(callsAnywhere(firing, "com/flansmod/common/PlayerData", "setHeatOf"),
				"a shot writes the gun's own heat");
		var gunType = node("com/flansmod/common/types/GunType");
		check(hasField(gunType, "heatPerShot") && hasField(gunType, "heatCoolRate")
				&& hasField(gunType, "heatRateMult") && hasField(gunType, "heatLockTicks"),
				"the pack's data keys exist on GunType");
		check(hasString(gunType, "HeatRateMult"), "HeatRateMult is a parsed key");
		var hud = node("com/flansmod/client/GunHud");
		check(callsAnywhere(hud, "com/flansmod/common/BarrelHeat", "enabled"),
				"the HUD respects the mechanic switch");
		check(hasString(hud, "hud.flansmod.overheat"), "the HUD shows the OVERHEAT label");

		// Overheat jam: the charge key racks the bolt; the heat drops but the barrel stays warm.
		near(GunJamming.clearedHeat(1F), .35F, "clearing a jam leaves a warm barrel");
		near(GunJamming.clearedHeat(.1F), .1F, "clearing a cool gun does not heat it");
		check(callsAnywhere(firing, "com/flansmod/common/GunJamming", "setJam"),
				"crossing the overheat line jams the gun");
		check(callsAnywhere(firing, "com/flansmod/common/GunJamming", "jammed"),
				"a jammed gun refuses the trigger");
		var reloading = node("com/flansmod/common/GunReloading");
		check(callsAnywhere(reloading, "com/flansmod/common/GunJamming", "jammed"),
				"other reload actions are refused while jammed");
		check(callsAnywhere(reloading, "com/flansmod/common/GunJamming", "clear"),
				"the charge key clears the jam");
		check(hasString(hud, "hud.flansmod.jammed") && hasString(hud, "hud.flansmod.jammed_hint"),
				"the HUD tells the player to press G");

		System.out.println("PASS VerifyBarrelHeat: " + checks + " assertions; pure heat rules, shipped defaults,"
				+ " pack overrides, fire-rate-only penalty and final-JAR wiring. No game/GPU/input.");
	}
}
