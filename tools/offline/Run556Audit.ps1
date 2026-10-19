param(
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [switch]$VerifyRepairs
)

# Audit an existing release JAR. No production build, game process, input or pack writes.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $projectRoot
try {
    $version = (Get-Content -LiteralPath gradle.properties | Where-Object { $_ -match '^mod_version=' }).Substring('mod_version='.Length).Trim()
    $jar = Join-Path $projectRoot "build/libs/flansmod-$version.jar"
    $jarHashBefore = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    $out = Join-Path $projectRoot "build/verification-556-$version"
    $reports = Join-Path $projectRoot "reports/eft/556-series-$version"
    New-Item -ItemType Directory -Path $out -Force | Out-Null
    $raw = [IO.File]::ReadAllText((Join-Path $projectRoot 'build/offline-classpath.txt')).Trim()
    $looseClasses = Join-Path $projectRoot 'build/classes/java/main'
    $looseResources = Join-Path $projectRoot 'build/resources/main'
    $entries = $raw -split ';' | Where-Object { $_ -and $_ -ne $looseClasses -and $_ -ne $looseResources -and $_ -notmatch '[/\\]build[/\\]libs[/\\]flansmod-[^/\\]+\.jar$' }
    $classpath = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    [IO.File]::WriteAllText((Join-Path $out 'classpath.txt'), $classpath, [Text.UTF8Encoding]::new($false))
    $sources = @('tools/offline/stubs/com/flansmod/common/FlansMod.java',
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java',
        'tools/offline/OfflinePack.java', 'tools/offline/Audit556Series.java')
    if ($VerifyRepairs) { $sources += @('tools/offline/Verify556Series.java','tools/offline/Render556RepairPreview.java') }
    & "$Jdk/bin/javac.exe" -proc:none -encoding UTF-8 -cp $classpath -d $out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Audit tool compilation failed.' }
    $log = Join-Path $out 'audit.log'
    & "$Jdk/bin/java.exe" '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '-Dlog4j.configurationFile=tools/offline/log4j2.xml' -cp ($out + ';' + $classpath) Audit556Series run/flan $reports *> $log
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 45; throw 'Audit execution failed.' }
    if ($VerifyRepairs) {
        $verificationLog = Join-Path $out 'Verify556Series.log'
        & "$Jdk/bin/java.exe" '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '-Dlog4j.configurationFile=tools/offline/log4j2.xml' -cp ($out + ';' + $classpath) Verify556Series run/flan *> $verificationLog
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $verificationLog -Tail 45; throw 'Magazine repair verification failed.' }
        Write-Output (Get-Content -LiteralPath $verificationLog -Tail 1)
    }
    $jarHashAfter = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    if ($jarHashBefore -ne $jarHashAfter) { throw 'Production JAR changed during audit.' }
    Write-Output (Get-Content -LiteralPath $log -TotalCount 1)
    Write-Output "Read-only production artifact: $jarHashAfter"
    Write-Output "Reports: $reports"
} finally {
    Pop-Location
}
