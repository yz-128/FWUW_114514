param([string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1')

# Read and verify the final artifact, with production classes exclusively from its JAR.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $projectRoot
try {
    $version = (Get-Content gradle.properties | Where-Object { $_ -match '^mod_version=' }).Substring('mod_version='.Length).Trim()
    $jar = Join-Path $projectRoot "build/libs/flansmod-$version.jar"
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    $out = Join-Path $projectRoot "build/verification-all-armour-$version"
    $reports = Join-Path $projectRoot "reports/eft/armour-$version"
    New-Item -ItemType Directory -Path $out -Force | Out-Null
    $raw = [IO.File]::ReadAllText((Join-Path $projectRoot 'build/offline-classpath.txt')).Trim()
    $classes = Join-Path $projectRoot 'build/classes/java/main'
    $resources = Join-Path $projectRoot 'build/resources/main'
    $entries = $raw -split ';' | Where-Object { $_ -and $_ -ne $classes -and $_ -ne $resources -and $_ -notmatch '[/\\]build[/\\]libs[/\\]flansmod-[^/\\]+\.jar$' }
    $classpath = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    [IO.File]::WriteAllText((Join-Path $out 'classpath.txt'),$classpath,[Text.UTF8Encoding]::new($false))
    $sources = @('tools/offline/stubs/com/flansmod/common/FlansMod.java',
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java',
        'tools/offline/OfflinePack.java','tools/offline/VerifyArmourItems.java','tools/offline/VerifyArmourRendering.java',
        'tools/offline/AuditAllArmour.java','tools/offline/VerifyAllArmour.java')
    & "$Jdk/bin/javac.exe" -proc:none -encoding UTF-8 -cp $classpath -d $out @sources
    if($LASTEXITCODE -ne 0){throw 'Armour verification compilation failed.'}
    foreach($name in @('AuditAllArmour','VerifyAllArmour')) {
        $log = Join-Path $out "$name.final.log"
        $testArgs = @('run/flan')
        if($name -eq 'AuditAllArmour'){$testArgs += $reports}
        & "$Jdk/bin/java.exe" '-Djava.awt.headless=true' '-Djoml.useUnsafe=false' '-Dlog4j.configurationFile=tools/offline/log4j2.xml' -cp ($out+';'+$classpath) $name @testArgs *> $log
        if($LASTEXITCODE -ne 0){Get-Content -LiteralPath $log -Tail 30;throw "$name failed."}
        Write-Output (Get-Content -LiteralPath $log -Tail 1)
    }
    $summary = Get-Content -LiteralPath (Join-Path $reports 'summary.json') -Raw | ConvertFrom-Json
    if($summary.findingRows -ne 0){throw "Unresolved armour audit findings: $($summary.findingRows)"}
    if((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $hash){throw 'Production JAR changed during audit.'}
    [IO.File]::WriteAllText((Join-Path $out 'artifact-sha256.txt'),$hash,[Text.UTF8Encoding]::new($false))
    Write-Output "Verified $($summary.definitions) definitions / $($summary.registered) items / zero findings; SHA256=$hash"
} finally { Pop-Location }
