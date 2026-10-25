param(
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$GradleUserHome = 'C:/Users/Administrator/.gradle',
    [string]$Classifier = '',
    [string[]]$Only = @(),
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Continue'
$repairRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $repairRoot
try {
    $env:JAVA_HOME = $Jdk
    if (!$SkipBuild) {
        & ./gradlew.bat --offline build writeOfflineClasspath --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    }
    $repairVersion = ((Get-Content gradle.properties | Where-Object { $_ -match '^mod_version=' }) -split '=',2)[1].Trim()
    $repairSuffix = if ($Classifier) { '-'+$Classifier } else { '' }
    $repairJar = "build/libs/flansmod-$repairVersion$repairSuffix.jar"
    $repairOutput = "build/verification-deep-repair-$repairVersion$repairSuffix"
    New-Item -ItemType Directory -Path $repairOutput -Force | Out-Null
    $repairClasspath = [IO.File]::ReadAllText((Join-Path $repairRoot 'build/offline-classpath.txt')).Trim() -split ';' |
        Where-Object { $_ -and $_.Replace('\','/') -notmatch '/build/(classes/java/main|resources/main)$' }
    $repairClasspath += Get-ChildItem -LiteralPath (Join-Path $GradleUserHome 'caches/modules-2/files-2.1/org.lwjgl') -Recurse -File -Filter '*3.3.3-natives-windows.jar' | ForEach-Object { $_.FullName }
    $repairCp = ((@($repairJar)+@($repairClasspath) | Select-Object -Unique) -join ';').Replace('\','/')
    # Windows' Java launcher reads @argfiles before the VM's encoding properties apply.
    # Keep this project's Chinese path out of that native launcher input.
    $repairCp = $repairCp.Replace($repairRoot.Replace('\','/')+'/', '')
    $repairNames = @('VerifyLegacyScopeBake','VerifyScopeMaskProjection','VerifyScopeImageComposer','VerifyScopeBakedPipeline','VerifyScopeOrientation',
        'VerifyAllSights','VerifyGunSightState','VerifyG33SightLine','VerifySightFoldGeometry','VerifySightFoldPlayback',
        'VerifyScopeReticles','VerifySightPicture','VerifySightAlignment','VerifyScopeZoom','VerifyScopeEyeRelief',
        'VerifyReticleResources','VerifyNativeScopeRules','VerifyScopeFramebuffer','VerifyScopeTargetState','VerifyScopeWorldView',
        'VerifyScopeDepthOcclusion','VerifyMagazineData','VerifyGunActions','VerifyReloadRendering','VerifyReloadInput',
        'VerifyHoldingArms','VerifyModelTransforms','VerifyArmourItems','VerifyArmourRendering',
        'VerifyAttachmentAssemblies','VerifyAttachmentMounting','VerifyGunAttachmentCatalog',
        'VerifyGunModification','VerifyGunModificationView')
    $repairSources = @('tools/offline/stubs/com/flansmod/common/FlansMod.java',
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java',
        'tools/offline/OfflinePack.java','tools/offline/FPPreview.java','tools/offline/VerifyMountedScopes.java',
        'tools/offline/VerifyScopeComposition.java','tools/offline/NativeScopeReference.java')
    $repairSources += $repairNames | ForEach-Object { "tools/offline/$_.java" }
    $repairSources = $repairSources | Select-Object -Unique
    $repairCompile = '-proc:none -encoding UTF-8 -cp "'+$repairOutput+';'+$repairCp+'" -d "'+$repairOutput+'" '+($repairSources -join ' ')
    [IO.File]::WriteAllText((Join-Path $repairRoot "$repairOutput/javac.args"),$repairCompile,[Text.UTF8Encoding]::new($false))
    & "$Jdk/bin/javac.exe" "@$repairOutput/javac.args"
    if ($LASTEXITCODE -ne 0) { throw 'Tool compilation failed' }
    $repairRun = '-Djava.awt.headless=true -Djoml.useUnsafe=false -Dflansmod.scopeProbes=false -Dlog4j.configurationFile=tools/offline/log4j2.xml -cp "'+$repairOutput+';'+$repairCp+'"'
    [IO.File]::WriteAllText((Join-Path $repairRoot "$repairOutput/java.args"),$repairRun,[Text.UTF8Encoding]::new($false))
    $repairEft = 'run/flan/EscapeFromTarcraftReforged_1.5.3'
    $repairOctagon = 'run/flan/OctagonWeaponsReforged_1.33'
    $repairResults = @()
    $repairRunNames = if ($Only.Count) { @($repairNames | Where-Object { $_ -in $Only }) } else { $repairNames }
    foreach ($repairName in $repairRunNames) {
        $repairArgs = @($repairEft)
        if ($repairName -in @('VerifyAllSights','VerifyGunSightState','VerifySightFoldGeometry','VerifySightPicture','VerifyReticleResources','VerifyScopeEyeRelief','VerifyNativeScopeRules','VerifyScopeMaskProjection')) { $repairArgs += $repairOctagon }
        if ($repairName -in @('VerifyArmourItems','VerifyArmourRendering')) { $repairArgs = @('run/flan/[FMUM]EscapeFromTarCraft-Armor','run/flan/[FMUM]EscapeFromTarCraft-Armor(Scav)') }
        if ($repairName -in @('VerifyScopeImageComposer','VerifyReloadInput','VerifyScopeFramebuffer','VerifyScopeTargetState')) { $repairArgs = @() }
        if ($repairName -eq 'VerifyScopeWorldView') { $repairArgs = @("$repairOutput/world-view-images") }
        if ($repairName -in @('VerifyScopeBakedPipeline','VerifyScopeDepthOcclusion','VerifyScopeOrientation')) { $repairArgs = @("$repairOutput/$repairName-images",$repairEft,$repairOctagon) }
        $repairLog = "$repairOutput/$repairName.final.log"
        & "$Jdk/bin/java.exe" "@$repairOutput/java.args" $repairName @repairArgs *> $repairLog
        $repairCode = $LASTEXITCODE
        $repairTail = (Get-Content -LiteralPath $repairLog -Tail 1) -join ''
        $repairResults += [PSCustomObject]@{name=$repairName;exitCode=$repairCode;result=$repairTail}
        if ($repairCode -eq 0) { Write-Output ("PASS {0}: {1}" -f $repairName,$repairTail) }
        else { Write-Output ("FAIL {0}" -f $repairName); Get-Content -LiteralPath $repairLog -Tail 14 }
    }
    if (!$Only.Count -or 'VerifyAllSights' -in $Only) {
        $repairLegacyLog = "$repairOutput/VerifyAllSights.legacy.final.log"
        & "$Jdk/bin/java.exe" "@$repairOutput/java.args" VerifyAllSights 'run/flan/[FMUM]EscapeFromTarCraft-Guns' *> $repairLegacyLog
        $repairLegacyCode=$LASTEXITCODE
        $repairLegacyTail=(Get-Content -LiteralPath $repairLegacyLog -Tail 1) -join ''
        $repairResults += [PSCustomObject]@{name='VerifyAllSights.legacy';exitCode=$repairLegacyCode;result=$repairLegacyTail}
        Write-Output ("{0} VerifyAllSights.legacy: {1}" -f $(if($repairLegacyCode -eq 0){'PASS'}else{'FAIL'}),$repairLegacyTail)
    }
    $repairResults | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath "$repairOutput/results.json" -Encoding UTF8
    Get-FileHash -LiteralPath $repairJar -Algorithm SHA256 | Format-List | Out-File -LiteralPath "$repairOutput/artifact-sha256.txt" -Encoding UTF8
    if (@($repairResults | Where-Object { $_.exitCode -ne 0 }).Count -gt 0) { throw 'Deep repair verification has failures; inspect results.json' }
} finally { Pop-Location }
