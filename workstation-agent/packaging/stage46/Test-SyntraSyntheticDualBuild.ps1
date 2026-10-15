# Stage 46.18: two clean MSVC builds of a tiny fixed synthetic fixture.
# Only on disposable GitHub-hosted Windows CI; not an install or release.
# In-run byte-for-byte identity does NOT establish publisher provenance.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows -or [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    throw 'STAGE46_18_WINDOWS_CI_REQUIRED'
}
$repo = (Resolve-Path -LiteralPath '.').Path
$source = Join-Path $repo 'workstation-agent\packaging\stage46\WindowsSyntheticReproducibleWorkerProbe.cpp'
$root = Join-Path $env:RUNNER_TEMP 'syntra-stage46-18-dual-build'
$a = Join-Path $root 'a'
$b = Join-Path $root 'b'
New-Item -ItemType Directory -Force -Path $a, $b | Out-Null
$exeA = Join-Path $a 'synthetic-worker.exe'
$exeB = Join-Path $b 'synthetic-worker.exe'
$objA = Join-Path $a 'synthetic-worker.obj'
$objB = Join-Path $b 'synthetic-worker.obj'
$vswhere = Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) { throw 'MSVC_REQUIRED' }
$vs = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if ($LASTEXITCODE -ne 0 -or -not $vs) { throw 'MSVC_BUILD_TOOLS_REQUIRED' }
$vcvars = Join-Path $vs 'VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path -LiteralPath $vcvars -PathType Leaf)) { throw 'MSVC_ENV_SCRIPT_REQUIRED' }

function Build-FixedFixture {
    param([string]$Exe, [string]$Obj)
    # /Brepro passed at compile AND link. Do not add PDB/debug paths or
    # machine-specific timestamps. Both builds use the same source/toolchain.
    $command = 'call "' + $vcvars + '" >nul && cl /nologo /std:c++17 /O2 /MT /GS /W4 /Brepro /Fo:"' +
        $Obj + '" /Fe:"' + $Exe + '" "' + $source + '" /link /INCREMENTAL:NO /Brepro'
    & $env:ComSpec /d /s /c $command
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $Exe -PathType Leaf)) {
        throw 'STAGE46_18_FIXED_SYNTHETIC_COMPILATION_FAILED'
    }
}
Build-FixedFixture -Exe $exeA -Obj $objA
Build-FixedFixture -Exe $exeB -Obj $objB

$hashA = (Get-FileHash -LiteralPath $exeA -Algorithm SHA256).Hash.ToLowerInvariant()
$hashB = (Get-FileHash -LiteralPath $exeB -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hashA -ne $hashB) { throw 'STAGE46_18_NOT_REPRODUCIBLE_ON_SAME_RUNNER' }
Write-Host 'STAGE46_18_SAME_RUNNER_TWO_BUILDS_BYTE_IDENTICAL'

# Only the fixed inert fixture self-test is executed, never arbitrary code.
$output = @(& $exeA --self-test | ForEach-Object { $_.ToString().Trim() })
$exit = $LASTEXITCODE
if ($exit -ne 0 -or $output.Count -ne 1 -or
    $output[0] -cne 'STAGE46_18_FIXED_SYNTHETIC_ARTIFACT_SELFTEST_ONLY') {
    throw 'STAGE46_18_SYNTHETIC_SELF_TEST_FAILED'
}

$altered = Join-Path $root 'tampered-not-executable.test-only'
try {
    Copy-Item -LiteralPath $exeA -Destination $altered -ErrorAction Stop
    $stream = [System.IO.File]::Open($altered, [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try {
        [void]$stream.Seek(0, [System.IO.SeekOrigin]::End)
        $stream.WriteByte(0x51)
    } finally { $stream.Dispose() }
    $hashAltered = (Get-FileHash -LiteralPath $altered -Algorithm SHA256).Hash.ToLowerInvariant()
} finally {
    Remove-Item -LiteralPath $altered -Force -ErrorAction SilentlyContinue
}
if ($hashA -eq $hashAltered) { throw 'STAGE46_18_TAMPER_COPY_FALSELY_ACCEPTED' }
if ((Get-FileHash -LiteralPath $exeA -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hashA) {
    throw 'STAGE46_18_ORIGINAL_EXECUTABLE_MUTATED'
}
$signatureStatus = (Get-AuthenticodeSignature -LiteralPath $exeA).Status.ToString()
if ($signatureStatus -cne 'NotSigned') {
    throw 'STAGE46_18_FIXTURE_SIGNATURE_UNEXPECTED_RELEASE_NOT_AUTHORIZED'
}
Write-Host 'STAGE46_18_UNSIGNED_FIXTURE_REMAINS_UNTRUSTED'

$receipt = @{
    schema_version = 1
    fixture_kind = 'FIXED_UNSIGNED_SYNTHETIC_CI_ONLY'
    build_a_sha256 = $hashA
    build_b_sha256 = $hashB
    mutated_copy_sha256 = $hashAltered
    build_a_exit_code = 0
    build_b_exit_code = 0
    self_test_exit_code = $exit
    authenticode_status = $signatureStatus
    approved_publisher_certificate_sha256 = $null
    independently_approved_artifact_sha256 = $null
    signed_release_manifest = $false
}
$receiptPath = Join-Path $env:RUNNER_TEMP 'syntra-stage46-18-dual-build-receipt.json'
$receipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $receiptPath -Encoding utf8
python ./tools/security/stage46_synthetic_dual_build_gate.py --receipt $receiptPath
if ($LASTEXITCODE -ne 0) { throw 'STAGE46_18_REPRODUCIBILITY_EVIDENCE_REJECTED' }
Write-Host 'STAGE46_18_REPRODUCIBLE_FIXTURE_ONLY_NOT_SIGNED_RELEASE'
Write-Host 'STAGE46_18_PHYSICAL_OWNER_PC_AND_PRODUCTION_NO_GO'
