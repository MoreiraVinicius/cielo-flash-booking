Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-DockerTelemetry {
    $rows = @(docker stats --no-stream --format '{{json .}}' 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'docker stats failed'
    }
    return @($rows | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json })
}

function Get-PostgresTelemetry {
    $sql = "select json_build_object('connections', count(*), 'waitingLocks', count(*) filter (where wait_event_type = 'Lock')) from pg_stat_activity where datname = current_database();"
    $json = docker compose exec -T postgres psql -U flash_booking -d flash_booking -Atc $sql 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'postgres telemetry query failed'
    }
    return ($json | ConvertFrom-Json)
}

function Get-ValkeyTelemetry {
    $lines = @(docker compose exec -T valkey valkey-cli INFO stats 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'valkey telemetry query failed'
    }
    $values = @{}
    foreach ($line in $lines) {
        if ($line -match '^([^:]+):([0-9]+)') {
            $values[$matches[1]] = [long]$matches[2]
        }
    }
    if (!$values.ContainsKey('keyspace_hits') -or !$values.ContainsKey('keyspace_misses')) {
        throw 'valkey telemetry is missing hit or miss counters'
    }
    return [ordered]@{ hits = $values['keyspace_hits']; misses = $values['keyspace_misses'] }
}

function Get-InventoryAudit {
    $sql = @'
WITH pending AS (
    SELECT event_id, COALESCE(SUM(quantity), 0) AS quantity
    FROM reservation
    WHERE status = 'PENDING'
    GROUP BY event_id
)
SELECT COALESCE(json_agg(row_to_json(result)), '[]'::json)
FROM (
    SELECT
        event.id AS "eventId",
        event.available >= 0 AS "availableNonNegative",
        NOT EXISTS (SELECT 1 FROM reservation WHERE quantity <= 0) AS "quantitiesPositive",
        event.capacity - event.available = COALESCE(pending.quantity, 0) AS "inventoryBalanced"
    FROM event
    LEFT JOIN pending ON pending.event_id = event.id
    ORDER BY event.id
) result;
'@
    $json = docker compose exec -T postgres psql -U flash_booking -d flash_booking -Atc $sql 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'inventory audit query failed'
    }
    $events = @($json | ConvertFrom-Json)
    return [ordered]@{
        events = $events
        availableNonNegative = @($events | Where-Object { -not $_.availableNonNegative }).Count -eq 0
        quantitiesPositive = @($events | Where-Object { -not $_.quantitiesPositive }).Count -eq 0
        inventoryBalanced = @($events | Where-Object { -not $_.inventoryBalanced }).Count -eq 0
    }
}

function Get-LocalTelemetrySnapshot {
    $snapshot = [ordered]@{
        capturedAt = [datetime]::UtcNow.ToString('o')
        evidenceComplete = $true
        containers = @()
        postgres = $null
        valkey = $null
        errors = @()
    }
    try { $snapshot.containers = @(Get-DockerTelemetry) } catch { $snapshot.evidenceComplete = $false; $snapshot.errors += $_.Exception.Message }
    try { $snapshot.postgres = Get-PostgresTelemetry } catch { $snapshot.evidenceComplete = $false; $snapshot.errors += $_.Exception.Message }
    try { $snapshot.valkey = Get-ValkeyTelemetry } catch { $snapshot.evidenceComplete = $false; $snapshot.errors += $_.Exception.Message }
    return $snapshot
}

function Write-LocalTelemetrySnapshot {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )
    $snapshot = Get-LocalTelemetrySnapshot
    $snapshot | ConvertTo-Json -Depth 8 -Compress | Add-Content -LiteralPath $Path -Encoding utf8
    return $snapshot
}
