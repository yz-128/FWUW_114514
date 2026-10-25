param(
    [ValidateSet('modern','classic')][string]$Variant = 'modern',
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1'
)
# Final-JAR material parsing, hand/fold vertices and actual GUI variant. No game/GL.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $root
try {
    $version = (Get-Content -LiteralPath 'gradle.properties' | Where-Object { $_ -match '^mod_version=' }).Substring('mod_version='.Length).Trim()
    $suffix = if ($Variant -eq 'classic') { '-classic' } else { '' }
    $jar = Join-Path $root "build/libs/flansmod-$version$suffix.jar"
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    $output = Join-Path $root "reports/eft/emission-$version/$Variant"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $classes = Join-Path $root ('build/verification-emission-tools-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $classes | Out-Null
    $entries = [IO.File]::ReadAllText('build/offline-classpath.txt').Trim() -split ';' | Where-Object {
        $_ -and $_.Replace('\','/') -notmatch '/build/(classes|resources)(/|$)'
    }
    $cp = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    & "$Jdk/bin/javac.exe" -proc:none -implicit:none -encoding UTF-8 -cp $cp -d $classes `
        'tools/offline/stubs/com/flansmod/common/FlansMod.java' `
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java' `
        'tools/offline/OfflinePack.java' 'tools/offline/VerifyLegacyEmission.java' 'tools/offline/VerifyGuiLegacyEmission.java'
    if ($LASTEXITCODE -ne 0) { throw 'Emission verification compilation failed.' }
    $arguments = @('-Djava.awt.headless=true','-Djoml.useUnsafe=false','-Dlog4j.configurationFile=tools/offline/log4j2.xml','-cp',($classes+';'+$cp))
    foreach ($name in @('VerifyLegacyEmission','VerifyGuiLegacyEmission')) {
        $log = Join-Path $output "$name.final.log"
        & "$Jdk/bin/java.exe" @arguments $name 'run/flan' *> $log
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 30; throw "$Variant $name failed." }
        Get-Content -LiteralPath $log -Tail 1
    }
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $hash) { throw 'Artifact changed during verification.' }
    [IO.File]::WriteAllText((Join-Path $output 'artifact-sha256.txt'),$hash,[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $output 'classpath.txt'),($classes+';'+$cp),[Text.UTF8Encoding]::new($false))
    Write-Output "Verified $Variant legacy materials; final JAR $hash"
} finally { Pop-Location }
