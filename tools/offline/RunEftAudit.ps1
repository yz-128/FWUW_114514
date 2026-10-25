param(
    [string]$OriginalPack = 'C:\Users\Administrator\AppData\Local\Temp\eftr\EscapeFromTarcraftReforged_1.5.3',
    [string]$GradleUserHome = 'C:\Users\Administrator\.gradle'
)
# Native tools write progress and JVM warnings to stderr. PowerShell 5.1 turns redirected native
# stderr into a terminating NativeCommandError under 'Stop', which would abort a passing run, so
# every step below is gated on $LASTEXITCODE instead.
$ErrorActionPreference = 'Continue'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $projectRoot
try {
    & ./gradlew.bat -g $GradleUserHome writeOfflineClasspath -q
    if ($LASTEXITCODE -ne 0) { throw 'Gradle failed.' }
    # Windows caps a native command line, and PowerShell 5.1 does not reliably quote an argument
    # containing a space before a semicolon. Passing the classpath through a javac/java argfile
    # avoids both. The project path contains a non-ASCII directory name and javac decodes argfiles
    # with the platform charset, so the project's own entries are made relative — that leaves only
    # ASCII jar paths, which every charset decodes identically.
    $root = (Get-Location).Path
    $classpath = [IO.File]::ReadAllText(
            (Join-Path $root 'build/offline-classpath.txt'), [Text.Encoding]::UTF8).Trim()
    $classpath = $classpath.Replace($root + '\', '').Replace($root, '.')
    New-Item -ItemType Directory -Path 'build/offline','reports/eft/materials' -Force | Out-Null
    Set-Content -LiteralPath 'build/offline-javac-args.txt' -Encoding ASCII -Value ('-cp ' + $classpath)
    Set-Content -LiteralPath 'build/offline-java-args.txt' -Encoding ASCII -Value ('-cp build/offline;' + $classpath)
    Set-Content -LiteralPath 'build/offline-data-java-args.txt' -Encoding ASCII -Value ('-cp build/offline-data;build/offline;' + $classpath)
    New-Item -ItemType Directory -Path 'build/offline-data' -Force | Out-Null
    & javac -encoding UTF-8 '@build/offline-javac-args.txt' -d 'build/offline-data' (Join-Path $PSScriptRoot 'data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java')
    if ($LASTEXITCODE -ne 0) { throw 'Offline data fixture compilation failed.' }
    $sources = @('OfflinePack.java','FPPreview.java','TPPreview.java','MaterialAudit.java','VerifyLegacyRender.java','VerifyModelTransforms.java','VerifyGunActions.java','VerifyGunModification.java','VerifyGunModificationView.java','VerifyGunAttachmentCatalog.java','VerifyMagazineData.java','VerifyReloadRendering.java','VerifyReloadInput.java','VerifyRecoilStats.java','VerifyAmmoDetails.java','VerifyHoldingArms.java','VerifyMuzzleEffects.java','VerifyEjections.java','VerifySounds.java','VerifyWorldEffects.java','VerifyMuzzleGlow.java','VerifyShaderMuzzleLighting.java','VerifyBulletBlocks.java','VerifySoundBundles.java','VerifyScopeZoom.java','VerifySightPicture.java','VerifyScopeReticles.java','VerifySightAlignment.java','VerifyGunSightState.java','VerifyCasingPhysics.java','VerifyRestWallShape.java','CrossPackAudit.java') |
        ForEach-Object { Join-Path $PSScriptRoot $_ }
    $sources += Join-Path $PSScriptRoot 'stubs/com/flansmod/common/FlansMod.java'
    & javac -encoding UTF-8 '@build/offline-javac-args.txt' -d 'build/offline' @sources
    if ($LASTEXITCODE -ne 0) { throw 'Offline tool compilation failed.' }
    $javaOptions = @('-Dlog4j.configurationFile=tools/offline/log4j2.xml','-Djava.awt.headless=true','-Djoml.useUnsafe=false','@build/offline-java-args.txt')
    $pack = 'run/flan/EscapeFromTarcraftReforged_1.5.3'
    & java @javaOptions VerifyModelTransforms $pack *> 'reports/eft/model_transform_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Original model transform order contracts failed; see model_transform_verification.log.' }
    & java @javaOptions VerifyReloadInput *> 'reports/eft/reload_input_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Reload input contracts failed; see reload_input_verification.log.' }
    & java @javaOptions VerifyLegacyRender $pack *> 'reports/eft/verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Legacy rendering contracts failed; see verification.log.' }
    & java @javaOptions VerifyHoldingArms $pack *> 'reports/eft/holding_pose_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Holding arm/model part contracts failed; see holding_pose_verification.log.' }
    & java @javaOptions VerifyMuzzleEffects $pack *> 'reports/eft/muzzle_effects_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Muzzle effect contracts failed; see muzzle_effects_verification.log.' }
    & java @javaOptions VerifyWorldEffects $pack *> 'reports/eft/world_effects_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'World smoke/casing view contracts failed; see world_effects_verification.log.' }
    & java @javaOptions VerifyGunActions $pack *> 'reports/eft/actions_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Gun action contracts failed; see actions_verification.log.' }
    & java @javaOptions VerifyReloadRendering $pack *> 'reports/eft/reload_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Reload rendering contracts failed; see reload_verification.log.' }
    & java @javaOptions VerifyScopeZoom $pack *> 'reports/eft/scope_zoom_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Scope magnification/parsing contracts failed; see scope_zoom_verification.log.' }
    & java @javaOptions VerifySightPicture $pack 'run/flan/OctagonWeaponsReforged_1.33' *> 'reports/eft/sight_picture_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Sight picture/ray contracts failed; see sight_picture_verification.log.' }
    & java @javaOptions VerifySightAlignment $pack *> 'reports/eft/sight_alignment_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Scope alignment/ocular contracts failed; see sight_alignment_verification.log.' }
    & java @javaOptions VerifyCasingPhysics $pack *> 'reports/eft/casing_physics_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Casing physics contracts failed; see casing_physics_verification.log.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyRestWallShape *> 'reports/eft/rest_wall_shape_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Muzzle-against-wall shape contracts failed; see rest_wall_shape_verification.log.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyScopeReticles $pack *> 'reports/eft/scope_reticle_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Scope reticle composition contracts failed; see scope_reticle_verification.log.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyBulletBlocks $pack *> 'reports/eft/bullet_blocks_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Native soft-block collision contracts failed; see bullet_blocks_verification.log.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyMuzzleGlow $pack *> 'reports/eft/muzzle_glow_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Muzzle glow geometry/state contracts failed; see muzzle_glow_verification.log.' }
    $shaderClasspath = 'build/offline-data;build/offline;' + $classpath
    $irisJar = Get-ChildItem -LiteralPath 'run/mods' -Filter 'iris-neoforge*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($irisJar) { $shaderClasspath += ';' + $irisJar.FullName.Replace($root + '\', '') }
    $shaderTestArgs = @($pack)
    $lwjglClasspathJar = $classpath -split ';' | Where-Object { $_ -match '[\\/]org\.lwjgl[\\/]lwjgl[\\/]' } | Select-Object -First 1
    if ($lwjglClasspathJar) {
        $lwjglVersionPath = Split-Path -Parent (Split-Path -Parent $lwjglClasspathJar)
        $lwjglNative = Get-ChildItem -LiteralPath $lwjglVersionPath -Recurse -Filter '*natives-windows.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($lwjglNative) { $shaderClasspath += ';' + $lwjglNative.FullName; $shaderTestArgs += '--native-smoke' }
    }
    Set-Content -LiteralPath 'build/offline-shader-java-args.txt' -Encoding ASCII -Value ('-cp ' + $shaderClasspath)
    & java '-Dorg.lwjgl.system.allocator=system' '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-shader-java-args.txt' VerifyShaderMuzzleLighting @shaderTestArgs *> 'reports/eft/shader_muzzle_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Shader light/projection/lifecycle contracts failed; see shader_muzzle_verification.log.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '@build/offline-data-java-args.txt' VerifyMagazineData $pack *> 'reports/eft/magazine_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Actual magazine/NBT contracts failed; see magazine_verification.log.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyGunModification $pack *> 'reports/eft/gun_modification_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Actual gun modification inventory/save contracts failed; see gun_modification_verification.log.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyGunModificationView $pack *> 'reports/eft/gun_modification_view_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Gun modification framing/normals contracts failed.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyGunAttachmentCatalog $pack *> 'reports/eft/gun_attachment_catalog_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Gun attachment catalogue/preview contracts failed.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyEjections $pack *> 'reports/eft/ejections_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Original casing physics/assets/item contracts failed.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifySounds $pack *> 'reports/eft/sounds_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Pack sound resources/vanilla sound packet contracts failed.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' com.flansmod.common.VerifySoundBundles *> 'reports/eft/sound_bundle_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Combined pack sound registration contracts failed.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' CrossPackAudit 'run/flan' 'reports/eft/crosspack' *> 'reports/eft/crosspack_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Cross-pack resource inspection failed.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyRecoilStats $pack *> 'reports/eft/recoil_stats_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Recoil/stat contracts failed; see recoil_stats_verification.log.' }
    & java '-Dlog4j.configurationFile=tools/offline/log4j2.xml' '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '@build/offline-data-java-args.txt' VerifyAmmoDetails $pack *> 'reports/eft/ammo_stats_verification.log'
    if ($LASTEXITCODE -ne 0) { throw 'Ammo tooltip contracts failed; see ammo_stats_verification.log.' }
    & java @javaOptions MaterialAudit $pack $OriginalPack 'reports/eft/materials' *> 'reports/eft/material_audit.log'
    if ($LASTEXITCODE -ne 0) { throw 'Material audit failed; see material_audit.log.' }
    foreach ($state in @(
        @{name='idle';ads='0';sprint='0';reload='0';recoil='0'},
        @{name='ads';ads='1';sprint='0';reload='0';recoil='0'},
        @{name='sprint';ads='0';sprint='1';reload='0';recoil='0'},
        @{name='reload';ads='0';sprint='0';reload='1';recoil='0'},
        @{name='recoil';ads='0';sprint='0';reload='0';recoil='1'}
    )) {
        $output = 'reports/eft/AK74M_' + $state.name
        & java ('-Dfp.ads='+$state.ads) ('-Dfp.sprint='+$state.sprint) ('-Dfp.reload='+$state.reload) ('-Dfp.recoil='+$state.recoil) @javaOptions FPPreview $pack AK74M ($output+'.png') *> ($output+'.log')
        if ($LASTEXITCODE -ne 0) { throw ('Preview failed: '+$state.name) }
    }
    & java '-Dfp.tintArms=false' @javaOptions FPPreview $pack AK74M 'reports/eft/AK74M_skin.png' *> 'reports/eft/AK74M_skin.log'
    if ($LASTEXITCODE -ne 0) { throw 'Skinned preview failed.' }
    foreach($state in @(
        @{name='takeout';operation='NONE';progress='0';takeOut='.5';motion='false'},
        @{name='check_ammo';operation='CHECK_AMMO';progress='.75';takeOut='0';motion='false'},
        @{name='check_chamber';operation='CHECK_CHAMBER';progress='.75';takeOut='0';motion='false'},
        @{name='check_chamber_bc';operation='CHECK_CHAMBER_BC';progress='.75';takeOut='0';motion='false'},
        @{name='rest';operation='REST';progress='1';takeOut='0';motion='false'},
        @{name='view_follow';operation='NONE';progress='0';takeOut='0';motion='true'}
    )) {
        $output='reports/eft/AK74M_action_'+$state.name
        & java '-Dfp.tintArms=false' '-Dfp.mag=6L20_545x39_30Rounds' ('-Dfp.operation='+$state.operation) ('-Dfp.progress='+$state.progress) ('-Dfp.takeOut='+$state.takeOut) ('-Dfp.motion='+$state.motion) @javaOptions FPPreview $pack AK74M ($output+'.png') *> ($output+'.log')
        if($LASTEXITCODE -ne 0) { throw ('Action preview failed: '+$state.name) }
    }
    foreach ($phase in @(
        @{name='unload_mid';step='UNLOAD_MAG';progress='.5'},
        @{name='load_mid';step='LOAD_MAG';progress='.5'},
        @{name='charge_mid';step='CHARGE';progress='.8'}
    )) {
        $output='reports/eft/AK74M_reload_'+$phase.name
        & java '-Dfp.tintArms=false' '-Dfp.mag=6L20_545x39_30Rounds' ('-Dfp.reloadStep='+$phase.step) ('-Dfp.reloadProgress='+$phase.progress) @javaOptions FPPreview $pack AK74M ($output+'.png') *> ($output+'.log')
        if($LASTEXITCODE -ne 0) { throw ('Reload preview failed: '+$phase.name) }
    }
    foreach ($rounds in @(0,1,29,30)) {
        $output='reports/eft/6L20_seated_'+$rounds
        & java '-Dfp.tintArms=false' ('-Dfp.rounds='+$rounds) @javaOptions FPPreview $pack 6L20_545x39_30Rounds ($output+'.png') *> ($output+'.log')
        if($LASTEXITCODE -ne 0) { throw ('Magazine preview failed: '+$rounds) }
    }
    foreach ($comparison in @(@{name='body_center';body='true'},@{name='attachment';body='false'})) {
        $output = 'reports/eft/AK74M_ads_' + $comparison.name
        & java '-Dfp.ads=1' '-Dfp.tintArms=false' ('-Dfp.gunBodyAim='+$comparison.body) @javaOptions FPPreview $pack AK74M ($output+'.png') *> ($output+'.log')
        if ($LASTEXITCODE -ne 0) { throw ('ADS comparison failed: '+$comparison.name) }
    }
    foreach ($view in @(@{name='front';yaw='180';pitch='0'},@{name='back';yaw='0';pitch='0'},@{name='side';yaw='95';pitch='0'},@{name='up';yaw='160';pitch='-35'},@{name='down';yaw='160';pitch='35'})) {
        $output = 'reports/eft/AK74M_third_' + $view.name
        & java '-Dfp.tintArms=false' ('-Dtp.viewYaw='+$view.yaw) ('-Dtp.headPitch='+$view.pitch) @javaOptions TPPreview $pack AK74M ($output+'.png') *> ($output+'.log')
        if ($LASTEXITCODE -ne 0) { throw ('Third-person preview failed: '+$view.name) }
    }
    Write-Output 'Offline EFT checks and reports completed. No game was launched and no desktop input was generated.'
} finally {
    Pop-Location
}
