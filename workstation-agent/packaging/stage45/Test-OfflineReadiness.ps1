[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

if ($env:GITHUB_ACTIONS -cne 'true' -or
    $env:AETHERIS_STAGE45_WINDOWS_LAB -cne 'true' -or
    [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    throw 'Stage 45.5.17 test fixture is restricted to disposable GitHub Actions'
}

Import-Module (Join-Path $PSScriptRoot 'OfflineReadinessPolicy.psm1') -Force
$caseRoot=Join-Path $env:RUNNER_TEMP ("aetheris-stage45-"+[guid]::NewGuid().ToString('N'))
$bundle=Join-Path $caseRoot 'bundle'
$root=Join-Path $caseRoot 'approved-workspace'
$outside=Join-Path $caseRoot 'missing'
$manifestFile=Join-Path $bundle 'offline-review.json'
$jarFile=Join-Path $bundle 'aetheris-host-agent.jar'
$scriptFile=Join-Path $PSScriptRoot 'Invoke-OfflineReadiness.ps1'
$success=0

function Expect-Denied([scriptblock]$Action,[string]$Label) {
    $wasDenied=$false
    try {& $Action | Out-Null}
    catch {$wasDenied=$true}
    if (-not $wasDenied) {throw "Expected denial: $Label"}
    $script:success++
}
function Require([bool]$Condition,[string]$Label) {
    if (-not $Condition) {throw "Failed assertion: $Label"}
    $script:success++
}

try {
    New-Item -ItemType Directory -Path $bundle -Force | Out-Null
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    [IO.File]::WriteAllBytes($jarFile,[Text.Encoding]::UTF8.GetBytes('CI synthetic jar bytes - never executable'))
    $digest=(Get-FileHash -LiteralPath $jarFile -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest=[ordered]@{
        schema='aetheris:stage45:offline-review:v1'
        artifact='aetheris-host-agent.jar'
        sha256=$digest
        bytes=[long](Get-Item $jarFile).Length
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
    $manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $manifestFile -Encoding utf8NoBOM

    $observed=Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots @($root)
    Require ($observed.status -eq 'REVIEW_ONLY_NO_ACTIVATION') 'review-only status'
    Require (-not $observed.activationAllowed) 'activation remains forbidden'
    Require (-not $observed.productionApproved) 'production remains forbidden'

    $before=(Get-FileHash -LiteralPath $jarFile).Hash
    $response=(& pwsh -NoProfile -NonInteractive -File $scriptFile -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots $root -SyntheticCI | ConvertFrom-Json)
    Require ($LASTEXITCODE -eq 0) 'synthetic CLI readiness exits zero'
    Require ($response.platformEvidence -eq 'CI_SYNTHETIC_NOT_WINDOWS_CERTIFIED') 'no false physical certification'
    Require (-not $response.installed -and -not $response.daemonStarted -and -not $response.startupShortcutCreated -and -not $response.wslInvoked) 'no installation or actions'
    Require ((Get-FileHash -LiteralPath $jarFile).Hash -eq $before) 'review did not change artifact'

    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 ('a'*64) -WorkspaceRoots @($root)} 'independent digest mismatch'
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 'malformed' -WorkspaceRoots @($root)} 'missing trusted digest'
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots @($outside)} 'nonexistent grant'
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots @([IO.Path]::GetPathRoot($root))} 'filesystem root grant'

    $manifest.activationAllowed=$true
    $manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $manifestFile -Encoding utf8NoBOM
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots @($root)} 'activation flag enabled'
    $manifest.activationAllowed=$false

    $manifest.shellAllowed=$true
    $manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $manifestFile -Encoding utf8NoBOM
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots @($root)} 'shell permitted'
    $manifest.shellAllowed=$false

    $manifest.signatureVerified=$true
    $manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $manifestFile -Encoding utf8NoBOM
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots @($root)} 'unsubstantiated signature verification'
    $manifest.signatureVerified=$false
    $manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $manifestFile -Encoding utf8NoBOM

    $source=[IO.File]::ReadAllBytes($jarFile)
    [IO.File]::WriteAllBytes($jarFile,([byte[]]($source + @(0x41))))
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots @($root)} 'jar mutation'
    [IO.File]::WriteAllBytes($jarFile,$source)

    $link=Join-Path $caseRoot 'linked-bundle'
    New-Item -ItemType SymbolicLink -Path $link -Target $bundle -ErrorAction Stop | Out-Null
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $link -ExpectedSha256 $digest -WorkspaceRoots @($root)} 'symlink bundle'
    $rootLink=Join-Path $caseRoot 'linked-workspace'
    New-Item -ItemType SymbolicLink -Path $rootLink -Target $root -ErrorAction Stop | Out-Null
    Expect-Denied {Invoke-Stage45OfflineReview -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots @($rootLink)} 'linked workspace'

    # Verify the CLI never accepts the CI bypass when the exact
    # CI-only environment gate is absent.
    $saved=$env:AETHERIS_STAGE45_WINDOWS_LAB
    try {
        Remove-Item Env:AETHERIS_STAGE45_WINDOWS_LAB
        $rejected=(& pwsh -NoProfile -NonInteractive -File $scriptFile -BundleDirectory $bundle -ExpectedSha256 $digest -WorkspaceRoots $root -SyntheticCI | ConvertFrom-Json)
        Require ($LASTEXITCODE -ne 0 -and $rejected.status -eq 'DENIED') 'CI bypass rejected without environment'
    } finally {
        $env:AETHERIS_STAGE45_WINDOWS_LAB=$saved
    }

    Write-Host "STAGE45_READONLY_PREFLIGHT_PASS assertions=$success"
} finally {
    Remove-Item -LiteralPath $caseRoot -Recurse -Force -ErrorAction SilentlyContinue
}
