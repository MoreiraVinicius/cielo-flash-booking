$ErrorActionPreference = 'Stop'

$resultDirectory = Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

docker compose --profile concurrency up --build --detach --scale command-api-concurrency=2
try {
    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 2
        try { $health = Invoke-RestMethod -Uri 'http://localhost:8081/actuator/health' } catch { $health = $null }
    } while (($null -eq $health -or $health.status -ne 'UP') -and (Get-Date) -lt $deadline)
    if ($null -eq $health -or $health.status -ne 'UP') { throw 'query-api did not become healthy' }

    foreach ($scenario in 'query', 'command', 'mixed') {
        & k6 run --summary-export (Join-Path $resultDirectory "$scenario-summary.json") (Join-Path $PSScriptRoot "$scenario.js")
        if ($LASTEXITCODE -ne 0) { throw "k6 scenario failed: $scenario" }
    }

    $cacheInfo = docker compose exec -T valkey valkey-cli INFO stats
    $databaseSnapshot = docker compose exec -T postgres psql -U flash_booking -d flash_booking -Atc "select json_build_object('connections', count(*), 'waitingLocks', count(*) filter (where wait_event_type = 'Lock')) from pg_stat_activity where datname = current_database();"
    $cacheInfo | Set-Content -LiteralPath (Join-Path $resultDirectory 'valkey-info.txt')
    $databaseSnapshot | Set-Content -LiteralPath (Join-Path $resultDirectory 'postgres-snapshot.json')
    Write-Output "Performance evidence written to $resultDirectory"
} finally {
    docker compose --profile concurrency down
}
