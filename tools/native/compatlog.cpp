// compatlog — offline compatibility-matrix reader for the Flan's Mod port.
//
// Development tool only: it never enters the mod jar (Gradle packages src/main; no source set
// includes tools/). The compatibility round collects one dev-client log per configuration
// (all optimisation mods on, then each one removed) and this tool turns them into one table:
// which mods were present, which Iris/Fabulous/scope-camera branch ran, the [RenderPerf] window
// numbers, and the first real error of each run.
//
// C++ rather than the existing C tooling because this is text parsing/aggregation over several
// files with optional fields, which is exactly what <filesystem>/<regex>/<optional> are for;
// pngstat stays C because it is a tight pixel loop.
//
// Usage:
//   compatlog <log-or-directory>... [--out <file.md>]
//
// Exit codes: 0 = report emitted (NOT compatibility acceptance), 2 = usage, 3 = unreadable input/output.
#ifdef _WIN32
#include <windows.h>
#endif

#include <algorithm>
#include <cctype>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <cmath>
#include <map>
#include <set>
#include <optional>
#include <regex>
#include <sstream>
#include <string>
#include <vector>

namespace fs = std::filesystem;

namespace {

const char* const WATCHED[] = {
    "iris", "sodium", "sclp", "acceleratedrendering", "entityculling", "modernfix",
    "ferritecore", "lithium", "c2me", "flerovium",
};

struct Compat {
    bool found = false;
    std::string mods;
    std::string iris;
    std::string fabulous;
    std::string dual;
    std::string gl;
    std::string vendor;
    std::string renderer;
};

struct Perf {
    int windows = 0;
    double frames = 0, nanos = 0, scopeNanos = 0, cpuNanos = 0, rigQuads = 0;
    int cpuWindows = 0;
};

struct Findings {
    int mixin = 0;   // a mixin that failed to apply: a real conflict
    int probe = 0;   // ModLauncher probing an optional target class: usually benign
    int compatProbe = 0; // a probe for one of the watched optimisation mods
    int fatal = 0;
    int asset = 0;
    int error = 0, warning = 0;
    std::vector<std::string> first;
};

struct Run {
    fs::path path;
    std::string name;
    Compat compat;
    std::map<std::string, Perf> scenes;
    int legacyWindows = 0, excludedWindows = 0, invalidWindows = 0;
    bool done = false, faultAfterDone = false;
    bool harnessTerminated = false, timedOut = false;
    std::optional<bool> gracefulExit;
    std::string harnessStatus;
    std::optional<int> exitCode;
    Findings findings;
};

#ifdef _WIN32
// The repo path contains CJK characters. On Windows the child process receives a wide command
// line and the MinGW CRT narrows it with the ANSI code page, which can make std::filesystem's
// narrow conversion throw; rebuild the path from the wide form instead.
std::wstring widen(const char* text) {
    const int len = static_cast<int>(std::strlen(text));
    if (len == 0) return {};
    int size = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, text, len, nullptr, 0);
    UINT codePage = CP_UTF8;
    if (size <= 0) {
        codePage = CP_ACP;
        size = MultiByteToWideChar(codePage, 0, text, len, nullptr, 0);
    }
    if (size <= 0) return {};
    std::wstring wide(static_cast<std::size_t>(size), L'\0');
    MultiByteToWideChar(codePage, 0, text, len, wide.data(), size);
    return wide;
}
fs::path toPath(const char* text) { return fs::path(widen(text)); }
#else
fs::path toPath(const char* text) { return fs::path(text); }
#endif

std::string utf8(const fs::path& path) {
    auto value = path.u8string();
    return {reinterpret_cast<const char*>(value.data()), value.size()};
}

std::string lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return s;
}

bool has(const std::string& haystack, const std::string& needle) {
    return haystack.find(needle) != std::string::npos;
}

// Packages of the mods this round is about: a probe for one of them can mean a version mismatch
// between the installed jar and whatever references it.
bool watchedClass(const std::string& low) {
    for (const char* hint : WATCHED)
        if (has(low, hint)) return true;
    return false;
}


std::optional<std::string> capture(const std::string& line, const std::string& re) {
    std::smatch m;
    if (std::regex_search(line, m, std::regex(re)) && m.size() > 1)
        return m[1].str();
    return std::nullopt;
}

std::optional<double> number(const std::string& line, const std::string& key) {
    auto value = capture(line, "(?:^|[ \\t])" + key + R"(=([-+]?[0-9]+(?:\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)(?=ms/frame|/frame|[ \t)]|$))");
    if (!value) return std::nullopt;
    try { double n = std::stod(*value); if (std::isfinite(n)) return n; } catch (...) { }
    return std::nullopt;
}

