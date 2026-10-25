param(
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$GradleUserHome = 'C:/Users/Administrator/.gradle',
    [switch]$GpuChecks
)

# Production classes come from the final JAR, never from an extra loose-class override.
# The two stubs only provide offline mod bootstrap and feature flags; no game/world is started.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = $Jdk
    & ./gradlew.bat -g $GradleUserHome --offline build writeOfflineClasspath --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    $versionLine = Get-Content gradle.properties | Where-Object { $_ -match '^mod_version=' }
    $version = $versionLine.Substring('mod_version='.Length).Trim()
    $jar = Join-Path $projectRoot "build/libs/flansmod-$version.jar"
    $output = Join-Path $projectRoot "build/verification-all-optics-$version"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $raw = [IO.File]::ReadAllText((Join-Path $projectRoot 'build/offline-classpath.txt')).Trim()
    $entries = $raw -split ';' | Where-Object { $_ -and $_.Replace('\','/') -notmatch '/build/(classes/java/main|resources/main)$' }
    if ($GpuChecks) {
        $nativeRoot = Join-Path $GradleUserHome 'caches/modules-2/files-2.1/org.lwjgl'
        $entries += Get-ChildItem -LiteralPath $nativeRoot -Recurse -File -Filter '*3.3.3-natives-windows.jar' | ForEach-Object { $_.FullName }
    }
    $classpath = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    [IO.File]::WriteAllText((Join-Path $output 'classpath.txt'), $classpath, [Text.UTF8Encoding]::new($false))
    $names = @('VerifyAllSights','VerifyGunSightState','VerifyG33SightLine','VerifySightFoldGeometry','VerifyScopeReticles',
        'VerifySightPicture','VerifySightAlignment','VerifySightFoldPlayback','VerifyScopeZoom','VerifyScopeEyeRelief',
        'VerifyModelTransforms','VerifyGunActions','VerifyMagazineData','VerifyReloadRendering',
        'VerifyReloadInput','VerifyHoldingArms','VerifyLegacyRender','VerifyArmourItems',
        'VerifyArmourRendering','VerifyAttachmentAssemblies','VerifyAttachmentMounting',
        'VerifyGunAttachmentCatalog','VerifyGunModification','VerifyGunModificationView','VerifyReticleResources','VerifyNativeScopeRules')
    if ($GpuChecks) { $names += @('VerifyScopeFramebuffer','VerifyScopeComposition','VerifyOpticsPixels','VerifyG33Picture','VerifyMountedScopes','VerifyScopeDepthOcclusion') }
    $sources = @('tools/offline/stubs/com/flansmod/common/FlansMod.java',
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java',
        'tools/offline/OfflinePack.java','tools/offline/FPPreview.java','tools/offline/VerifyMountedScopes.java','tools/offline/VerifyScopeComposition.java','tools/offline/NativeScopeReference.java')
    $sources += $names | ForEach-Object { "tools/offline/$_.java" }; $sources = $sources | Select-Object -Unique
    & "$Jdk/bin/javac.exe" -proc:none -encoding UTF-8 -cp ($output + ';' + $classpath) -d $output @sources
    if ($LASTEXITCODE -ne 0) { throw 'Verification tool compilation failed.' }
    $common = @('-Djava.awt.headless=true','-Djoml.useUnsafe=false',
        '-Dlog4j.configurationFile=tools/offline/log4j2.xml','-cp',($output + ';' + $classpath))
    $eft = 'run/flan/EscapeFromTarcraftReforged_1.5.3'
    $octagon = 'run/flan/OctagonWeaponsReforged_1.33'
    $armour = @('run/flan/[FMUM]EscapeFromTarCraft-Armor','run/flan/[FMUM]EscapeFromTarCraft-Armor(Scav)')
    foreach ($name in $names) {
        $testArgs = @($eft)
        if ($name -in @('VerifyAllSights','VerifyGunSightState','VerifySightFoldGeometry','VerifySightPicture','VerifyReticleResources','VerifyScopeEyeRelief','VerifyNativeScopeRules')) { $testArgs += $octagon }
        if ($name -in @('VerifyArmourItems','VerifyArmourRendering')) { $testArgs = $armour }
        if ($name -in @('VerifyReloadInput','VerifyScopeFramebuffer')) { $testArgs = @() }
        if ($name -eq 'VerifyScopeComposition') { $testArgs += (Join-Path $output 'composition-images') }
        if ($name -eq 'VerifyOpticsPixels') { $testArgs = @((Join-Path $output 'all-pixel-images'),$eft,$octagon) }
        if ($name -eq 'VerifyMountedScopes') { $testArgs = @((Join-Path $output 'mounted-images'),$eft,$octagon) }
        if ($name -eq 'VerifyScopeDepthOcclusion') { $testArgs = @((Join-Path $output 'depth-occlusion-images'),$eft,$octagon) }
        if ($name -eq 'VerifyG33Picture') { $testArgs = @($eft,(Join-Path $output 'g33-picture-images')) }
        $log = Join-Path $output "$name.final.log"
        & "$Jdk/bin/java.exe" @common $name @testArgs *> $log
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 50; throw "$name failed." }
        Write-Output ("PASS {0}: {1}" -f $name, ((Get-Content -LiteralPath $log -Tail 1) -join ''))
    }
    $legacyLog = Join-Path $output 'VerifyAllSights.legacy.final.log'
    & "$Jdk/bin/java.exe" @common VerifyAllSights 'run/flan/[FMUM]EscapeFromTarCraft-Guns' *> $legacyLog
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $legacyLog -Tail 50; throw 'Legacy optical definitions failed.' }
    Write-Output ('PASS legacy: ' + (Get-Content -LiteralPath $legacyLog -Tail 1))
    if ($GpuChecks) {
        $legacyPixels = Join-Path $output 'VerifyOpticsPixels.legacy.final.log'
        & "$Jdk/bin/java.exe" @common VerifyOpticsPixels (Join-Path $output 'legacy-pixel-images') 'run/flan/[FMUM]EscapeFromTarCraft-Guns' *> $legacyPixels
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $legacyPixels -Tail 50; throw 'Legacy optics GPU pixels failed.' }
        Write-Output ('PASS legacy GPU: ' + (Get-Content -LiteralPath $legacyPixels -Tail 1))
    }
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    [IO.File]::WriteAllText((Join-Path $output 'artifact-sha256.txt'), $hash, [Text.UTF8Encoding]::new($false))
    Write-Output ("Verified {0}; SHA256={1}" -f $jar, $hash)
} finally {
    Pop-Location
}
