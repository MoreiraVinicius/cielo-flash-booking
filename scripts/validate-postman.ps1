$ErrorActionPreference = 'Stop'

$collectionPath = 'postman/flash-booking-aws.postman_collection.json'
$localEnvironmentPath = 'postman/flash-booking.local.postman_environment.json'
$awsEnvironmentPath = 'postman/flash-booking.aws.postman_environment.json'

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-EnvironmentValues {
    param([string]$Path)

    $environment = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    $values = @{}
    foreach ($entry in $environment.values) {
        $values[$entry.key] = $entry.value
    }
    return @{ Environment = $environment; Values = $values }
}

function Find-Folder {
    param(
        [object]$Collection,
        [string]$Name
    )

    return @($Collection.item | Where-Object { $_.name -eq $Name })[0]
}

function Get-RequestNames {
    param([object]$Folder)

    return @($Folder.item | ForEach-Object { $_.name })
}

function Find-Request {
    param(
        [object]$Folder,
        [string]$Name
    )

    return @($Folder.item | Where-Object { $_.name -eq $Name })[0]
}

function Get-TestScript {
    param([object]$Request)

    return ((@($Request.event | Where-Object { $_.listen -eq 'test' } | ForEach-Object { $_.script.exec }) -join "`n"))
}

function Assert-RequestTests {
    param(
        [object]$Folder,
        [hashtable]$ExpectedScripts
    )

    foreach ($requestName in $ExpectedScripts.Keys) {
        $request = Find-Request -Folder $Folder -Name $requestName
        Assert-Condition ($null -ne $request) "Idempotency battery request is missing: $requestName"
        $script = Get-TestScript -Request $request
        foreach ($expectedFragment in $ExpectedScripts[$requestName]) {
            Assert-Condition ($script.Contains($expectedFragment)) "Idempotency battery request $requestName is missing assertion: $expectedFragment"
        }
    }
}

$collectionRaw = Get-Content -LiteralPath $collectionPath -Raw
$collection = $collectionRaw | ConvertFrom-Json
$local = Get-EnvironmentValues -Path $localEnvironmentPath
$aws = Get-EnvironmentValues -Path $awsEnvironmentPath

