[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$BundleDirectory,
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$ExpectedSha256,
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string[]]$WorkspaceRoots,
    [switch]$SyntheticCI
)

$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

try {
    if ($PSVersionTable.PSVersion.Major -lt 7) {
        throw 'POWERSHELL_7_REQUIRED_FOR_READONLY_REVIEW'
    }
    $runningOnWindows=[System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
        [System.Runtime.InteropServices.OSPlatform]::Windows)
    if ($SyntheticCI) {
        if ($env:GITHUB_ACTIONS -cne 'true' -or
            $env:AETHERIS_STAGE45_WINDOWS_LAB -cne 'true') {
            throw 'SYNTHETIC_CI_NOT_AUTHORIZED'
        }
    } elseif (-not $runningOnWindows) {
        throw 'TARGET_READINESS_REQUIRES_WINDOWS'
    }

    Import-Module (Join-Path $PSScriptRoot 'OfflineReadinessPolicy.psm1') -Force
    $review=Invoke-Stage45OfflineReview -BundleDirectory $BundleDirectory -ExpectedSha256 $ExpectedSha256 -WorkspaceRoots $WorkspaceRoots

    if (-not $SyntheticCI) {
        if ($env:USERNAME -match '^(SYSTEM|LOCAL SERVICE|NETWORK SERVICE)$') {
            throw 'CURRENT_USER_SESSION_REQUIRED'
        }
        # This is an observation, not a Windows installer or a consent flow.
        # PowerShell 7 and Java 21 are prerequisites for a subsequent
        # separately owner-approved target-PC verification.
        $java=Get-Command java.exe -ErrorAction SilentlyContinue
        if ($null -eq $java) {throw 'JAVA_NOT_FOUND_ON_PATH'}
        $versionLine=(& $java.Source -version 2>&1 | Select-Object -First 1) -join ''
        if ($versionLine -notmatch '"(\d+)') {throw 'JAVA_VERSION_UNVERIFIED'}
        if ([int]$Matches[1] -lt 21) {throw 'JAVA_21_REQUIRED'}
    }

    $output=[ordered]@{
        schema='aetheris:stage45:readiness-result:v1'
        status=$review.status
        platformEvidence=$(if ($SyntheticCI) {'CI_SYNTHETIC_NOT_WINDOWS_CERTIFIED'} else {'WINDOWS_READONLY_LOCAL_CHECK'})
        verifiedArtifactSha256=$review.artifactSha256
        artifactBytes=$review.artifactBytes
        workspaceCount=$review.allowedWorkspaceCount
        offlineRequired=$true
        installed=$false
        activated=$false
        daemonStarted=$false
        startupShortcutCreated=$false
        wslInvoked=$false
        ownerApprovalVerified=$false
        releaseAuthorized=$false
        nextGate='INDEPENDENT_SIGNATURE_AND_MANUAL_WINDOWS_SECURITY_REVIEW'
    }
    $output | ConvertTo-Json -Compress -Depth 3
    exit 0
} catch {
    $code=($_.Exception.Message -split '[\r\n]')[0]
    # Never echo paths, local environment data, credentials, or stack traces.
    if ($code -notmatch '^[A-Z][A-Z0-9_]{5,96}$') {$code='READINESS_CHECK_FAILED'}
    ([ordered]@{
        schema='aetheris:stage45:readiness-result:v1'
        status='DENIED'
        reason=$code
        installed=$false
        activated=$false
        releaseAuthorized=$false
    } | ConvertTo-Json -Compress -Depth 3)
    exit 1
}
