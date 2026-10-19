import com.flansmod.common.TextDecoding;
import com.flansmod.common.types.*;

import java.nio.file.*;
import java.util.*;

/**
 * Checks how the real packs' scope keys are parsed.
 *
 * <p>{@code ZoomLevel <glass> <mask> <level> <sensitivity> ...} used to be read as a single float,
 * which silently produced 0 because the first token is a model name. This asserts the model names
 * survive and the step arrays line up. The zoomDivisor assertions cover the declared magnification
 * ({@code level * 0.1}), which is what the world camera is now divided by.
 *
 * <p>Parses the definition files directly rather than going through {@code FlansMod}, so it needs
 * no registry bootstrap — the point is the reader, not item registration.
 */
@SuppressWarnings("deprecation") // Intentionally checks the isolated old default-step API.
public final class VerifyScopeZoom {
    private static int checks, failures;

    public static void main(String[] args) throws Exception {
        Path attachments = Path.of(args[0], "attachments");

        expect(attachments, "PU35RifleScope", "EFTRAtt.PSO1Glass", "EFTRAtt.eftBlackMask",
                new float[]{35F}, 3.5F);
        expect(attachments, "PSO1", "EFTRAtt.PSO1Glass", "EFTRAtt.eftBlackMask",
                new float[]{40F}, 4.0F);
        expect(attachments, "NF7x", "EFTRAtt.PSO1Glass", "EFTRAtt.eftBlackMask",
                new float[]{70F}, 7.0F);
        expect(attachments, "SeecterDR1x4x", "EFTRAtt.PSO1Glass", "EFTRAtt.eftBlackMask",
                new float[]{48.75F, 15.5F}, 4.875F);
        expect(attachments, "G33Enabled", "EFTRAtt.PSO1Glass", "EFTRAtt.eftBlackMask",
                new float[]{39F}, 3.9F);

        // A part with no ZoomLevel must stay unmagnified rather than inherit another's value.
        AttachmentType crossline = parse(attachments, "Crossline.txt");
        check("Crossline present", crossline != null);
        if (crossline != null) {
            check("Crossline has no zoom steps", crossline.fovZoomLevels.length == 0);
            check("Crossline divisor is 1", Math.abs(crossline.zoomDivisor() - 1F) < 1e-6F);
            check("Crossline sensitivity is 1", Math.abs(crossline.sensitivityMultiplier() - 1F) < 1e-6F);
        }

        // Old API compatibility only: mutate this isolated parsed definition, never a runtime part.
        AttachmentType dr = parse(attachments, "SeecterDR1x4x.txt");
        if (dr != null && dr.fovZoomLevels.length == 2) {
            dr.zoomStep = 1;
            check("step 1 magnification = 1.55", Math.abs(dr.zoomDivisor() - 1.55F) < 1e-4F);
            dr.zoomStep = 99;
            check("out-of-range step clamps high", Math.abs(dr.zoomDivisor() - 1.55F) < 1e-4F);
            dr.zoomStep = -5;
            check("out-of-range step clamps low", Math.abs(dr.zoomDivisor() - 4.875F) < 1e-4F);
        } else {
            check("SeecterDR1x4x has two steps", false);
        }

        // AimTimeMult's second number is the in-use value.
        if (crossline == null || crossline.aimTimeMult != 1F)
            check("Crossline AimTimeMult defaults to 1", crossline != null && crossline.aimTimeMult == 1F);

        var numericFile = new TypeFile(EnumType.attachment,"legacy_numeric_zoom",false);
        numericFile.lines.addAll(List.of("ShortName legacy_numeric_zoom","ZoomLevel 4"));
        var numeric = new AttachmentType(numericFile); numeric.read(numericFile);
        check("old numeric ZoomLevel preserves scalar", Math.abs(numeric.zoomLevel - 4F) < 1e-6F);
        check("old numeric ZoomLevel compatibility divisor = 4", Math.abs(numeric.zoomDivisor(0) - 4F) < 1e-6F);
        check("numeric ZoomLevel is not misread as a glass model", numeric.scopeGlassModel == null && numeric.fovZoomLevels.length == 0);
        check("numeric ZoomLevel default sensitivity is 1", Math.abs(numeric.sensitivityMultiplier(0) - 1F) < 1e-6F);

        System.out.println("VerifyScopeZoom: " + checks + " declared-magnification checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    private static void expect(Path dir, String shortName, String glass, String mask,
                               float[] levels, float declaredMagnification) throws Exception {
        AttachmentType type = parse(dir, shortName + ".txt");
        if (type == null) { check(shortName + " present", false); return; }
        check(shortName + " glass model = " + glass, glass.equals(type.scopeGlassModel));
        check(shortName + " mask model = " + mask, mask.equals(type.blackMaskModel));
        check(shortName + " has " + levels.length + " step(s)",
                type.fovZoomLevels.length == levels.length);
        for (int i = 0; i < Math.min(levels.length, type.fovZoomLevels.length); i++)
            check(shortName + " step " + i + " = " + levels[i],
                    Math.abs(type.fovZoomLevels[i] - levels[i]) < 1e-4F);
        check(shortName + " sensitivity entries = steps",
                type.mouseSensitivityMult.length == levels.length);
        check(shortName + " declared magnification = " + declaredMagnification,
                Math.abs(type.zoomDivisor() - declaredMagnification) < 1e-4F);
    }

    /** Reads one attachment definition the way the loader does, without touching the registry. */
    private static AttachmentType parse(Path dir, String fileName) throws Exception {
        Path file = dir.resolve(fileName);
        if (!Files.isRegularFile(file)) { System.out.println("missing: " + file); return null; }
        TypeFile tf = new TypeFile(EnumType.attachment, fileName.substring(0, fileName.lastIndexOf('.')));
        tf.lines.addAll(TextDecoding.readLines(file));
        AttachmentType type = new AttachmentType(tf);
        try { type.read(tf); } catch (Exception e) { System.out.println("parse failed: " + e); return null; }
        return type;
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL: " + what); }
    }
}
