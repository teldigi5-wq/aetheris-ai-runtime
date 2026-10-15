# Stage 46.13 CI-only Windows reduced-privilege process identity proof.
# No real devices, speech models, networking, desktop operations or deployment.
param([Parameter(Mandatory=$true)][string]$ClassesPath)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'WINDOWS_REDUCED_TOKEN_LAB_UNSUPPORTED_HOST' }

$classes = (Resolve-Path -LiteralPath $ClassesPath -ErrorAction Stop).Path
$source = Join-Path $PSScriptRoot 'WindowsSyntheticJobLab.cs'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw 'TRUSTED_JAVA_EXE_NOT_AVAILABLE'
}
$probe = Join-Path $classes 'io\aetheris\orchestrator\voice\SyntraSyntheticJobProbeMain.class'
if (-not (Test-Path -LiteralPath $probe -PathType Leaf)) {
    throw 'INERT_JAVA_PROBE_NOT_COMPILED'
}
Add-Type -Path $source -ErrorAction Stop

function Assert-ReducedEvidence {
    param([object]$e, [string]$expected)
    Write-Host ('STAGE46_13_DIAGNOSTIC verdict='+$e.Verdict+
        '; expected='+$expected+
        '; tokenCreated='+$e.PrivilegeReducedTokenCreated+
        '; reducedApplied='+$e.PrivilegeReducedTokenUsedForChild+
        '; fallbackSameToken='+$e.UsedReducedTokenAlternateLaunch+
        '; parentPrivCount='+$e.ParentEnabledPrivilegeCount+
        '; childPrivCount='+$e.ChildEnabledPrivilegeCount+
        '; kernelReadBack='+$e.KernelLimitsReadBack+
        '; assignedSuspended='+$e.AssignedWhileSuspended+
        '; childExited='+$e.ChildExited+
        '; childExitCode='+$e.ExitCode)
    if ($e.Verdict -ne $expected -or
        -not $e.PrivilegeReducedTokenCreated -or
        -not $e.PrivilegeReducedTokenUsedForChild -or
        $e.ChildEnabledPrivilegeCount -gt 1 -or
        $e.ChildEnabledPrivilegeCount -gt $e.ParentEnabledPrivilegeCount -or
        -not $e.KernelLimitsReadBack -or
        -not $e.AssignedWhileSuspended -or
        -not $e.ChildExited -or
        -not $e.KillOnJobClose -or
        $e.ActiveProcessLimit -ne 1 -or
        $e.ProcessMemoryLimit -ne 536870912 -or
        $e.JobMemoryLimit -ne 536870912 -or
        $e.CpuHardCapRate -ne 5000) {
        throw ('REDUCED_CHILD_KERNEL_POLICY_EVIDENCE_FAILED_'+$expected)
    }
    Write-Host ('STAGE46_13_REDUCED_TOKEN_'+$expected)
}
$e = [SyntraStage4612Lab.WindowsSyntheticJobLab]::RunPrivilegeReduced(
    $java, $classes, 'EXIT', 5000)
Assert-ReducedEvidence $e 'EXITED_SUCCESS'

# The identity-restricted child should continue to receive only the minimal,
# explicitly constructed environment, never the parent test placeholders.
$env:SYNTRA_SYNTHETIC_SECRET_SENTINEL='synthetic-sentinel-not-secret'
$env:OPENAI_API_KEY='synthetic-placeholder-not-a-key'
try {
    $e=[SyntraStage4612Lab.WindowsSyntheticJobLab]::RunPrivilegeReduced(
        $java, $classes, 'CHECK_ENV', 5000)
    Assert-ReducedEvidence $e 'EXITED_SUCCESS'
} finally {
    Remove-Item Env:SYNTRA_SYNTHETIC_SECRET_SENTINEL -ErrorAction SilentlyContinue
    Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
}

$e=[SyntraStage4612Lab.WindowsSyntheticJobLab]::RunPrivilegeReduced(
    $java, $classes, 'STALL', 1000)
Assert-ReducedEvidence $e 'TIMED_OUT_KILLED'

# No unrestricted fallback after reduced-token launch errors. Invalid modes
# and wait budgets must be rejected prior to a process launch.
foreach ($bad in @('COMMAND','NETWORK','OPEN_FILE')) {
    $denied=$false
    try {
        [SyntraStage4612Lab.WindowsSyntheticJobLab]::RunPrivilegeReduced(
            $java,$classes,$bad,1000) | Out-Null
    } catch [System.Management.Automation.MethodInvocationException] {
        $denied=$true
    }
    if (-not $denied) { throw 'UNRECOGNIZED_REDUCED_TOKEN_LAB_MODE_ACCEPTED' }
}
Write-Host 'STAGE46_13_WINDOWS_REDUCED_TOKEN_CHILD_VERIFIED'
Write-Host 'STAGE46_13_FILESYSTEM_AND_NETWORK_SANDBOX_NOT_CLAIMED'
Write-Host 'STAGE46_13_PRODUCTION_AND_OWNER_PC_PILOT_NO_GO'
