param(
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$GradleUserHome = 'C:/Users/Administrator/.gradle'
)
# Tactical devices (flashlight / laser): real pack parsing, model references, stack switches, keys
# and payload wiring against the final JAR. No game/GPU/input.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = $Jdk
    & ./gradlew.bat -g $GradleUserHome --offline build writeOfflineClasspath --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    $version = (Get-Content gradle.properties | Where-Object { $_ -match '^mod_version=' }).Substring('mod_version='.Length).Trim()
    $jar = Join-Path $projectRoot "build/libs/flansmod-$version.jar"
    $output = Join-Path $projectRoot "build/verification-tactical-$version"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $entries = [IO.File]::ReadAllText((Join-Path $projectRoot 'build/offline-classpath.txt')).Trim() -split ';' |
        Where-Object { $_ -and $_.Replace('\','/') -notmatch '/build/(classes/java/main|resources/main)$' }
    $classpath = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    $sources = @(
        'tools/offline/stubs/com/flansmod/common/FlansMod.java',
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java',
        'tools/offline/OfflinePack.java',
        'tools/offline/VerifyTacticalDevices.java',
        'tools/offline/VerifyMountedEmission.java'
    )
    & "$Jdk/bin/javac.exe" -proc:none -encoding UTF-8 -cp $classpath -d $output @sources
    if ($LASTEXITCODE -ne 0) { throw 'Tactical verification compilation failed.' }
    $arguments = @('-Djava.awt.headless=true','-Djoml.useUnsafe=false',
        '-Dlog4j.configurationFile=tools/offline/log4j2.xml','-cp',($output + ';' + $classpath))
    & "$Jdk/bin/java.exe" @arguments VerifyTacticalDevices $jar 'run/flan' *> (Join-Path $output 'VerifyTacticalDevices.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $output 'VerifyTacticalDevices.log') -Tail 40; throw 'VerifyTacticalDevices failed.' }
    Write-Output (Get-Content (Join-Path $output 'VerifyTacticalDevices.log') -Tail 1)
    & "$Jdk/bin/java.exe" @arguments VerifyMountedEmission 'run/flan' *> (Join-Path $output 'VerifyMountedEmission.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $output 'VerifyMountedEmission.log') -Tail 40; throw 'VerifyMountedEmission failed.' }
    Write-Output (Get-Content (Join-Path $output 'VerifyMountedEmission.log') -Tail 1)
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    [IO.File]::WriteAllText((Join-Path $output 'artifact-sha256.txt'), $hash, [Text.UTF8Encoding]::new($false))
    Write-Output "Verified tactical devices on $jar; SHA256=$hash"
} finally { Pop-Location }
