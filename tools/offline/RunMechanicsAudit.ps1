# Verifies the gameplay mechanics added on top of the 1.7.10 port.
#
#   .\RunMechanicsAudit.ps1                 # latest non-classic jar
#   .\RunMechanicsAudit.ps1 -Jar path\to.jar
#
# Everything here is offline: the mechanics are pure functions plus data fields, so the checks
# parse real classes, run the rules and inspect the final jar, without launching the game.
param([string]$JarPath = '')
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Resolve-Path (Join-Path $here '..\..')
if (-not $env:JAVA_HOME) { throw 'JAVA_HOME must point at the JDK used for the build.' }
$Jdk = Join-Path $env:JAVA_HOME 'bin'

$jarFile = if ($JarPath) {
    Get-Item -LiteralPath $JarPath
} else {
    Get-ChildItem (Join-Path $root 'build\libs') -Filter 'flansmod-*.jar' |
        Where-Object { $_.Name -notmatch 'classic' } |
        Sort-Object LastWriteTime | Select-Object -Last 1
}
if (-not $jarFile) { throw 'No jar found under build/libs.' }
$artifactHash = (Get-FileHash -LiteralPath $jarFile.FullName -Algorithm SHA256).Hash

$raw = Get-Content (Join-Path $root 'build\offline-classpath.txt') -Raw
$entries = ($raw -split ';') | Where-Object { $_ -and ($_ -replace '\\', '/') -notmatch '/build/(classes|resources|libs)(/|$)' }
$classpath = (@($jarFile.FullName) + @($entries) | Select-Object -Unique) -join ';'

$out = Join-Path $root 'build\verification-mechanics'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources = @(
    'tools/offline/stubs/com/flansmod/common/FlansMod.java',
    'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java',
    'tools/offline/VerifyBarrelHeat.java',
    'tools/offline/VerifyBulletBlockPenetration.java',
    'tools/offline/VerifyMechanicsSettings.java'
) | ForEach-Object { Join-Path $root $_ }
& "$Jdk\javac.exe" -proc:none -encoding UTF-8 -cp $classpath -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'Mechanics verification tool compilation failed.' }

foreach ($program in @('VerifyBarrelHeat', 'VerifyBulletBlockPenetration', 'VerifyMechanicsSettings')) {
    $log = Join-Path $out ($program + '.log')
    $java = @('-Djava.awt.headless=true', '-cp', ($out + ';' + $classpath), $program, $jarFile.FullName)
    & "$Jdk\java.exe" @java *> $log
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $log -Tail 40
        throw ($program + ' failed.')
    }
    Write-Output ('PASS ' + ((Get-Content -LiteralPath $log -Tail 1) -join ''))
}

$finalHash = (Get-FileHash -LiteralPath $jarFile.FullName -Algorithm SHA256).Hash
if ($finalHash -ne $artifactHash) { throw 'Final JAR changed during verification; rerun against one immutable build.' }
Write-Output ("Verified mechanics on {0}; SHA256={1}" -f $jarFile.FullName, $finalHash)
