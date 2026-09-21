<#
.SYNOPSIS
Generates a WAV narration from a UTF-8 text file using a Brazilian Portuguese SAPI voice.

.EXAMPLE
.\scripts\generate-interview-audio.ps1 -InputPath .\roteiro.txt

.EXAMPLE
.\scripts\generate-interview-audio.ps1 -InputPath .\roteiro.txt -OutputPath .\artefatos\roteiro.wav
#>
param(
    [Parameter(Mandatory)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$InputPath,
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $resolvedOutput = [IO.Path]::ChangeExtension($resolvedInput, ".wav")
}
else {
    $resolvedOutput = [IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
}
$text = Get-Content -LiteralPath $resolvedInput -Raw -Encoding UTF8

$voice = New-Object -ComObject SAPI.SpVoice
$stream = New-Object -ComObject SAPI.SpFileStream
$format = New-Object -ComObject SAPI.SpAudioFormat
try {
    $portugueseVoice = $voice.GetVoices() |
        Where-Object { $_.GetDescription() -match "Portuguese|Português|Brazil|Brasil" } |
        Select-Object -First 1
    if ($null -eq $portugueseVoice) {
        throw "No Brazilian Portuguese SAPI voice is available."
    }

    $voice.Voice = $portugueseVoice
    $voice.Rate = -1
    $voice.Volume = 100
    $format.Type = 22
    $stream.Format = $format
    $stream.Open($resolvedOutput, 3, $false)
    $voice.AudioOutputStream = $stream
    [void]$voice.Speak($text)
}
finally {
    if ($null -ne $stream) {
        $stream.Close()
    }
}

Get-Item -LiteralPath $resolvedOutput | Select-Object FullName, Length, LastWriteTime
