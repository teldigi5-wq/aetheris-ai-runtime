# Stage 46.17 fixed synthetic Windows WFP read-only drop-snapshot.
# Must run on GitHub-hosted Windows CI only. No packet capture, logging
# enablement, firewall modification, secrets or user network identifiers.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows -or [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    throw 'STAGE46_17_WINDOWS_DISPOSABLE_CI_ONLY'
}
$repo = (Resolve-Path -LiteralPath '.').Path
$source = Join-Path $repo 'workstation-agent\packaging\stage46\WindowsSyntheticWfpDropAuditReadOnly.cpp'
$exe = Join-Path $env:RUNNER_TEMP 'SyntraStage4617WfpReadOnlySnapshot.exe'
$vswhere = Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) {
    throw 'STAGE46_17_TRUSTED_MSVC_REQUIRED'
}
$vs = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or -not $vs) {
    throw 'STAGE46_17_MSVC_TOOLS_MISSING'
}
$vcvars = Join-Path $vs 'VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path -LiteralPath $vcvars -PathType Leaf)) { throw 'STAGE46_17_MSVC_SETUP_MISSING' }
$build = 'call "' + $vcvars + '" >nul && cl /nologo /std:c++17 /EHsc /W4 /MT /Fe:"' +
    $exe + '" "' + $source + '" /link fwpuclnt.lib rpcrt4.lib'
& $env:ComSpec /d /s /c $build
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $exe -PathType Leaf)) {
    throw 'STAGE46_17_NATIVE_SNAPSHOT_COMPILE_FAILED'
}
$before = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash
$log = @(& $exe 2>&1 | ForEach-Object { $_.ToString().Trim() })
$exit = $LASTEXITCODE
$log | ForEach-Object { Write-Host $_ }
$after = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash
if ($exit -ne 0 -or $before -ne $after) {
    throw 'STAGE46_17_SNAPSHOT_BINARY_CHANGED_OR_FAILED'
}
$allowed = @(
    'STAGE46_17_WFP_EVENT_LOGGING_DISABLED',
    'STAGE46_17_WFP_SNAPSHOT_UNAVAILABLE',
    'STAGE46_17_WFP_SNAPSHOT_EMPTY',
    'STAGE46_17_WFP_UNCORRELATED_DROP_SNAPSHOT_PRESENT',
    'STAGE46_17_WFP_PROCESS_CORRELATION_NOT_VERIFIED',
    'STAGE46_17_NO_WFP_FILTER_OR_AUDIT_CONFIGURATION_CHANGED'
)
$markers = @($log | Where-Object { $allowed -ccontains $_ } | Sort-Object -Unique)
if ($markers.Count -ne 3 -or
    -not ($markers -ccontains 'STAGE46_17_WFP_PROCESS_CORRELATION_NOT_VERIFIED') -or
    -not ($markers -ccontains 'STAGE46_17_NO_WFP_FILTER_OR_AUDIT_CONFIGURATION_CHANGED')) {
    throw 'STAGE46_17_INCOMPLETE_OR_AMBIGUOUS_WFP_SNAPSHOT'
}
$earlier = Join-Path $env:RUNNER_TEMP 'syntra-stage46-16-wfp-provenance.json'
if (-not (Test-Path -LiteralPath $earlier -PathType Leaf)) {
    throw 'STAGE46_17_PREVIOUS_WFP_PROVENANCE_RECEIPT_REQUIRED'
}
$parent = Get-Content -LiteralPath $earlier -Raw -Encoding utf8 | ConvertFrom-Json -Depth 16
$receipt = @{
    schema_version = 1
    stage46_16_evidence = $parent
    wfp_drop_probe_exit_code = $exit
    wfp_drop_observed_markers = $markers
}
$receiptPath = Join-Path $env:RUNNER_TEMP 'syntra-stage46-17-readonly-drop-receipt.json'
$receipt | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $receiptPath -Encoding utf8
python ./tools/security/stage46_wfp_drop_audit_gate.py --evidence $receiptPath --release-spec ./docs/runtime/stage46-trusted-signed-worker-release-spec.json
if ($LASTEXITCODE -ne 0) { throw 'STAGE46_17_UNTRUSTED_WFP_DROP_RECEIPT_REJECTED' }
Write-Host 'STAGE46_17_READ_ONLY_DROP_SNAPSHOT_AND_RELEASE_SPEC_PASS'
Write-Host 'STAGE46_17_APP_SPECIFIC_WFP_BLOCK_UNVERIFIED'
Write-Host 'STAGE46_17_UNSIGNED_WORKER_AND_OWNER_PC_RELEASE_NO_GO'
