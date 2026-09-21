[CmdletBinding()]
param(
    [string]$InputPath,
    [string]$OutputDirectory,
    [switch]$ContractTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Require-Property {
    param($Object, [string]$Name)
    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name] -or $null -eq $Object.$Name) {
        throw "missing required property: $Name"
    }
    return $Object.$Name
}

function Get-Verdict {
    param($Run)
    $analysis = Require-Property $Run 'analysis'
    $profile = (Require-Property (Require-Property $Run 'input') 'profile')
    if ($analysis.generatorBound) { return [ordered]@{ status = 'INCONCLUSIVE'; reason = 'generator-bound'; sustainableRate = $null } }
    if ($analysis.soldOutAccidental) { return [ordered]@{ status = 'INVALID'; reason = 'accidental-sold-out'; sustainableRate = $null } }
    if (!$Run.telemetry.evidenceComplete) { return [ordered]@{ status = 'INCOMPLETE'; reason = 'local-telemetry-incomplete'; sustainableRate = $null } }
    if ($Run.PSObject.Properties['cloudwatch'] -and !$Run.cloudwatch.evidenceComplete) { return [ordered]@{ status = 'INCOMPLETE'; reason = 'cloudwatch-correlation-incomplete'; sustainableRate = $null } }
    if ($analysis.technicalFailureRate -ge 0.01 -or $analysis.queryP95 -ge 500 -or $analysis.commandP95 -ge 750) {
        return [ordered]@{ status = 'FAIL'; reason = 'threshold-breach'; sustainableRate = $null }
    }
    if ($profile -eq 'stress') {
        $passing = @($analysis.steps | Where-Object { $_.passed })
        if ($passing.Count -eq 0) { return [ordered]@{ status = 'INCONCLUSIVE'; reason = 'no-sustainable-step'; sustainableRate = $null } }
        return [ordered]@{ status = 'PASS'; reason = 'last-passing-step'; sustainableRate = $passing[-1].rate }
    }
    if ($profile -eq 'spike' -and $analysis.recoverySeconds -gt 30) { return [ordered]@{ status = 'FAIL'; reason = 'spike-recovery-exceeded'; sustainableRate = $null } }
    if ($profile -eq 'soak' -and ($analysis.p95GrowthPercent -gt 20 -or $analysis.rssGrowthPercent -gt 25 -or $analysis.connectionsGrowthPercent -gt 20)) {
        return [ordered]@{ status = 'FAIL'; reason = 'soak-degradation'; sustainableRate = $null }
    }
    return [ordered]@{ status = 'PASS'; reason = 'profile-thresholds-satisfied'; sustainableRate = $null }
}

function Convert-ToCanonicalResult {
    param($Run)
    foreach ($name in 'metadata', 'input', 'http', 'telemetry', 'audit', 'analysis') { [void](Require-Property $Run $name) }
    foreach ($name in 'sourceCommit', 'dirtyAtStart', 'capturedAt', 'environment') { [void](Require-Property $Run.metadata $name) }
    foreach ($name in 'profile', 'workload', 'seed', 'runId', 'rates', 'durations', 'thresholds') { [void](Require-Property $Run.input $name) }
    foreach ($name in 'availableNonNegative', 'quantitiesPositive', 'inventoryBalanced') {
        if (!(Require-Property $Run.audit $name)) { throw "audit failed: $name" }
    }
    $verdict = Get-Verdict $Run
    return [ordered]@{
        schemaVersion = 1
        capturedAt = $Run.metadata.capturedAt
        sourceCommit = $Run.metadata.sourceCommit
        dirtyAtStart = $Run.metadata.dirtyAtStart
        environment = $Run.metadata.environment
        input = $Run.input
        http = $Run.http
        telemetry = $Run.telemetry
        cloudwatch = if ($Run.PSObject.Properties['cloudwatch']) { $Run.cloudwatch } else { $null }
        audit = $Run.audit
        verdict = $verdict
        limitations = @('Local evidence is not production capacity, AWS high-load capacity, failover proof, or direct JVM heap-leak proof.')
    }
}

