Set-StrictMode -Version Latest

# Stage 45.5.17: offline REVIEW policy only, no installation primitives.
# The expected SHA-256 must come from a separately trusted owner-approved
# source. A manifest packaged beside a jar is NOT independent provenance.
function Assert-Stage45Manifest {
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Manifest)
    $required = @('schema','artifact','sha256','bytes','target',
        'offlineRequired','reviewOnly','activationAllowed','autostartAllowed',
        'networkAccessAllowed','shellAllowed','desktopActionsAllowed',
        'wslExecutionAllowed','productionApproved','signatureVerified')
    if ($Manifest.Count -ne $required.Count) {
        throw 'MANIFEST_SCHEMA_FIELDS_DENIED'
    }
    foreach ($field in $required) {
        if (-not $Manifest.Contains($field)) {
            throw 'MANIFEST_REQUIRED_FIELD_MISSING'
        }
    }
    if ($Manifest['schema'] -cne 'aetheris:stage45:offline-review:v1' -or
        $Manifest['artifact'] -cne 'aetheris-host-agent.jar' -or
        $Manifest['target'] -cne 'windows-x64' -or
        $Manifest['sha256'] -cnotmatch '^[0-9a-f]{64}$') {
        throw 'MANIFEST_IDENTITY_DENIED'
    }
    if ($Manifest['bytes'] -isnot [long] -and
        $Manifest['bytes'] -isnot [int] -and
        $Manifest['bytes'] -isnot [bigint]) {
        throw 'MANIFEST_ARTIFACT_LENGTH_INVALID'
    }
    if ($Manifest['bytes'] -le 0 -or $Manifest['bytes'] -gt 268435456) {
        throw 'MANIFEST_ARTIFACT_LENGTH_DENIED'
    }
    $requiredTrue = @('offlineRequired','reviewOnly')
    $requiredFalse = @('activationAllowed','autostartAllowed',
        'networkAccessAllowed','shellAllowed','desktopActionsAllowed',
        'wslExecutionAllowed','productionApproved','signatureVerified')
    foreach ($flag in $requiredTrue) {
        if ($Manifest[$flag] -isnot [bool] -or $Manifest[$flag] -ne $true) {
            throw 'MANIFEST_OFFLINE_REVIEW_POLICY_DENIED'
        }
    }
    foreach ($flag in $requiredFalse) {
        if ($Manifest[$flag] -isnot [bool] -or $Manifest[$flag] -ne $false) {
            throw 'MANIFEST_ACTIVATION_POLICY_DENIED'
        }
    }
}

function Assert-Stage45PathNoReparse {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)
    $full=[System.IO.Path]::GetFullPath($Path)
    $cursor=$full
    while (-not [string]::IsNullOrEmpty($cursor)) {
        if (Test-Path -LiteralPath $cursor) {
            $item=Get-Item -LiteralPath $cursor -Force -ErrorAction Stop
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'PATH_REPARSE_ANCESTOR_DENIED'
            }
        }
        $parent=[System.IO.Path]::GetDirectoryName($cursor)
        if ([string]::IsNullOrEmpty($parent) -or
            [string]::Equals($parent,$cursor,[StringComparison]::OrdinalIgnoreCase)) {
            break
        }
        $cursor=$parent
    }
    return $full
}

function Assert-Stage45WorkspaceRoot {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root)
    $full=Assert-Stage45PathNoReparse -Path $Root
    if (-not (Test-Path -LiteralPath $full -PathType Container)) {
        throw 'WORKSPACE_DIRECTORY_MISSING'
    }
    $trimmed=[System.IO.Path]::TrimEndingDirectorySeparator($full)
    $filesystemRoot=[System.IO.Path]::TrimEndingDirectorySeparator(
        [System.IO.Path]::GetPathRoot($full))
    if ($trimmed -ieq $filesystemRoot) {throw 'WORKSPACE_FILESYSTEM_ROOT_DENIED'}
    $blocked=@($HOME,$env:USERPROFILE,$env:WINDIR,$env:ProgramFiles,
        $env:ProgramData) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach($reserved in $blocked) {
        $normalized=[System.IO.Path]::TrimEndingDirectorySeparator(
            [System.IO.Path]::GetFullPath($reserved))
        if ($trimmed -ieq $normalized) {throw 'WORKSPACE_BROAD_SCOPE_DENIED'}
    }
    return $full
}

