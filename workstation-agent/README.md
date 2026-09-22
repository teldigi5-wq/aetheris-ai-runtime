# Aetheris Workstation Agent — Stage 11

`workstation-agent` is the least-privilege Windows-side process for Syntra + Aetheris. It is intentionally separate from the main orchestrator so operating-system actions have a small, auditable surface.

## Security boundary

The production runtime:

- runs only on Windows;
- binds HTTP only to `127.0.0.1`;
- accepts only signed, short-lived `REMOTE` command envelopes;
- verifies the configured host UUID;
- rejects envelopes with a TTL above 60 seconds or excessive future clock skew;
- persists command IDs until expiry to reject replay after process restart;
- loads its HMAC signing key from a CurrentUser DPAPI-protected local file;
- allows scalar command arguments only;
- exposes no generic shell, PowerShell, `cmd.exe`, registry, scheduled-task, process-create, UAC-bypass, disk-management or arbitrary-command primitive.

The internal DPAPI helper uses a fixed PowerShell/.NET `ProtectedData` expression solely to unprotect the local signing key. It is not parameterized by model/user command text and is not reachable through the host-agent command API.

## Allowed capabilities

The command surface is deliberately limited to:

| Capability | Action | Boundary |
|---|---|---|
| `system.telemetry` | `snapshot` | Read-only CPU/memory/OS telemetry. GPU telemetry remains adapter-pending. |
| `process.status` | `status` | Read-only status for a single validated process name. |
| `app.launch` | `launch` | Starts one exact executable selected by an owner-configured alias; no arguments or shell. |
| `workspace.open` | `open` | Opens one existing regular file inside an owner-configured workspace root after real-path validation. |

Closing processes, arbitrary terminal commands, admin actions and generic file write/delete are not Stage 11 host-agent capabilities.

## Build

```powershell
mvn -f workstation-agent/pom.xml -B test package
```

The shaded jar is produced as:

```text
workstation-agent/target/aetheris-host-agent.jar
```

For a source package with checksums and manifest:

```powershell
./workstation-agent/packaging/build-package.ps1
```

Without a detached signature the package is explicitly non-production evidence. Stage 11 production activation requires the existing signature, provenance, regression and rollback gates to pass.

## Install on the owner workstation

Installation is a **target-PC validation step**, not something CI can claim completed.

Run from a normal user PowerShell session:

```powershell
./workstation-agent/packaging/install.ps1 `
  -HostId '<paired-host-uuid>' `
  -WorkspaceRoots 'D:\Projects\Aetheris','D:\Projects\FloodGuard' `
  -Apps @{ vscode='C:\Users\<user>\AppData\Local\Programs\Microsoft VS Code\Code.exe' }
```

The installer:

1. requires Java 21+;
2. refuses drive-root and whole-user-profile workspace grants;
3. backs up a previous installation before replacement;
4. asks interactively for the existing 32+ byte host-command signing key and stores only its DPAPI-protected form;
5. writes owner-approved roots/app aliases;
6. creates a current-user startup shortcut;
7. starts the agent; and
8. requires the loopback health endpoint to report `loopbackOnly=true` and `shellCapability=false`, otherwise it rolls back.

The signing key must match the orchestrator's provisioned host-command key. Do not paste production credentials into Git, chat logs, issue bodies, screenshots or documentation.

## Remove / rollback

```powershell
./workstation-agent/packaging/uninstall.ps1
```

By default removal stops the process, removes the startup entry and moves the install directory to a timestamped rollback location. Use `-Purge` only when the owner intentionally wants the local agent files and DPAPI-protected key deleted.

## Remote operation

The host agent itself is **not a public remote server**. It remains loopback-only. Remote Syntra/Aetheris operation must terminate through the Stage 10/11 private transport boundary with TLS 1.3, mutual device authentication, active/revocable pairing, capability scopes and short-lived sessions. Stage 11 certificate-bound remote sessions do not change this rule.

## Stage 11 truth

CI can prove compilation, envelope verification, replay protection, capability validation, path containment and package-generation logic. CI cannot prove the user's physical microphone, RTX GPU behavior, DPAPI user profile, real application paths, private tunnel or desktop performance. Those remain target-workstation evidence gates.