void parsePerf(Run& run, const std::string& line) {
    auto scene = capture(line, R"((?:^|\s)scene=([^\s]+))");
    if (!scene) { ++run.legacyWindows; return; }
    auto phase = capture(line, R"((?:^|\s)phase=([^\s]+))");
    if (!phase || *phase != "measure" || (*scene != "ads" && *scene != "gunsmith" && *scene != "world")) {
        ++run.excludedWindows; return;
    }
    const auto fields = line.substr(0, line.find("(totals:"));
    auto frames = number(fields, "frames"), nanos = number(fields, "windowNs");
    auto scope = number(fields, "scope"), quads = number(fields, "rigQuads"), fps = number(fields, "fps");
    if (!frames || *frames <= 0 || !nanos || *nanos <= 0 || !scope || *scope < 0
        || !quads || *quads < 0 || !fps || *fps <= 0) { ++run.invalidWindows; return; }
    auto& p = run.scenes[*scene];
    ++p.windows; p.frames += *frames; p.nanos += *nanos;
    p.scopeNanos += *scope * *frames * 1e6; p.rigQuads += *quads * *frames;
    if (auto cpu = number(fields, "cpuFrame"); cpu && *cpu >= 0) {
        ++p.cpuWindows; p.cpuNanos += *cpu * *frames * 1e6;
    }
}

void parseStatus(Run& run, const std::string& line) {
    if (auto status = capture(line, R"((?:^|\s)status=([^\s]+))")) run.harnessStatus = *status;
    if (auto code = number(line, "exitCode")) run.exitCode = static_cast<int>(*code);
    if (auto v = capture(line, R"((?:^|\s)harnessTerminated=(true|false))")) run.harnessTerminated = *v == "true";
    if (auto v = capture(line, R"((?:^|\s)gracefulExitVerified=(true|false))")) run.gracefulExit = *v == "true";
    if (auto v = capture(line, R"((?:^|\s)timeout=(true|false))")) run.timedOut = *v == "true";
}

// Log lines start with "[<date>] [<thread>/INFO] [<logger>]: " — strip that so the table and the
// findings stay readable.
std::string messageOf(const std::string& line) {
    auto pos = line.find("]: ");
    if (pos != std::string::npos)
        return line.substr(pos + 3);
    return line;
}

std::string clip(std::string text, std::size_t maxLen) {
    if (text.size() > maxLen)
        text = text.substr(0, maxLen - 1) + "…";
    for (char& c : text)
        if (c == '|' || c == '\n' || c == '\r')
            c = ' ';
    return text;
}

void parseLine(Run& run, const std::string& line) {
    if (has(line, "[CompatRun]")) { parseStatus(run, line); return; }
    if (has(line, "[DebugCompat] done")) run.done = true;
    if (has(line, "[Compat]")) {
        run.compat.found = true;
        if (auto v = capture(line, R"(mods=\{([^}]*)\})")) run.compat.mods = *v;
        if (auto v = capture(line, R"(iris=([^\s]+))")) run.compat.iris = *v;
        if (auto v = capture(line, R"(fabulous=([^\s]+))")) run.compat.fabulous = *v;
        if (auto v = capture(line, R"(scopeDualCamera=([^\s]+))")) run.compat.dual = *v;
        if (auto v = capture(line, R"(gl=(.*?)\s+vendor=)")) run.compat.gl = *v;
        if (auto v = capture(line, R"(vendor=(.*?)\s+renderer=)")) run.compat.vendor = *v;
        if (auto v = capture(line, R"(renderer=(.*)$)")) run.compat.renderer = *v;
        return;
    }
    if (has(line, "[RenderPerf]")) {
        parsePerf(run, line);
        return;
    }
    const std::string low = lower(line);
    const bool error = has(low, "/error]");
    if (error) ++run.findings.error;
    if (has(low, "/warn]")) ++run.findings.warning;
    // Only this explicit optional-target probe marker is classified as a probe. A later thrown
    // NoClassDefFoundError remains a fault even when the same class was probed earlier.
    if (has(low, "error loading class:")) {
        ++run.findings.probe;
        if (watchedClass(low)) ++run.findings.compatProbe;
        return;
    }
    bool isMixin = has(low, "mixin apply failed") || has(low, "mixinapplyerror")
                   || has(low, "invalidmixinexception") || has(low, "invalidinjectionexception")
                   || has(low, "injectionerror:") || has(low, "critical injection failure")
                   || has(low, "write access detected to @final")
                   || (error && has(low, "mixin") && (has(low, "failed") || has(low, "exception")));
    bool isFatal = has(low, "java.lang.nosuchmethoderror:") || has(low, "java.lang.noclassdeffounderror:")
                   || has(low, "exception in thread \"") || has(low, "unreported exception thrown")
                   || has(low, "encountered an unexpected exception") || has(low, "this crash report has been saved")
                   || has(low, "crash report saved to") || has(low, "modloadingcrashexception:");
    bool isAsset = ((has(low, "unable to load") || has(low, "failed to load"))
                       && (has(low, "texture") || has(low, "model"))) || has(low, "missing texture")
                   || (has(low, "does not exist") && (has(low, "sound") || has(low, "texture") || has(low, "model")));
    if (!isMixin && !isFatal && !isAsset && !error)
        return;
    if (isMixin) ++run.findings.mixin;
    if (isFatal) { ++run.findings.fatal; if (run.done) run.faultAfterDone = true; }
    if (isAsset) ++run.findings.asset;
    if (run.findings.first.size() < 6)
        run.findings.first.push_back(clip(messageOf(line), 200));
}

