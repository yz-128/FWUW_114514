param(
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$GradleUserHome = 'C:/Users/Administrator/.gradle'
)
# Actual final-JAR target clear/copy in an invisible GLFW context. No Minecraft client/world.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location -LiteralPath $root
try {
    $version = (Get-Content -LiteralPath 'gradle.properties' | Where-Object { $_ -match '^mod_version=' }).Substring('mod_version='.Length).Trim()
    $jar = Join-Path $root "build/libs/flansmod-$version.jar"
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    $output = Join-Path $root "reports/eft/compat-$version/scope-state"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $classes = Join-Path $root ('build/verification-scope-state-tools-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $classes | Out-Null
    $entries = [IO.File]::ReadAllText('build/offline-classpath.txt').Trim() -split ';' | Where-Object {
        $_ -and $_.Replace('\','/') -notmatch '/build/(classes|resources)(/|$)'
    }
    $entries += Get-ChildItem -LiteralPath (Join-Path $GradleUserHome 'caches/modules-2/files-2.1/org.lwjgl') -Recurse -File -Filter '*3.3.3-natives-windows.jar' | ForEach-Object { $_.FullName }
    $cp = (@($jar) + @($entries) | Select-Object -Unique) -join ';'
    & "$Jdk/bin/javac.exe" -proc:none -implicit:none -encoding UTF-8 -cp $cp -d $classes `
        'tools/offline/stubs/com/flansmod/common/FlansMod.java' `
        'tools/offline/data-stubs/net/neoforged/neoforge/common/util/flag/FeatureFlagLoader.java' `
        'tools/offline/VerifyScopeTargetState.java' 'tools/offline/VerifyScopeFramebuffer.java'
    if ($LASTEXITCODE -ne 0) { throw 'Scope state tool compilation failed.' }
    $arguments = @('-Djava.awt.headless=true','-Djoml.useUnsafe=false','-Dlog4j.configurationFile=tools/offline/log4j2.xml','-cp',($classes+';'+$cp))
    foreach ($name in @('VerifyScopeTargetState','VerifyScopeFramebuffer')) {
        $log = Join-Path $output "$name.final.log"
        & "$Jdk/bin/java.exe" @arguments $name *> $log
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 25; throw "$name failed." }
        Get-Content -LiteralPath $log -Tail 1
    }
    $control = Join-Path $output 'VerifyScopeTargetState.negative-control.log'
    & "$Jdk/bin/java.exe" @arguments VerifyScopeTargetState --legacy-control *> $control
    if ($LASTEXITCODE -eq 0 -or -not (Select-String -LiteralPath $control -SimpleMatch '148 failures')) {
        throw 'Negative control failed to detect the original partial-clear/state-leak conditions.'
    }
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $hash) { throw 'Artifact changed during verification.' }
    [IO.File]::WriteAllText((Join-Path $output 'artifact-sha256.txt'),$hash,[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $output 'classpath.txt'),($classes+';'+$cp),[Text.UTF8Encoding]::new($false))
    Write-Output "PASS negative control: reconstructed original clear sequence has expected 148 failures. Final JAR $hash"
} finally { Pop-Location }
