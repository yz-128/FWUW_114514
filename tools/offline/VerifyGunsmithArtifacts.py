"""Check the two published UI variants and preserve final-JAR evidence. No game launch."""
from datetime import datetime
from hashlib import sha256
import json
from pathlib import Path
import re
import shutil
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
VERSION = re.search(r"^mod_version=(.+)$", (ROOT / "gradle.properties").read_text("utf-8"), re.M)[1].strip()
OUT = ROOT / "reports/eft" / f"gunsmith-tarkov-{VERSION}"
GUI = re.compile(r"com/flansmod/client/gui/GunModification(?:Screen|Preview)(?:\$[^/]+)?\.class")
LANG = {"assets/flansmod/lang/zh_cn.json", "assets/flansmod/lang/en_us.json"}
SHARED_TESTS = ["VerifyGunModification", "VerifyGunModificationView", "VerifyGunAttachmentCatalog",
                "VerifyAttachmentAssemblies", "VerifyAttachmentMounting", "VerifyFireModeSelector", "VerifyGunModificationRendering"]
MODERN_TESTS = SHARED_TESTS + ["VerifyGunModificationLayout", "VerifyGunModificationRailDrag", "VerifyGunModificationPicking"]


def digest(data):
    return sha256(data).hexdigest().upper()


def entries(path):
    with ZipFile(path) as jar:
        files = [entry.filename for entry in jar.infolist() if not entry.is_dir()]
        assert len(files) == len(set(files)), f"Duplicate ZIP entries: {path}"
        return {name: jar.read(name) for name in files}


def main():
    jars = {"modern": ROOT / "build/libs" / f"flansmod-{VERSION}.jar",
            "classic": ROOT / "build/libs" / f"flansmod-{VERSION}-classic.jar"}
    contents = {variant: entries(path) for variant, path in jars.items()}
    modern, classic = contents["modern"], contents["classic"]
    differences = sorted(name for name in modern.keys() | classic.keys() if modern.get(name) != classic.get(name))
    assert differences, "The two interfaces unexpectedly contain identical files"
    assert all(GUI.fullmatch(name) or name in LANG for name in differences), f"Unexpected variant differences: {differences}"
    metadata = "META-INF/neoforge.mods.toml"
    assert modern[metadata] == classic[metadata], "Variant mod metadata differs"
    assert re.search(r'^version\s*=\s*"' + re.escape(VERSION) + '"', modern[metadata].decode("utf-8"), re.M)

    compared = {}
    for variant, data in contents.items():
        count = 0
        expected = {}
        for file in (ROOT / "build/classes/java/main").rglob("*.class"):
            name = file.relative_to(ROOT / "build/classes/java/main").as_posix()
            if variant == "classic" and GUI.fullmatch(name):
                continue
            expected[name] = file.read_bytes()
        if variant == "classic":
            for file in (ROOT / "build/classes/java/classicUi").rglob("*.class"):
                name = file.relative_to(ROOT / "build/classes/java/classicUi").as_posix()
                assert GUI.fullmatch(name), f"Classic source set replaces a non-UI class: {name}"
                expected[name] = file.read_bytes()
        for name, bytecode in expected.items():
            assert data.get(name) == bytecode, f"JAR does not match the compiled {variant} class: {name}"
            count += 1
        assert {name for name in data if name.endswith(".class")} == set(expected), f"Extra/missing {variant} classes"
        compared[variant] = count

    # Classic keeps its layout while receiving rendering and label fixes. Compare the packaged
    # resources to the current classic source set, rather than freezing pre-fix 0.5.9 files.
    for name in LANG:
        assert classic[name] == (ROOT / "src/classicUi/resources" / name).read_bytes()
        assert modern[name] == (ROOT / "src/main/resources" / name).read_bytes()
        json.loads(modern[name])
        json.loads(classic[name])

    OUT.mkdir(parents=True, exist_ok=True)
    artifacts = {}
    for variant, jar in jars.items():
        suffix = "-classic" if variant == "classic" else ""
        source = ROOT / "build" / f"verification-gunsmith-{VERSION}{suffix}"
        jar_hash = digest(jar.read_bytes())
        assert (source / "artifact-sha256.txt").read_text("utf-8").strip() == jar_hash, f"{variant} audit used a different JAR"
        log_target = OUT / variant
        log_target.mkdir(exist_ok=True)
        results = {}
        for test in MODERN_TESTS if variant == "modern" else SHARED_TESTS:
            log = source / f"{test}.final.log"
            last = log.read_text("utf-8-sig").strip().splitlines()[-1]
            assert "PASS" in last or test == "VerifyGunModificationRendering" and "0 findings" in last, f"Incomplete/failed final log: {log}"
            results[log.name] = last
            shutil.copy2(log, log_target / log.name)
        shutil.copy2(source / "artifact-sha256.txt", log_target / "artifact-sha256.txt")
        shutil.copytree(source / "preview-depth", log_target / "preview-depth", dirs_exist_ok=True)
        artifacts[variant] = {"path": jar.relative_to(ROOT).as_posix(), "version": VERSION,
                              "bytes": jar.stat().st_size, "sha256": jar_hash,
                              "compiledClassesCompared": compared[variant], "compiledClassMismatches": [],
                              "checkGroups": len(results), "results": results}
    previews = ROOT / "build" / f"verification-gunsmith-{VERSION}" / "previews"
    assert len(list(previews.glob("AK74M-*.png"))) == 7
    shutil.copytree(previews, OUT / "previews", dirs_exist_ok=True)
    for filename in ("gunsmith-" + VERSION + "-build.log", "gunsmith-" + VERSION + "-modern-audit.log", "gunsmith-" + VERSION + "-classic-audit.log"):
        shutil.copy2(ROOT / "build" / filename, OUT / filename)
    shutil.copy2(ROOT / "build" / f"verification-gunsmith-{VERSION}" / "RenderTarkovGunsmithLayout.final.log", OUT / "modern/RenderTarkovGunsmithLayout.final.log")
    protocol = re.search(r'VERSION\s*=\s*"(\d+)"', (ROOT / "src/main/java/com/flansmod/common/network/FlanNetwork.java").read_text("utf-8"))[1]
    report = {"checkedAt": datetime.now().astimezone().isoformat(), "version": VERSION, "protocol": int(protocol),
              "artifacts": artifacts, "variantDifferences": differences,
              "commonClassCount": sum(name.endswith(".class") and not GUI.fullmatch(name) for name in modern),
              "allNonUiEntriesIdentical": True, "metadataIdentical": True,
              "previews": 7, "previewKind": "Offline AWT layout, actual pack meshes, sample inventory/stats; not a game screenshot",
              "modernRailDrag": "Actual projected rail axis, snapped steps, local deep-copy preview, one MOVE on release, Esc cancels",
              "classicRailControls": "Original plus/minus controls",
              "classicUiSourceSha256": {name: digest((ROOT / "src/classicUi/java/com/flansmod/client/gui" / name).read_bytes())
                                        for name in ("GunModificationScreen.java", "GunModificationPreview.java")},
              "noGameStartedOrControlled": True, "noAutomaticDeployment": True, "contentPacksModifiedThisRound": False,
              "realGameAcceptancePending": True}
    (OUT / "artifact.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", "utf-8")
    print(json.dumps({"PASS": True, "version": VERSION, "classes": compared,
                      "commonClassesIdentical": report["commonClassCount"], "allowedUiDifferences": len(differences),
                      "artifacts": {key: {k: value[k] for k in ("bytes", "sha256", "checkGroups")} for key, value in artifacts.items()}}, ensure_ascii=False))


if __name__ == "__main__":
    main()
