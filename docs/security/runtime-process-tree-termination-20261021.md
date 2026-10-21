# Aetheris command process-tree termination — Oct 21, 2026

## Risk addressed
Before this draft, a timed-out or interrupted host command invoked
`process.destroyForcibly()` only on its direct ProcessBuilder child. A tool
(e.g. a compiler, build system, or Java/Node script) could spawn a separate
long-running subprocess that inherited file descriptors or continued executing
after the parent process had been cancelled.

## New behavior
When a command times out or its controlling Java thread is interrupted,
the runtime takes a snapshot of the `ProcessHandle.descendants()` tree,
kills the parent and attempts to kill each observed descendant, then gives
the parent a short bounded wait to exit. The buffered output collector
uses synchronized `StringBuffer` storage to avoid unsynchronized access
to a live output-reading virtual thread after a slow shutdown.

A real Java subprocess -> grandchild regression test verifies this
behavior on the hosted Linux runner. The host command service remains
**disabled by default** via the prior PR #180. The test is narrowly
opted into Java execution inside a temporary workspace.

## Honest limitations
This best-effort userspace snapshot is *not guaranteed process isolation*.
Detached/daemonized processes, descendants spawned after enumeration,
multiple PID namespaces, zombie accounting, escalation by privileged
binaries, Windows process-tree semantics, and malicious processes
exiting successfully while leaving children behind remain outside this
fix. Only a reviewed ephemeral OS-isolated worker with process-group,
cgroup/job-object enforcement, restricted mounts and network and no
inherited production credentials can certify untrusted-code execution.

No local owner PC, production deployment, privileged runtime controls,
persistent databases, or protected main branch were touched.
