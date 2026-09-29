param(
    [string]$ReadmePath,
    [string]$AwsVisualDirectory
)

$PSDefaultParameterValues['Get-Content:Encoding'] = 'UTF8'

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

# The README is a concise navigation page; current system truth lives in .specs.
foreach ($requiredTruth in @(
    'case técnico independente',
    'reserva temporária de ingressos',
    'não processa pagamentos, não interpreta o estado financeiro e não emite ingressos.',
    'Spring Boot 4.0.8',
    'Jackson 3',
    'ficam desligados por padrão',
    'aws-plan.md',
    'O ciclo de confirmação externa está implementado no runtime local:',
    'não foi aplicada'
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
Assert-Condition -Condition ($localReferences.Count -ge 10) -Message 'expected concise navigation to maintained specs and runbooks'

# Images require useful alt text; SVGs must be valid standalone XML.
$imageMatches = [regex]::Matches($readme, '!\[([^\]]*)\]\(([^)]+)\)')
Assert-Condition -Condition ($imageMatches.Count -ge 3) -Message 'expected the hero, data model, and transactional-outbox visuals'
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
    'flash-booking-data-model.svg',
    'flash-booking-transactional-outbox.svg'
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

# The README links the current overview and confirmation views. Historical deployment
# diagrams remain in docs/images with their scope and deployment status stated accessibly.

$detailsOpen = [regex]::Matches($readme, '<details>').Count
$detailsClose = [regex]::Matches($readme, '</details>').Count
Assert-Condition -Condition ($detailsOpen -eq $detailsClose) -Message "unbalanced deep-dive markup, found $detailsOpen/$detailsClose"

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
            'PENDING → EXPIRED',
            'devolve vaga',
            'deduplicado por registro',
            'solicita o SES',
            'maxReceiveCount = 5',
            'Reconciliador usa o relógio do banco: recupera expirações atrasadas',
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
        ForbiddenFacts = @('POST /reservations', 'M414 554 H348 V486 H958 V494', 'M872 834 H1158')
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
            'Topologia Multi-AZ não provisionada',
            'ciclo de confirmação está em diagrama próprio',
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

# The README points readers to the authoritative contract instead of copying it.
$demoSpecPath = Join-Path $repositoryRoot '.specs\features\flash-booking-demo\spec.md'
$demoSpec = Get-Content -LiteralPath $demoSpecPath -Raw
$expectedEndpoints = @(
    'POST /events',
    'GET /events/{id}',
    'POST /events/{id}/reservations',
    'GET /reservations/{id}',
    'DELETE /reservations/{id}'
)
Assert-Contains -Text $readme -Expected '.specs/features/flash-booking-demo/spec.md' -Context 'authoritative API spec navigation'
foreach ($endpoint in $expectedEndpoints) {
    Assert-Contains -Text $demoSpec -Expected $endpoint -Context 'authoritative API contract'
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

# Edge-burst evidence preserves observed outcomes without turning them into capacity claims.
$edgeBurstSvg = Get-Content -LiteralPath (Join-Path $repositoryRoot 'docs\images\flash-booking-edge-burst.svg') -Raw
foreach ($fact in @(
    '80 GETs assinados',
    '834 ms',
    '80 × 503',
    '0 × 429',
    'WAF → 403',
    '20 POSTs assinados',
    '20 × 400',
    'DELETE não executado',
    'MELHOR ESFORÇO',
    'NÃO É TESTE DDOS',
    'não é capacidade sustentável'
)) {
    Assert-Contains -Text $edgeBurstSvg -Expected $fact -Context 'edge burst evidence contract'
}
Assert-Contains -Text $edgeBurstSvg -Expected 'targets não implicam 429 determinístico' -Context 'edge burst best-effort disclaimer'
Assert-Condition -Condition ($edgeBurstSvg -notmatch '(?i)capacidade garantida') -Message 'edge burst visual overstates the observed protection'

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
foreach ($fact in '>7<', '>13<', '>29<', '>94<', '>43/43<', 'baseline integral histórico', '20 tarefas', 'sem apply, benchmark ou failover remoto') {
    Assert-Contains -Text $specDrivenSvg -Expected $fact -Context 'spec-driven evidence trail'
}

$architectureSvg = Get-Content -LiteralPath (Join-Path $repositoryRoot 'docs\images\flash-booking-architecture-evolution.svg') -Raw
foreach ($fact in 'RDS PostgreSQL Single-AZ', 'Aurora + RDS Proxy', 'Valkey Multi-AZ', 'VALIDADA', 'NÃO APLICADA') {
    Assert-Contains -Text $architectureSvg -Expected $fact -Context 'architecture comparison'
}

# The reservation lifecycle is implemented locally; AWS resources remain unapplied.
foreach ($fact in @(
    'PENDING',
    'CANCELLED',
    'EXPIRED',
    'CONFIRMED',
    'CANCELLATION_PENDING',
    'implementado no runtime local',
    'não foi aplicada',
    'módulo externo',
    'ReservationHeld',
    'ReservationConfirmationRequested',
    'ReservationConfirmationRejected',
    'ReservationHoldClosed',
    'ReservationCancellationRequested',
    'C4 Model e ciclo de confirmação',
    'flash-booking-confirmation-c4-context.svg',
    'flash-booking-confirmation-c4-containers.svg',
    'flash-booking-confirmation-lifecycle.svg'
)) {
    Assert-Contains -Text $readme -Expected $fact -Context 'reservation lifecycle implementation'
}

$lifecycleContracts = @(
    @{ Name = 'flash-booking-confirmation-c4-context.svg'; Facts = @('RUNTIME LOCAL', 'AWS NÃO APLICADA', 'Flash Booking', 'ÚNICO DONO', 'SQS direta', 'Compensa rejeição') },
    @{ Name = 'flash-booking-confirmation-c4-containers.svg'; Facts = @('RUNTIME LOCAL', 'AWS NÃO APLICADA', 'Command API', 'Query API', 'Worker', 'POSTGRESQL', 'Saída ao externo', 'Entrada ao Flash', 'Outbox e inbox são tabelas', 'nenhuma assinatura SNS') },
    @{ Name = 'flash-booking-confirmation-lifecycle.svg'; Facts = @('CICLO IMPLEMENTADO', 'AWS NÃO APLICADA', 'PENDING', 'CONFIRMED', 'CANCELLATION_PENDING', 'CANCELLED', 'EXPIRED', 'ReservationConfirmationRejected', 'ReservationHoldClosed', 'resolutionId') }
)
foreach ($contract in $lifecycleContracts) {
    $path = Join-Path $awsVisualRoot $contract.Name
    Assert-Condition -Condition (Test-Path -LiteralPath $path -PathType Leaf) -Message "missing lifecycle diagram: $($contract.Name)"
    $content = Get-Content -LiteralPath $path -Raw
    [xml]$xml = $content
    Assert-Condition -Condition ($xml.DocumentElement.LocalName -eq 'svg') -Message "proposed diagram has no svg root: $($contract.Name)"
    Assert-Condition -Condition ($content -match 'role="img" aria-labelledby="title desc"') -Message "lifecycle diagram lacks accessibility labels: $($contract.Name)"
    Assert-Condition -Condition ($null -ne $xml.DocumentElement.title -and $null -ne $xml.DocumentElement.desc) -Message "lifecycle diagram lacks title or description: $($contract.Name)"
    foreach ($fact in $contract.Facts) {
        Assert-Contains -Text $content -Expected $fact -Context $contract.Name
    }
}

# The AWS sequence must show complete integration result names in visible text,
# not only in the SVG accessibility description.
$confirmationAwsSvg = Get-Content -LiteralPath (Join-Path $awsVisualRoot 'flash-booking-confirmation-aws-components.svg') -Raw
foreach ($visibleMessage in @(
    'ReservationHeld</text>',
    'ReservationConfirmationRequested</text>',
    'ReservationConfirmed ou</text>',
    'ReservationConfirmationRejected.</text>',
    'ReservationCancellationRequested.'
)) {
    Assert-Contains -Text $confirmationAwsSvg -Expected $visibleMessage -Context 'visible AWS confirmation sequence contract'
}

Write-Output "validate-readme: PASS - current specs linked, $($imageMatches.Count) README images, $($localReferences.Count) local references, 3 performance scenarios"
