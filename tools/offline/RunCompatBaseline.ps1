# Source folders are read-only. Default invocation writes a plan; launch is explicit.
param(
    [string[]]$Configs = @('none', 'all', 'no-iris', 'no-sodium', 'no-modernfix',
                          'no-flerovium', 'no-ferritecore', 'no-lithium', 'no-sclp'),
    [switch]$Prepare,
    [switch]$Run,
    [string]$World = '新的世界',
    [int]$TimeoutSeconds = 480,
    [string]$Jdk = 'C:/Users/Administrator/.jdks/jdk-21.0.12.1+1',
    [string]$GradleUserHome = 'C:/Users/Administrator/.gradle',
    [string]$Python = 'C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$arguments = @((Join-Path $PSScriptRoot 'compat_matrix.py'), '--root', $root,
    '--configs', ($Configs -join ','), '--world', $World, '--timeout', $TimeoutSeconds,
    '--jdk', $Jdk, '--gradle-user-home', $GradleUserHome)
if ($Prepare) { $arguments += '--prepare' }
if ($Run) { $arguments += '--run' }
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw 'Compatibility matrix preparation or collection failed.' }
