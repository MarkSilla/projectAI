param(
    [string]$OutputPath = "app/src/main/assets/models/model.gguf",
    [string]$ModelUrl = "https://huggingface.co/ggml-org/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf"
)

$ErrorActionPreference = "Stop"

$resolvedOutput = Resolve-Path (Join-Path (Get-Location) $OutputPath) -ErrorAction SilentlyContinue
if (-not $resolvedOutput) {
    $dir = Split-Path (Join-Path (Get-Location) $OutputPath) -Parent
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $resolvedOutput = (Join-Path (Get-Location) $OutputPath)
}

Write-Host "Downloading GGUF model from: $ModelUrl"
Write-Host "Saving to: $resolvedOutput"

try {
    Invoke-WebRequest -Uri $ModelUrl -OutFile $resolvedOutput
    Write-Host "Download complete."
    Get-Item $resolvedOutput | Select-Object FullName, Length
} catch {
    Write-Error "Download failed. The environment may require a different Hugging Face mirror or a valid authenticated URL."
    throw
}
