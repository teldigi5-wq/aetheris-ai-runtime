# Stage 46.16 Windows WFP diagnostic: strictly READ ONLY, fixed native probe.
# Only GitHub-hosted Windows test runners; no owner-PC, audio/model use, or
# WFP filter, firewall policy, registry or network configuration mutation.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'STAGE46_16_WINDOWS_REQUIRED' }
if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    throw 'STAGE46_16_ISOLATED_CI_TEMP_REQUIRED'
}
$repo = (Resolve-Path -LiteralPath '.').Path
$source = Join-Path $repo 'workstation-agent\packaging\stage46\WindowsSyntheticWfpReadOnlyProbe.cpp'
$exe = Join-Path $env:RUNNER_TEMP 'SyntraStage4616WfpReadOnly.exe'
$vswhere = Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) {
    throw 'STAGE46_16_TRUSTED_MSVC_REQUIRED'
}
$vs = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or -not $vs) {
    throw 'STAGE46_16_MSVC_TOOLS_MISSING'
}
$vcvars = Join-Path $vs 'VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path -LiteralPath $vcvars -PathType Leaf)) {
    throw 'STAGE46_16_MSVC_SETUP_MISSING'
}
$compile = 'call "' + $vcvars + '" >nul && cl /nologo /std:c++17 /EHsc /W4 /MT /Fe:"' +
    $exe + '" "' + $source + '" /link fwpuclnt.lib rpcrt4.lib'
& $env:ComSpec /d /s /c $compile
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $exe -PathType Leaf)) {
    throw 'STAGE46_16_FIXED_WFP_PROBE_COMPILATION_FAILED'
}
$expected = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash
$logLines = @(& $exe 2>&1 | ForEach-Object { $_.ToString().Trim() })
$probeExitCode = $LASTEXITCODE
$logLines | ForEach-Object { Write-Host $_ }
$actual = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash
if ($expected -ne $actual) { throw 'STAGE46_16_WFP_PROBE_BINARY_CHANGED' }
$allowed = @(
    'STAGE46_16_WFP_BFE_READ_ONLY_SESSION_OPENED',
    'STAGE46_16_WFP_ALE_AUTH_CONNECT_V4_READ_ONLY_LAYER_PRESENT',
    'STAGE46_16_WFP_ALE_AUTH_CONNECT_V6_READ_ONLY_LAYER_PRESENT',
    'STAGE46_16_WFP_NO_FILTER_OR_FIREWALL_RULES_MODIFIED',
    'STAGE46_16_NO_CORRELATED_BLOCK_EVENT_EGRESS_UNVERIFIED'
)
$markers = @($logLines | Where-Object { $allowed -ccontains $_ } | Sort-Object -Unique)
if ($probeExitCode -ne 0 -or $markers.Count -ne $allowed.Count) {
    throw 'STAGE46_16_WFP_READ_ONLY_LAYERS_NOT_VERIFIED'
}
$previous = Join-Path $env:RUNNER_TEMP 'syntra-stage46-15-worker-evidence.json'
if (-not (Test-Path -LiteralPath $previous -PathType Leaf)) {
    throw 'STAGE46_16_PREVIOUS_WORKER_EVIDENCE_REQUIRED'
}
$oldEvidence = Get-Content -LiteralPath $previous -Raw -Encoding utf8 | ConvertFrom-Json -Depth 12
$evidence = @{
    schema_version = 1
    stage46_15_evidence = $oldEvidence
    wfp_probe_exit_code = $probeExitCode
    wfp_observed_markers = $markers
    # The native fixture remains unsigned; no accepted release certificate.
    publisher_certificate_sha256 = $null
}
$receipt = Join-Path $env:RUNNER_TEMP 'syntra-stage46-16-wfp-provenance.json'
$evidence | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $receipt -Encoding utf8
python ./tools/security/stage46_wfp_provenance_gate.py --evidence $receipt
if ($LASTEXITCODE -ne 0) {
    throw 'STAGE46_16_WFP_PROVENANCE_EVIDENCE_REJECTED'
}
Write-Host 'STAGE46_16_WINDOWS_READ_ONLY_WFP_DIAGNOSTIC_PASS'
Write-Host 'STAGE46_16_NETWORK_DENIAL_AND_SIGNED_RELEASE_NOT_VERIFIED'
Write-Host 'STAGE46_16_OWNER_PC_AND_PRODUCTION_NO_GO'
