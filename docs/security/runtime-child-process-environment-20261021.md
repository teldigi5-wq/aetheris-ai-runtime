# Child-process environment boundary — security draft (2026-10-21)

Prior to this change, explicitly authorized `CommandSandboxService` executions
received a full copy of the orchestrator process environment, which may contain
database passwords, model tokens, GitHub credentials and command-injection
environment variables such as `JAVA_TOOL_OPTIONS`, `NODE_OPTIONS` or
`LD_PRELOAD`.

Now each new `ProcessBuilder` clears all environment entries and restores only a
filtered executable PATH (absolute, existing directories outside the canonical
workspace), plus Windows `SystemRoot`, `WINDIR` and `PATHEXT` where
necessary for native execution. No HOME, proxy, AWS, cloud-key, GitHub-key,
database password, Java injection, npm configuration or model token inherits
by default. Untrusted cwd/relative PATH entries and PATH entries under the
workspace are rejected, including symlink-resolved ones. Command execution
remains **disabled by default**, as established by the previous stacked PR.

## Compatibility
Opted-in commands that rely on `HOME`, `JAVA_HOME`, custom
library paths, credentials, proxies or other environment configuration can
fail intentionally. Migrate such operations into a separate reviewed worker
with scoped credentials; do **not** restore broad inherited environment in
the orchestrator. `java -version` regression remains test-only operator
opt-in.

## Limitations
Environment sanitization does not restrict OS permissions, network egress,
code execution, symlink races, executable path hijacking in other host PATH
directories, command arguments, project build scripts or secrets accessible
through mounted files. Parent processes may still expose credentials to
privileged local users. A real untrusted-code sandbox needs an unprivileged
ephemeral container/VM, network and filesystem constraints, PID/resource
limits, non-secret mounts and owner approval. Windows physical execution,
live service-to-service behavior and production rollout are not yet tested.
