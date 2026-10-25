"""Read the seven installed jars and supplied source snapshots; never changes either."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tomllib
import zipfile

TARGETS = {
    "sodium": "Sodium-1.21.1", "iris": "Iris-1.21.1",
    "modernfix": "ModernFix-1.21.1", "ferritecore": "FerriteCore-1.21.1",
    "lithium": "Lithium-1.21.1", "flerovium": "Flerovium-1.21.1", "sclp": "SCLP-1.21",
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def source_info(path: Path) -> dict:
    props = {}
    prop_file = path / "gradle.properties"
    if prop_file.exists():
        for line in prop_file.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    digest = hashlib.sha256()
    count = 0
    for file in sorted(path.rglob("*"), key=lambda p: p.as_posix()):
        rel = file.relative_to(path)
        if not file.is_file() or any(p in {".git", ".gradle", "build", ".idea"} for p in rel.parts):
            continue
        digest.update(rel.as_posix().encode("utf-8") + b"\0" + file.read_bytes() + b"\0")
        count += 1
    commit = None
    dirty = None
    if (path / ".git").exists():
        commit = subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()
        dirty = bool(subprocess.check_output(["git", "-C", str(path), "status", "--porcelain"], text=True).strip())
    return {"directory": str(path.resolve()), "gitCommit": commit, "gitDirty": dirty,
            "snapshotSha256": digest.hexdigest(), "snapshotFiles": count,
            "snapshotDefinition": "sorted relative UTF-8 path + NUL + bytes + NUL; excludes .git/.gradle/build/.idea",
            "declaredVersion": props.get("mod_version", props.get("version")),
            "declaredSodiumVersion": props.get("sodium_version"),
            "properties": props}


def collect(mods: Path, source: Path) -> dict:
    found = {}
    for file in sorted(mods.glob("*.jar")):
        with zipfile.ZipFile(file) as jar:
            metadata_path = next((p for p in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
                                  if p in jar.namelist()), None)
            if not metadata_path:
                continue
            metadata = tomllib.loads(jar.read(metadata_path).decode("utf-8"))
            for mod in metadata.get("mods", []):
                mod_id = mod["modId"]
                if mod_id not in TARGETS:
                    continue
                if mod_id in found:
                    raise ValueError("duplicate installed target mod: " + mod_id)
                deps = metadata.get("dependencies", {}).get(mod_id, [])
                mixins = metadata.get("mixins", [])
                manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
                for line in manifest.splitlines():
                    if line.startswith("MixinConfigs:"):
                        mixins += [{"config": c.strip()} for c in line.split(":", 1)[1].split(",")]
                found[mod_id] = {"modId": mod_id, "version": mod.get("version"),
                    "jar": str(file.resolve()), "fileName": file.name, "bytes": file.stat().st_size,
                    "sha256": sha(file.read_bytes()), "metadataPath": metadata_path,
                    "loaderVersion": metadata.get("loaderVersion"), "dependencies": deps,
                    "requiredDependencies": [d for d in deps if d.get("type", "required") == "required"],
                    "mixins": mixins, "source": source_info(source / TARGETS[mod_id]),
                    "warnings": []}
                if mod_id == "sclp":
                    found[mod_id]["warnings"].append(
                        "Installed 3.17.8 differs from source properties 3.8; jar includes SCLPMixinPlugin, "
                        "MixinPageBuilderImpl and MixinSodiumVideoSettingsScreen absent from the supplied source. "
                        "Use jar evidence for exact API validation.")
                if mod_id == "iris":
                    found[mod_id]["warnings"].append(
                        "Installed jar declares minecraft [1.21,1.21.1), excluding this 1.21.1 runtime; "
                        "dev launch reaching a frame does not validate this declaration for production loading.")
    missing = sorted(set(TARGETS) - set(found))
    if missing:
        raise ValueError("missing target mods: " + ", ".join(missing))
    return {"schema": 1, "targetMinecraft": "1.21.1", "mods": [found[i] for i in sorted(found)],
            "observedRuntimeDependencies": [
                {"modId": "iris", "requires": "sodium", "reason":
                 "historical no-sodium/client.log:1801-1806: Iris SystemReport injector needs VertexSerializer"},
                {"modId": "sclp", "requires": "sodium", "reason":
                 "Sodium integration source/README; absent from jar required metadata; remove with Sodium in this seven-mod matrix"}],
            "limits": ["Only Iris source snapshot has git history; directory snapshot hash identifies the other six.",
                       "No game launch, class transformation or performance measurement was performed by this extractor."]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", type=Path, default=Path("run/mods"))
    parser.add_argument("--source", type=Path, default=Path("../要做的兼容"))
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    result = collect(args.mods, args.source)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Collected", len(result["mods"]), "installed jar metadata records:", args.out)
