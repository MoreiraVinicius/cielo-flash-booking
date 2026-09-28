$ErrorActionPreference = 'Stop'

$projectName = 'flash-booking-observability-smoke'
$composeArgs = @(
    'compose',
    '-p', $projectName,
    '-f', 'compose.yaml',
    '-f', 'compose.observability.yaml',
    '--profile', 'observability'
)

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker @composeArgs @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

try {
    Invoke-Compose -Arguments @('up', '-d', 'postgres', 'valkey', 'command-api', 'otel-collector')

    $deadline = (Get-Date).AddMinutes(3)
    do {
        try {
            $health = Invoke-RestMethod -Uri 'http://localhost:8082/actuator/health'
        } catch {
            $health = $null
        }

        if ($health.status -eq 'UP') {
            break
        }

        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if ($health.status -ne 'UP') {
        throw 'command-api did not become healthy within 3 minutes'
    }

    $event = Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/events' `
        -ContentType 'application/json' `
        -Headers @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() } `
        -Body (@{ name = 'OTLP smoke event'; capacity = 2 } | ConvertTo-Json -Compress)

    $reservation = Invoke-RestMethod -Method Post -Uri "http://localhost:8082/events/$($event.id)/reservations" `
        -ContentType 'application/json' `
        -Headers @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() } `
        -Body (@{ quantity = 1; customer = @{ name = 'OTLP smoke'; email = 'otel-smoke@example.com' } } | ConvertTo-Json -Compress)

    if ($reservation.status -ne 'PENDING') {
        throw "unexpected reservation status: $($reservation.status)"
    }

    Start-Sleep -Seconds 5
    $collectorLogs = (Invoke-Compose -Arguments @('logs', '--no-color', 'otel-collector') | Out-String)
    if ($collectorLogs -notmatch 'ResourceSpans|Span #') {
        throw 'collector did not report an OTLP trace'
    }
    if ($collectorLogs -notmatch 'ResourceMetrics|http\.server\.requests') {
        throw 'collector did not report OTLP HTTP metrics'
    }

    Write-Output "Observability smoke passed: event $($event.id), reservation $($reservation.id), HTTP trace and metrics received."
} finally {
    Invoke-Compose -Arguments @('down', '--remove-orphans')
}
