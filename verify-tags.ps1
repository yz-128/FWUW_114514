# Compare merged singular tag files against the original dev-compiled plural ones.
$ErrorActionPreference = 'Stop'
$newRoot = Join-Path $PSScriptRoot 'src\main\resources'
$oldRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'FlansModReloaded-1.20\src\main\resources'

$renames = @{ 'blocks'='block'; 'items'='item'; 'entity_types'='entity_type'; 'fluids'='fluid'; 'game_events'='game_event' }
$nsRename = @{ 'forge'='c' }

$oldFiles = Get-ChildItem -Path (Join-Path $oldRoot 'data') -Recurse -File -Filter '*.json' |
    Where-Object { $_.FullName -match '\\tags\\' -and $_.FullName -notmatch '\\partial_data\\' }

Write-Host "Original dev-compiled tag files: $($oldFiles.Count)"

$mismatch = 0
$missing = 0
$ok = 0

foreach ($of in $oldFiles) {
    $rel = $of.FullName.Substring((Join-Path $oldRoot 'data').Length + 1) -replace '\\','/'
    # data/<ns>/tags/<dir>/<rest>
    $p = $rel -split '/'
    $ns = $p[0]
    if ($nsRename.ContainsKey($ns)) { $ns = $nsRename[$ns] }
    $dir = $p[2]
    if ($renames.ContainsKey($dir)) { $dir = $renames[$dir] }
    $rest = ($p[3..($p.Length-1)]) -join '/'
    $targetRel = "$ns/tags/$dir/$rest"
    $newPath = Join-Path $newRoot ("data/" + $targetRel -replace '/','\')

    if (-not (Test-Path $newPath)) {
        Write-Host "MISSING: $targetRel"
        $missing++
        continue
    }

    $oldJson = Get-Content -Raw -Path $of.FullName | ConvertFrom-Json
    $newJson = Get-Content -Raw -Path $newPath | ConvertFrom-Json
    $oldVals = @($oldJson.values | ForEach-Object { "$_" } | Sort-Object)
    $newVals = @($newJson.values | ForEach-Object { "$_" } | Sort-Object)

    $diff = Compare-Object $oldVals $newVals
    if ($diff) {
        Write-Host "DIFF: $targetRel"
        $diff | ForEach-Object { Write-Host "    $($_.SideIndicator) $($_.InputObject)" }
        $mismatch++
    } else {
        $ok++
    }
}

Write-Host ""
Write-Host "=== Result: ok=$ok mismatch=$mismatch missing=$missing ==="
