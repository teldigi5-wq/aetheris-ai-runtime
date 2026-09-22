[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [switch]$Purge
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Stage 11 AetherisHostAgent uninstaller is Windows-only.' }

$baseRoot = Join-Path $env:LOCALAPPDATA 'Aetheris'
$installDir = Join-Path $baseRoot 'HostAgent'
$startupDir = [Environment]::GetFolderPath('Startup')
$shortcutPath = Join-Path $startupDir 'Aetheris Host Agent.lnk'

if (Test-Path $installDir) {
    $pidFile = Join-Path $installDir 'agent.pid'
    if (Test-Path $pidFile) {
        $existingPid = (Get-Content $pidFile -Raw).Trim()
        if ($existingPid -match '^\d+$') {
            $process = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
            if ($null -ne $process -and $PSCmdlet.ShouldProcess("PID $existingPid", 'Stop AetherisHostAgent')) {
                Stop-Process -Id $process.Id -ErrorAction Stop
                $process.WaitForExit(5000) | Out-Null
            }
        }
    }
}

if ((Test-Path $shortcutPath) -and $PSCmdlet.ShouldProcess($shortcutPath, 'Remove startup shortcut')) {
    Remove-Item $shortcutPath -Force
}

if (-not (Test-Path $installDir)) {
    Write-Host 'AetherisHostAgent is not installed for this user.'
    return
}

if ($Purge) {
    if ($PSCmdlet.ShouldProcess($installDir, 'Permanently remove host-agent files and DPAPI-protected local key')) {
        Remove-Item $installDir -Recurse -Force
        Write-Host 'AetherisHostAgent files were purged for the current user.'
    }
} else {
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $removedDir = Join-Path $baseRoot ("HostAgent.removed.$timestamp")
    if ($PSCmdlet.ShouldProcess($installDir, "Move to reversible removal backup $removedDir")) {
        Move-Item $installDir $removedDir
        Write-Host "AetherisHostAgent removed from startup and retained for rollback at $removedDir"
    }
}