std::optional<Run> readLog(const fs::path& path) {
    Run run;
    run.path = path;
    // Every baseline run is stored as <configuration>/client.log, so the directory name is the
    // configuration; a loose log keeps its own stem.
    run.name = utf8((path.filename() == "client.log" && !path.parent_path().empty())
            ? path.parent_path().filename() : path.stem());
    std::ifstream in(path, std::ios::binary);
    if (!in) return std::nullopt;
    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        parseLine(run, line); // unprefixed throwable lines also matter
    }
    std::ifstream status(path.parent_path() / "status.txt");
    while (std::getline(status, line)) parseStatus(run, line);
    return run;
}

std::string fmt(double value, int decimals) {
    std::ostringstream out;
    out << std::fixed << std::setprecision(decimals) << value;
    return out.str();
}

std::string modsOf(const Run& run) {
    if (!run.compat.found) return "—";
    if (run.compat.mods.empty()) return "?";
    return run.compat.mods;
}

std::string state(const Run& run) {
    if (run.findings.fatal) return run.faultAfterDone ? "fault_after_capture" : "runtime_fault";
    if (run.timedOut || run.harnessStatus == "timeout") return "timeout";
    if (!run.harnessStatus.empty() && run.harnessStatus != "captured" && run.harnessStatus != "complete"
        && run.harnessStatus != "captured_needs_visual_review")
        return clip(run.harnessStatus, 80);
    if (run.exitCode && *run.exitCode != 0 && !run.harnessTerminated) return "process_failed";
    if (!run.done) return "capture_not_confirmed";
    if (run.findings.mixin || run.findings.error) return "captured_with_errors";
    if (run.harnessTerminated) return "captured_runner_terminated";
    if (run.harnessStatus == "captured_needs_visual_review") return run.harnessStatus;
    return run.exitCode ? "captured_exit_0" : "captured_exit_unknown";
}

int windows(const Run& run) {
    int n = 0; for (const auto& [scene, perf] : run.scenes) n += perf.windows; return n;
}

} // namespace

