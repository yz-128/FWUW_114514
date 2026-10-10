# Flan's Mod 1.21.1 resource migration helper (run with: powershell -File migrate-resources.ps1)
$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot 'src\main\resources'
$dataDir = Join-Path $root 'data'

# 1.21 renamed tag directories to singular
$dirRenames = @{
    'blocks'        = 'block'
    'items'         = 'item'
    'entity_types'  = 'entity_type'
    'fluids'        = 'fluid'
    'game_events'   = 'game_event'
}

Write-Host "=== Collecting partial_data tag files ==="
$tagFiles = Get-ChildItem -Path $dataDir -Recurse -File -Filter '*.json' |
    Where-Object { $_.FullName -match '\\partial_data\\' }

Write-Host "Found $($tagFiles.Count) partial_data json files"

# target path -> list of value strings
$merged = @{}
$order = New-Object System.Collections.ArrayList

foreach ($f in $tagFiles) {
    $full = $f.FullName
    $marker = '\partial_data\'
    $idx = $full.IndexOf($marker)
    $after = $full.Substring($idx + $marker.Length)
    $parts = $after -split '\\'
    $namespace = $parts[0]
    $rel = ($parts[1..($parts.Length - 1)]) -join '/'
    if (-not $rel.StartsWith('tags/')) { continue }

    # NeoForge common tags moved from the forge namespace to c
    if ($namespace -eq 'forge') { $namespace = 'c' }

    # rename the first path segment after tags/ when it is a pluralised registry dir
    $relParts = $rel -split '/'
    $regDir = $relParts[1]
    if ($dirRenames.ContainsKey($regDir)) { $relParts[1] = $dirRenames[$regDir] }
    $rel = $relParts -join '/'

    $target = "data/$namespace/$rel"

    $json = Get-Content -Raw -Path $full | ConvertFrom-Json
    $vals = @()
    if ($json.values) { $vals = @($json.values) }

    if (-not $merged.ContainsKey($target)) {
        $merged[$target] = New-Object System.Collections.ArrayList
        [void]$order.Add($target)
    }
    foreach ($v in $vals) {
        if (-not $merged[$target].Contains($v)) { [void]$merged[$target].Add($v) }
    }
}

Write-Host "=== Writing $($order.Count) merged tag files ==="
foreach ($target in $order) {
    $outPath = Join-Path $root ($target -replace '/', '\')
    $outDir = Split-Path -Parent $outPath
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
    $obj = [ordered]@{
        replace = $false
        values  = @($merged[$target])
    }
    $jsonText = $obj | ConvertTo-Json -Depth 10
    Set-Content -Path $outPath -Value $jsonText -Encoding UTF8
    Write-Host "  $target ($($merged[$target].Count) values)"
}

Write-Host "=== Removing partial_data directories ==="
Get-ChildItem -Path $dataDir -Recurse -Directory -Filter 'partial_data' |
    ForEach-Object { Remove-Item -Recurse -Force $_.FullName; Write-Host "  removed $($_.FullName)" }

Write-Host "=== Removing dev-workspace compiled tags (superseded by merged partial_data) ==="
foreach ($p in @('data\forge\tags', 'data\minecraft\tags')) {
    $full = Join-Path $root $p
    if (Test-Path $full) { Remove-Item -Recurse -Force $full; Write-Host "  removed $p" }
}

Write-Host "=== Moving loot modifier global list to neoforge namespace ==="
$oldLoot = Join-Path $root 'data\forge\loot_modifiers\global_loot_modifiers.json'
if (Test-Path $oldLoot) {
    $newLootDir = Join-Path $root 'data\neoforge\loot_modifiers'
    New-Item -ItemType Directory -Path $newLootDir -Force | Out-Null
    $loot = Get-Content -Raw -Path $oldLoot | ConvertFrom-Json
    $entries = @()
    foreach ($e in $loot.entries) { $entries += $e.Trim() }
    $obj = [ordered]@{ replace = $false; entries = $entries }
    Set-Content -Path (Join-Path $newLootDir 'global_loot_modifiers.json') -Value ($obj | ConvertTo-Json -Depth 10) -Encoding UTF8
    Write-Host "  wrote data/neoforge/loot_modifiers/global_loot_modifiers.json with $($entries.Count) entries"
}
$forgeDir = Join-Path $root 'data\forge'
if (Test-Path $forgeDir) { Remove-Item -Recurse -Force $forgeDir; Write-Host "  removed data/forge" }

Write-Host "=== Done ==="
