$ErrorActionPreference = 'Stop'

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $parameters = @{ Method = $Method; Uri = $Uri; Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Compress
    }
    Invoke-RestMethod @parameters
}

$event = Invoke-JsonRequest -Method POST -Uri 'http://localhost:8082/events' -Headers @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() } -Body @{ name = 'Compose smoke event'; capacity = 2 }
$eventId = $event.id
$queriedEvent = Invoke-JsonRequest -Method GET -Uri "http://localhost:8081/events/$eventId"
if ($queriedEvent.id -ne $eventId) { throw 'query-api did not expose the created event' }

$reservation = Invoke-JsonRequest -Method POST -Uri "http://localhost:8082/events/$eventId/reservations" -Headers @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() } -Body @{ quantity = 1; customer = @{ name = 'Compose smoke'; email = 'compose-smoke@example.com' } }
$reservationId = $reservation.id
$queriedReservation = Invoke-JsonRequest -Method GET -Uri "http://localhost:8081/reservations/$reservationId"
if ($queriedReservation.id -ne $reservationId) { throw 'query-api did not expose the created reservation' }

$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Seconds 1
    $messages = Invoke-RestMethod -Uri 'http://localhost:8025/api/v1/messages'
} while ($messages.messages.Count -lt 1 -and (Get-Date) -lt $deadline)

if ($messages.messages.Count -lt 1) { throw 'worker did not deliver the reservation email to Mailpit' }
Write-Output "Compose smoke passed for reservation $reservationId"
