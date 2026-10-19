param(
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$GradleUserHome = 'C:/Users/Administrator/.gradle'
)

# Production classes come from the final JAR, never from an extra loose-class override.
# The two stubs only provide offline mod bootstrap and feature flags; no game/world is started.
$ErrorActionPreference = 'Continue'
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
    $looseClasses = Join-Path $projectRoot 'build/classes/java/main'
    $looseResources = Join-Path $projectRoot 'build/resources/main'
    $entries = $raw -split ';' | Where-Object { $_ -and $_ -ne $looseClasses -and $_ -ne $looseResources }
    $classpath = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    [IO.File]::WriteAllText((Join-Path $output 'classpath.txt'), $classpath, [Text.UTF8Encoding]::new($false))
    $names = @('VerifyAllSights','VerifyGunSightState','VerifySightFoldGeometry','VerifyScopeReticles',
        'VerifySightPicture','VerifySightAlignment','VerifySightFoldPlayback','VerifyScopeZoom',
        'VerifyModelTransforms','VerifyGunActions','VerifyMagazineData','VerifyReloadRendering',
        'VerifyReloadInput','VerifyHoldingArms','VerifyLegacyRender','VerifyArmourItems',
        'VerifyArmourRendering','VerifyAttachmentAssemblies','VerifyAttachmentMounting',
        'VerifyGunAttachmentCatalog','VerifyGunModification','VerifyGunModificationView')
    $sources = @('tools/offline/stubs/com/flansmod/common/FlansMod.java',
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java',
        'tools/offline/OfflinePack.java','tools/offline/FPPreview.java')
    $sources += $names | ForEach-Object { "tools/offline/$_.java" }
    & "$Jdk/bin/javac.exe" -proc:none -encoding UTF-8 -cp ($output + ';' + $classpath) -d $output @sources
    if ($LASTEXITCODE -ne 0) { throw 'Verification tool compilation failed.' }
    $common = @('-Djava.awt.headless=true','-Djoml.useUnsafe=false',
        '-Dlog4j.configurationFile=tools/offline/log4j2.xml','-cp',($output + ';' + $classpath))
    $eft = 'run/flan/EscapeFromTarcraftReforged_1.5.3'
    $octagon = 'run/flan/OctagonWeaponsReforged_1.33'
    $armour = @('run/flan/[FMUM]EscapeFromTarCraft-Armor','run/flan/[FMUM]EscapeFromTarCraft-Armor(Scav)')
    foreach ($name in $names) {
        $testArgs = @($eft)
        if ($name -in @('VerifyAllSights','VerifyGunSightState','VerifySightFoldGeometry','VerifySightPicture')) { $testArgs += $octagon }
        if ($name -in @('VerifyArmourItems','VerifyArmourRendering')) { $testArgs = $armour }
        if ($name -eq 'VerifyReloadInput') { $testArgs = @() }
        $log = Join-Path $output "$name.final.log"
        & "$Jdk/bin/java.exe" @common $name @testArgs *> $log
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 50; throw "$name failed." }
        Write-Output ("PASS {0}: {1}" -f $name, ((Get-Content -LiteralPath $log -Tail 1) -join ''))
    }
    $legacyLog = Join-Path $output 'VerifyAllSights.legacy.final.log'
    & "$Jdk/bin/java.exe" @common VerifyAllSights 'run/flan/[FMUM]EscapeFromTarCraft-Guns' *> $legacyLog
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $legacyLog -Tail 50; throw 'Legacy optical definitions failed.' }
    Write-Output ('PASS legacy: ' + (Get-Content -LiteralPath $legacyLog -Tail 1))
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    [IO.File]::WriteAllText((Join-Path $output 'artifact-sha256.txt'), $hash, [Text.UTF8Encoding]::new($false))
    Write-Output ("Verified {0}; SHA256={1}" -f $jar, $hash)
} finally {
    Pop-Location
}