int main(int argc, char** argv) {
    std::vector<fs::path> inputs;
    std::optional<fs::path> outPath;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--out" && i + 1 < argc) {
            outPath = toPath(argv[++i]);
        } else if (arg == "-h" || arg == "--help") {
            std::cout << "usage: compatlog <log-or-directory>... [--out <file.md>]\n";
            return 0;
        } else {
            inputs.emplace_back(toPath(arg.c_str()));
        }
    }
    if (inputs.empty()) {
        std::cerr << "usage: compatlog <log-or-directory>... [--out <file.md>]\n";
        return 2;
    }

    std::set<fs::path> paths;
    for (const auto& input : inputs) {
        std::error_code ec;
        if (fs::is_directory(input, ec)) {
            bool found = false;
            for (const auto& entry : fs::recursive_directory_iterator(input, ec))
                if (entry.is_regular_file() && entry.path().filename() == "client.log") {
                    paths.insert(entry.path()); found = true;
                }
            if (!found) for (const auto& entry : fs::directory_iterator(input, ec))
                if (entry.is_regular_file() && entry.path().extension() == ".log") paths.insert(entry.path());
        } else if (fs::is_regular_file(input, ec)) {
            paths.insert(input);
        } else {
            std::cerr << "compatlog: cannot read " << input.string() << "\n";
        }
    }
    std::vector<Run> runs;
    for (const auto& path : paths) if (auto run = readLog(path)) runs.push_back(std::move(*run));
    if (runs.empty()) {
        std::cerr << "compatlog: no readable log\n";
        return 3;
    }
    std::sort(runs.begin(), runs.end(), [](const Run& a, const Run& b) { return a.name < b.name; });

    std::ostringstream md;
    md << "# Compatibility matrix (generated by compatlog)\n\n";
    md << "Capture completion, process exit and visual acceptance are separate. Error columns count diagnostic lines, not distinct crashes.\n\n";
    md << "| run | mods | iris | fabulous | scope camera | state | done | exit | runner stopped | graceful exit | measured windows | legacy windows | mixin | probe | fatal | asset | ERROR | WARN |\n";
    md << "| --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n";
    for (const auto& run : runs) {
        md << "| " << run.name
           << " | " << clip(modsOf(run), 300)
           << " | " << (run.compat.iris.empty() ? "—" : run.compat.iris)
           << " | " << (run.compat.fabulous.empty() ? "—" : run.compat.fabulous)
           << " | " << (run.compat.dual.empty() ? "—" : run.compat.dual)
           << " | " << state(run) << " | " << (run.done ? "yes" : "no")
           << " | " << (run.exitCode ? std::to_string(*run.exitCode) : "unknown")
           << " | " << (run.harnessTerminated ? "yes" : "no")
           << " | " << (run.gracefulExit ? (*run.gracefulExit ? "verified" : "not_verified") : "unknown")
           << " | " << windows(run) << " | " << run.legacyWindows
           << " | " << run.findings.mixin
           << " | " << run.findings.probe
           << " | " << run.findings.fatal
           << " | " << run.findings.asset
           << " | " << run.findings.error
           << " | " << run.findings.warning
           << " |\n";
    }

    md << "\n## Performance by measured scene\n\n"
       << "Only scene=ads/gunsmith/world phase=measure windows with positive frames/windowNs are aggregated. "
       << "FPS uses total frames / total wall time; per-frame CPU/quads use frame weights. No averages across different scenes. "
       << "These are CPU submission durations, not GPU timings. Compare only identical scene settings, shader state and workload.\n\n"
       << "| run | scene | windows | frames | wall seconds | fps | scope CPU ms/frame | CPU ms/frame | rig quads/frame |\n"
       << "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n";
    for (const auto& run : runs) for (const auto& [scene, p] : run.scenes)
        md << "| " << clip(run.name, 120) << " | " << scene << " | " << p.windows << " | " << fmt(p.frames, 0)
           << " | " << fmt(p.nanos / 1e9, 3) << " | " << fmt(p.frames * 1e9 / p.nanos, 2)
           << " | " << fmt(p.scopeNanos / p.frames / 1e6, 3)
           << " | " << (p.cpuWindows == p.windows ? fmt(p.cpuNanos / p.frames / 1e6, 3) : "unknown")
           << " | " << fmt(p.rigQuads / p.frames, 1) << " |\n";

    md << "\n## Findings\n\n";
    for (const auto& run : runs) {
        md << "- **" << run.name << "**: " << state(run) << "\n";
        if (!run.compat.found)
            md << "  - No `[Compat]` report; actual loaded set/render branch is unknown.\n";
        else if (!run.compat.mods.empty()) {
            md << "  - loaded: " << clip(run.compat.mods, 160) << "\n";
            if (!run.compat.gl.empty() || !run.compat.vendor.empty())
                md << "  - GL: " << clip(run.compat.gl, 40) << " / " << clip(run.compat.vendor, 40)
                   << " / " << clip(run.compat.renderer, 60) << "\n";
            if (has("," + run.compat.mods + ",", ",iris,") && run.compat.iris == "absent")
                md << "  - warning: Iris is loaded but the shader probe reports `absent`\n";
        }
        if (run.legacyWindows) md << "  - legacy_unscoped: " << run.legacyWindows
            << " windows preserved as a count only; menu/loading/scene mixtures are not comparable.\n";
        if (run.excludedWindows) md << "  - Excluded warmup/menu/transition/unlabelled-phase windows: " << run.excludedWindows << "\n";
        if (run.invalidWindows) md << "  - Invalid measured windows (not aggregated): " << run.invalidWindows << "\n";
        if (!windows(run)) md << "  - No comparable measured-scene performance samples.\n";
        if (!run.exitCode) md << "  - Process exit not recorded; a done marker does not establish clean shutdown.\n";
        if (run.findings.probe) md << "  - Explicit optional-target probes: " << run.findings.probe
            << "; watched-mod targets: " << run.findings.compatProbe << ". Later thrown errors are retained.\n";
        if (run.findings.fatal + run.findings.mixin + run.findings.asset + run.findings.error > 0) {
            md << "  - errors: mixin=" << run.findings.mixin << ", fatal=" << run.findings.fatal
               << ", asset=" << run.findings.asset << "\n";
            for (const auto& first : run.findings.first)
                md << "    - `" << first << "`\n";
        } else {
            md << "  - No flagged error lines; this does not establish visual acceptance.\n";
        }
    }

    std::cout << md.str();
    if (outPath) {
        std::ofstream file(*outPath);
        if (!file) {
            std::cerr << "compatlog: cannot write " << outPath->string() << "\n";
            return 3;
        }
        file << md.str();
    }
    return 0;
}
