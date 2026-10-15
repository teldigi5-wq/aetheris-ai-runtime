# Stage 46.12: GitHub-hosted Windows-only, synthetic/inert Job Object tests.
# No live microphone, speech model, workstation installation, services,
# app execution from transcripts, shell commands, or production privileges.
param(
    [Parameter(Mandatory=$true)][string]$ClassesPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $IsWindows) { throw 'WINDOWS_JOB_LAB_UNSUPPORTED_HOST' }
$root = (Resolve-Path -LiteralPath $ClassesPath -ErrorAction Stop).Path
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $scriptRoot 'WindowsSyntheticJobLab.cs'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
    throw 'FIXED_JOB_LAB_SOURCE_MISSING'
}
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw 'REQUIRED_JAVA_HOME_NOT_CONFIGURED'
}
$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
    throw 'FIXED_JAVA21_EXE_MISSING'
}
$probe = Join-Path $root 'io\aetheris\orchestrator\voice\SyntraSyntheticJobProbeMain.class'
if (-not (Test-Path -LiteralPath $probe -PathType Leaf)) {
    throw 'PRECOMPILED_SYNTHETIC_JOB_PROBE_MISSING'
}

# Compiled exclusively for this explicit Windows CI lab invocation.
Add-Type -Path $source -ErrorAction Stop

function Confirm-JobEvidence {
    param([object]$Result)
    if (-not $Result.KernelLimitsReadBack -or
        -not $Result.AssignedWhileSuspended -or
        -not $Result.KillOnJobClose -or
        $Result.ActiveProcessLimit -ne 1 -or
        $Result.ProcessMemoryLimit -ne 536870912 -or
        $Result.JobMemoryLimit -ne 536870912 -or
        $Result.CpuHardCapRate -ne 5000 -or
        -not $Result.ChildExited -or
        $Result.Pid -le 0) {
        throw 'WINDOWS_KERNEL_JOB_POLICY_NOT_CONFIRMED'
    }
}
function Invoke-Case {
    param([string]$Mode, [int]$WaitBudget, [string]$Expected)
    $result = [SyntraStage4612Lab.WindowsSyntheticJobLab]::Run(
        $javaExe, $root, $Mode, $WaitBudget)
    Confirm-JobEvidence -Result $result
    if ($result.Verdict -ne $Expected) {
        throw "SYNTHETIC_JOB_TEST_CASE_FAILED_$Mode"
    }
    Write-Host ('STAGE46_12_'+$Mode+'_'+$Expected)
}

Invoke-Case -Mode 'EXIT' -WaitBudget 5000 -Expected 'EXITED_SUCCESS'
Invoke-Case -Mode 'TRY_CHILD' -WaitBudget 5000 -Expected 'EXITED_SUCCESS'
Invoke-Case -Mode 'FAIL' -WaitBudget 5000 -Expected 'CHILD_REJECTED'

# Parent test secrets must never appear inside the fresh child environment.
$env:SYNTRA_SYNTHETIC_SECRET_SENTINEL = 'SYNTHETIC_TEST_ONLY_DO_NOT_COPY'
$env:OPENAI_API_KEY = 'synthetic-lab-placeholder-never-real'
try {
    Invoke-Case -Mode 'CHECK_ENV' -WaitBudget 5000 -Expected 'EXITED_SUCCESS'
} finally {
    Remove-Item Env:SYNTRA_SYNTHETIC_SECRET_SENTINEL -ErrorAction SilentlyContinue
    Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
}

Invoke-Case -Mode 'STALL' -WaitBudget 1000 -Expected 'TIMED_OUT_KILLED'

# Invalid modes cannot select arbitrary code and invalid timeouts never start.
foreach ($badMode in @('POWERSHELL','CMD','TRY_CHILD ARG')) {
    $rejected = $false
    try {
        [SyntraStage4612Lab.WindowsSyntheticJobLab]::Run(
            $javaExe, $root, $badMode, 2000) | Out-Null
    } catch [System.Management.Automation.MethodInvocationException] {
        $rejected = $true
    }
    if (-not $rejected) { throw 'UNTRUSTED_JOB_MODE_ACCEPTED' }
}
Write-Host 'STAGE46_12_WINDOWS_JOB_OBJECT_KERNEL_LIMITS_VERIFIED'
Write-Host 'STAGE46_12_PHYSICAL_OWNER_PC_AND_PRODUCTION_NO_GO'
