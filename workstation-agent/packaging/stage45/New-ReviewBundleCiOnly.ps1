[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$SourceJar,
    [Parameter(Mandatory=$true)][string]$ReviewDirectory
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

if ($env:GITHUB_ACTIONS -cne 'true' -or
    $env:AETHERIS_STAGE45_WINDOWS_LAB -cne 'true' -or
    [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    throw 'CI_ONLY_REVIEW_BUNDLE_GENERATOR'
}
$runner=[IO.Path]::GetFullPath($env:RUNNER_TEMP).TrimEnd([IO.Path]::DirectorySeparatorChar)
$target=[IO.Path]::GetFullPath($ReviewDirectory)
$prefix=$runner+[IO.Path]::DirectorySeparatorChar
if (-not $target.StartsWith($prefix,[StringComparison]::Ordinal) -or
    $target -ceq $runner -or (Test-Path -LiteralPath $target)) {
    throw 'REVIEW_OUTPUT_MUST_BE_NEW_AND_UNDER_CI_TEMP'
}
$source=[IO.Path]::GetFullPath($SourceJar)
if (-not (Test-Path -LiteralPath $source -PathType Leaf) -or
    (Get-Item -LiteralPath $source -Force).Attributes.HasFlag(
        [IO.FileAttributes]::ReparsePoint)) {
    throw 'REVIEW_BUILD_JAR_NOT_REGULAR'
}
$length=(Get-Item -LiteralPath $source -Force).Length
if ($length -le 0 -or $length -gt 268435456) {
    throw 'REVIEW_BUILD_JAR_SIZE_DENIED'
}
New-Item -ItemType Directory -Path $target -ErrorAction Stop | Out-Null
$destination=Join-Path $target 'aetheris-host-agent.jar'
Copy-Item -LiteralPath $source -Destination $destination -ErrorAction Stop
$hash=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest=[ordered]@{
    schema='aetheris:stage45:offline-review:v1'
    artifact='aetheris-host-agent.jar'
    sha256=$hash
    bytes=[long](Get-Item -LiteralPath $destination).Length
    target='windows-x64'
    offlineRequired=$true
    reviewOnly=$true
    activationAllowed=$false
    autostartAllowed=$false
    networkAccessAllowed=$false
    shellAllowed=$false
    desktopActionsAllowed=$false
    wslExecutionAllowed=$false
    productionApproved=$false
    signatureVerified=$false
}
$manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $target 'offline-review.json') -Encoding utf8NoBOM
# Self-computed CI hash is evidence of local file consistency ONLY, not
# independent integrity, release-signature or provenance certification.
Write-Host 'STAGE45_CI_ONLY_REVIEW_BUNDLE_PREPARED'