function Convert-ToReport {
    param($Result)
    $capacityLabel = if ($Result.input.profile -eq 'capacity') { 'capacidade local provisória' } else { 'não é capacidade de produção' }
    return @"
# Resultado de carga dinâmica

**Verdict:** $($Result.verdict.status) ($($Result.verdict.reason))

- Perfil: $($Result.input.profile)
- Workload: $($Result.input.workload)
- Run ID: $($Result.input.runId)
- Fonte: $capacityLabel
- Commit: $($Result.sourceCommit)
- Telemetria local completa: $($Result.telemetry.evidenceComplete)
- Auditoria de inventário: $($Result.audit.inventoryBalanced)

## Limitações

- Este resultado não é capacidade de produção, prova de failover, nem prova direta de heap JVM.
- CloudWatch, quando presente, correlaciona infraestrutura por janela UTC e não substitui p95/p99 do k6.
"@
}

function Write-CanonicalResult {
    param([Parameter(Mandatory = $true)]$Result, [Parameter(Mandatory = $true)][string]$Directory)
    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    $Result | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $Directory 'result.json') -Encoding utf8
    Convert-ToReport $Result | Set-Content -LiteralPath (Join-Path $Directory 'report.md') -Encoding utf8
}

function New-ContractRun {
    param([string]$Profile = 'stress')
    return [pscustomobject]@{
        metadata = [pscustomobject]@{ sourceCommit = 'abc123'; dirtyAtStart = $false; capturedAt = '2026-09-21T12:00:00Z'; environment = [pscustomobject]@{ scope = 'local' } }
        input = [pscustomobject]@{ profile = $Profile; workload = 'mixed'; seed = 20260921; runId = 'contract'; rates = [pscustomobject]@{}; durations = [pscustomobject]@{}; thresholds = [pscustomobject]@{} }
        http = [pscustomobject]@{ perRouteAndPhase = @() }
        telemetry = [pscustomobject]@{ evidenceComplete = $true; containers = @(); postgres = @(); valkey = @() }
        audit = [pscustomobject]@{ availableNonNegative = $true; quantitiesPositive = $true; inventoryBalanced = $true }
        analysis = [pscustomobject]@{ generatorBound = $false; soldOutAccidental = $false; technicalFailureRate = 0; queryP95 = 100; commandP95 = 100; steps = @([pscustomobject]@{ rate = 20; passed = $true }, [pscustomobject]@{ rate = 40; passed = $true }, [pscustomobject]@{ rate = 60; passed = $false }); recoverySeconds = 20; p95GrowthPercent = 10; rssGrowthPercent = 10; connectionsGrowthPercent = 10 }
    }
}

function Invoke-NormalizerContractTest {
    $stress = Convert-ToCanonicalResult (New-ContractRun 'stress')
    $generatorBoundRun = New-ContractRun 'capacity'; $generatorBoundRun.analysis.generatorBound = $true
    $generatorBound = Convert-ToCanonicalResult $generatorBoundRun
    $soakRun = New-ContractRun 'soak'; $soakRun.analysis.rssGrowthPercent = 26
    $soak = Convert-ToCanonicalResult $soakRun
    $cloudwatchRun = New-ContractRun 'load'; $cloudwatchRun | Add-Member -NotePropertyName cloudwatch -NotePropertyValue ([pscustomobject]@{ evidenceComplete = $false; incompleteSeries = @('api1') })
    $cloudwatch = Convert-ToCanonicalResult $cloudwatchRun
    $missingRejected = $false
    try { Convert-ToCanonicalResult ([pscustomobject]@{}) | Out-Null } catch { $missingRejected = $_.Exception.Message -like 'missing required property*' }
    if ($stress.verdict.status -ne 'PASS' -or $stress.verdict.sustainableRate -ne 40 -or $generatorBound.verdict.status -ne 'INCONCLUSIVE' -or $soak.verdict.reason -ne 'soak-degradation' -or $cloudwatch.verdict.status -ne 'INCOMPLETE' -or !$missingRejected -or (Convert-ToReport $stress) -notmatch 'não é capacidade de produção') {
        throw 'normalizer contract failed'
    }
    Write-Output 'Normalizer contract passed: verdict, incomplete evidence, degradation, and truthful labels are valid.'
}

if ($ContractTest) { Invoke-NormalizerContractTest; return }
if ([string]::IsNullOrWhiteSpace($InputPath) -or [string]::IsNullOrWhiteSpace($OutputDirectory)) { throw 'InputPath and OutputDirectory are required unless ContractTest is used' }
$run = Get-Content -Raw -LiteralPath $InputPath | ConvertFrom-Json
Write-CanonicalResult -Result (Convert-ToCanonicalResult $run) -Directory $OutputDirectory
