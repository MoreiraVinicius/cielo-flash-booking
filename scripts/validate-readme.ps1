param(
    [string]$ReadmePath,
    [string]$AwsVisualDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-Condition {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Condition,
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if (-not $Condition) {
        throw "README validation failed: $Message"
    }
}

function Assert-Contains {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text,
        [Parameter(Mandatory = $true)]
        [string]$Expected,
        [Parameter(Mandatory = $true)]
        [string]$Context
    )

    $found = $Text.IndexOf($Expected, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
    Assert-Condition -Condition $found -Message "$Context is missing '$Expected'"
}

function Resolve-RepositoryTarget {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot,
        [Parameter(Mandatory = $true)]
        [string]$Target
    )

    $cleanTarget = $Target.Trim('<', '>')
    $withoutFragment = ($cleanTarget -split '#', 2)[0]
    $decoded = [uri]::UnescapeDataString($withoutFragment)
    $fullPath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $decoded))
    $insideRepository = $fullPath.StartsWith($RepositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)
    Assert-Condition -Condition $insideRepository -Message "reference leaves the repository: $Target"
    return $fullPath
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$readmePath = if ([string]::IsNullOrWhiteSpace($ReadmePath)) {
    Join-Path $repositoryRoot 'README.md'
} else {
    [IO.Path]::GetFullPath($ReadmePath)
}
Assert-Condition -Condition (Test-Path -LiteralPath $readmePath -PathType Leaf) -Message "README path does not exist: $readmePath"
$readme = Get-Content -LiteralPath $readmePath -Raw
$awsVisualRoot = if ([string]::IsNullOrWhiteSpace($AwsVisualDirectory)) {
    Join-Path $repositoryRoot 'docs\images'
} else {
    [IO.Path]::GetFullPath($AwsVisualDirectory)
}
Assert-Condition -Condition (Test-Path -LiteralPath $awsVisualRoot -PathType Container) -Message "AWS visual directory does not exist: $awsVisualRoot"

# Product scope and evidence must stay explicit.
foreach ($requiredTruth in @(
    'case técnico independente',
    'reserva temporária de ingressos',
    'Pagamento, compra confirmada e emissão de ingresso não fazem parte desta entrega',
    '43/43 critérios',
    '38 testes unitários + 56 de integração = 94 aprovados',
    '47/47 unitários',
    'execução PostgreSQL completa',
    'arquitetura-alvo planejada',
    'não foi provisionada, benchmarkada nem validada remotamente',
    'não comprova distribuição multiprocesso',
    'Nenhum número novo foi inventado'
)) {
    Assert-Contains -Text $readme -Expected $requiredTruth -Context 'README truth contract'
}

# Every local Markdown reference must resolve inside the repository.
$referenceMatches = [regex]::Matches($readme, '\]\(([^)]+)\)')
$localReferences = @()
foreach ($match in $referenceMatches) {
    $target = $match.Groups[1].Value
    if ($target -match '^(https?://|mailto:|#)') {
        continue
    }
    $localReferences += $target
    $resolved = Resolve-RepositoryTarget -RepositoryRoot $repositoryRoot -Target $target
    Assert-Condition -Condition (Test-Path -LiteralPath $resolved) -Message "missing local reference: $target"
}
Assert-Condition -Condition ($localReferences.Count -ge 30) -Message 'expected the complete documentation map and visual gallery'

# Images require useful alt text; SVGs must be valid standalone XML.
$imageMatches = [regex]::Matches($readme, '!\[([^\]]*)\]\(([^)]+)\)')
Assert-Condition -Condition ($imageMatches.Count -ge 13) -Message 'expected at least thirteen explanatory images'
foreach ($match in $imageMatches) {
    $alt = $match.Groups[1].Value
    $target = $match.Groups[2].Value
    Assert-Condition -Condition (-not [string]::IsNullOrWhiteSpace($alt)) -Message "image has empty alt text: $target"
    $resolved = Resolve-RepositoryTarget -RepositoryRoot $repositoryRoot -Target $target
    Assert-Condition -Condition (Test-Path -LiteralPath $resolved) -Message "missing README image: $target"
    if ([IO.Path]::GetExtension($resolved) -eq '.svg') {
        try {
            [xml](Get-Content -LiteralPath $resolved -Raw) | Out-Null
        } catch {
            throw "README validation failed: invalid SVG XML at $target - $($_.Exception.Message)"
        }
    }
}

