import com.flansmod.client.model.FmumCasing;
import com.flansmod.common.types.BulletType;
import com.flansmod.common.types.InfoType;
import org.joml.Matrix4f;

import java.nio.file.Path;

/**
 * Checks the ejected-case physics without a running client.
 *
 * <p>{@code FmumCasing.tick} takes the level as a parameter, so a null level reproduces the
 * original free-fall behaviour. This covers what can be checked without a world: the lifetime
 * change, ballistic decay, the spin, and that a case with nothing to land on never settles.
 * Terrain collision itself needs a level and is left to in-game verification.
 */
public final class VerifyCasingPhysics {
    private static int checks, failures;

    public static void main(String[] args) throws Exception {
        var pack = new OfflinePack(Path.of(args[0]));
        pack.readTypes();
        BulletType bullet = (BulletType) InfoType.infoTypes.stream()
                .filter(t -> t instanceof BulletType)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the pack has no bullet type"));

        // A real gun model, because the lifetime comes from the model's caseTime field — every
        // EFT gun authors 60 there. A null model would only exercise the 30-tick fallback.
        var gun = (com.flansmod.common.types.GunType) InfoType.infoTypes.stream()
                .filter(t -> t instanceof com.flansmod.common.types.GunType)
                .filter(t -> "AK74M".equals(t.shortName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AK74M missing from the pack"));
        var model = com.flansmod.client.model.loader.ModelCache.load(gun);
        check("AK74M model loaded", model != null);
        check("AK74M authors caseTime 60",
                model != null && Float.compare(model.fmumScalars.getOrDefault("caseTime", 0F), 60F) == 0);

        // Lifetime: every EFT gun authors caseTime 60, and the port multiplies it.
        FmumCasing casing = casing(bullet, model);
        check("lifetime is 4x the authored caseTime",
                casing.lifetime == (int) (60 * FmumCasing.LIFETIME_MULTIPLIER));
        check("lifetime is 240 ticks", casing.lifetime == 240);

        // Free fall with drag, no world: the original behaviour, unchanged.
        float startY = casing.velocity.y;
        casing.tick(null, 0, 0, 0);
        check("gravity pulls the case down", casing.velocity.y < startY);
        check("air drag shrinks the speed every tick",
                casing.velocity.length() < casing.launchSpeed);
        check("case is alive on its first tick", casing.alive());

        // Spin decays and the case keeps rotating.
        check("spin advances", casing.spin != 0F);

        // A case whose time has run out reports dead, so the renderer drops it.
        for (int i = 0; i < casing.lifetime + 2; i++)
            casing.tick(null, 0, 0, 0);
        check("case dies at its lifetime", !casing.alive());

        // Without a world the case never settles: there is nothing to land on.
        FmumCasing airborne = casing(bullet, model);
        for (int i = 0; i < 200; i++)
            airborne.tick(null, 0, 0, 0);
        check("no collision surface means no settle", !airborne.grounded);
        check("case keeps falling without a world", airborne.position.y < 0F);

        System.out.println("VerifyCasingPhysics: " + checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    /** A casing built the way the renderer builds one. */
    private static FmumCasing casing(BulletType bullet,
                                     com.flansmod.client.model.loader.ModelGeometry.Model model) {
        var gun = new Matrix4f();
        return new FmumCasing(bullet, model, gun, false, 1234L, 0F);
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL: " + what); }
    }
}
