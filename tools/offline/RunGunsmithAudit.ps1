param(
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$GradleUserHome = 'C:/Users/Administrator/.gradle',
    [ValidateSet('modern', 'classic')]
    [string]$Variant = 'modern',
    [switch]$SkipBuild
)

# Headless checks and labelled CPU previews only. No game launch or deployment.
# Production code comes from the final JAR; the only overrides are the two bootstrap stubs.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$previousJavaHome = $env:JAVA_HOME
Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = $Jdk
    $gradleTasks = @(if ($SkipBuild) { 'writeOfflineClasspath' } else {
        'build'
        if ($Variant -eq 'classic') { 'classicJar' }
        'writeOfflineClasspath'
    })
    & ./gradlew.bat -g $GradleUserHome --offline @gradleTasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build/classpath generation failed.' }

    $versionLine = @(Get-Content -LiteralPath 'gradle.properties' | Where-Object { $_ -match '^mod_version=' })
    if ($versionLine.Count -ne 1) { throw 'Expected exactly one mod_version in gradle.properties.' }
    $version = $versionLine[0].Substring('mod_version='.Length).Trim()
    $variantSuffix = if ($Variant -eq 'classic') { '-classic' } else { '' }
    $jar = Join-Path $projectRoot "build/libs/flansmod-$version$variantSuffix.jar"
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Final artifact is missing: $jar" }
    $artifactHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    $output = Join-Path $projectRoot "build/verification-gunsmith-$version$variantSuffix"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    # A fresh tool directory prevents stale loose production classes from shadowing the JAR.
    $toolClasses = Join-Path $output ('tools-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $toolClasses | Out-Null

    $raw = [IO.File]::ReadAllText((Join-Path $projectRoot 'build/offline-classpath.txt')).Trim()
    $entries = $raw -split ';' | Where-Object {
        $_ -and $_.Replace('\', '/') -notmatch '/build/(classes|resources)(/|$)'
    }
    $classpath = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    [IO.File]::WriteAllText((Join-Path $output 'classpath.txt'), $classpath, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $output 'tool-classes.txt'), $toolClasses, [Text.UTF8Encoding]::new($false))

    $names = @('VerifyGunModification', 'VerifyGunModificationView', 'VerifyGunAttachmentCatalog',
        'VerifyAttachmentAssemblies', 'VerifyAttachmentMounting', 'VerifyFireModeSelector')
    if ($Variant -eq 'modern') { $names += @('VerifyGunModificationLayout', 'VerifyGunModificationRailDrag', 'VerifyGunModificationPicking') }
    $sources = @('tools/offline/stubs/com/flansmod/common/FlansMod.java',
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java',
        'tools/offline/OfflinePack.java', 'tools/offline/FPPreview.java')
    $sources += $names | ForEach-Object { "tools/offline/$_.java" }
    & "$Jdk/bin/javac.exe" -proc:none -implicit:none -encoding UTF-8 -cp $classpath -d $toolClasses @sources
    if ($LASTEXITCODE -ne 0) { throw 'Gunsmith verification tool compilation failed.' }

    $common = @('-Djava.awt.headless=true', '-Djoml.useUnsafe=false',
        '-Dlog4j.configurationFile=tools/offline/log4j2.xml', '-cp', ($toolClasses + ';' + $classpath))
    $eft = 'run/flan/EscapeFromTarcraftReforged_1.5.3'
    foreach ($name in $names) {
        $testArgs = @(if ($name -eq 'VerifyFireModeSelector') { $jar; $eft }
            elseif ($name -ne 'VerifyGunModificationLayout') { $eft })
        $log = Join-Path $output "$name.final.log"
        & "$Jdk/bin/java.exe" @common $name @testArgs *> $log
        if ($LASTEXITCODE -ne 0) {
            Get-Content -LiteralPath $log -Tail 50
            throw "$name failed."
        }
        Write-Output ("PASS {0}: {1}" -f $name, ((Get-Content -LiteralPath $log -Tail 1) -join ''))
    }

    if ($Variant -eq 'modern') {
        # The new renderer reuses the earlier helper's CPU mesh rasterizer for item thumbnails.
        $renderSources = @('tools/offline/RenderGunModificationLayout.java', 'tools/offline/RenderTarkovGunsmithLayout.java')
        & "$Jdk/bin/javac.exe" -proc:none -implicit:none -encoding UTF-8 -cp ($toolClasses + ';' + $classpath) -d $toolClasses @renderSources
        if ($LASTEXITCODE -ne 0) { throw 'Offline gunsmith layout renderer compilation failed.' }
        $previews = Join-Path $output 'previews'
        $renderLog = Join-Path $output 'RenderTarkovGunsmithLayout.final.log'
        & "$Jdk/bin/java.exe" @common RenderTarkovGunsmithLayout $eft $previews 'AK74M' *> $renderLog
        if ($LASTEXITCODE -ne 0) {
            Get-Content -LiteralPath $renderLog -Tail 50
            throw 'Offline gunsmith layout rendering failed.'
        }
        $expectedPreviews = @('AK74M-320x240.png', 'AK74M-320x240-inventory.png',
            'AK74M-480x270.png', 'AK74M-480x270-inventory.png', 'AK74M-640x360.png',
            'AK74M-960x540.png', 'AK74M-1280x720.png')
        foreach ($name in $expectedPreviews) {
            $preview = Join-Path $previews $name
            if (-not (Test-Path -LiteralPath $preview -PathType Leaf) -or (Get-Item -LiteralPath $preview).Length -eq 0) {
                throw "Expected offline layout preview is missing or empty: $preview"
            }
        }
        Write-Output ("PASS RenderTarkovGunsmithLayout: {0} labelled offline previews in {1}" -f $expectedPreviews.Count, $previews)

    } else {
        Write-Output 'Classic variant: shared behaviour, selector and GPU rendering checks; no modern layout or offline UI previews are claimed.'
    }

    # Production preview RenderType in a hidden GPU context. This is the regression that
    # catches the see-through receiver: the layer must write real depth and its pixels must be
    # independent of piece draw order.
    $nativeRoot = Join-Path $GradleUserHome 'caches/modules-2/files-2.1/org.lwjgl'
    $natives = @(Get-ChildItem -LiteralPath $nativeRoot -Recurse -File -Filter '*3.3.3-natives-windows.jar' |
        ForEach-Object { $_.FullName })
    $gpuClasspath = (@($toolClasses) + @($classpath) + $natives) -join ';'
    $gpuSources = @('tools/offline/OfflinePack.java', 'tools/offline/FPPreview.java',
        'tools/offline/VerifyScopeComposition.java', 'tools/offline/VerifyGunModificationRendering.java',
        'tools/offline/stubs/com/flansmod/common/FlansMod.java',
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java')
    & "$Jdk/bin/javac.exe" -proc:none -encoding UTF-8 -cp $gpuClasspath -d $toolClasses @gpuSources
    if ($LASTEXITCODE -ne 0) { throw 'Gunsmith preview depth verification compilation failed.' }
    $depthOutput = Join-Path $output 'preview-depth'
    New-Item -ItemType Directory -Path $depthOutput -Force | Out-Null
    $depthLog = Join-Path $output 'VerifyGunModificationRendering.final.log'
    & "$Jdk/bin/java.exe" @('-Djava.awt.headless=true', '-Djoml.useUnsafe=false', '-cp', $gpuClasspath) `
        VerifyGunModificationRendering $depthOutput $eft *> $depthLog
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $depthLog -Tail 50
        throw 'Gunsmith preview depth verification failed.'
    }
    Write-Output ("PASS VerifyGunModificationRendering: {0}" -f ((Get-Content -LiteralPath $depthLog -Tail 1) -join ''))

    $finalHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    if ($finalHash -ne $artifactHash) { throw 'Final JAR changed during verification; rerun against one immutable build.' }
    [IO.File]::WriteAllText((Join-Path $output 'artifact-sha256.txt'), $finalHash, [Text.UTF8Encoding]::new($false))
    Write-Output ("Verified {0} variant: {1}; SHA256={2}" -f $Variant, $jar, $finalHash)
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