foreach ($requiredAsset in @(
    'flash-booking-hero.svg',
    'flash-booking-last-ticket.svg',
    'flash-booking-idempotency.png',
    'flash-booking-spec-driven.svg',
    'flash-booking-architecture-evolution.svg',
    'flash-booking-performance.svg',
    'flash-booking-aws-eventual-consistency.svg',
    'flash-booking-aws-demo.svg',
    'flash-booking-aws-high-load.svg',
    'flash-booking-c4-demo.svg',
    'flash-booking-c4-high-load.svg',
    'flash-booking-c4-components.svg',
    'flash-booking-sequence-reservation.svg'
)) {
    Assert-Contains -Text $readme -Expected $requiredAsset -Context 'README image set'
}

foreach ($obsoleteAsset in @(
    'flash-booking-aws-demo-v4.png',
    'flash-booking-aws-demo-v5.png',
    'flash-booking-aws-high-load-v1.png',
    'flash-booking-aws-high-load-v2.svg'
)) {
    Assert-Condition -Condition ($readme.IndexOf($obsoleteAsset, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) -Message "obsolete architecture asset is still referenced: $obsoleteAsset"
    Assert-Condition -Condition (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot "docs\images\$obsoleteAsset"))) -Message "obsolete architecture asset still exists: $obsoleteAsset"
}