function Invoke-Stage45OfflineReview {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$BundleDirectory,
        [Parameter(Mandatory)][string]$ExpectedSha256,
        [Parameter(Mandatory)][string[]]$WorkspaceRoots
    )
    if ($ExpectedSha256 -cnotmatch '^[0-9a-f]{64}$') {
        throw 'INDEPENDENT_SHA256_REQUIRED'
    }
    $bundle=Assert-Stage45PathNoReparse -Path $BundleDirectory
    if (-not (Test-Path -LiteralPath $bundle -PathType Container)) {
        throw 'REVIEW_BUNDLE_MISSING'
    }
    if ($WorkspaceRoots.Count -lt 1 -or $WorkspaceRoots.Count -gt 4) {
        throw 'WORKSPACE_GRANT_COUNT_DENIED'
    }
    $scopes=@()
    foreach($root in $WorkspaceRoots) {
        $scopes+=Assert-Stage45WorkspaceRoot -Root $root
    }
    if (@($scopes | Select-Object -Unique).Count -ne @($scopes).Count) {
        throw 'WORKSPACE_DUPLICATE_ROOT_DENIED'
    }
    $manifestPath=Assert-Stage45PathNoReparse -Path (Join-Path $bundle 'offline-review.json')
    $jarPath=Assert-Stage45PathNoReparse -Path (Join-Path $bundle 'aetheris-host-agent.jar')
    foreach($path in @($manifestPath,$jarPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw 'REVIEW_ARTIFACT_MISSING'
        }
    }
    $manifestFile=Get-Item -LiteralPath $manifestPath -Force
    $jarFile=Get-Item -LiteralPath $jarPath -Force
    if ($manifestFile.Length -gt 4096 -or $manifestFile.Length -lt 100) {
        throw 'MANIFEST_SIZE_DENIED'
    }
    if ($jarFile.Length -gt 268435456 -or $jarFile.Length -lt 1) {
        throw 'JAR_SIZE_DENIED'
    }
    $json=[System.IO.File]::ReadAllText($manifestPath,[Text.Encoding]::UTF8)
    $manifest=ConvertFrom-Json -InputObject $json -AsHashtable -ErrorAction Stop
    if ($manifest -isnot [System.Collections.IDictionary]) {
        throw 'MANIFEST_SHAPE_DENIED'
    }
    Assert-Stage45Manifest -Manifest $manifest
    if ($manifest['bytes'] -ne $jarFile.Length) {
        throw 'MANIFEST_JAR_SIZE_MISMATCH'
    }
    $actual=(Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -cne $ExpectedSha256 -or $actual -cne $manifest['sha256']) {
        throw 'UNTRUSTED_JAR_DIGEST_MISMATCH'
    }
    # Explicitly no claims about detached code signature / Windows
    # Authenticode / device TPM / DPAPI / WSL / live owner approval.
    return [pscustomobject]@{
        status='REVIEW_ONLY_NO_ACTIVATION'
        artifactSha256=$actual
        artifactBytes=[long]$jarFile.Length
        allowedWorkspaceCount=$scopes.Count
        offlineRequired=$true
        activationAllowed=$false
        productionApproved=$false
        operatorAction='INDEPENDENT_SIGNATURE_AND_TARGET_WINDOWS_REVIEW_REQUIRED'
    }
}

Export-ModuleMember -Function Assert-Stage45Manifest,Assert-Stage45PathNoReparse,Assert-Stage45WorkspaceRoot,Invoke-Stage45OfflineReview
