[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$HostId,
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string[]]$WorkspaceRoots,
    [string]$JarPath = (Join-Path $PSScriptRoot '..\target\aetheris-host-agent.jar'),
    [ValidateRange(1024,65535)][int]$Port = 17771,
    [hashtable]$Apps = @{}
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Convert-ToPropertyPath([string]$PathValue) {
    return ([System.IO.Path]::GetFullPath($PathValue)).Replace('\','/')
}

function Stop-ExistingAgent([string]$InstallDir) {
    $pidFile = Join-Path $InstallDir 'agent.pid'
    if (Test-Path $pidFile) {
        $existingPid = (Get-Content $pidFile -Raw).Trim()
        if ($existingPid -match '^\d+$') {
            $p = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
            if ($null -ne $p) { Stop-Process -Id $p.Id -ErrorAction Stop; $p.WaitForExit(5000) | Out-Null }
        }
    }
}

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Stage 11 AetherisHostAgent installer is Windows-only.' }
[Guid]$parsedHost = [Guid]::Empty
if (-not [Guid]::TryParse($HostId, [ref]$parsedHost)) { throw 'HostId must be a valid UUID/GUID.' }

$java = Get-Command java.exe -ErrorAction Stop
$javaw = Get-Command javaw.exe -ErrorAction Stop
$versionText = (& $java.Source -version 2>&1 | Select-Object -First 1) -join ''
if ($versionText -notmatch '"(\d+)') { throw 'Unable to determine Java version.' }
if ([int]$Matches[1] -lt 21) { throw 'Java 21 or newer is required.' }

$sourceJar = [System.IO.Path]::GetFullPath($JarPath)
if (-not (Test-Path $sourceJar -PathType Leaf)) { throw "Host-agent jar not found: $sourceJar" }

$normalizedRoots = @()
foreach ($root in $WorkspaceRoots) {
    $resolved = [System.IO.Path]::GetFullPath($root)
    if (-not (Test-Path $resolved -PathType Container)) { throw "Workspace root does not exist: $resolved" }
    $driveRoot = [System.IO.Path]::GetPathRoot($resolved).TrimEnd('\')
    if ($resolved.TrimEnd('\') -eq $driveRoot) { throw 'A drive root cannot be registered as an Aetheris workspace root.' }
    if ($resolved.TrimEnd('\') -eq $env:USERPROFILE.TrimEnd('\')) { throw 'The entire user profile cannot be registered as an Aetheris workspace root.' }
    $normalizedRoots += (Convert-ToPropertyPath $resolved)
}

$baseRoot = Join-Path $env:LOCALAPPDATA 'Aetheris'
$installDir = Join-Path $baseRoot 'HostAgent'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDir = Join-Path $baseRoot ("HostAgent.backup.$timestamp")
$startupDir = [Environment]::GetFolderPath('Startup')
$shortcutPath = Join-Path $startupDir 'Aetheris Host Agent.lnk'

New-Item -ItemType Directory -Path $baseRoot -Force | Out-Null
$hadPrevious = Test-Path $installDir
if ($hadPrevious) {
    Stop-ExistingAgent $installDir
    Move-Item -Path $installDir -Destination $backupDir
}

try {
    New-Item -ItemType Directory -Path $installDir -Force | Out-Null
    $destJar = Join-Path $installDir 'aetheris-host-agent.jar'
    Copy-Item $sourceJar $destJar -Force

    $secure = Read-Host 'Enter the same 32+ character host-command signing key configured in the Aetheris orchestrator' -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $plainBytes = $null
    try {
        $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        $plainBytes = [Text.Encoding]::UTF8.GetBytes($plain)
        if ($plainBytes.Length -lt 32) { throw 'Host-command signing key must be at least 32 UTF-8 bytes.' }
        $cipher = [Security.Cryptography.ProtectedData]::Protect($plainBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        [IO.File]::WriteAllText((Join-Path $installDir 'key.dpapi'), [Convert]::ToBase64String($cipher), [Text.Encoding]::ASCII)
        [Array]::Clear($cipher, 0, $cipher.Length)
        $plain = $null
    } finally {
        if ($null -ne $plainBytes) { [Array]::Clear($plainBytes, 0, $plainBytes.Length) }
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("host.id=$($parsedHost.ToString())")
    $lines.Add("listen.port=$Port")
    $lines.Add('signing-key-file=key.dpapi')
    $lines.Add('workspace-roots=' + ($normalizedRoots -join ';'))
    foreach ($alias in ($Apps.Keys | Sort-Object)) {
        $name = [string]$alias
        if ($name -notmatch '^[A-Za-z0-9._-]{1,64}$') { throw "Invalid application alias: $name" }
        $appPath = [System.IO.Path]::GetFullPath([string]$Apps[$alias])
        if (-not (Test-Path $appPath -PathType Leaf)) { throw "Allowlisted application does not exist: $appPath" }
        $lines.Add("app.$($name.ToLowerInvariant())=$(Convert-ToPropertyPath $appPath)")
    }
    $configPath = Join-Path $installDir 'agent.properties'
    [IO.File]::WriteAllLines($configPath, $lines, [Text.Encoding]::UTF8)

    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $javaw.Source
    $shortcut.Arguments = "-jar `"$destJar`" --config `"$configPath`""
    $shortcut.WorkingDirectory = $installDir
    $shortcut.Description = 'Aetheris least-privilege Windows host agent'
    $shortcut.Save()

    $process = Start-Process -FilePath $javaw.Source -ArgumentList @('-jar', $destJar, '--config', $configPath) -WorkingDirectory $installDir -PassThru
    $healthy = $false
    for ($i=0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 300
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -Method Get -TimeoutSec 2
            if ($health.status -eq 'UP' -and $health.loopbackOnly -eq $true -and $health.shellCapability -eq $false) { $healthy = $true; break }
        } catch {}
    }
    if (-not $healthy) { throw 'AetherisHostAgent failed its loopback health check.' }

    Write-Host "AetherisHostAgent installed for the current Windows user at $installDir"
    Write-Host "Loopback health check passed on 127.0.0.1:$Port. No shell capability is enabled."
    if ($hadPrevious) { Write-Host "Previous installation retained for rollback at $backupDir" }
} catch {
    if (Get-Variable process -ErrorAction SilentlyContinue) {
        Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
    }
    Remove-Item $shortcutPath -Force -ErrorAction SilentlyContinue
    Remove-Item $installDir -Recurse -Force -ErrorAction SilentlyContinue
    if ($hadPrevious -and (Test-Path $backupDir)) { Move-Item $backupDir $installDir }
    throw
}
