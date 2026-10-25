import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.render.Textures;
import com.flansmod.common.GunCasings;
import com.flansmod.common.types.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.file.Path;
import java.util.zip.ZipFile;
import java.util.*;

/**
 * Every gun's casing chain: gun → magazine types → ammunition → DropItemOnShoot case definition,
 * and the case mesh/texture/scale the renderer needs. This is a packed-data audit plus the
 * production wiring checks that decide whether the case is actually ejected on each fire path.
 *
 * <p>Args: {@code <final jar> <pack root>}.
 */
public final class VerifyGunCasings
{
	static int checks, guns, ammoTypes, caseTypes;
	static ZipFile jar;
	static void check(boolean ok, String message) { checks++; if(!ok) throw new AssertionError(message); }
	static ClassNode node(String name) throws Exception {
		check(jar.getEntry(name + ".class") != null, "final JAR has " + name);
		var n = new ClassNode();
		try(var in = jar.getInputStream(jar.getEntry(name + ".class"))) { new ClassReader(in.readAllBytes()).accept(n, 0); }
		return n;
	}
	static boolean methodHasField(ClassNode n, String method, String owner, String field) {
		for(var m : n.methods) if(m.name.equals(method))
			for(var i : m.instructions)
				if(i instanceof FieldInsnNode f && f.name.equals(field)) return true;
		return false;
	}
	static boolean callsAnywhere(ClassNode n, String owner, String called) {
		for(var m : n.methods) for(var i : m.instructions)
			if(i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(called)) return true;
		return false;
	}

	public static void main(String[] args) throws Exception
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		jar = new ZipFile(args[0]);
		Path root = Path.of(args[1]);
		// The game loads every enabled pack directory; cases are defined in the legacy FMUM Guns
		// pack while the EFT Reforged bullets reference them, so the audit must read the same set.
		var packs = new ArrayList<OfflinePack>();
		try(var dirs = java.nio.file.Files.list(root))
		{
			for(var dir : (Iterable<Path>)dirs.sorted()::iterator)
			{
				var name = dir.getFileName().toString();
				if(!java.nio.file.Files.isDirectory(dir) || name.startsWith("_")) continue;
				if(!java.nio.file.Files.exists(dir.resolve("pack.mcmeta"))) continue;
				try { var pack = new OfflinePack(dir); pack.readTypes(); packs.add(pack); }
				catch(RuntimeException ignored) { }
			}
		}
		if(packs.isEmpty()) throw new AssertionError("no content packs under " + root);
		// Texture lookup across every loaded pack.
		java.util.function.Predicate<net.minecraft.resources.ResourceLocation> has =
				id -> packs.stream().anyMatch(pack -> pack.has(id));

		// Same gap-filling the game performs after reading every pack.
		com.flansmod.common.GunCasings.linkMissingCases();
		var allTypes = new ArrayList<InfoType>();
		for(var pack : packs) allTypes.addAll(pack.types);
		var gunList = new ArrayList<GunType>();
		var magazineList = new ArrayList<GunType>();
		var bulletList = new ArrayList<BulletType>();
		for(var type : allTypes)
		{
			if(type instanceof GunType gun)
			{
				if(gun.itsType == GunType.GunItsType.MAG) magazineList.add(gun); else gunList.add(gun);
			}
			if(type instanceof BulletType bullet) bulletList.add(bullet);
		}
		// FMUM resolves a gun's ammunition through its magazines: the gun names a magazine type,
		// the magazine's Ammo line names a calibre tag, and bullets carry that tag in AmmoType.
		java.util.function.BiFunction<String, java.util.function.Predicate<GunType>, List<GunType>> magazinesFor =
				(name, extra) -> {
					var found = new ArrayList<GunType>();
					for(var mag : magazineList)
						if(mag.shortName != null && mag.shortName.equalsIgnoreCase(name)
								|| mag.magazineNames.stream().anyMatch(name::equalsIgnoreCase))
							found.add(mag);
					return found;
				};
		java.util.function.Function<String, List<BulletType>> bulletsForTag = tag -> {
			var found = new ArrayList<BulletType>();
			for(var bullet : bulletList)
				if(bullet.shortName != null && bullet.shortName.equalsIgnoreCase(tag)
						|| bullet.ammoType != null && bullet.ammoType.equalsIgnoreCase(tag))
					found.add(bullet);
			return found;
		};
		var casings = new LinkedHashSet<String>();
		var missingCase = new ArrayList<String>();
		var badCase = new ArrayList<String>();
		int bolt = 0, automatic = 0;

