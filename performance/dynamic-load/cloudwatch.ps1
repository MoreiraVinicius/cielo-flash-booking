[CmdletBinding()]
param(
    [switch]$ContractTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-CloudWatchWindow {
    param([datetime]$StartUtc, [datetime]$EndUtc)
    if ($StartUtc.Kind -ne [DateTimeKind]::Utc -or $EndUtc.Kind -ne [DateTimeKind]::Utc -or $EndUtc -le $StartUtc) {
        throw 'CloudWatch requires an ascending UTC window'
    }
}

function New-CloudWatchMetricPlan {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Resources
    )
    foreach ($name in 'ApiName', 'Stage', 'ClusterName', 'QueryService', 'CommandService', 'DbInstanceIdentifier', 'CacheClusterId', 'ExpirationQueueName', 'NotificationQueueName', 'ExpirationDlqName', 'NotificationDlqName') {
        if ([string]::IsNullOrWhiteSpace([string]$Resources[$name])) {
            throw "CloudWatch resource '$name' is required"
        }
    }
    $queries = @()
    $metric = {
        param($id, $namespace, $metricName, $dimensions)
        [ordered]@{
            Id = $id
            MetricStat = [ordered]@{
                Metric = [ordered]@{ Namespace = $namespace; MetricName = $metricName; Dimensions = $dimensions }
                Period = 60
                Stat = 'Average'
            }
            ReturnData = $true
        }
    }
    foreach ($apiMetric in 'Count', '4XXError', '5XXError', 'Latency', 'IntegrationLatency') {
        $queries += & $metric "api$($queries.Count)" 'AWS/ApiGateway' $apiMetric @(@{ Name = 'ApiName'; Value = $Resources.ApiName }, @{ Name = 'Stage'; Value = $Resources.Stage })
    }
    foreach ($service in @($Resources.QueryService, $Resources.CommandService)) {
        foreach ($ecsMetric in 'CPUUtilization', 'MemoryUtilization') {
            $queries += & $metric "ecs$($queries.Count)" 'AWS/ECS' $ecsMetric @(@{ Name = 'ClusterName'; Value = $Resources.ClusterName }, @{ Name = 'ServiceName'; Value = $service })
        }
    }
    foreach ($rdsMetric in 'CPUUtilization', 'DatabaseConnections') {
        $queries += & $metric "rds$($queries.Count)" 'AWS/RDS' $rdsMetric @(@{ Name = 'DBInstanceIdentifier'; Value = $Resources.DbInstanceIdentifier })
    }
    foreach ($cacheMetric in 'EngineCPUUtilization', 'CurrConnections', 'CacheHits', 'CacheMisses') {
        $queries += & $metric "cache$($queries.Count)" 'AWS/ElastiCache' $cacheMetric @(@{ Name = 'CacheClusterId'; Value = $Resources.CacheClusterId })
    }
    foreach ($queue in @($Resources.ExpirationQueueName, $Resources.NotificationQueueName, $Resources.ExpirationDlqName, $Resources.NotificationDlqName)) {
        foreach ($sqsMetric in 'ApproximateNumberOfMessagesVisible', 'ApproximateAgeOfOldestMessage') {
            $queries += & $metric "sqs$($queries.Count)" 'AWS/SQS' $sqsMetric @(@{ Name = 'QueueName'; Value = $queue })
        }
    }
    return $queries
}

function Convert-CloudWatchResponse {
    param(
        [Parameter(Mandatory = $true)]$Response,
        [Parameter(Mandatory = $true)]$Plan
    )
    $received = @($Response.MetricDataResults | Where-Object { $_.Timestamps.Count -gt 0 } | ForEach-Object { $_.Id })
    $expected = @($Plan | ForEach-Object { $_.Id })
    return [ordered]@{
        metricData = @($Response.MetricDataResults)
        incompleteSeries = @($expected | Where-Object { $received -notcontains $_ })
        evidenceComplete = @($expected | Where-Object { $received -notcontains $_ }).Count -eq 0
    }
}

function Read-CloudWatchEvidence {
    param(
        [Parameter(Mandatory = $true)][datetime]$StartUtc,
        [Parameter(Mandatory = $true)][datetime]$EndUtc,
        [Parameter(Mandatory = $true)][string]$Region,
        [Parameter(Mandatory = $true)][hashtable]$Resources
    )
    Assert-CloudWatchWindow -StartUtc $StartUtc -EndUtc $EndUtc
    $plan = New-CloudWatchMetricPlan -Resources $Resources
    $planPath = [IO.Path]::GetTempFileName()
    try {
        $plan | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $planPath -Encoding utf8
        $responseJson = aws cloudwatch get-metric-data --region $Region --start-time $StartUtc.ToString('o') --end-time $EndUtc.ToString('o') --scan-by TimestampAscending --metric-data-queries "file://$planPath" --output json
        if ($LASTEXITCODE -ne 0) { throw 'CloudWatch GetMetricData failed' }
        $evidence = Convert-CloudWatchResponse -Response ($responseJson | ConvertFrom-Json) -Plan $plan
        $evidence.runWindowUtc = [ordered]@{ start = $StartUtc.ToString('o'); end = $EndUtc.ToString('o') }
        return $evidence
    } finally {
        Remove-Item -LiteralPath $planPath -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-CloudWatchContractTest {
    $resources = @{
        ApiName = 'flash-booking'; Stage = 'demo'; ClusterName = 'flash-booking'; QueryService = 'query-api'; CommandService = 'command-api'
        DbInstanceIdentifier = 'flash-booking-db'; CacheClusterId = 'flash-booking-cache'
        ExpirationQueueName = 'expiration'; NotificationQueueName = 'notification'; ExpirationDlqName = 'expiration-dlq'; NotificationDlqName = 'notification-dlq'
    }
    $plan = New-CloudWatchMetricPlan -Resources $resources
    $response = [pscustomobject]@{ MetricDataResults = @([pscustomobject]@{ Id = $plan[0].Id; Timestamps = @('2026-09-21T10:00:00Z'); Values = @(1) }) }
    $evidence = Convert-CloudWatchResponse -Response $response -Plan $plan
    $passed = $plan.Count -eq 23 -and @($plan | Where-Object { $_.MetricStat.Period -ne 60 }).Count -eq 0 -and $plan[0].MetricStat.Metric.Namespace -eq 'AWS/ApiGateway' -and $evidence.evidenceComplete -eq $false -and $evidence.incompleteSeries.Count -eq 22
    if (!$passed) { throw 'CloudWatch contract failed' }
    Write-Output 'CloudWatch contract passed: read-only one-minute plan and incomplete-series handling are valid.'
}

if ($ContractTest) { Invoke-CloudWatchContractTest }
