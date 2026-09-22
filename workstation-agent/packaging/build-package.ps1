[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\dist'),
    [string]$DetachedSignatureFile = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$moduleRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dist = [System.IO.Path]::GetFullPath($OutputDirectory)

Push-Location $moduleRoot
try {
    & mvn -B clean test package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build/test/package failed.' }
} finally { Pop-Location }

if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Path $dist -Force | Out-Null

$jar = Join-Path $moduleRoot 'target\aetheris-host-agent.jar'
if (-not (Test-Path $jar -PathType Leaf)) { throw 'Expected shaded host-agent jar was not produced.' }
Copy-Item $jar (Join-Path $dist 'aetheris-host-agent.jar')
Copy-Item (Join-Path $PSScriptRoot 'install.ps1') (Join-Path $dist 'install.ps1')
Copy-Item (Join-Path $PSScriptRoot 'uninstall.ps1') (Join-Path $dist 'uninstall.ps1')

$signatureProvided = $false
if (-not [string]::IsNullOrWhiteSpace($DetachedSignatureFile)) {
    $sig = [System.IO.Path]::GetFullPath($DetachedSignatureFile)
    if (-not (Test-Path $sig -PathType Leaf)) { throw "Detached signature file does not exist: $sig" }
    Copy-Item $sig (Join-Path $dist 'signature.txt')
    $signatureProvided = $true
}

$manifest = [ordered]@{
    package = 'AetherisHostAgent'
    stage = 11
    version = '0.1.0-SNAPSHOT'
    builtAtUtc = [DateTime]::UtcNow.ToString('o')
    runtime = 'Java 21+'
    platform = 'Windows target / CI-portable tests'
    loopbackOnly = $true
    shellCapability = $false
    requiresAdminAtRuntime = $false
    detachedSignatureProvided = $signatureProvided
    signatureVerified = $false
    productionActivationAllowed = $false
    activationGate = 'VERIFY_SIGNATURE_PROVENANCE_REGRESSION_ROLLBACK_ON_TARGET'
    liveMoneyEnabled = $false
    withdrawalsEnabled = $false
    transfersEnabled = $false
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $dist 'manifest.json') -Encoding UTF8

$filesToHash = Get-ChildItem $dist -File | Where-Object { $_.Name -ne 'checksums.sha256' } | Sort-Object Name
$checksumLines = foreach ($file in $filesToHash) {
    $hash = (Get-FileHash $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($file.Name)"
}
[IO.File]::WriteAllLines((Join-Path $dist 'checksums.sha256'), $checksumLines, [Text.Encoding]::ASCII)

Write-Host "Stage 11 host-agent package created at $dist"
Write-Warning 'Package activation remains blocked by policy. A signature file being present is not the same as a verified signature; Stage 11 still requires signature, provenance, regression, rollback and target-host gates before production activation.'
