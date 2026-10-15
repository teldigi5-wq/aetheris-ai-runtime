# Stage 46.14 — disposable native AppContainer lab on hosted Windows CI only.
# No installation, physical microphones, STT/TTS, real files, or external network.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'STAGE46_14_WINDOWS_HOST_REQUIRED' }
if ([string]::IsNullOrEmpty($env:RUNNER_TEMP)) { throw 'STAGE46_14_CI_TEMP_REQUIRED' }
$repo = (Resolve-Path -LiteralPath '.').Path
$source = Join-Path $repo 'workstation-agent\packaging\stage46\WindowsSyntheticAppContainerLab.cpp'
$exe = Join-Path $env:RUNNER_TEMP 'SyntraStage4614AppContainerLab.exe'
$vswhere = Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) { throw 'STAGE46_14_MSVC_TOOLCHAIN_REQUIRED' }
$vs = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or -not $vs) { throw 'STAGE46_14_MSVC_BUILD_TOOLS_REQUIRED' }
$vars = Join-Path $vs 'VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path -LiteralPath $vars -PathType Leaf)) { throw 'STAGE46_14_VCVARS64_REQUIRED' }
# Only fixed CI paths are used; no shell source from a transcript or user text.
$command = 'call "' + $vars + '" >nul && cl /nologo /std:c++17 /EHsc /W4 /MT /DUNICODE /D_UNICODE /Fe:"' +
    $exe + '" "' + $source + '" /link userenv.lib advapi32.lib ws2_32.lib'
& $env:ComSpec /d /s /c $command
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $exe -PathType Leaf)) {
    throw 'STAGE46_14_NATIVE_BUILD_FAILED'
}
Write-Host 'STAGE46_14_FIXED_NATIVE_APP_CONTAINER_LAB_COMPILED'

# SHA-256 here is an immutable-in-this-CI-run check, NOT a signing
# certificate or a vetted source-of-truth release manifest. Verify a
# separately tampered copy fails the comparison and NEVER execute it.
$expected = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
$tampered = "$exe.tampered"
try {
    Copy-Item -LiteralPath $exe -Destination $tampered -ErrorAction Stop
    $stream = [System.IO.File]::Open($tampered,
        [System.IO.FileMode]::Open, [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None)
    try {
        [void]$stream.Seek(0,[System.IO.SeekOrigin]::End)
        $stream.WriteByte(0x58)
    } finally { $stream.Dispose() }
    $tamperedHash = (Get-FileHash -LiteralPath $tampered -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expected -eq $tamperedHash) { throw 'STAGE46_15_TAMPERED_COPY_FALSELY_ACCEPTED' }
} finally {
    Remove-Item -LiteralPath $tampered -Force -ErrorAction SilentlyContinue
}
$authenticodeStatus = (Get-AuthenticodeSignature -LiteralPath $exe).Status.ToString()
$beforeRun = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
if ($beforeRun -ne $expected) { throw 'STAGE46_15_WORKER_CHANGED_BEFORE_EXECUTION' }

# Capture only bounded, non-sensitive synthetic lab markers; no user data.
$observedLines = @(& $exe 2>&1 | ForEach-Object { $_.ToString() })
$labExit = $LASTEXITCODE
$observedLines | ForEach-Object { Write-Host $_ }
$afterRun = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
if ($afterRun -ne $expected) { throw 'STAGE46_15_WORKER_CHANGED_DURING_EXECUTION' }
if ($labExit -ne 0) { throw 'STAGE46_14_APP_CONTAINER_KERNEL_TEST_FAILED' }
$markers = @($observedLines |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_.StartsWith('STAGE46_14_') -or $_.StartsWith('STAGE46_15_') } |
    Sort-Object -Unique)
# The unsigned synthetic fixture is *not* an approved publisher.
Write-Host 'STAGE46_15_SHA256_STABLE_TAMPERED_COPY_REJECTED'
Write-Host ('STAGE46_15_AUTHENTICODE_STATUS_' + $authenticodeStatus)
$evidence = @{
    schema_version = 1
    worker_sha256_before = $beforeRun
    worker_sha256_after = $afterRun
    tampered_fixture_sha256 = $tamperedHash
    authenticode_status = $authenticodeStatus
    appcontainer_lab_exit_code = $labExit
    observed_markers = $markers
}
$receipt = Join-Path $env:RUNNER_TEMP 'syntra-stage46-15-worker-evidence.json'
$evidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $receipt -Encoding utf8
python ./tools/security/stage46_worker_trust_evidence.py --evidence $receipt
if ($LASTEXITCODE -ne 0) { throw 'STAGE46_15_FAIL_CLOSED_WORKER_NETWORK_EVIDENCE' }
Write-Host 'STAGE46_15_SYNTHETIC_LAB_PASS_NOT_PRODUCTION_AUTHORIZATION'
Write-Host 'STAGE46_14_WINDOWS_NATIVE_APP_CONTAINER_LAB_SUCCESS'
Write-Host 'STAGE46_14_PHYSICAL_OWNER_PC_AND_PRODUCTION_NO_GO'
