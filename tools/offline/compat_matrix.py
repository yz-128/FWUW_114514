"""Read-only source inventory; explicit preparation/launch in fresh, isolated game directories.

Default action writes a plan. No source mods, configs, saves or running processes are changed.
Python 3.11+, standard library only. Runtime results remain pending until --run is requested.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import tomllib
import uuid
import zipfile

TARGETS = ("modernfix", "sodium", "iris", "ferritecore", "lithium", "flerovium", "sclp")
DEFAULT_CONFIGS = ("none", "all", *("no-" + m for m in TARGETS))
# Missing metadata dependencies: Iris's SystemReport/vertex code and SCLP's Sodium mixins.
# Evidence: reports/eft/compat-0.5.23/matrix-source-review.md and jar-metadata.json.
RUNTIME_DEPENDENCIES = {"iris": {"sodium"}, "sclp": {"sodium"}}
EXPECTED_SHOTS = tuple(
    "flansmod_compat_" + scene + "_t" + str(tick) + ".png"
    for scene, ticks in (("gunsmith", (392, 404, 416)), ("ads", (452, 464, 476)),
                         ("grenade", (512, 524, 536, 552))) for tick in ticks)


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""): digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def metadata(path: Path) -> list[dict]:
    with zipfile.ZipFile(path) as jar:
        meta_path = next((p for p in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
                          if p in jar.namelist()), None)
        if not meta_path: return []
        data = tomllib.loads(jar.read(meta_path).decode("utf-8-sig"))
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace") if "META-INF/MANIFEST.MF" in jar.namelist() else ""
        version_match = re.search(r"(?m)^Implementation-Version: (.+)\r?$", manifest)
        file_version = version_match.group(1).strip() if version_match else "unknown"
        result = []
        for mod in data.get("mods", []):
            mod_id = mod["modId"]
            version = str(mod.get("version", "unknown")).replace("${file.jarVersion}", file_version)
            result.append(dict(id=mod_id, version=version, file=path.name, sha256=sha(path),
                               dependencies=data.get("dependencies", {}).get(mod_id, [])))
        return result


def inventory(folder: Path) -> dict[str, dict]:
    result = {}
    for path in sorted(folder.glob("*.jar")):
        for mod in metadata(path):
            if mod["id"] in result: raise ValueError("Duplicate mod id: " + mod["id"])
            result[mod["id"]] = mod
    missing = set(TARGETS) - result.keys()
    if missing: raise ValueError("Missing target JARs: " + ", ".join(sorted(missing)))
    return result


def required(dep: dict) -> bool:
    return dep.get("side", "BOTH") != "SERVER" and dep.get("type", "required" if dep.get("mandatory") else "optional") == "required"


def version_key(value: str):
    value = value.strip()
    match = re.fullmatch(r"(\d+(?:\.\d+)*)(?:([+-].+))?", value)
    if not match: raise ValueError("Cannot verify version: " + value)
    nums = tuple(int(n) for n in match[1].split("."))
    return nums + (0,) * (6 - len(nums)), 0 if match[2] and match[2].startswith("-") else 1


def satisfies(value: str, spec: str) -> bool:
    spec = spec.strip()
    if not spec or spec == "*": return True
    # A bare recommended version is conservatively matched exactly in our preflight.
    if re.fullmatch(r"\d+(?:\.\d+)*(?:[+-].+)?", spec):
        return version_key(value) == version_key(spec)
    if spec.startswith("[") and spec.endswith("]") and "," not in spec:
        return version_key(value) == version_key(spec[1:-1])
    match = re.fullmatch(r"([\[(])([^,]*),([^,]*)([\])])", spec)
    if not match: raise ValueError("Cannot verify range: " + spec)
    left, lower, upper, right = match.groups()
    lower, upper = lower.strip(), upper.strip()
    key = version_key(value)
    return ((not lower or key > version_key(lower) or (left == "[" and key == version_key(lower)))
            and (not upper or key < version_key(upper) or (right == "]" and key == version_key(upper))))


def configuration(name: str, mods: dict, platform: dict) -> dict:
    if name not in DEFAULT_CONFIGS: raise ValueError("Unknown configuration: " + name)
    selected = set(TARGETS) if name != "none" else set()
    if name.startswith("no-"): selected.remove(name[3:])
    reasons = {}
    changed = True
    while changed:
        changed = False
        for mod_id in sorted(selected):
            needs = {d["modId"] for d in mods[mod_id]["dependencies"] if required(d)}
            needs |= RUNTIME_DEPENDENCIES.get(mod_id, set())
            absent = (needs & set(TARGETS)) - selected
            if absent:
                selected.remove(mod_id)
                reasons[mod_id] = "requires " + ",".join(sorted(absent))
                changed = True
    errors = []
    versions = platform | {m: mods[m]["version"] for m in selected}
    for mod_id in sorted(selected):
        for dep in mods[mod_id]["dependencies"]:
            if not required(dep): continue
            other, spec = dep["modId"], dep.get("versionRange", "")
            try:
                if other not in versions or not satisfies(versions[other], spec):
                    errors.append(f"{mod_id} requires {other} {spec}; selected={versions.get(other, 'absent')}")
            except ValueError as error:
                errors.append(f"{mod_id}: {error}")
    return dict(name=name, expectedMods=sorted(selected), expectedVersions={m: mods[m]['version'] for m in sorted(selected)}, excludedByDependency=reasons,
                status="blocked_metadata" if errors else "ready_to_prepare", errors=errors)


def properties(path: Path) -> dict:
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.startswith("#"))


def assert_unchanged(folder: Path, hashes: dict):
    current = {p.name: sha(p) for p in folder.glob("*.jar")}
    if current != hashes: raise ValueError("Source mods changed since planning; create a fresh plan.")


def copy_tree(source: Path, target: Path):
    # Do not follow junctions/symlinks to other worlds or write through a source junction.
    if source.is_symlink() or (hasattr(source, "is_junction") and source.is_junction()):
        raise ValueError("Refusing source link: " + str(source))
    for base, dirs, files in os.walk(source, followlinks=False):
        for name in dirs + files:
            child = Path(base) / name
            if child.is_symlink() or (hasattr(child, "is_junction") and child.is_junction()):
                raise ValueError("Refusing source link: " + str(child))
    shutil.copytree(source, target, ignore=shutil.ignore_patterns("session.lock"))


def prepare(root: Path, out: Path, plan: dict, world: str):
    seed = (root / "run/saves" / world).resolve()
    if seed.parent != (root / "run/saves").resolve() or not (seed / "level.dat").is_file():
        raise ValueError("World must name an existing directory directly under run/saves.")
    # Check vanilla's lock without changing the source world. Windows denies reads while held.
    lock = seed / "session.lock"
    if lock.exists():
        with lock.open("rb") as stream: stream.read()
    assert_unchanged(root / "run/mods", plan["sourceModHashes"])
    port = root / plan["artifact"]["file"]
    if sha(port) != plan["artifact"]["sha256"]: raise ValueError("Port artifact changed after plan.")
    for config in plan["configs"]:
        if config["errors"]: continue
        game = out / config["name"] / "game"
        game.mkdir(parents=True)
        (game / "mods").mkdir()
        for mod_id in config["expectedMods"]:
            item = plan["inventory"][mod_id]
            shutil.copy2(root / "run/mods" / item["file"], game / "mods" / item["file"])
        for directory in ("config", "defaultconfigs", "flan", "resourcepacks"):
            source = root / "run" / directory
            if source.is_dir(): copy_tree(source, game / directory)
        (game / "saves").mkdir()
        copy_tree(seed, game / "saves/compat-seed")
        # Fix comparable graphics settings; the reference settings and saves stay read-only.
        opts = root / "run/options.txt"
        lines = opts.read_text(encoding="utf-8").splitlines() if opts.exists() else []
        overrides = dict(maxFps="120", enableVsync="false", renderDistance="8", simulationDistance="5", fov="0.0", graphicsMode="1", guiScale="2")
        lines = [s for s in lines if s.split(":", 1)[0] not in overrides]
        (game / "options.txt").write_text("\n".join(lines + [k + ":" + v for k, v in overrides.items()]) + "\n", encoding="utf-8")
        iris = game / "config/iris.properties"
        iris.parent.mkdir(exist_ok=True)
        iris.write_text("enableShaders=false\n", encoding="utf-8")
        config.update(status="prepared_not_run", gameDirectory=str(game), world="compat-seed")
    assert_unchanged(root / "run/mods", plan["sourceModHashes"])


def collect(config: dict, log: str, game: Path, result_dir: Path) -> dict:
    reports = re.findall(r"\[Compat\] mods=\{([^}]*)(?:\})", log)
    actual = set(reports[-1].split(",")) - {""} if reports else set()
    relevant = actual & set(TARGETS)
    missing = sorted(set(config["expectedMods"]) - relevant)
    unexpected = sorted(actual - set(config["expectedMods"]))
    reported_versions = re.findall(r"\[Compat\].*? versions=\{([^}]*)\}", log)
    actual_versions = dict(v.split('@', 1) for v in reported_versions[-1].split(',') if '@' in v) if reported_versions else {}
    bad_versions = {m: dict(expected=v, actual=actual_versions.get(m))
                    for m, v in config.get('expectedVersions', {}).items() if actual_versions.get(m) != v}
    shot_dir = game / "screenshots"
    missing_shots = [s for s in EXPECTED_SHOTS if not (shot_dir / s).is_file() or (shot_dir / s).stat().st_size < 100]
    ads = [float(n) for n in re.findall(r"flansmod_compat_ads_t\d+\.png \(ads blend=([0-9.]+)\)", log)]
    done = "[DebugCompat] done" in log
    failures = []
    if not reports: failures.append("no actual environment report")
    if missing or unexpected: failures.append("loaded mod inventory mismatch")
    if bad_versions: failures.append("loaded mod version mismatch")
    if missing_shots: failures.append("missing screenshots")
    if len(ads) != 3 or any(blend < .99 for blend in ads): failures.append("ADS did not reach full aim")
    if not done: failures.append("scene sequence incomplete")
    # Keep visual captures and performance completeness separate. Menu/warmup samples cannot
    # make an ADS measurement complete, and an empty scene never means zero rendering cost.
    measured = set()
    for line in log.splitlines():
        if '[RenderPerf]' not in line or 'phase=measure' not in line: continue
        scene = re.search(r'\bscene=(world|ads|gunsmith)\b', line)
        frames = re.search(r'\bframes=(\d+)', line)
        duration = re.search(r'\bwindowNs=(\d+)', line)
        if scene and frames and duration and int(frames[1]) > 0 and int(duration[1]) > 0:
            measured.add(scene[1])
    missing_perf = sorted({'world', 'ads', 'gunsmith'} - measured)
    runtime_errors = [line for line in log.splitlines() if re.search(
        r'/FATAL\]|Mixin apply failed|InvalidMixinException|InjectionError|Reported exception thrown|'
        r'Crash report saved|crash report has been saved|Exception in thread|'
        r'\b(?:java\.[\w.]+\.)?(?:NoSuchMethodError|NoClassDefFoundError)\b|'
        r'Write access detected to @Final field', line, re.IGNORECASE)
        and not re.search(r'Error loading class:|DEBUG.*(?:Transforming|Mixing)', line, re.IGNORECASE)]
    if runtime_errors: failures.append('runtime errors require review')
    # No screenshot alone proves correct rendering or shader-on compatibility.
    return dict(status="runtime_failed" if runtime_errors else "capture_incomplete" if failures else "captured_needs_visual_review", done=done,
                missingMods=missing, unexpectedMods=unexpected, missingScreenshots=missing_shots,
                mismatchedVersions=bad_versions, runtimeErrors=runtime_errors[:20],
                performanceStatus='incomplete' if missing_perf else 'scene_samples_available_not_a_fps_claim',
                missingPerformanceScenes=missing_perf,
                adsBlends=ads, failures=failures, screenshots=str(shot_dir),
                shaderOnTested=False, optionsGuiTested=False, visualAcceptance="pending")


def run_one(root: Path, out: Path, config: dict, jdk: Path, cache: Path, timeout: int):
    game = Path(config["gameDirectory"])
    result_dir = out / config["name"]
    args = [str(jdk / "bin/java.exe"), "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8",
            "-classpath", str(root / "gradle/wrapper/gradle-wrapper.jar"), "org.gradle.wrapper.GradleWrapperMain",
            "-g", str(cache), "--offline", "--no-daemon", "--console=plain", "runClientAuto",
            "-PflansCapture", "-PflansCompat", "-PflansPerf", "-PflansWorld=compat-seed",
            "-PflansGameDir=" + str(game)]
    env = dict(os.environ, JAVA_HOME=str(jdk))
    stdout, stderr = result_dir / "gradle.log", result_dir / "gradle.err.log"
    terminated, timed_out = False, False
    with stdout.open("wb") as out_stream, stderr.open("wb") as err_stream:
        process = subprocess.Popen(args, cwd=root, env=env, stdout=out_stream, stderr=err_stream,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        deadline = time.monotonic() + timeout
        try:
            while process.poll() is None:
                log_path = game / "logs/latest.log"
                log = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
                if "[DebugCompat] done" in log:
                    time.sleep(3)  # async Screenshot.grab writers, not a graceful-exit claim
                    break
                if time.monotonic() >= deadline:
                    timed_out = True
                    break
                time.sleep(2)
        finally:
            # Only this live subprocess tree, never processes discovered by name or command line.
            if process.poll() is None:
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
                terminated = True
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired: pass
    log_path = game / "logs/latest.log"
    log = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
    if not log:
        log = stdout.read_text(encoding="utf-8", errors="replace") + stderr.read_text(encoding="utf-8", errors="replace")
    (result_dir / "client.log").write_text(log, encoding="utf-8")
    result = collect(config, log, game, result_dir)
    result.update(processExitCode=process.returncode, harnessTerminated=terminated, timeout=timed_out,
                  gracefulExitVerified=not terminated and process.returncode == 0)
    if timed_out: result["status"] = "timeout"
    elif not terminated and process.returncode not in (None, 0): result["status"] = "runtime_failed"
    elif not result["done"] and process.returncode is not None: result["status"] = "startup_or_scene_failed"
    exit_code = process.returncode if process.returncode is not None else -1
    with (result_dir / 'client.log').open('a', encoding='utf-8') as stream:
        stream.write(f"\n[CompatRun] status={result['status']} exitCode={exit_code} termination={'runner' if terminated else 'natural'} harnessTerminated={str(terminated).lower()} gracefulExitVerified={str(result['gracefulExitVerified']).lower()} timeout={str(timed_out).lower()}\n")
    write_json(result_dir / "result.json", result)
    (result_dir / "status.txt").write_text(result["status"] + "\n", encoding="utf-8")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--configs", default=",".join(DEFAULT_CONFIGS))
    parser.add_argument("--prepare", action="store_true", help="copy a seed save/mods into new isolated directories")
    parser.add_argument("--run", action="store_true", help="explicitly launch only newly prepared isolated clients")
    parser.add_argument("--world", default="新的世界")
    parser.add_argument("--jdk", type=Path, default=Path("C:/Users/Administrator/.jdks/jdk-21.0.12.1+1"))
    parser.add_argument("--gradle-user-home", type=Path, default=Path("C:/Users/Administrator/.gradle"))
    parser.add_argument("--timeout", type=int, default=480)
    args = parser.parse_args()
    root = args.root.resolve()
    props = properties(root / "gradle.properties")
    artifact = root / f"build/libs/flansmod-{props['mod_version']}.jar"
    if not artifact.is_file(): raise ValueError("Build the final artifact first: " + str(artifact))
    run_id = time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8]
    out = root / "reports/eft" / ("compat-" + props["mod_version"]) / "matrix" / run_id
    out.mkdir(parents=True, exist_ok=False)
    mods = inventory(root / "run/mods")
    platform = {"minecraft": props["minecraft_version"], "neoforge": props["neo_version"], "java": "21"}
    configs = [configuration(n, mods, platform) for n in args.configs.split(",")]
    plan = dict(schema=1, runId=run_id, platform=platform,
                artifact=dict(file=str(artifact.relative_to(root)), sha256=sha(artifact)),
                inventory=mods, configs=configs, runtimeDependencies={k: sorted(v) for k, v in RUNTIME_DEPENDENCIES.items()},
                sourceModHashes={p.name: sha(p) for p in (root / "run/mods").glob("*.jar")},
                launched=False, shaderMode="off", graphics="fancy", shaderOnAcceptance="pending",
                optionsGuiAcceptance="pending", extraAmbientModsIncluded=False)
    write_json(out / "plan.json", plan)
    if args.prepare or args.run:
        prepare(root, out, plan, args.world)
        write_json(out / "plan.json", plan)
    if args.run:
        plan["launched"] = True
        write_json(out / "plan.json", plan)
        try:
            for config in configs:
                if config["status"] == "prepared_not_run":
                    config["result"] = run_one(root, out, config, args.jdk, args.gradle_user_home, args.timeout)
        finally:
            assert_unchanged(root / "run/mods", plan["sourceModHashes"])
            write_json(out / "plan.json", plan)
        collector = root / 'tools/native/bin/compatlog.exe'
        logs = [str(out / c['name'] / 'client.log') for c in configs if 'result' in c]
        if collector.is_file() and logs:
            subprocess.run([str(collector), *logs, '--out', str(out / 'matrix.md')], check=True, stdout=subprocess.DEVNULL)
    print("Plan: " + str(out / "plan.json"))
    for config in configs: print(config["name"] + ": " + config["status"] + ("; " + "; ".join(config["errors"]) if config["errors"] else ""))


if __name__ == "__main__":
    main()
