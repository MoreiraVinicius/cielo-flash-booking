param(
    [switch]$PublishBaseline
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Wait-ForHealth {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [Parameter(Mandatory = $true)]
        [datetime]$Deadline
    )

    do {
        try {
            $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') {
                return
            }
        } catch {
            # The service may still be starting. Retry until the shared deadline.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $Deadline)

    throw "service did not become healthy: $BaseUrl"
}

function Get-CommandApiUrls {
    $containerIds = @(docker compose --profile concurrency ps --quiet command-api-concurrency | Where-Object { $_ })
    if ($LASTEXITCODE -ne 0) {
        throw 'could not list command-api-concurrency containers'
    }
    if ($containerIds.Count -lt 2) {
        throw "expected at least two command-api-concurrency containers, found $($containerIds.Count)"
    }

    $urls = foreach ($containerId in $containerIds) {
        $hostPort = docker inspect --format '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' $containerId
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($hostPort)) {
            throw "container $containerId does not publish its HTTP port"
        }
        "http://127.0.0.1:$($hostPort.Trim())"
    }

    return @($urls | Sort-Object -Unique)
}

function Read-K6Scenario {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Label,
        [Parameter(Mandatory = $true)]
        [int]$Vus,
        [Parameter(Mandatory = $true)]
        [int]$DurationSeconds,
        [Parameter(Mandatory = $true)]
        [string]$SummaryPath,
        [Parameter(Mandatory = $true)]
        [string]$Workload,
        [bool]$AggregateLatency = $false
    )

    $summary = Get-Content -LiteralPath $SummaryPath -Raw | ConvertFrom-Json
    $duration = $summary.metrics.http_req_duration
    $requests = $summary.metrics.http_reqs
    $failures = $summary.metrics.http_req_failed
    $checks = $summary.metrics.checks

    [ordered]@{
        name = $Name
        label = $Label
        workload = $Workload
        vus = $Vus
        durationSeconds = $DurationSeconds
        requests = [int]$requests.count
        requestsPerSecond = [math]::Round([double]$requests.rate, 2)
        latencyMs = [ordered]@{
            p50 = [math]::Round([double]$duration.med, 2)
            p95 = [math]::Round([double]$duration.'p(95)', 2)
            p99 = [math]::Round([double]$duration.'p(99)', 2)
        }
        httpFailureRate = [math]::Round([double]$failures.value, 6)
        checksPassed = [int]$checks.passes
        checksFailed = [int]$checks.fails
        aggregateLatency = $AggregateLatency
    }
}

function Read-StatValue {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Lines,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $line = $Lines | Where-Object { $_ -match "^${Name}:([0-9]+)" } | Select-Object -First 1
    if ($null -eq $line) {
        throw "Valkey statistic not found: $Name"
    }
    return [long]([regex]::Match($line, '^.+:([0-9]+)').Groups[1].Value)
}

foreach ($requiredCommand in 'docker', 'k6', 'git') {
    if ($null -eq (Get-Command $requiredCommand -ErrorAction SilentlyContinue)) {
        throw "required command is unavailable: $requiredCommand"
    }
}

$resultDirectory = Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

$sourceCommit = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) {
    throw 'could not read the source commit'
}
$worktreeCleanAtStart = @(git status --porcelain).Count -eq 0
if ($PublishBaseline -and -not $worktreeCleanAtStart) {
    throw 'refusing to publish a canonical baseline from a dirty worktree'
}

$capturedAt = [datetime]::UtcNow.ToString('o')
$previousCommandBaseUrls = $env:COMMAND_BASE_URLS
$previousQueryBaseUrl = $env:QUERY_BASE_URL
$stackStarted = $false

docker compose --profile concurrency up --build --detach --scale command-api-concurrency=2
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose up failed'
}
$stackStarted = $true

