# E4 cross-process operator-local model attempt limit — review only

This increment builds on E4 plan-first local-only Ollama CLI PR #202. It does **not** grant owner approval, certify provider quality, enable cloud inference, or satisfy Stage45 release gates.

## Functionality

- The CLI is still **plan-only** by default, with no directory creation, provider calls, source edits or fallback.
- Only explicit `--run-local` with both existing local opt-ins may attempt a model. A separate **existing, operator-owned, private, absolute quota directory** is now mandatory.
- Every `LocalProposalSession.propose` reserves one of **three UTC-day slots** on disk **before** even the local Ollama /api/tags probe. A provider error consumes the slot; a fourth new process is denied without contacting the provider.
- Linux file locks and Windows byte-range locks serialize cooperating local processes. Atomic JSON replacement and fsync help recover from process interruptions; malformed state, symlinks, unsafe Unix directory/file permissions and clock rollback fail closed rather than resetting usage.
- No prompt, token, source, path, credentials or proposal is stored in the ledger; only `schema`, UTC `day` and `used`.
- No code patch, owner-action authority, cloud routing or model download is enabled by this change.

## Local operator setup

Linux/macOS terminal:

```sh
mkdir -p "$HOME/.aetheris-e4-quota"
chmod 700 "$HOME/.aetheris-e4-quota"
export AETHERIS_E4_LOCAL_QUOTA_DIR="$HOME/.aetheris-e4-quota"
export AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN=yes
export AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes
python tools/enterprise/e3_factory/e4_local_proposal_cli.py --model qwen2.5-coder:1.5b --task scroll --run-local
```

Windows PowerShell (the directory must already exist):

```powershell
$root = Join-Path $HOME '.aetheris-e4-quota'
New-Item -ItemType Directory -Path $root -Force | Out-Null
$env:AETHERIS_E4_LOCAL_QUOTA_DIR = $root
$env:AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN = 'yes'
$env:AETHERIS_E36_LOCAL_INFERENCE_OPT_IN = 'yes'
python tools/enterprise/e3_factory/e4_local_proposal_cli.py --model qwen2.5-coder:1.5b --task scroll --run-local
```

The operator must also install the exact allowed model locally in Ollama; this feature does **not** pull it. A negative/failed provider attempt still consumes a slot.

## Limits and trust

This is **not** an authentication or tamper-resistant accounting system. A process under the same OS account can edit/delete its local ledger, reconfigure its quota root, or manipulate its clock; it cannot enforce policies on other machines. Windows filesystem ACLs, symlink races under a compromised same-user process, network shares and power-loss guarantees are **not independently certified**. For trustworthy user/tenant-wide metering, E4 still requires a centrally authenticated atomic database/quota authority, real provider pricing/usage evidence and trusted session identity.

CI tests offline cross-process reservations and existing mock Ollama dispatch on Windows/Linux; **no real model inference is performed in CI**. No protected main, production route, account, secret or deployment is modified.