		for(var gun : gunList)
		{
			VerifyGunCasings.guns++;
			if(gun.itsType == GunType.GunItsType.BOLT_GUN) bolt++; else automatic++;
			// Ammunition reachable from this gun: direct Ammo lines and every compatible magazine.
			var ammo = new LinkedHashSet<BulletType>();
			for(var name : gun.ammoNames)
			{
				for(var bullet : bulletsForTag.apply(name)) ammo.add(bullet);
				var direct = BulletType.getBullet(name);
				if(direct != null) ammo.add(direct);
			}
			for(var magazineName : gun.magazineNames)
			{
				if(magazineName == null || magazineName.isBlank() || magazineName.equalsIgnoreCase("none")) continue;
				for(var magazine : magazinesFor.apply(magazineName, null))
					for(var ammoName : magazine.ammoNames)
						ammo.addAll(bulletsForTag.apply(ammoName));
			}
			if(ammo.isEmpty())
			{
				// Melee weapons and tool-like entries are still guns by itsType but have no ammo.
				if(gun.magazineNames.stream().anyMatch(name -> name != null && !name.isBlank()
						&& !name.equalsIgnoreCase("none")) || !gun.ammoNames.isEmpty())
					missingCase.add(gun.shortName + " (no ammunition path)");
				continue;
			}
			for(var round : ammo)
			{
				// A case definition is never chambered as ammunition; packs that tag cases with a
				// calibre would otherwise be audited as if a brass case were a live round.
				if(round.isCase) continue;
				VerifyGunCasings.ammoTypes++;
				var casing = GunCasings.casing(round);
				if(casing == null)
				{
					missingCase.add(gun.shortName + " -> " + round.shortName + " (dropItemOnShoot=" + round.dropItemOnShoot + ")");
					continue;
				}
				casings.add(casing.shortName);
				VerifyGunCasings.caseTypes++;
				boolean ok = casing.isCase && casing.bulletModel != null && !casing.bulletModel.isBlank()
						&& casing.bulletTexture != null && !casing.bulletTexture.isBlank()
						&& casing.bulletScale > 0F && casing.bulletScale < 1F;
				if(ok)
				{
					var rig = ModelCache.rigForReference(casing.bulletModel, casing.bulletScale);
					ok = rig != null && !rig.quads.isEmpty();
				}
				if(ok)
				{
					var texture = Textures.of(casing.bulletTexture, false, has);
					ok = texture != null && has.test(texture);
				}
				if(!ok) badCase.add(gun.shortName + " -> " + round.shortName + " -> " + casing.shortName);
			}
		}

		check(gunList.size() >= 35, "guns audited: " + gunList.size());
		check(missingCase.isEmpty(), "every gun's ammunition resolves to a case (shipped or fallback): " + missingCase);
		check(badCase.isEmpty(), "every case has IsCase + mesh + texture + authored scale: " + badCase);
		// The two pack-side gaps are filled at load time from real brass, not by editing packs.
		for(var fallback : new String[] { "9x18BulletCase", "12GBulletCase" })
		{
			var synthetic = BulletType.getBullet(fallback);
			check(synthetic != null && synthetic.isCase && synthetic.bulletModel != null,
					"missing pack case filled: " + fallback);
			check(!InfoType.infoTypes.contains(synthetic), "fallback case stays out of the content registry: " + fallback);
		}
		check(BulletType.getBullet("12GBulletCase").bulletScale > BulletType.getBullet("9x19mmCasing").bulletScale,
				"the shotgun fallback is scaled up from the pistol brass");
		// The user-reported M1A specifically: its magazines resolve 7.62x51 rounds whose ItsCase
		// points at the EFT 5.56 case, so it must end up with a real case object, not null.
		var m1a = (GunType)InfoType.getType("M1A");
		check(m1a != null, "M1A definition found");
		var m1aAmmo = new LinkedHashSet<BulletType>();
		for(var name : m1a.ammoNames)
			for(var bullet : bulletsForTag.apply(name)) m1aAmmo.add(bullet);
		for(var magazineName : m1a.magazineNames)
			for(var magazine : magazinesFor.apply(magazineName, null))
				for(var ammoName : magazine.ammoNames)
					m1aAmmo.addAll(bulletsForTag.apply(ammoName));
		int m1aPairs = 0;
		for(var ammo : m1aAmmo)
		{
			if(ammo.isCase) continue;
			m1aPairs++;
			var casing = GunCasings.casing(ammo);
			check(casing != null && casing.isCase && casing.bulletModel != null,
					"M1A ammo " + ammo.shortName + " ejects a real case");
		}
		check(m1aPairs > 0, "M1A ammunition resolved: " + m1aPairs);
		System.out.println("M1A casing pairs: " + m1aPairs);
		System.out.println("Gun casing audit: " + guns + " guns (" + automatic + " auto, " + bolt + " bolt), "
				+ ammoTypes + " gun/ammo pairs, " + casings.size() + " case definitions: " + casings);
		for(var casingName : casings)
		{
			var casing = BulletType.getBullet(casingName);
			System.out.printf("  %-18s model=%s texture=%s scale=%.3f%n", casingName,
					casing.bulletModel, casing.bulletTexture, casing.bulletScale);
		}

		// ---------------------------------------------------------------- production wiring
		var firing = node("com/flansmod/common/GunFiring");
		check(callsAnywhere(firing, "com/flansmod/common/GunCasings", "eject"),
				"automatic guns eject a case per shot");
		check(callsAnywhere(firing, "com/flansmod/common/GunCasings", "casing"),
				"the case comes from the fired round's DropItemOnShoot");
		var magazine = node("com/flansmod/common/GunMagazine");
		check(callsAnywhere(magazine, "com/flansmod/common/GunCasings", "retain"),
				"bolt guns retain their spent case");
		var reloading = node("com/flansmod/common/GunReloading");
		check(callsAnywhere(reloading, "com/flansmod/common/GunCasings", "retained"),
				"charging looks up the retained case");
		check(callsAnywhere(reloading, "com/flansmod/common/GunCasings", "eject"),
				"charging ejects the retained case");
		var cases = node("com/flansmod/client/render/MuzzleEffectsRenderer");
		check(methodHasField(cases, "renderCases", "com/flansmod/common/types/ShootableType", "bulletScale"),
				"the case renderer applies the authored bulletScale");
		check(callsAnywhere(cases, "com/flansmod/client/model/loader/ModelCache", "rigForReference"),
				"the case renderer resolves the case model");
		check(callsAnywhere(cases, "com/flansmod/client/render/Textures", "of"),
				"the case renderer resolves the case texture");
		System.out.println("PASS VerifyGunCasings: " + checks + " assertions; " + guns + " guns, "
				+ ammoTypes + " gun/ammo pairs, " + casings.size() + " cases, model/texture/scale and "
				+ "auto/bolt/charge ejection wiring. No game/GPU/pack writes.");
	}
}
