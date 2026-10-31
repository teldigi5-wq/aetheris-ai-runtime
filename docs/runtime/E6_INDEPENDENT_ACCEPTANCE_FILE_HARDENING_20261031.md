# E6 Task 8B — Bounded local evidence intake and replay hardening

**31 October 2026 · security improvement stacked on draft Task 8 PR #245 · Stage45 NO_GO.**

## Problem

The original inventory gate checked `lstat` before separately calling `readFile`, and the CLI checked manifest byte size *after* reading it. This allowed unbounded memory allocation from a potentially oversized manifest and a potential pathname-replacement window for owner-supplied case files. The evidence validation is a local risk reducer, not sandboxing or proof of human/device authenticity.

## Implemented

- Read evidence files via one open file descriptor with `O_NOFOLLOW`/`O_NONBLOCK` where the OS supports those flags. Apply `fstat` size and link-count bounds before reading, allocate at most the declared size, read explicitly into a bounded buffer, then check EOF, inode/device, length and modification time.
- Reject final-file symlinks, parent-directory symlinks, NTFS/hardlinked evidence (`nlink != 1`), file replacement detected by before/opened/after inode checks, and mismatched SHA-256. Re-check canonical path after the read. Never follow an evidence path outside the chosen private case directory.
- Restrict evidence path components to portable ASCII letters, digits, underscores, periods and hyphens. Reject dot segments, Windows device names (e.g. `CON`, `NUL`, `COM1`, `LPT9`), alternate stream colons and other unsafe characters. Keep allowed extensions limited to the original research file types.
- CLI now checks `manifest.json` type/link count, canonical placement and a **256 KiB limit before allocating it**. Symlink, huge, malformed, changed or otherwise unsafe manifest files produce `E6_LOCAL_MANIFEST_READ_DENIED` and exit 2, not an acceptance signal or a verbose path-exposing crash.
- Added synthetic negative tests for hardlink and symlink attacks, oversized manifest, invalid JSON, unsafe filenames and retained missing-manifest blocked semantics. The source-based nine-domain gate is unchanged: no real hardware evidence has been added.

## Remaining limitations

File descriptor, `O_NOFOLLOW` and before/after path checks **mitigate but do not eliminate** malicious concurrent parent-directory replacement (TOCTOU), particularly on filesystems without directory-descriptor-based traversal. Node's support for no-follow flags varies by platform; the supplemental path and identity checks are defense-in-depth, not a claim of full filesystem race immunity. Treat owner evidence as a trusted local snapshot inside a non-world-writable directory. Do not run intake concurrently with untrusted processes that can mutate it; prefer a read-only or owner-restricted filesystem copy for final review.

Local SHA hashes prove byte agreement with reported values, **not** that GPU results are genuine, human reviewer aliases are independently verified, software licenses are cleared or accessibility/security audits have passed. This PR does not change the Blender asset, website, CI runtime dependencies, owner GPU capture, protected main, production deployments or Stage45 release status.

## Review next

Run the actual local owner hardware benchmark on supported physical desktop/mobile devices, then gather nine independently corroborated evidence domains. Independent human review and explicit owner approval remain outstanding. **Task 7 blocked, Task 8 blocked, Stage45 NO_GO.**
