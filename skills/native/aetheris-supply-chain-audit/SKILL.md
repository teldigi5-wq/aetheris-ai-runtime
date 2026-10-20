---
name: aetheris-supply-chain-audit
description: Audit dependencies, licenses, CI action pinning, SBOM and signed provenance with fail-closed advisories before software release.
---

# Software Supply Chain Review
Read-only, local evidence first. Identify dependency graph and ecosystems, package lock consistency, known advisory source/date, licensing obligations, pinned GitHub Actions versions/commits, release artifacts, provenance, SBOM completeness and Docker image digests.

Use OSV-Scanner and OpenSSF Scorecard only when tooling, network and a scoped CI job are explicitly approved. Never silently install remote packages, pipe downloads to shells, or assert a zero-vulnerability score when database access was unavailable. Record exact tool version, database snapshot/time, affected package/version, evidence and reachable context. Flag stale advisories and unreviewed third-party skills.

Recommend deterministic lockfiles, SHA-pinned CI actions, least-permission GITHUB_TOKEN, read-only mounts and approval before dependency upgrades. A GitHub PR must remain draft if security-critical evidence is missing.