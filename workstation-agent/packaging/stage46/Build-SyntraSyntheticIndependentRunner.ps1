# Stage 46.19 fixed fixture compiled once per dedicated hosted Windows runner.
# No third-party API tokens, signing keys, speaker/mic, model, firewall changes,
# artifact uploads, deployments or owner hardware. The jobs A and B are separate
# Windows GitHub-hosted machines, but share the same vendor/toolchain trust base.
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('A','B')]
    [string]$Builder
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows -or [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP) -or
    [string]::IsNullOrWhiteSpace($env:GITHUB_OUTPUT)) {
    throw 'STAGE46_19_DEDICATED_WINDOWS_CI_REQUIRED'
}
$source = (Resolve-Path -LiteralPath './workstation-agent/packaging/stage46/WindowsSyntheticReproducibleWorkerProbe.cpp').Path
$root = Join-Path $env:RUNNER_TEMP 'syntra-stage46-19-independent-build'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$exe = Join-Path $root 'synthetic-worker.exe'
$obj = Join-Path $root 'synthetic-worker.obj'
$vswhere = Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) {
    throw 'STAGE46_19_MSVC_REQUIRED'
}
$vs = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or -not $vs) { throw 'STAGE46_19_MSVC_TOOLCHAIN_UNAVAILABLE' }
$vcvars = Join-Path $vs 'VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path -LiteralPath $vcvars -PathType Leaf)) {
    throw 'STAGE46_19_MSVC_ENVIRONMENT_MISSING'
}

# Same fixed source and compile command as Stage 46.18, each on a distinct
# Windows runner. No output binary leaves the ephemeral CI machine.
$command = 'call "' + $vcvars + '" >nul && cl /nologo /std:c++17 /O2 /MT /GS /W4 /Brepro /Fo:"' +
    $obj + '" /Fe:"' + $exe + '" "' + $source + '" /link /INCREMENTAL:NO /Brepro'
& $env:ComSpec /d /s /c $command
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $exe -PathType Leaf)) {
    throw 'STAGE46_19_INERT_NATIVE_COMPILE_FAILED'
}

$sourceSha = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
$shaBefore = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
$markers = @(& $exe --self-test | ForEach-Object { $_.ToString().Trim() })
$exit = $LASTEXITCODE
if ($exit -ne 0 -or $markers.Count -ne 1 -or
    $markers[0] -cne 'STAGE46_18_FIXED_SYNTHETIC_ARTIFACT_SELFTEST_ONLY') {
    throw 'STAGE46_19_FIXED_WORKER_SELFTEST_FAILED'
}
$shaAfter = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
if ($shaBefore -ne $shaAfter) {
    throw 'STAGE46_19_NATIVE_ARTIFACT_CHANGED_DURING_TEST'
}
$signingStatus = (Get-AuthenticodeSignature -LiteralPath $exe).Status.ToString()
if ($signingStatus -cne 'NotSigned') {
    throw 'STAGE46_19_UNEXPECTEDLY_SIGNED_FIXTURE_CANNOT_BE_APPROVED'
}
# Github Actions outputs are short hashes only; no GitHub token, local path,
# user identity or binary content is leaked to another job.
Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value ('digest=' + $shaAfter) -Encoding utf8
Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value ('source_digest=' + $sourceSha) -Encoding utf8
Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value ('signature=' + $signingStatus) -Encoding utf8
Write-Host ('STAGE46_19_WINDOWS_BUILDER_'+$Builder+'_UNSIGNED_ARTIFACT_HASH_ONLY')
Write-Host 'STAGE46_19_NO_INDEPENDENT_SIGNER_OR_PRODUCTION_ARTIFACT'
