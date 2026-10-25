param(
    [string]$CandidateJar = '',
    [string]$BaselineJar = 'build/libs/flansmod-0.5.22.jar',
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$Output = ''
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $project
try {
    $version = (Get-Content -LiteralPath 'gradle.properties' | Where-Object { $_ -match '^mod_version=' }).Substring('mod_version='.Length).Trim()
    if (-not $CandidateJar) { $CandidateJar = "build/libs/flansmod-$version.jar" }
    if (-not $Output) { $Output = "build/verification-selector-cache-$version" }
    $candidate = (Get-Item -LiteralPath $CandidateJar).FullName
    $baseline = (Get-Item -LiteralPath $BaselineJar).FullName
    $candidateHash = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash
    $baselineHash = (Get-FileHash -LiteralPath $baseline -Algorithm SHA256).Hash
    $evidence = [IO.Path]::GetFullPath($Output)
    New-Item -ItemType Directory -Path $evidence -Force | Out-Null
    $classes = Join-Path $evidence ('tools-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $classes | Out-Null
    $dependencies = ([IO.File]::ReadAllText('build/offline-classpath.txt').Trim() -split ';' | Where-Object {
        $_ -and $_.Replace('\', '/') -notmatch '/build/(classes|resources)(/|$)'
    }) -join ';'
    & "$Jdk/bin/javac.exe" -proc:none -implicit:none -encoding UTF-8 -cp ($candidate + ';' + $dependencies) `
        -d $classes 'tools/offline/VerifySelectorCache.java'
    if ($LASTEXITCODE -ne 0) { throw 'Selector tool compilation failed.' }
    $candidateClasspath = $classes + ';' + $candidate + ';' + $dependencies
    $baselineClasspath = $classes + ';' + $baseline + ';' + $dependencies
    & "$Jdk/bin/java.exe" '-Djava.awt.headless=true' -cp $candidateClasspath VerifySelectorCache --verify 'run/flan' `
        *> (Join-Path $evidence 'VerifySelectorCache.final.log')
    if ($LASTEXITCODE -ne 0) { throw 'Selector semantic/cache verification failed.' }
    Get-Content -LiteralPath (Join-Path $evidence 'VerifySelectorCache.final.log') -Tail 1

    $source = 'run/flan/EscapeFromTarcraftReforged_1.5.3/com/flansmod/client/model/EFTRWea/ModelAK74M.class'
    $results = @{}
    foreach ($name in @('baseline', 'candidate')) {
        $classpath = if ($name -eq 'baseline') { $baselineClasspath } else { $candidateClasspath }
        $log = Join-Path $evidence "$name.benchmark.json"
        & "$Jdk/bin/java.exe" '-Djava.awt.headless=true' '-Xms128m' '-Xmx512m' -cp $classpath `
            VerifySelectorCache --benchmark $source 10000 *> $log
        if ($LASTEXITCODE -ne 0) { throw "$name selector allocation benchmark failed." }
        $results[$name] = Get-Content -LiteralPath $log -Raw | ConvertFrom-Json
    }
    if ($results.baseline.sourceSha256 -ne $results.candidate.sourceSha256 -or
        $results.baseline.iterations -ne $results.candidate.iterations -or
        $results.baseline.quads -ne $results.candidate.quads) { throw 'Benchmark inputs differ.' }
    if ($results.baseline.medianBytes -le 0) { throw 'Baseline allocation measurement is invalid.' }
    if ($results.candidate.bytesPerIteration -gt 1) { throw 'Cached selector still allocates in the hot path.' }
    if ((Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash -ne $candidateHash -or
        (Get-FileHash -LiteralPath $baseline -Algorithm SHA256).Hash -ne $baselineHash) { throw 'JAR changed during audit.' }
    $artifact = [ordered]@{
        candidateJar = $candidate; candidateSha256 = $candidateHash
        baselineJar = $baseline; baselineSha256 = $baselineHash
        model = [IO.Path]::GetFullPath($source)
        baseline = $results.baseline; candidate = $results.candidate
        allocatedBytesReduction = 1 - ($results.candidate.medianBytes / $results.baseline.medianBytes)
        measurement = 'Median of 7 samples, 10000 iterations/sample after 10000 warmup iterations; each iteration calls switchModelSlice, slideSwitchModelSlice and switchModelHidden; ThreadMXBean current-thread bytes. Pure CPU, no game/GPU/FPS claims.'
    }
    $artifact | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'artifact.json') -Encoding utf8
    Write-Output ("PASS allocation benchmark: {0} -> {1} bytes/iteration; final JAR {2}" -f
        $results.baseline.bytesPerIteration, $results.candidate.bytesPerIteration, $candidateHash)
} finally { Pop-Location }
