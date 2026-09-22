[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$scripts = @(
    (Join-Path $PSScriptRoot 'install.ps1'),
    (Join-Path $PSScriptRoot 'uninstall.ps1'),
    (Join-Path $PSScriptRoot 'build-package.ps1')
)

foreach ($script in $scripts) {
    if (-not (Test-Path $script -PathType Leaf)) { throw "Missing packaging script: $script" }
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($script, [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count -gt 0) {
        $messages = ($errors | ForEach-Object { $_.Message }) -join '; '
        throw "PowerShell parse failure in ${script}: $messages"
    }
    $content = Get-Content $script -Raw
    foreach ($forbidden in @('Invoke-Expression','IEX ','ExecutionPolicy Bypass','-EncodedCommand','schtasks.exe','reg.exe add','netsh advfirewall')) {
        if ($content.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            throw "Forbidden packaging pattern '$forbidden' found in $script"
        }
    }
}

Write-Host 'Stage 11 packaging scripts parsed successfully and contain no forbidden execution-policy/admin shortcut patterns.'
