# Runtime workspace symlink boundary — 2026-10-21

## Threat and protection

Prior `WorkspaceSandboxService` normalized only lexical `..` path segments,
so a symlink under `workspace` could point to a directory/file outside it.
Read, write, and command working-directory resolution all followed these links.

This change canonicalizes the configured workspace root at initialization and
checks every existing path component using real filesystem resolution before
reads, writes, and working-directory selection. Symlinks pointing **outside**
the workspace and broken symlinks fail closed; links staying within the root
remain readable. File writes refuse final-component symlinks, and standard
file operations continue to work. Paths still cannot contain lexical
`..` escape or absolute filenames.

## Scope and remaining risks

This is a significant traversal mitigation, **not a hardened OS/filesystem
sandbox**. Concurrent attackers who can modify workspace symlinks may still
attempt time-of-check/time-of-use races during ordinary file operations.
Hard links, mount points, child process access, OS permissions, and alternate
Windows path forms need separate review. Production untrusted workload execution
requires an ephemeral unprivileged container or VM with read-only mounts,
secure file descriptors, restricted network and explicit owner approvals.

No persisted user data is migrated, no workstation runs are executed, and
no privilege or protected main branch is altered. Compatibility: writable
final-component symlinks are rejected intentionally; operators should target
actual in-root files instead. The hosted Linux CI checks the symlink behavior
but does not certify owner-PC Windows behavior.
