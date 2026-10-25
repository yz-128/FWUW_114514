# Builds the offline native tools (tools/native/*.c) into tools/native/bin/.
#
# These tools are DEVELOPMENT-ONLY. They never enter the mod jar: Gradle packages
# src/main (java + resources) and no source set includes tools/. build.ps1 ends by
# asserting that the shipped jar contains no native binary.
#
# Toolchain lookup order: $env:CC -> Zig (`zig cc`) -> gcc on PATH -> WinLibs/MinGW
# install dirs -> clang. C sources use gcc -std=c11, C++ sources g++ -std=c++20.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Resolve-Path (Join-Path $here '..\..')
$out  = Join-Path $here 'bin'
New-Item -ItemType Directory -Force -Path $out | Out-Null

function Find-Compiler {
    if ($env:CC) { return @{ exe = $env:CC; prefix = @() } }

    $zig = Get-ChildItem 'E:\JAVAMCMODGJ\tools\zig-*\zig.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($zig) { return @{ exe = $zig.FullName; prefix = @('cc') } }
    $zigPath = Get-Command zig -ErrorAction SilentlyContinue
    if ($zigPath) { return @{ exe = $zigPath.Source; prefix = @('cc') } }

    $gcc = Get-Command gcc -ErrorAction SilentlyContinue
    if ($gcc) { return @{ exe = $gcc.Source; prefix = @() } }

    $winlibs = Get-ChildItem 'E:\JAVAMCMODGJ\tools\msys64\mingw64\bin\gcc.exe',
                              'E:\JAVAMCMODGJ\tools\msys64\ucrt64\bin\gcc.exe',
                              'C:\Program Files\WinLibs*\mingw64\bin\gcc.exe',
                              'C:\Program Files (x86)\WinLibs*\mingw64\bin\gcc.exe',
                              'C:\msys64\ucrt64\bin\gcc.exe',
                              'C:\msys64\mingw64\bin\gcc.exe' -ErrorAction SilentlyContinue |
               Select-Object -First 1
    if ($winlibs) { return @{ exe = $winlibs.FullName; prefix = @() } }

    $clang = Get-Command clang -ErrorAction SilentlyContinue
    if ($clang) { return @{ exe = $clang.Source; prefix = @() } }

    throw 'No C compiler found. Install one (winget install BrechtSanders.WinLibs.POSIX.UCRT) or set $env:CC.'
}

$tool = Find-Compiler
$sources = Get-ChildItem $here -File | Where-Object { $_.Extension -in @('.c', '.cpp') }
if (-not $sources) { Write-Host 'no native sources'; exit 0 }

# The repo path contains CJK characters ("移植i"). MinGW's ld cannot open an
# output path passed from PowerShell (ANSI conversion turns it into '?'), while a
# POSIX shell passes UTF-8 through correctly, so compilation runs through bash.
$bash = @('C:\Program Files\Git\bin\bash.exe',
          'C:\Program Files\Git\usr\bin\bash.exe',
          'E:\JAVAMCMODGJ\tools\msys64\usr\bin\bash.exe') |
        Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $bash) { throw 'bash not found; cannot compile under a CJK path' }

function To-Posix([string]$win) {
    $w = $win.Replace('\','/')
    if ($w -match '^([A-Za-z]):(.*)$') { return '/' + $matches[1].ToLower() + $matches[2] }
    return $w
}

$toolBin = To-Posix (Split-Path -Parent $tool.exe)
foreach ($src in $sources) {
    $exe = Join-Path $out ($src.BaseName + '.exe')
    $isCpp = $src.Extension -eq '.cpp'
    $compiler = if ($isCpp) { 'g++' } else { 'gcc' }
    $std = if ($isCpp) { '-std=c++20' } else { '-std=c11' }
    $cmd = 'export PATH="{0}:$PATH"; {4} {5} -O2 -Wall -Wextra -static -static-libgcc -I "{1}" -o "{2}" "{3}"' -f `
        $toolBin, (To-Posix $here), (To-Posix $exe), (To-Posix $src.FullName), $compiler, $std
    Write-Host ("cc {0} -> {1}" -f $src.Name, $exe)
    & $bash -lc $cmd
    if ($LASTEXITCODE -ne 0) { throw "compile failed: $($src.Name)" }
}

# The jar must stay pure Java + resources.
$jar = Get-ChildItem (Join-Path $root 'build\libs') -Filter 'flansmod-*.jar' -ErrorAction SilentlyContinue |
       Sort-Object LastWriteTime | Select-Object -Last 1
if ($jar) {
    $jdkJar = Join-Path $env:JAVA_HOME 'bin\jar.exe'
    if (-not (Test-Path $jdkJar)) { $jdkJar = (Get-Command jar -ErrorAction SilentlyContinue).Source }
    if ($jdkJar) {
        $hits = & $jdkJar tf $jar.FullName | Where-Object { $_ -match '\.(exe|dll|so|dylib)$' }
        if ($hits) { throw ("native files leaked into {0}: {1}" -f $jar.Name, ($hits -join ', ')) }
        Write-Host ("jar-clean: {0} has no native payload" -f $jar.Name)
    }
}
