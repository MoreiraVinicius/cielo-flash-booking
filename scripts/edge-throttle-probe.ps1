param(
  [Parameter(Mandatory)]
  [ValidatePattern('^https://')]
  [string]$RequestUri,

  [ValidateSet('GET', 'POST', 'DELETE')]
  [string]$HttpMethod = 'GET',

  [ValidateRange(2, 500)]
  [int]$RequestCount = 80,

  [ValidateRange(2, 500)]
  [int]$Parallelism = 80,

  [string]$Profile = 'demo-api-invoker',
  [string]$Region = 'sa-east-1'
)

$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSVersion.Major -lt 7) {
  throw 'PowerShell 7 or later is required for a synchronized parallel probe.'
}

$credentialLines = aws configure export-credentials --profile $Profile --region $Region --format env-no-export
if ($LASTEXITCODE -ne 0) {
  throw 'Could not load temporary credentials for the API invocation role.'
}

$credentials = @{}
foreach ($line in $credentialLines) {
  if ($line -match '^(AWS_[A-Z_]+)=(.*)$') {
    $credentials[$Matches[1]] = $Matches[2].Trim('"')
  }
}

foreach ($requiredName in 'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN') {
  if (-not $credentials.ContainsKey($requiredName)) {
    throw "Credential export did not return $requiredName."
  }
}

$probeResults = 1..$RequestCount | ForEach-Object -Parallel {
  $startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $statusCode = curl.exe --silent --connect-timeout 15 --max-time 30 --output NUL --write-out '%{http_code}' `
    --request $using:HttpMethod `
    --aws-sigv4 "aws:amz:$using:Region`:execute-api" `
    --user "$($using:credentials['AWS_ACCESS_KEY_ID']):$($using:credentials['AWS_SECRET_ACCESS_KEY'])" `
    --header "x-amz-security-token: $($using:credentials['AWS_SESSION_TOKEN'])" `
    $using:RequestUri
  "$statusCode|$startedAt"
} -ThrottleLimit $Parallelism

$results = @($probeResults | ForEach-Object {
  $parts = $_ -split '\|', 2
  [pscustomobject]@{ status = [int]$parts[0]; startedAt = [long]$parts[1] }
})
$statusCounts = @($results.status | Group-Object | Sort-Object Name | ForEach-Object {
  [pscustomobject]@{ status = [int]$_.Name; count = $_.Count }
})
$throttledCount = @($statusCounts | Where-Object status -eq 429 | Measure-Object -Property count -Sum).Sum
if (-not $throttledCount) {
  $throttledCount = 0
}

[pscustomobject]@{
  requestCount  = $RequestCount
  parallelism   = $Parallelism
  method        = $HttpMethod
  dispatchWindowMs = (($results.startedAt | Measure-Object -Maximum).Maximum - ($results.startedAt | Measure-Object -Minimum).Minimum)
  statusCounts  = $statusCounts
  throttledCount = $throttledCount
  passed        = $throttledCount -gt 0
} | ConvertTo-Json -Compress

if ($throttledCount -eq 0) {
  throw 'Expected at least one HTTP 429 from API Gateway throttling.'
}
