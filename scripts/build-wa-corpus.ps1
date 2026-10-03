param(
    [string] $OutputDirectory = (Join-Path $env:TEMP ("flashbooking-wa-" + [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")))
)

$ErrorActionPreference = "Stop"
$indexUrl = "https://docs.aws.amazon.com/wellarchitected/latest/framework/toc-contents.json"
$baseUrl = "https://docs.aws.amazon.com/wellarchitected/latest/framework/"
$retrievedAt = [DateTime]::UtcNow.ToString("o")
$toc = Invoke-RestMethod -Uri $indexUrl -TimeoutSec 30
$pillarsRoot = @($toc.contents | Where-Object { $_.title -eq "The pillars of the framework" })
$appendixRoot = @($toc.contents | Where-Object { $_.href -eq "appendix.html" })
if ($pillarsRoot.Count -ne 1 -or $appendixRoot.Count -ne 1) {
    throw "AWS TOC does not contain one pillar root and one appendix root"
}

$pillars = @{}
$questions = @{}
$bestPractices = @{}

function Visit-WaNode {
    param($Node, [string] $PillarId, [string] $PillarName)

    $title = [string] $Node.title
    $href = [string] $Node.href
    if ($title -match '^([A-Z]{2,5})\s+(\d{1,2})\s+(.+)$' -or $href -match '^([a-z]{2,5})-(\d{2})\.html$') {
        $questionId = $Matches[1].ToUpperInvariant() + ([int] $Matches[2]).ToString('00')
        if (-not $questions.ContainsKey($questionId)) {
            $questions[$questionId] = [ordered]@{
                schema_version = 'wa-review.corpus.v1'
                pillar_id = $PillarId
                pillar_name = $PillarName
                question_id = $questionId
                question_title = $title
                question_url = $baseUrl + $href
                retrieved_at = $retrievedAt
            }
        }
    }
    if ($title -match '^([A-Z]{2,5}\d{2}-BP\d{2})\s+(.+)$') {
        $bpId = $Matches[1]
        $questionId = $bpId.Split('-')[0]
        if (-not $bestPractices.ContainsKey($bpId)) {
            $bestPractices[$bpId] = [ordered]@{
                schema_version = 'wa-review.corpus.v1'
                pillar_id = $PillarId
                pillar_name = $PillarName
                question_id = $questionId
                question_title = $null
                question_url = $null
                bp_id = $bpId
                bp_title = $Matches[2]
                bp_url = $baseUrl + $href
                retrieved_at = $retrievedAt
            }
        }
    }
    foreach ($child in @($Node.contents)) {
        if ($null -ne $child) {
            Visit-WaNode -Node $child -PillarId $PillarId -PillarName $PillarName
        }
    }
}

foreach ($root in @($pillarsRoot[0], $appendixRoot[0])) {
    foreach ($node in @($root.contents)) {
        $pillarName = [string] $node.title
        $pillarId = ([string] $node.href) -replace '^a-', '' -replace '\.html$', ''
        $pillars[$pillarId] = $pillarName
        Visit-WaNode -Node $node -PillarId $pillarId -PillarName $pillarName
    }
}

foreach ($bp in $bestPractices.Values) {
    if ($questions.ContainsKey($bp.question_id)) {
        $bp.question_title = $questions[$bp.question_id].question_title
        $bp.question_url = $questions[$bp.question_id].question_url
    }
}

$questionList = @($questions.Values | Sort-Object { $_['pillar_id'] }, { $_['question_id'] })
$bpList = @($bestPractices.Values | Sort-Object { $_['pillar_id'] }, { $_['question_id'] }, { $_['bp_id'] })
$pillarCounts = [ordered]@{}
foreach ($pillarId in @($pillars.Keys | Sort-Object)) {
    $pillarCounts[$pillarId] = [ordered]@{
        name = $pillars[$pillarId]
        questions = @($questionList | Where-Object { $_.pillar_id -eq $pillarId }).Count
        best_practices = @($bpList | Where-Object { $_.pillar_id -eq $pillarId }).Count
    }
}

$errors = [System.Collections.Generic.List[string]]::new()
if ($pillars.Count -eq 0 -or $bpList.Count -lt ($pillars.Count * 5)) {
    $errors.Add('Pillar or BP count below sanity floor')
}
foreach ($pillarId in $pillars.Keys) {
    if ($pillarCounts[$pillarId].questions -eq 0 -or $pillarCounts[$pillarId].best_practices -eq 0) {
        $errors.Add("Pillar without questions or BPs: $pillarId")
    }
}
foreach ($bp in $bpList) {
    if ($bp.bp_id -notmatch '^[A-Z]{2,5}\d{2}-BP\d{2}$' -or -not $questions.ContainsKey($bp.question_id) -or -not $pillars.ContainsKey($bp.pillar_id)) {
        $errors.Add("Invalid BP reference: $($bp.bp_id)")
    }
}
foreach ($question in $questionList) {
    if (@($bpList | Where-Object { $_.question_id -eq $question.question_id }).Count -eq 0) {
        $errors.Add("Question without BPs: $($question.question_id)")
    }
}
if (@($bpList | ForEach-Object bp_id | Sort-Object -Unique).Count -ne $bpList.Count) {
    $errors.Add('Duplicate BP IDs')
}

$manifest = [ordered]@{
    schema_version = 'wa-review.corpus.v1'
    valid = ($errors.Count -eq 0)
    source_url = $indexUrl
    retrieved_at = $retrievedAt
    pillar_count = $pillars.Count
    question_count = $questionList.Count
    best_practice_count = $bpList.Count
    pillars = $pillarCounts
    errors = @($errors)
}

New-Item -ItemType Directory -Path (Join-Path $OutputDirectory 'corpus') -Force | Out-Null
$corpusDirectory = Join-Path $OutputDirectory 'corpus'
$bpList | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 5 } | Set-Content -LiteralPath (Join-Path $corpusDirectory 'best-practices.jsonl') -Encoding utf8
$questionList | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 5 } | Set-Content -LiteralPath (Join-Path $corpusDirectory 'questions.jsonl') -Encoding utf8
$manifest | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $corpusDirectory 'manifest.json') -Encoding utf8

$manifest | ConvertTo-Json -Depth 7
"Corpus directory: $corpusDirectory"
if (-not $manifest.valid) {
    exit 1
}
