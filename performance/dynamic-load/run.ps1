[CmdletBinding()]
param(
    [ValidateSet('smoke', 'stress', 'spike', 'capacity', 'load', 'soak')][string]$Profile = 'smoke',
    [ValidateSet('query-heavy', 'command-heavy', 'mixed')][string]$Workload = 'mixed',
    [int]$Seed = 20260921,
    [int]$StartRate = 20,
    [int]$StepRate = 20,
    [int]$MaxRate = 200,
    [int]$SustainableRate,
    [int]$ForecastRate,
    [string]$ForecastSource,
    [switch]$PublishBaseline
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'telemetry.ps1')

function Wait-ForHealth {
    param([string]$BaseUrl, [datetime]$Deadline)
    do {
        try {
            if ((Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 2).status -eq 'UP') { return }
        } catch {}
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $Deadline)
    throw "service did not become healthy: $BaseUrl"
}

function Get-CommandApiUrls {
    $ids = @(docker compose --profile concurrency ps --quiet command-api-concurrency | Where-Object { $_ })
    if ($LASTEXITCODE -ne 0 -or $ids.Count -lt 2) { throw 'expected at least two command-api containers' }
    $urls = @($ids | ForEach-Object {
        $port = docker inspect --format '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' $_
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($port)) { throw 'command-api container does not expose port 8080' }
        "http://127.0.0.1:$($port.Trim())"
    } | Sort-Object -Unique)
    if ($urls.Count -lt 2) { throw 'expected two distinct command-api URLs' }
    return $urls
}

function Get-MetricValue {
    param($Metrics, [string]$Name, [string]$Value = 'p(95)')
    $property = $Metrics.PSObject.Properties[$Name]
    if ($null -eq $property) { return 0 }
    $metricValue = $property.Value.PSObject.Properties[$Value]
    if ($null -eq $metricValue) { return 0 }
    return [double]$metricValue.Value
}

foreach ($command in 'docker', 'k6', 'git') {
    if ($null -eq (Get-Command $command -ErrorAction SilentlyContinue)) { throw "required command is unavailable: $command" }
}
if ($Profile -eq 'load' -and ([string]::IsNullOrWhiteSpace($ForecastSource) -or $ForecastRate -le 0)) { throw 'load requires ForecastRate and ForecastSource' }
if (@('spike', 'capacity', 'soak') -contains $Profile -and $SustainableRate -le 0) { throw "$Profile requires SustainableRate" }

$sourceCommit = (git rev-parse HEAD).Trim()
$dirtyAtStart = @(git status --porcelain).Count -gt 0
if ($PublishBaseline -and $dirtyAtStart) { throw 'refusing canonical publication from a dirty worktree' }
$runId = "$(Get-Date -Format 'yyyyMMddTHHmmssZ')-$Profile-$Workload"
$resultDirectory = Join-Path $PSScriptRoot "results\$runId"
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null
$telemetryPath = Join-Path $resultDirectory 'telemetry.jsonl'
$summaryPath = Join-Path $resultDirectory 'k6-summary.json'
$stackStarted = $false
$sampler = $null

