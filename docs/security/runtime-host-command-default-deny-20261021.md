# Host command execution security migration — 21 October 2026

This change corrects a dangerously broad default in `CommandSandboxService`.
Earlier local runtime config permitted `git,mvn,npm,node,java,javac` commands
by default. These tools can launch code and do NOT confine operating-system
file access, process privileges, network access or credentials merely because
a workspace directory was selected.

**Fail-closed default:** `AETHERIS_ALLOWED_COMMANDS` and
`aetheris.execution.allowed-commands` are now blank unless an operator
explicitly sets trusted executable names. A command invocation outside that
set is rejected *before process launch*. Qualified executable paths
(`/tmp/evil/java`, `./java`, `C:\\evil\\java.exe`) are rejected even
when `java` is otherwise on the operator allowlist. Explicitly approved bare
commands still resolve via host `PATH`, so a trusted fixed PATH is needed.

Stage4 hosted integration tests opt into `java` only to retain the existing
bounded `java -version` proof. No production workflow gains an implicit
execution privilege through this test-only setting.

## Warning: not a container sandbox

Explicit opt-in is a *dangerous host-process capability*: `node -e`,
`npm` scripts, `java -jar` and `mvn` goals can execute arbitrary code.
The ProcessBuilder child inherits the orchestrator process environment,
including potentially sensitive material, and workspace path checks do
not stop child processes reading elsewhere. Do not enable for untrusted
models, web inputs, or remote agents on a privileged service. For a real
agent coding runtime, move execution to a dedicated, ephemeral,
unprivileged network-restricted container or VM with minimal environment,
filesystem quotas, timeouts, and explicit owner authorization.

Existing operator scripts that depended on the permissive default will
intentionally fail until migrated to a separately reviewed worker. This
slice does not provide a production-grade execution sandbox and changes
no protected main branch or deployed PC configuration.