Assert-Condition ($collection.info.schema -eq 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json') 'Collection must use Postman Collection v2.1.'
Assert-Condition ($collection.auth.type -eq 'awsv4') 'Collection must use AWS Signature v4 by default.'
Assert-Condition (($collection.auth.awsv4 | Where-Object { $_.key -eq 'service' }).value -eq 'execute-api') 'AWS Signature v4 must target execute-api.'

$localFolder = Find-Folder -Collection $collection -Name 'Local | fluxo completo do case'
$awsFolder = Find-Folder -Collection $collection -Name 'AWS | fronteira e fluxo do case'
Assert-Condition ($null -ne $localFolder) 'Collection must contain the Local flow folder.'
Assert-Condition ($null -ne $awsFolder) 'Collection must contain the AWS flow folder.'
Assert-Condition ($localFolder.auth.type -eq 'noauth') 'Local flow must override authentication with noauth.'

$expectedLocalRequests = @(
    '01 | Criar evento imediato',
    '02 | Consultar disponibilidade',
    '03 | Criar reserva',
    '04 | Repetir reserva com a mesma chave',
    '05 | Consultar reserva',
    '06 | Rejeitar capacidade insuficiente',
    '07 | Cancelar reserva antes do prazo',
    '08 | Confirmar capacidade devolvida',
    '09 | Rejeitar comando sem Idempotency-Key',
    '10 | Criar venda futura',
    '11 | Rejeitar reserva antes da abertura',
    '12 | Rejeitar mesma chave com payload diferente',
    '13 | Rejeitar mesma chave em outro endpoint',
    '14 | Repetir conflito de capacidade com a mesma chave',
    '15 | Rejeitar Idempotency-Key maior que 128 caracteres'
)

$expectedAwsRequests = @(
    '00 | Rejeitar chamada sem SigV4',
    '01 | Criar evento assinado',
    '02 | Consultar evento assinado',
    '03 | Criar reserva assinada',
    '04 | Repetir reserva assinada',
    '05 | Consultar reserva assinada',
    '06 | Rejeitar capacidade insuficiente assinada',
    '07 | Cancelar reserva assinada',
    '08 | Confirmar capacidade devolvida assinada',
    '09 | Rejeitar comando assinado sem Idempotency-Key',
    '10 | Rejeitar chave AWS com payload diferente',
    '11 | Rejeitar chave AWS em outro endpoint',
    '12 | Repetir conflito de capacidade AWS',
    '13 | Rejeitar Idempotency-Key AWS maior que 128 caracteres'
)

$localNames = Get-RequestNames -Folder $localFolder
$awsNames = Get-RequestNames -Folder $awsFolder
foreach ($requestName in $expectedLocalRequests) {
    Assert-Condition ($localNames -contains $requestName) "Local request is missing: $requestName"
}
foreach ($requestName in $expectedAwsRequests) {
    Assert-Condition ($awsNames -contains $requestName) "AWS request is missing: $requestName"
}
Assert-Condition (($localNames -join "`n") -eq ($expectedLocalRequests -join "`n")) 'Local requests must remain in the documented Runner order.'
Assert-Condition (($awsNames -join "`n") -eq ($expectedAwsRequests -join "`n")) 'AWS requests must remain in the documented Runner order.'

Assert-RequestTests -Folder $localFolder -ExpectedScripts @{
    '04 | Repetir reserva com a mesma chave' = @('pm.response.to.have.status(201)', "pm.response.json().id).to.eql(pm.collectionVariables.get('reservationId'))")
    '09 | Rejeitar comando sem Idempotency-Key' = @('pm.response.to.have.status(400)', "'invalid-request'", 'application/problem+json')
    '12 | Rejeitar mesma chave com payload diferente' = @('pm.response.to.have.status(409)', "'resource-conflict'", 'application/problem+json')
    '13 | Rejeitar mesma chave em outro endpoint' = @('pm.response.to.have.status(409)', "'resource-conflict'", 'application/problem+json')
    '14 | Repetir conflito de capacidade com a mesma chave' = @('pm.response.to.have.status(409)', "'resource-conflict'", 'application/problem+json')
    '15 | Rejeitar Idempotency-Key maior que 128 caracteres' = @('pm.response.to.have.status(400)', "'invalid-request'", 'application/problem+json')
}

Assert-RequestTests -Folder $awsFolder -ExpectedScripts @{
    '04 | Repetir reserva assinada' = @('pm.response.to.have.status(201)', "pm.response.json().id).to.eql(pm.collectionVariables.get('reservationId'))")
    '09 | Rejeitar comando assinado sem Idempotency-Key' = @('pm.response.to.have.status(400)', "'invalid-request'", 'application/problem+json')
    '10 | Rejeitar chave AWS com payload diferente' = @('pm.response.to.have.status(409)', "'resource-conflict'", 'application/problem+json')
    '11 | Rejeitar chave AWS em outro endpoint' = @('pm.response.to.have.status(409)', "'resource-conflict'", 'application/problem+json')
    '12 | Repetir conflito de capacidade AWS' = @('pm.response.to.have.status(409)', "'resource-conflict'", 'application/problem+json')
    '13 | Rejeitar Idempotency-Key AWS maior que 128 caracteres' = @('pm.response.to.have.status(400)', "'invalid-request'", 'application/problem+json')
}

$unsignedBoundary = @($awsFolder.item | Where-Object { $_.name -eq '00 | Rejeitar chamada sem SigV4' })[0]
Assert-Condition ($unsignedBoundary.request.auth.type -eq 'noauth') 'The AWS unsigned boundary request must use noauth.'

$requiredAssertions = @(
    'pm.response.to.have.status(201)',
    'pm.response.to.have.status(200)',
    'pm.response.to.have.status(400)',
    'pm.response.to.have.status(403)',
    'pm.response.to.have.status(409)',
    'application/problem+json',
    'CANCELLED_BY_REQUEST',
    'saleStartsAt'
)
foreach ($assertion in $requiredAssertions) {
    Assert-Condition ($collectionRaw.Contains($assertion)) "Collection assertion or contract text is missing: $assertion"
}
Assert-Condition (([regex]::Matches($collectionRaw, 'pm\.test\(')).Count -ge 67) 'Collection must contain at least 67 Postman assertions.'

$immediateEvent = Find-Request -Folder $localFolder -Name '01 | Criar evento imediato'
$reservation = Find-Request -Folder $localFolder -Name '03 | Criar reserva'
$futureEvent = Find-Request -Folder $localFolder -Name '10 | Criar venda futura'
$immediateEventBody = $immediateEvent.request.body.raw | ConvertFrom-Json
$reservationBody = $reservation.request.body.raw | ConvertFrom-Json
$futureEventBody = $futureEvent.request.body.raw | ConvertFrom-Json

Assert-Condition ($immediateEventBody.capacity -eq 2) 'Local event request must provide the safe capacity-2 sample body.'
Assert-Condition ($immediateEventBody.name -like 'Postman {{runId}}*') 'Local event request must use runId in its sample name.'
Assert-Condition ($reservationBody.quantity -eq 1) 'Local reservation request must provide the safe quantity-1 sample body.'
Assert-Condition ($reservationBody.customer.email -eq '{{customerEmail}}') 'Local reservation body must use the environment customer email.'
Assert-Condition ($futureEventBody.startsAt -eq '{{saleStartsAt}}') 'Future event body must use the generated start instant.'
Assert-Condition ($futureEventBody.endsAt -eq '{{saleEndsAt}}') 'Future event body must use the generated end instant.'

$requiredTeachingFragments = @(
    "pm.collectionVariables.set('eventId', event.id)",
    "pm.collectionVariables.set('reservationId', reservation.id)",
    "pm.collectionVariables.set('futureEventId', event.id)",
    "'resource-conflict'",
    "'invalid-request'",
    'tooLongIdempotencyKey',
    '{{eventId}}',
    '{{reservationId}}',
    '{{futureEventId}}',
    'Como uma resposta vira parâmetro',
    'Current value'
)
foreach ($fragment in $requiredTeachingFragments) {
    Assert-Condition ($collectionRaw.Contains($fragment)) "Collection must teach payloads and response chaining: $fragment"
}

$guideRaw = Get-Content -LiteralPath 'postman/README.md' -Raw
foreach ($fragment in @('Criar massa e reutilizar respostas na apresentação', '{{eventId}}', '{{reservationId}}', 'Scripts > Post-response', 'Current value')) {
    Assert-Condition ($guideRaw.Contains($fragment)) "Postman guide must explain response chaining: $fragment"
}

$requiredEnvironmentKeys = @(
    'commandBaseUrl',
    'queryBaseUrl',
    'customerEmail',
    'awsRegion',
    'awsAccessKeyId',
    'awsSecretAccessKey',
    'awsSessionToken'
)
foreach ($key in $requiredEnvironmentKeys) {
    Assert-Condition ($local.Values.ContainsKey($key)) "Local environment is missing $key."
    Assert-Condition ($aws.Values.ContainsKey($key)) "AWS environment is missing $key."
}

Assert-Condition ($local.Environment.name -eq 'Flash Booking Local') 'Local environment name is incorrect.'
Assert-Condition ($local.Values.commandBaseUrl -eq 'http://localhost:8082') 'Local commandBaseUrl must target Command API port 8082.'
Assert-Condition ($local.Values.queryBaseUrl -eq 'http://localhost:8081') 'Local queryBaseUrl must target Query API port 8081.'
Assert-Condition ($local.Values.customerEmail -eq 'postman+flash-booking@example.test') 'Local environment must use the documented non-personal customer fixture.'

Assert-Condition ($aws.Environment.name -eq 'Flash Booking AWS') 'AWS environment name is incorrect.'
Assert-Condition ($aws.Values.awsRegion -eq 'sa-east-1') 'AWS environment must default to the documented region.'
foreach ($key in @('commandBaseUrl', 'queryBaseUrl', 'customerEmail', 'awsAccessKeyId', 'awsSecretAccessKey', 'awsSessionToken')) {
    Assert-Condition ([string]::IsNullOrEmpty($aws.Values[$key])) "AWS environment must not version a value for $key."
}

Assert-Condition ($collectionRaw -notmatch '19b1ing1ee|moreiravinicius\.job') 'Collection must not contain a retired API endpoint or personal email.'

Write-Output 'Postman executable documentation validation passed.'
