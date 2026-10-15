[CmdletBinding()]
param([switch]$SyntheticCI)

# Read-only review script. This does not inspect microphone audio, enumerate
# user recordings, ask OS permission, install a driver or launch any speech
# engine. It can NEVER return an activation permit.
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest

try {
    if($PSVersionTable.PSVersion.Major -lt 7) {
        throw 'POWERSHELL_7_REQUIRED'
    }
    $runningOnWindows=[System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
        [System.Runtime.InteropServices.OSPlatform]::Windows)
    if($SyntheticCI) {
        if($env:GITHUB_ACTIONS -cne 'true' -or
           $env:AETHERIS_STAGE46_SPEECH_REVIEW_CI -cne 'true') {
            throw 'SYNTHETIC_CI_ACCESS_DENIED'
        }
    } elseif(-not $runningOnWindows) {
        throw 'TARGET_WINDOWS_REQUIRED'
    }

    # Physical microphone permission cannot be reliably inferred from the
    # presence of a sound device, a Windows privacy registry value, or a
    # generic running process. We do not pretend it has been obtained.
    $java21Seen=$false
    if($runningOnWindows -and -not $SyntheticCI) {
        $java=Get-Command java.exe -ErrorAction SilentlyContinue
        if($null -ne $java) {
            # 'java -version' does not run the app, record, or open audio.
            $versionLine=(& $java.Source -version 2>&1 | Select-Object -First 1) -join ''
            if($versionLine -match '"(\d+)') {
                $java21Seen=[int]$Matches[1] -ge 21
            }
        }
    }
    ([ordered]@{
        schema='aetheris:stage46:voice-device-review:v1'
        verdict='NO_GO'
        platformEvidence=$(if($SyntheticCI){'CI_SYNTHETIC_LINUX_NOT_WINDOWS'}else{'WINDOWS_READ_ONLY_CHECK'})
        java21Available=$java21Seen
        physicalMicrophonePermissionVerified=$false
        wakeWordModelVerified=$false
        localSttModelVerified=$false
        localTtsVoiceVerified=$false
        binaryAndModelLicenseReviewed=$false
        independentArtifactTrustVerified=$false
        voiceLatencyMeasured=$false
        recordingEnabled=$false
        speakerPlaybackEnabled=$false
        autostartEnabled=$false
        desktopActionAuthorized=$false
        installPerformed=$false
        wslInvoked=$false
        nextGate='OWNER_APPROVED_HARDWARE_AND_LICENSE_REVIEW_REQUIRED'
    } | ConvertTo-Json -Compress -Depth 3)
    exit 0
} catch {
    # Reject without emitting sensitive paths/stack traces.
    $code=($_.Exception.Message -split '[\r\n]')[0]
    if($code -cnotmatch '^[A-Z][A-Z0-9_]{5,100}$') {$code='SPEECH_REVIEW_FAILED'}
    ([ordered]@{
        schema='aetheris:stage46:voice-device-review:v1'
        verdict='DENIED'
        reason=$code
        installPerformed=$false
        recordingEnabled=$false
        desktopActionAuthorized=$false
    } | ConvertTo-Json -Compress -Depth 3)
    exit 1
}