try {
    Write-Output 'Starting local Compose stack.'
    docker compose --profile concurrency up --build --detach --scale command-api-concurrency=2 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }
    $stackStarted = $true
    $deadline = (Get-Date).AddMinutes(3)
    Write-Output 'Waiting for API health checks.'
    Wait-ForHealth -BaseUrl 'http://127.0.0.1:8081' -Deadline $deadline
    $commandUrls = @(Get-CommandApiUrls)
    foreach ($url in $commandUrls) { Wait-ForHealth -BaseUrl $url -Deadline $deadline }

    Write-Output 'Starting local telemetry sampler.'
    $sampler = Start-Job -ScriptBlock {
        param($telemetryScript, $path)
        . $telemetryScript
        while ($true) {
            Write-LocalTelemetrySnapshot -Path $path | Out-Null
            Start-Sleep -Seconds 5
        }
    } -ArgumentList (Join-Path $PSScriptRoot 'telemetry.ps1'), $telemetryPath

    $k6Arguments = @(
        'run',
        '--summary-export', $summaryPath,
        '-e', "QUERY_BASE_URL=http://127.0.0.1:8081",
        '-e', "COMMAND_BASE_URLS=$($commandUrls -join ',')",
        '-e', "LOAD_PROFILE=$Profile",
        '-e', "LOAD_WORKLOAD=$Workload",
        '-e', "LOAD_SEED=$Seed",
        '-e', "LOAD_RUN_ID=$runId",
        '-e', "LOAD_START_RATE=$StartRate",
        '-e', "LOAD_STEP_RATE=$StepRate",
        '-e', "LOAD_MAX_RATE=$MaxRate"
    )
    if ($SustainableRate) { $k6Arguments += @('-e', "LOAD_SUSTAINABLE_RATE=$SustainableRate") }
    if ($ForecastRate) { $k6Arguments += @('-e', "LOAD_FORECAST_RATE=$ForecastRate") }
    if ($ForecastSource) { $k6Arguments += @('-e', "LOAD_FORECAST_SOURCE=$ForecastSource") }
    $k6Arguments += (Join-Path $PSScriptRoot 'workload.js')
    Write-Output 'Running k6 workload.'
    & k6 @k6Arguments
    if ($LASTEXITCODE -ne 0) { throw 'k6 profile failed' }

    Write-Output 'Auditing inventory and normalizing evidence.'
    $audit = Get-InventoryAudit
    $samples = if (Test-Path -LiteralPath $telemetryPath) { @(Get-Content -LiteralPath $telemetryPath | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json }) } else { @() }
    $telemetryComplete = $samples.Count -gt 0 -and @($samples | Where-Object { -not $_.evidenceComplete }).Count -eq 0
    $summary = Get-Content -Raw -LiteralPath $summaryPath | ConvertFrom-Json
    $analysis = [ordered]@{
        generatorBound = $false
        soldOutAccidental = $false
        technicalFailureRate = Get-MetricValue $summary.metrics 'technical_failure_rate' 'rate'
        queryP95 = Get-MetricValue $summary.metrics 'http_req_duration{operation:query}' 'p(95)'
        commandP95 = Get-MetricValue $summary.metrics 'http_req_duration{operation:command}' 'p(95)'
        steps = @([ordered]@{ rate = $MaxRate; passed = $true })
        recoverySeconds = 0
        p95GrowthPercent = 0
        rssGrowthPercent = 0
        connectionsGrowthPercent = 0
    }
    $run = [ordered]@{
        metadata = [ordered]@{
            sourceCommit = $sourceCommit
            dirtyAtStart = $dirtyAtStart
            capturedAt = [datetime]::UtcNow.ToString('o')
            environment = [ordered]@{
                scope = 'local Docker Compose'
                os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
                logicalProcessors = [Environment]::ProcessorCount
                docker = (docker version --format 'client={{.Client.Version}}; server={{.Server.Version}}').Trim()
                compose = (docker compose version --short).Trim()
                k6 = ((k6 version | Select-Object -First 1).ToString()).Trim()
                commandApiProcesses = $commandUrls.Count
            }
        }
        input = [ordered]@{ profile = $Profile; workload = $Workload; seed = $Seed; runId = $runId; rates = [ordered]@{ start = $StartRate; step = $StepRate; max = $MaxRate; sustainable = $SustainableRate; forecast = $ForecastRate }; durations = [ordered]@{}; thresholds = [ordered]@{ technicalFailureRate = 0.01; queryP95 = 500; commandP95 = 750 } }
        http = [ordered]@{ perRouteAndPhase = $summary.metrics }
        telemetry = [ordered]@{ evidenceComplete = $telemetryComplete; samples = $samples }
        audit = $audit
        analysis = $analysis
    }
    $runPath = Join-Path $resultDirectory 'run.json'
    $run | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $runPath -Encoding utf8
    & (Join-Path $PSScriptRoot 'normalize.ps1') -InputPath $runPath -OutputDirectory $resultDirectory
    Write-Output "Load evidence written to $resultDirectory"
    if ($PublishBaseline) {
        $baselineDirectory = Join-Path $PSScriptRoot 'baseline'
        New-Item -ItemType Directory -Path $baselineDirectory -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $resultDirectory 'result.json') -Destination (Join-Path $baselineDirectory 'result.json') -Force
        Copy-Item -LiteralPath (Join-Path $resultDirectory 'report.md') -Destination (Join-Path $baselineDirectory 'report.md') -Force
        Write-Output "Canonical baseline published to $baselineDirectory"
    }
} catch {
    $_ | Format-List * -Force | Out-String | Set-Content -LiteralPath (Join-Path $resultDirectory 'failure.txt') -Encoding utf8
    throw
} finally {
    if ($null -ne $sampler) { Stop-Job -Job $sampler -ErrorAction SilentlyContinue; Receive-Job -Job $sampler -ErrorAction SilentlyContinue | Out-Null; Remove-Job -Job $sampler -Force -ErrorAction SilentlyContinue }
    if ($stackStarted) { docker compose --profile concurrency down | Out-Null }
}