# A filename mentioned in a cleanup list is not usage: only Markdown links count.
$imageRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot 'docs\images'))
$documentFiles = @(Get-ChildItem -LiteralPath $repositoryRoot -Recurse -File -Filter '*.md' | Where-Object {
    $_.FullName -notmatch '[\\/](\.git|target|\.tmp|\.codex|\.agents)[\\/]'
})
$referencedImages = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($document in $documentFiles) {
    $documentText = Get-Content -LiteralPath $document.FullName -Raw
    foreach ($reference in [regex]::Matches($documentText, '\]\(([^)]+)\)')) {
        $target = $reference.Groups[1].Value.Trim('<', '>')
        if ($target -match '^(https?://|mailto:|#|data:|[a-z]+://)') {
            continue
        }
        $decoded = [uri]::UnescapeDataString(($target -split '#', 2)[0])
        if ([string]::IsNullOrWhiteSpace($decoded)) {
            continue
        }
        $resolved = [IO.Path]::GetFullPath((Join-Path $document.DirectoryName $decoded))
        if ($resolved.StartsWith(($imageRoot + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) {
            $referencedImages.Add($resolved) | Out-Null
        }
    }
}
$imageInventory = @(Get-ChildItem -LiteralPath $imageRoot -File)
foreach ($asset in $imageInventory) {
    Assert-Condition -Condition ($referencedImages.Contains($asset.FullName)) -Message "image has no documentation link: $($asset.Name)"
}

$detailsOpen = [regex]::Matches($readme, '<details>').Count
$detailsClose = [regex]::Matches($readme, '</details>').Count
Assert-Condition -Condition ($detailsOpen -eq 6 -and $detailsClose -eq 6) -Message "expected six balanced deep dives, found $detailsOpen/$detailsClose"

# The AWS views have an explicit truth contract and must remain native, accessible SVGs.
$awsVisualContracts = @(
    @{
        Name = 'flash-booking-aws-eventual-consistency.svg'
        ViewBox = '0 0 1600 980'
        Facts = @(
            'PostgreSQL · uma única transação',
            'O comando termina no commit — antes da resposta HTTP',
            'EFEITOS APÓS O COMMIT',
            'Outbox no PostgreSQL',
            'UPDATE estoque + INSERT reserva PENDING',
            'UPSERT cliente + 2 eventos no outbox',
            'commit atômico: tudo persiste ou nada persiste',
            '201 Created',
            'POST /events/{id}/reservations',
            'GET /events/{id}',
            'GET /reservations/{id}',
            'Cache-aside somente no GET /events/{id}',
            'RDS direto · sem Valkey',
            'TTL máximo: 1 segundo',
            'AFTER_COMMIT · EVICT BEST EFFORT',
            'MISS / FALHA · QUERY API LÊ RDS',
            'cache nunca autoriza estoque',
            'ReservationCreated',
            'ReservationExpirationScheduled',
            'poll a cada 1 segundo',
            'falha de publicação:',
            'permanece PENDING',
            'Worker consumer',
            'condicional e idempotente',
            'deduplicado por registro',
            'solicita o SES',
            'maxReceiveCount = 5',
            'Reconciliador',
            '+ 5s',
            'após expiresAt',
            '≤ 30s',
            'SQS e SES não bloqueiam',
            'Falha assíncrona não altera a reserva PENDING nem o 201 já persistido',
            'Sem oversell'
        )
        Paths = @(
            'M258 297 H310',
            'M530 297 H594',
            'M996 297 H1074',
            'M420 338 V367 H537 V494',
            'M196 580 V592 H958 V580',
            'M1122 552 H1082',
            'M274 759 H322',
            'M532 746 H586',
            'M532 830 H586',
            'M872 742 H924',
            'M872 834 H924',
            'M1108 742 H1158',
            'M1108 834 H1158'
        )
        MinimumOccurrences = @(
            @{ Text = 'maxReceiveCount = 5'; Count = 2 },
            @{ Text = 'Worker consumer'; Count = 2 },
            @{ Text = 'DLQ'; Count = 2 }
        )
        ForbiddenFacts = @('POST /reservations', 'M414 554 H348 V486 H958 V494')
    },
    @{
        Name = 'flash-booking-aws-demo.svg'
        ViewBox = '0 0 1600 900'
        Facts = @(
            'APLICADA',
            'VALIDADA',
            'DESTRUÍDA',
            'AWS Region',
            'VPC em 2 AZs',
            'serviços regionais',
            '1 NAT GATEWAY',
            'AWS WAF + API Gateway',
            'REST Regional',
            'VPC Link v2',
            'Application Load Balancer interno',
            'Amazon ECS Fargate',
            '1 task por serviço',
            'Query API',
            'Command API',
            'Publisher + consumers',
            'PostgreSQL 16 · Single-AZ',
            'Valkey 7.2 · single-node',
            'Expiration Queue + DLQ',
            'Notification Queue + DLQ',
            'Amazon SES',
            'Amazon CloudWatch',
            'AWS Secrets Manager',
            'AWS Budgets',
            'SECURITY GROUPS'
        )
        Paths = @('M684 408 H704 V438 H1060 V424', 'M684 500 H716 V454 H1138 V424', 'M760 552 H742 V650 H684', 'M684 678 H760')
        MinimumOccurrences = @(
            @{ Text = 'Queue + DLQ'; Count = 2 }
        )
        ForbiddenFacts = @('Amazon VPC · 2 Availability Zones', 'M684 500 H718 V398 H994', 'M981 578 V610')
    },
    @{
        Name = 'flash-booking-aws-high-load.svg'
        ViewBox = '0 0 1600 900'
        Facts = @(
            'NÃO PROVISIONADA',
            'SEM CAPACIDADE MEDIDA',
            'AWS Region',
            'VPC em pelo menos 2 AZs',
            'serviços gerenciados regionais fora da VPC',
            'NAT GATEWAY POR AZ',
            'Mesmos três modos Java',
            'failover e escala ainda precisam de evidência',
            'tasks Multi-AZ · autoscaling independente',
            'QUERY · N TASKS',
            'COMMAND · N TASKS',
            'WORKER · N TASKS',
            'Multi-AZ · primary + réplica',
            'RDS Proxy + Aurora',
            'PostgreSQL Serverless v2',
            'Expiration Queue + DLQ',
            'Notification Queue + DLQ',
            'CloudWatch + tracing',
            'Auto Scaling',
            'ALVO NÃO PROVISIONADO NEM MEDIDO'
        )
        Paths = @('M684 410 H704 V448 H1060 V436', 'M684 510 H716 V464 H1138 V436', 'M760 554 H742 V654 H684', 'M684 682 H760')
        MinimumOccurrences = @(
            @{ Text = 'Queue + DLQ'; Count = 2 },
            @{ Text = 'N TASKS'; Count = 3 }
        )
        ForbiddenFacts = @('Amazon VPC · pelo menos 2 Availability Zones', 'M684 510 H718 V402 H994', 'M981 582 V616')
    }
)

foreach ($contract in $awsVisualContracts) {
    $svgPath = Join-Path $awsVisualRoot $contract.Name
    $svgText = Get-Content -LiteralPath $svgPath -Raw
    [xml]$svgXml = $svgText
    $svgRoot = $svgXml.DocumentElement
    Assert-Condition -Condition ($svgRoot.GetAttribute('viewBox') -eq $contract.ViewBox) -Message "$($contract.Name) has an unexpected canvas"
    Assert-Condition -Condition ($svgRoot.GetAttribute('role') -eq 'img') -Message "$($contract.Name) is missing role=img"
    Assert-Condition -Condition (-not [string]::IsNullOrWhiteSpace($svgRoot.GetAttribute('aria-labelledby'))) -Message "$($contract.Name) is missing an accessible label reference"
    $titleNode = $svgRoot.SelectSingleNode("*[local-name()='title']")
    $descriptionNode = $svgRoot.SelectSingleNode("*[local-name()='desc']")
    Assert-Condition -Condition ($null -ne $titleNode -and -not [string]::IsNullOrWhiteSpace($titleNode.InnerText)) -Message "$($contract.Name) is missing a native title"
    Assert-Condition -Condition ($null -ne $descriptionNode -and -not [string]::IsNullOrWhiteSpace($descriptionNode.InnerText)) -Message "$($contract.Name) is missing a native description"
    Assert-Condition -Condition ($svgText -notmatch '(?i)<foreignObject|<image\b') -Message "$($contract.Name) contains raster or foreignObject content"
    Assert-Condition -Condition ($svgText -notmatch '(?i)terraform') -Message "$($contract.Name) contains implementation-tool noise"
    foreach ($fact in $contract.Facts) {
        Assert-Contains -Text $svgText -Expected $fact -Context "$($contract.Name) truth contract"
    }
    foreach ($path in $contract.Paths) {
        Assert-Contains -Text $svgText -Expected $path -Context "$($contract.Name) routed-arrow contract"
    }
    foreach ($occurrence in $contract.MinimumOccurrences) {
        $actualCount = [regex]::Matches($svgText, [regex]::Escape($occurrence.Text), [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
        Assert-Condition -Condition ($actualCount -ge $occurrence.Count) -Message "$($contract.Name) needs at least $($occurrence.Count) occurrences of '$($occurrence.Text)', found $actualCount"
    }
    foreach ($forbiddenFact in $contract.ForbiddenFacts) {
        Assert-Condition -Condition ($svgText.IndexOf($forbiddenFact, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) -Message "$($contract.Name) retains an obsolete or misleading fact: $forbiddenFact"
    }
}

# The public contract is exactly the five routes from the case.
$endpointMatches = [regex]::Matches($readme, '(?m)^\| `(POST|GET|DELETE)` \| `([^`]+)` \|')
$actualEndpoints = @($endpointMatches | ForEach-Object { "$($_.Groups[1].Value) $($_.Groups[2].Value)" })
$expectedEndpoints = @(
    'POST /events',
    'GET /events/{id}',
    'POST /events/{id}/reservations',
    'GET /reservations/{id}',
    'DELETE /reservations/{id}'
)
Assert-Condition -Condition ($actualEndpoints.Count -eq 5) -Message "expected exactly five endpoint rows, found $($actualEndpoints.Count)"
foreach ($endpoint in $expectedEndpoints) {
    Assert-Condition -Condition ($actualEndpoints -contains $endpoint) -Message "missing endpoint row: $endpoint"
}

# The historical baseline is structured, honest about provenance, and matches the SVG.
$baselinePath = Join-Path $repositoryRoot 'performance\demo\baseline.json'
$baseline = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json
Assert-Condition -Condition ($baseline.schemaVersion -eq 1) -Message 'baseline schemaVersion must be 1'
Assert-Condition -Condition ($baseline.scenarios.Count -eq 3) -Message 'baseline must contain query, reservation, and mixed scenarios'
Assert-Condition -Condition ($baseline.environment.commandApiProcesses -ge 1) -Message 'baseline process count is missing'

$scenarioNames = @($baseline.scenarios | ForEach-Object { $_.name })
foreach ($scenarioName in 'query', 'reservation', 'mixed') {
    Assert-Condition -Condition ($scenarioNames -contains $scenarioName) -Message "baseline scenario is missing: $scenarioName"
}

foreach ($scenario in $baseline.scenarios) {
    Assert-Condition -Condition ($scenario.vus -gt 0) -Message "$($scenario.name) VUs must be positive"
    Assert-Condition -Condition ($scenario.durationSeconds -gt 0) -Message "$($scenario.name) duration must be positive"
    Assert-Condition -Condition ($scenario.requests -gt 0) -Message "$($scenario.name) request count must be positive"
    Assert-Condition -Condition ($scenario.requestsPerSecond -gt 0) -Message "$($scenario.name) req/s must be positive"
    Assert-Condition -Condition ($scenario.latencyMs.p50 -gt 0 -and $scenario.latencyMs.p95 -gt 0 -and $scenario.latencyMs.p99 -gt 0) -Message "$($scenario.name) latency percentiles are incomplete"
    Assert-Condition -Condition ($scenario.httpFailureRate -eq 0) -Message "$($scenario.name) no longer supports the documented zero-failure claim"
    Assert-Condition -Condition ($scenario.checksFailed -eq 0) -Message "$($scenario.name) has failed checks"
}

$limitations = $baseline.limitations -join ' '
Assert-Contains -Text $limitations -Expected 'not multiprocess evidence' -Context 'baseline limitations'
Assert-Contains -Text $limitations -Expected 'not production SLOs' -Context 'baseline limitations'
Assert-Contains -Text $limitations -Expected 'not provisioned or benchmarked' -Context 'baseline limitations'

$performanceReadme = Get-Content -LiteralPath (Join-Path $repositoryRoot 'performance\demo\README.md') -Raw
Assert-Condition -Condition ($performanceReadme -notmatch '\bTPS\b') -Message 'HTTP throughput must be labeled req/s, not TPS'

$performanceSvg = Get-Content -LiteralPath (Join-Path $repositoryRoot 'docs\images\flash-booking-performance.svg') -Raw
$portuguese = [Globalization.CultureInfo]::GetCultureInfo('pt-BR')
foreach ($scenario in $baseline.scenarios) {
    $rps = ([double]$scenario.requestsPerSecond).ToString('N2', $portuguese)
    $p95 = ([double]$scenario.latencyMs.p95).ToString('N2', $portuguese)
    Assert-Contains -Text $performanceSvg -Expected $rps -Context "$($scenario.name) chart throughput"
    Assert-Contains -Text $performanceSvg -Expected "$p95 ms" -Context "$($scenario.name) chart p95"
}
Assert-Contains -Text $performanceSvg -Expected 'Latência agregada de GET + POST' -Context 'mixed chart limitation'
Assert-Contains -Text $performanceSvg -Expected '1 endpoint de comandos exercitado' -Context 'historical chart limitation'

# The current harness must not repeat the historical single-endpoint flaw.
$compose = Get-Content -LiteralPath (Join-Path $repositoryRoot 'compose.yaml') -Raw
$runner = Get-Content -LiteralPath (Join-Path $repositoryRoot 'performance\demo\run.ps1') -Raw
$commandWorkload = Get-Content -LiteralPath (Join-Path $repositoryRoot 'performance\demo\command.js') -Raw
$mixedWorkload = Get-Content -LiteralPath (Join-Path $repositoryRoot 'performance\demo\mixed.js') -Raw
Assert-Contains -Text $compose -Expected 'command-api-concurrency:' -Context 'Compose concurrency profile'
Assert-Contains -Text $compose -Expected '- "8080"' -Context 'ephemeral command replica port'
Assert-Contains -Text $runner -Expected 'expected at least two command-api-concurrency containers' -Context 'runner replica guard'
Assert-Contains -Text $runner -Expected '$env:COMMAND_BASE_URLS = $commandBaseUrls -join' -Context 'runner target distribution'
Assert-Contains -Text $commandWorkload -Expected 'COMMAND_BASE_URLS' -Context 'command workload target list'
Assert-Contains -Text $commandWorkload -Expected 'command_target' -Context 'command workload process tag'
Assert-Contains -Text $mixedWorkload -Expected 'COMMAND_BASE_URLS' -Context 'mixed workload target list'
Assert-Contains -Text $mixedWorkload -Expected 'command_target' -Context 'mixed workload process tag'

# Summary visuals deliberately omit implementation tooling and preserve status boundaries.
foreach ($summarySvgName in @(
    'flash-booking-hero.svg',
    'flash-booking-last-ticket.svg',
    'flash-booking-spec-driven.svg',
    'flash-booking-architecture-evolution.svg',
    'flash-booking-performance.svg'
)) {
    $summarySvg = Get-Content -LiteralPath (Join-Path $repositoryRoot "docs\images\$summarySvgName") -Raw
    Assert-Condition -Condition ($summarySvg -notmatch '(?i)terraform') -Message "summary visual contains implementation-tool noise: $summarySvgName"
}

$specDrivenSvg = Get-Content -LiteralPath (Join-Path $repositoryRoot 'docs\images\flash-booking-spec-driven.svg') -Raw
foreach ($fact in '>7<', '>13<', '>29<', '>94<', '>43/43<', '20 tarefas', 'sem apply, benchmark ou failover remoto') {
    Assert-Contains -Text $specDrivenSvg -Expected $fact -Context 'spec-driven evidence trail'
}

$architectureSvg = Get-Content -LiteralPath (Join-Path $repositoryRoot 'docs\images\flash-booking-architecture-evolution.svg') -Raw
foreach ($fact in 'RDS PostgreSQL Single-AZ', 'Aurora + RDS Proxy', 'Valkey Multi-AZ', 'VALIDADA', 'NÃO APLICADA') {
    Assert-Contains -Text $architectureSvg -Expected $fact -Context 'architecture comparison'
}

Write-Output "validate-readme: PASS - 5 endpoints, $($imageMatches.Count) images, $($localReferences.Count) local references, 3 performance scenarios"
