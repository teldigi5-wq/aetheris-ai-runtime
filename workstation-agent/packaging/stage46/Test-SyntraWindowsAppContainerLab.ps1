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
& $exe
if ($LASTEXITCODE -ne 0) { throw 'STAGE46_14_APP_CONTAINER_KERNEL_TEST_FAILED' }
Write-Host 'STAGE46_14_WINDOWS_NATIVE_APP_CONTAINER_LAB_SUCCESS'
Write-Host 'STAGE46_14_PHYSICAL_OWNER_PC_AND_PRODUCTION_NO_GO'
