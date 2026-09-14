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

    $withoutFragment = ($Target -split '#', 2)[0]
    $decoded = [uri]::UnescapeDataString($withoutFragment)
    $fullPath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $decoded))
    $insideRepository = $fullPath.StartsWith($RepositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)
    Assert-Condition -Condition $insideRepository -Message "reference leaves the repository: $Target"
    return $fullPath
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$readmePath = Join-Path $repositoryRoot 'README.md'
$readme = Get-Content -LiteralPath $readmePath -Raw

# Product scope and evidence must stay explicit.
foreach ($requiredTruth in @(
    'case técnico independente',
    'reserva temporária de ingressos',
    'Pagamento, compra confirmada e emissão de ingresso não fazem parte desta entrega',
    '43/43 critérios',
    '38 testes unitários + 56 de integração = 94 aprovados',
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
Assert-Condition -Condition ($imageMatches.Count -ge 10) -Message 'expected at least ten explanatory images'
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
}

$detailsOpen = [regex]::Matches($readme, '<details>').Count
$detailsClose = [regex]::Matches($readme, '</details>').Count
Assert-Condition -Condition ($detailsOpen -eq 4 -and $detailsClose -eq 4) -Message "expected four balanced deep dives, found $detailsOpen/$detailsClose"

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
