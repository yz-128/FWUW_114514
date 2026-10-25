param(
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$Python = 'C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
)
# Final-JAR instrumentation plus pure fixture tests; never starts a Minecraft client.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $root
try {
    $version = (Get-Content -LiteralPath 'gradle.properties' | Where-Object { $_ -match '^mod_version=' }).Substring('mod_version='.Length).Trim()
    $jar = Join-Path $root "build/libs/flansmod-$version.jar"
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    $output = Join-Path $root "reports/eft/compat-$version/instrumentation"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $classes = Join-Path $root ('build/verification-compat-tools-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $classes | Out-Null
    $entries = [IO.File]::ReadAllText('build/offline-classpath.txt').Trim() -split ';' | Where-Object {
        $_ -and $_.Replace('\','/') -notmatch '/build/(classes|resources)(/|$)'
    }
    $cp = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    & "$Jdk/bin/javac.exe" -proc:none -implicit:none -encoding UTF-8 -cp $cp -d $classes `
        'tools/offline/stubs/com/flansmod/common/FlansMod.java' `
        'tools/offline/VerifyRenderPrep.java' 'tools/offline/VerifyRenderPerfWindows.java'
    if ($LASTEXITCODE -ne 0) { throw 'Compatibility instrumentation compilation failed.' }
    $arguments = @('-Djava.awt.headless=true', '-Dlog4j.configurationFile=tools/offline/log4j2.xml', '-cp', ($classes + ';' + $cp))
    & "$Jdk/bin/java.exe" @arguments VerifyRenderPrep $jar *> (Join-Path $output 'VerifyRenderPrep.final.log')
    if ($LASTEXITCODE -ne 0) { throw 'Disabled instrumentation/final JAR wiring failed.' }
    & "$Jdk/bin/java.exe" '-Dflansmod.renderPerf=true' @arguments VerifyRenderPerfWindows *> (Join-Path $output 'VerifyRenderPerfWindows.final.log')
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $output 'VerifyRenderPerfWindows.final.log') -Tail 20; throw 'Scene/window accounting failed.' }
    & $Python tools/offline/test_compat_matrix.py *> (Join-Path $output 'test_compat_matrix.final.log')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated matrix fixtures failed.' }
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $hash) { throw 'Artifact changed during verification.' }
    [IO.File]::WriteAllText((Join-Path $output 'artifact-sha256.txt'), $hash, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $output 'classpath.txt'), ($classes + ';' + $cp), [Text.UTF8Encoding]::new($false))
    Get-Content -LiteralPath (Join-Path $output 'VerifyRenderPrep.final.log') -Tail 1
    Get-Content -LiteralPath (Join-Path $output 'VerifyRenderPerfWindows.final.log') -Tail 1
    Get-Content -LiteralPath (Join-Path $output 'test_compat_matrix.final.log') -Tail 4
} finally { Pop-Location }
