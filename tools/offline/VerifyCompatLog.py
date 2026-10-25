"""Meaningful synthetic log regressions. No game launch, mod mutation or network access."""
import argparse
from pathlib import Path
import subprocess


def run(exe, case, text, status=None):
    case.mkdir(parents=True, exist_ok=True)
    log = case / "client.log"
    log.write_text(text, encoding="utf-8")
    if status is not None:
        (case / "status.txt").write_text(status, encoding="utf-8")
    result = subprocess.run([str(exe), str(log)], capture_output=True, encoding="utf-8", check=True)
    (case / "matrix.md").write_text(result.stdout, encoding="utf-8")
    return result.stdout


def rows(text, header):
    lines = text.splitlines()
    start = next(i for i, line in enumerate(lines) if line.startswith(header))
    fields = [s.strip() for s in lines[start].strip("|").split("|")]
    out = []
    for line in lines[start + 2:]:
        if not line.startswith("|"):
            break
        data = [s.strip() for s in line.strip("|").split("|")]
        assert len(data) == len(fields), (fields, data)
        out.append(dict(zip(fields, data)))
    return out


def main(exe, out):
    checks = 0
    def check(value, message):
        nonlocal checks
        checks += 1
        assert value, message
    compat = "[00:00:00] [Render thread/INFO] [flansmod/]: [Compat] mods={iris,sodium,sclp} iris=present-off fabulous=false scopeDualCamera=true gl=4.6 vendor=test renderer=fixture\n"
    perf = "[RenderPerf] frames={n} fps={fps} scope={scope}ms/frame rigQuads={quads}/frame scene={scene} phase={phase} cpuFrame={cpu}ms/frame windowNs={ns} (totals: frames=99999 scope=999ms rigQuads=99999)\n"
    clean = compat + "\n".join([
        "[main/DEBUG] [mixin/]: Applying mixin perf.datapack_reload_exceptions.RecipeManagerMixin",
        "[main/DEBUG] [mixin/]: Mixing MixinChainedJsonException from mixins.iris.json into net.minecraft.server.ChainedJsonException",
        "[main/DEBUG] [mixin/]: MixinChainedJsonException Class version 65 required is higher than class version 52",
        "[main/DEBUG] [mixin/]: @Inject::iris$causeException does use CallbackInfoReturnable",
        "[main/WARN] [mixin/]: Error loading class: optional/sclp/Class (java.lang.ClassNotFoundException)",
        "[DebugCompat] done", "[CompatRun] status=captured exitCode=0", ""])
    text = run(exe, out / "clean", clean)
    row = rows(text, "| run | mods |")[0]
    check(row["mixin"] == "0" and row["fatal"] == "0" and row["probe"] == "1", "normal DEBUG exception-named mixins are not failures")
    check(row["mods"] == "iris,sodium,sclp", "SCLP is retained in loaded set")
    check(row["state"] == "captured_exit_0", "done and clean exit are independent confirmed fields")
    check("watched-mod targets: 1" in text, "SCLP optional-target probe is watched")

    text = run(exe, out / "mixin-final", compat + "[Render thread/ERROR] [mixin/]: Write access detected to @Final field name in sclp.mixins.json:MixinPageBuilderImpl\n[DebugCompat] done\n")
    row = rows(text, "| run | mods |")[0]
    check(row["mixin"] == "1" and row["ERROR"] == "1", "SCLP @Final write is a real diagnostic")
    check(row["state"] == "captured_with_errors", "capture completion does not suppress mixin errors")

    text = run(exe, out / "crash-after-capture", compat + "[DebugCompat] done\n[Render thread/ERROR] [Minecraft]: Unreported exception thrown!\njava.lang.NullPointerException: null\n[Server thread/ERROR] [MinecraftServer]: Encountered an unexpected exception\nThis crash report has been saved to: crash.txt\n")
    row = rows(text, "| run | mods |")[0]
    check(row["state"] == "fault_after_capture" and row["fatal"] == "3", "post-done crash remains visible")
    check(row["exit"] == "unknown", "no fabricated successful exit")

    text = run(exe, out / "raw-linkage", "[main/WARN] [mixin/]: Error loading class: iris/Optional (java.lang.NoClassDefFoundError: opt/Missing)\nCaused by: java.lang.NoClassDefFoundError: net/caffeinemc/mods/sodium/api/vertex/serializer/VertexSerializer\nException in thread \"main\" java.lang.RuntimeException\n")
    row = rows(text, "| run | mods |")[0]
    check(row["probe"] == "1" and row["fatal"] == "2", "bare thrown linkage and main failures are not optional probes")
    check(row["state"] == "runtime_fault", "linkage failure prevents pass status")

    text = run(exe, out / "timeout", compat, "status=timeout exitCode=137\n")
    row = rows(text, "| run | mods |")[0]
    check(row["state"] == "timeout" and row["exit"] == "137" and row["done"] == "no", "timeout and nonzero process exit are both retained")
    text = run(exe, out / "unfinished", compat, "status=timeout\n")
    check(rows(text, "| run | mods |")[0]["state"] == "timeout", "timeout without exit is explicit")

    text = run(exe, out / "legacy", compat + "[RenderPerf] frames=1200 fps=59 scope=125.728ms/frame rigQuads=0/frame\n")
    row = rows(text, "| run | mods |")[0]
    check(row["legacy windows"] == "1" and row["measured windows"] == "0", "legacy mixed windows are excluded")
    check("legacy_unscoped" in text and not rows(text, "| run | scene |"), "no invented comparable legacy mean")

    text = compat + perf.format(n=100, fps=100, scope=1, quads=10, scene="ads", phase="measure", cpu=2, ns=1000000000)
    text += perf.format(n=300, fps=300, scope=3, quads=30, scene="ads", phase="measure", cpu=4, ns=1000000000)
    text += perf.format(n=60, fps=60, scope=0, quads=50, scene="world", phase="measure", cpu=10, ns=1000000000)
    text += perf.format(n=900, fps=900, scope=1000, quads=99999, scene="ads", phase="warmup", cpu=1000, ns=1000000000)
    text += perf.format(n=900, fps=900, scope=1000, quads=99999, scene="menu", phase="measure", cpu=1000, ns=1000000000)
    text += perf.format(n=900, fps=900, scope=1000, quads=99999, scene="transition", phase="measure", cpu=1000, ns=1000000000)
    text += perf.format(n=0, fps=50, scope=0, quads=0, scene="ads", phase="measure", cpu=0, ns=1000000000)
    text += perf.format(n="NaN", fps=50, scope=0, quads=0, scene="ads", phase="measure", cpu=0, ns=1000000000)
    text = run(exe, out / "中文场景", text)
    values = rows(text, "| run | scene |")
    check(len(values) == 2, "two scenes remain independent")
    ads = next(row for row in values if row["scene"] == "ads")
    check(ads["run"] == "中文场景", "UTF-8 directory name survives Windows CLI")
    check(ads["frames"] == "400" and ads["fps"] == "200.00", "FPS is total frames divided by total wall time")
    check(ads["scope CPU ms/frame"] == "2.500" and ads["CPU ms/frame"] == "3.500", "CPU timing uses frame weights")
    check(ads["rig quads/frame"] == "25.0", "quad throughput uses frame weights")
    check("windows: 3" in text and "Invalid measured windows (not aggregated): 2" in text, "warmup/menu/transition and invalid windows excluded")
    check(rows(text, "| run | mods |")[0]["measured windows"] == "3", "totals fields cannot substitute malformed current frames")
    text = run(exe, out / "assets", compat + "[Resource/WARN] [SoundManager]: File flansmod:sounds/missing.ogg does not exist\n[Resource/ERROR] [TextureManager]: Unable to load texture flansmod:missing.png\n[Resource/DEBUG] [cache]: Optional cache does not exist\n")
    row = rows(text, "| run | mods |")[0]
    check(row["asset"] == "2" and row["ERROR"] == "1", "actual assets and generic ERROR retained without flagging optional cache existence")
    text = run(exe, out / "runner-stop", compat + "[DebugCompat] done\n[CompatRun] status=captured_needs_visual_review exitCode=137 harnessTerminated=true gracefulExitVerified=false timeout=false\n")
    row = rows(text, "| run | mods |")[0]
    check(row["state"] == "captured_runner_terminated" and row["exit"] == "137" and row["graceful exit"] == "not_verified", "intentional runner cleanup does not invent clean exit or runtime failure")
    text = run(exe, out / "runner-stop-crash", compat + "[DebugCompat] done\n[Render thread/ERROR] [Minecraft]: Unreported exception thrown!\n[CompatRun] status=captured_needs_visual_review exitCode=0 harnessTerminated=true gracefulExitVerified=false timeout=false\n")
    check(rows(text, "| run | mods |")[0]["state"] == "fault_after_capture", "exit zero and intentional cleanup never suppress runtime crash")
    text = run(exe, out / "incomplete", compat + "[CompatRun] status=capture_incomplete exitCode=0 harnessTerminated=true gracefulExitVerified=false timeout=false\n")
    check(rows(text, "| run | mods |")[0]["state"] == "capture_incomplete", "harness incomplete capture retained separately")
    recursive = subprocess.run([str(exe), str(out)], capture_output=True, encoding="utf-8", check=True).stdout
    check(len(rows(recursive, "| run | mods |")) == 12, "parent directory recursively discovers client logs without counting matrix output")
    check(subprocess.run([str(exe), str(out / "does-not-exist.log")], capture_output=True).returncode == 3, "unreadable input is not success")
    check(subprocess.run([str(exe)], capture_output=True).returncode == 2, "missing arguments are usage failure")
    report = f"PASS VerifyCompatLog: {checks} assertions; all fixtures are synthetic. No game, mod mutation or network.\n"
    (out / "verification.txt").write_text(report, encoding="utf-8")
    print(report, end="")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--exe", type=Path, default=Path("tools/native/bin/compatlog.exe"))
    p.add_argument("--out", type=Path, default=Path("build/verification-compatlog-0.5.23"))
    args = p.parse_args()
    main(args.exe.resolve(), args.out.resolve())
