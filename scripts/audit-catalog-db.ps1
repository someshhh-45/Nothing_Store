# Read-only audit of the live storefront API against the Docker PostgreSQL products table.
# Run from the repository root: powershell -ExecutionPolicy Bypass -File scripts/audit-catalog-db.ps1
param([string]$BaseUrl = "http://localhost:8000")
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot
$compose = @("-f", "backend/compose.yml", "--env-file", "backend/.env")

function Field($obj, [string[]]$names) {
    if ($null -eq $obj) { return $null }
    $props = @($obj.PSObject.Properties)
    foreach ($name in $names) {
        $match = $props | Where-Object { ($_.Name -replace '[^a-zA-Z0-9]', '').ToLowerInvariant() -eq ($name -replace '[^a-zA-Z0-9]', '').ToLowerInvariant() } | Select-Object -First 1
        if ($match) { return $match.Value }
    }
    return $null
}
if (-not (Test-Path "backend/.env")) { throw "backend/.env missing. Configure the local stack first." }

Write-Host "Checking catalog API: $BaseUrl/api/store/catalog"
# Parse JSON explicitly: Windows PowerShell can wrap an Invoke-RestMethod JSON array
# as one pipeline item, causing a false "missing productId" error.
$endpoint = "$($BaseUrl.TrimEnd('/'))/api/store/catalog"
$response = Invoke-WebRequest -UseBasicParsing -Uri $endpoint -TimeoutSec 30
$parsed = ConvertFrom-Json -InputObject $response.Content
if ($parsed -isnot [array]) {
    $shape = if ($null -eq $parsed) { "null" } else { $parsed.GetType().FullName }
    $keys = if ($null -ne $parsed) { $parsed.PSObject.Properties.Name -join ', ' } else { "" }
    throw "Expected an array from catalog API. Got $shape (fields: $keys)."
}
$api = $parsed
if ($api.Count -eq 0) { throw "Catalog API returned an empty list." }
Write-Host "Parsed $($api.Count) API products."
Write-Host "Reading PostgreSQL public.products without modifying records..."
$sql = "SELECT row_to_json(p)::text FROM public.products p;"
$rows = @(& docker compose @compose exec -T postgres psql -X -A -t -P pager=off -U nothing -d nothing -c $sql)
if ($LASTEXITCODE -ne 0) { throw "Could not query Docker PostgreSQL. Verify the running Compose project." }
$db = @($rows | Where-Object { $_.Trim().StartsWith('{') } | ForEach-Object { $_ | ConvertFrom-Json })
if (-not $db.Count) { throw "No rows found in public.products." }

$dbIdNames = @("productId", "product_id", "id")
$apiIdNames = @("productId", "product_id", "id")
$dbIds = @{}
foreach ($p in $db) {
    $id = Field $p $dbIdNames
    if ($null -eq $id) { throw "Cannot identify product ID column in public.products. Available columns: $($p.PSObject.Properties.Name -join ', ')" }
    $dbIds[[string]$id] = $p
}
$apiIds = @{}
foreach ($p in $api) {
    $id = Field $p $apiIdNames
    if ($null -eq $id) {
        $fields = if ($null -eq $p) { "null" } else { $p.PSObject.Properties.Name -join ", " }
        throw "A catalog record has no product ID (fields: $fields)."
    }
    $apiIds[[string]$id] = $p
}
$apiMissingDb = @($apiIds.Keys | Where-Object { -not $dbIds.ContainsKey($_) } | Sort-Object)
$dbNotCatalog = @($dbIds.Keys | Where-Object { -not $apiIds.ContainsKey($_) } | Sort-Object)
$stockDifferences = @()
$stockCompared = 0
foreach ($id in $apiIds.Keys) {
    if (-not $dbIds.ContainsKey($id)) { continue }
    $a = Field $apiIds[$id] @("stock", "stockQuantity", "quantity")
    $b = Field $dbIds[$id] @("stock", "stockQuantity", "quantity")
    if ($null -ne $a -and $null -ne $b) {
        $stockCompared++
        if ([decimal]$a -ne [decimal]$b) {
            $stockDifferences += [pscustomobject]@{ ProductId = $id; ApiStock = $a; DbStock = $b }
        }
    }
}
Write-Host ""
Write-Host "PostgreSQL product rows: $($db.Count)"
Write-Host "Catalog API items: $($api.Count)"
Write-Host "API IDs not in PostgreSQL: $($apiMissingDb.Count)"
Write-Host "PostgreSQL IDs excluded from API: $($dbNotCatalog.Count)"
Write-Host "Stock values compared: $stockCompared"
Write-Host "Stock mismatches on shared IDs: $($stockDifferences.Count)"
if ($apiMissingDb.Count) { Write-Host "Unexpected API-only IDs:"; $apiMissingDb | ForEach-Object { Write-Host "  $_" } }
if ($dbNotCatalog.Count) {
    Write-Host "Database rows not exposed by catalog (may be intentionally hidden):"
    foreach ($id in $dbNotCatalog) {
        $p = $dbIds[$id]
        $name = Field $p @("productName", "name")
        $category = Field $p @("categoryName", "category")
        Write-Host "  ID=$id Name=$name Category=$category"
    }
}
if ($stockDifferences.Count) { $stockDifferences | Format-Table -AutoSize | Out-Host }
if ($apiMissingDb.Count -gt 0) { throw "FAIL: Some catalog product IDs do not exist in PostgreSQL." }
if ($stockDifferences.Count -gt 0) { throw "FAIL: Some API inventory values disagree with PostgreSQL." }
if ($stockCompared -eq 0) { Write-Warning "Could not identify stock columns for comparison; product ID audit passed, but stock was NOT checked." }
Write-Host "PASS: Every catalog API product has a PostgreSQL row, and any compared stock values match." -ForegroundColor Green
Write-Host "Note: This audits the catalog API, not every DOM element, payment, or order transaction."