try {
    $deadline = (Get-Date).AddMinutes(3)
    Wait-ForHealth -BaseUrl 'http://127.0.0.1:8081' -Deadline $deadline

    $commandBaseUrls = @(Get-CommandApiUrls)
    if ($commandBaseUrls.Count -lt 2) {
        throw 'the discovered command-api URL list contains fewer than two unique processes'
    }
    foreach ($commandBaseUrl in $commandBaseUrls) {
        Wait-ForHealth -BaseUrl $commandBaseUrl -Deadline $deadline
    }

    $env:QUERY_BASE_URL = 'http://127.0.0.1:8081'
    $env:COMMAND_BASE_URLS = $commandBaseUrls -join ','
    Write-Output "Command traffic will use $($commandBaseUrls.Count) explicit processes."

    docker compose exec -T valkey valkey-cli CONFIG RESETSTAT | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'could not reset Valkey counters before the benchmark'
    }

    foreach ($scenario in 'query', 'command', 'mixed') {
        $summaryPath = Join-Path $resultDirectory "$scenario-summary.json"
        & k6 run --summary-export $summaryPath (Join-Path $PSScriptRoot "$scenario.js")
        if ($LASTEXITCODE -ne 0) {
            throw "k6 scenario failed: $scenario"
        }
    }

    $cacheInfo = @(docker compose exec -T valkey valkey-cli INFO stats)
    if ($LASTEXITCODE -ne 0) {
        throw 'could not capture Valkey statistics'
    }
    $databaseSnapshotJson = docker compose exec -T postgres psql -U flash_booking -d flash_booking -Atc "select json_build_object('connections', count(*), 'waitingLocks', count(*) filter (where wait_event_type = 'Lock')) from pg_stat_activity where datname = current_database();"
    if ($LASTEXITCODE -ne 0) {
        throw 'could not capture PostgreSQL statistics'
    }

    $cacheInfo | Set-Content -LiteralPath (Join-Path $resultDirectory 'valkey-info.txt') -Encoding utf8
    $databaseSnapshotJson | Set-Content -LiteralPath (Join-Path $resultDirectory 'postgres-snapshot.json') -Encoding utf8

    $cacheHits = Read-StatValue -Lines $cacheInfo -Name 'keyspace_hits'
    $cacheMisses = Read-StatValue -Lines $cacheInfo -Name 'keyspace_misses'
    $cacheLookups = $cacheHits + $cacheMisses
    $cacheHitRate = 0
    if ($cacheLookups -gt 0) {
        $cacheHitRate = [math]::Round(100 * $cacheHits / $cacheLookups, 2)
    }

    $databaseSnapshot = $databaseSnapshotJson | ConvertFrom-Json
    $computerSystem = $null
    try {
        $computerSystem = Get-CimInstance -ClassName Win32_ComputerSystem -ErrorAction Stop
    } catch {
        # Hardware metadata is helpful but not required for a successful workload.
    }
    $memoryGb = $null
    if ($null -ne $computerSystem) {
        $memoryGb = [math]::Round([double]$computerSystem.TotalPhysicalMemory / 1GB, 1)
    }

    $baseline = [ordered]@{
        schemaVersion = 1
        capturedAt = $capturedAt
        sourceCommit = $sourceCommit
        worktreeCleanAtStart = $worktreeCleanAtStart
        environment = [ordered]@{
            scope = 'local Docker Desktop on Windows'
            os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
            logicalProcessors = [Environment]::ProcessorCount
            physicalMemoryGb = $memoryGb
            docker = (docker version --format 'client={{.Client.Version}}; server={{.Server.Version}}').Trim()
            dockerCompose = (docker compose version --short).Trim()
            k6 = ((k6 version | Select-Object -First 1).ToString()).Trim()
            applicationImage = (docker image inspect flash-booking:local --format '{{.Id}}').Trim()
            commandApiProcesses = $commandBaseUrls.Count
        }
        scenarios = @(
            Read-K6Scenario -Name 'query' -Label 'Consultas' -Vus 5 -DurationSeconds 15 -SummaryPath (Join-Path $resultDirectory 'query-summary.json') -Workload 'GET de disponibilidade'
            Read-K6Scenario -Name 'reservation' -Label 'Reservas' -Vus 3 -DurationSeconds 15 -SummaryPath (Join-Path $resultDirectory 'command-summary.json') -Workload 'POST de uma reserva por iteração em dois processos de comando'
            Read-K6Scenario -Name 'mixed' -Label 'Misto (agregado)' -Vus 4 -DurationSeconds 15 -SummaryPath (Join-Path $resultDirectory 'mixed-summary.json') -Workload 'um GET por iteração e um POST a cada três iterações' -AggregateLatency $true
        )
        cache = [ordered]@{
            scope = 'counters reset before all three scenarios and captured after the suite'
            hits = $cacheHits
            misses = $cacheMisses
            hitRatePercent = $cacheHitRate
        }
        database = [ordered]@{
            scope = 'final snapshot after all three scenarios; not a time series'
            connectionsAtEnd = [int]$databaseSnapshot.connections
            waitingLocksAtEnd = [int]$databaseSnapshot.waitingLocks
        }
        limitations = @(
            'Local 15-second constant-VU snapshots are not production SLOs or a sustainable capacity envelope.'
            'The mixed latency percentiles aggregate read and write requests.'
            'The PostgreSQL lock value is a final snapshot and does not prove zero waits during the run.'
            'Oversell safety is established by integration and concurrency assertions, not by this throughput chart.'
            'The high-load AWS target was not provisioned or benchmarked.'
        )
    }

    $baselineJson = $baseline | ConvertTo-Json -Depth 8
    $resultBaselinePath = Join-Path $resultDirectory 'baseline.json'
    $baselineJson | Set-Content -LiteralPath $resultBaselinePath -Encoding utf8

    if ($PublishBaseline) {
        $baselinePath = Join-Path $PSScriptRoot 'baseline.json'
        $baselineJson | Set-Content -LiteralPath $baselinePath -Encoding utf8
        Write-Output "Canonical baseline published to $baselinePath"
    } else {
        Write-Output "Run evidence written to $resultBaselinePath"
    }
} finally {
    $env:COMMAND_BASE_URLS = $previousCommandBaseUrls
    $env:QUERY_BASE_URL = $previousQueryBaseUrl
    if ($stackStarted) {
        docker compose --profile concurrency down
    }
}
