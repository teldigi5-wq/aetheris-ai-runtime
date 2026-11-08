# E9 — Hosted runtime image integrity and release-publication guard

**9 November 2026. DRAFT, no deployment or merge. Stage45 NO_GO; E9 NOT CERTIFIED.**

## Functional improvement to an existing workflow

The existing `.github/workflows/ai-runtime-image-provenance.yml` builds the runtime image, verifies non-root workspace writes, exports and round-trips a Docker-save archive, hashes it and uploads the evidence to GitHub Actions. It previously had unconditional `contents: write` permission and a push-to-`main` step that automatically created a GitHub prerelease of that archived image, despite Stage45 NO_GO and no independently verified owner release decision.

**This E9 review branch removes automatic `gh release create` publication and changes workflow permissions to `contents: read`.** The existing hosted image build, Docker save/load checks, and **private CI artifact upload** remain. It adds explicit `release_authorized:false`, `owner_release_authorization_verified:false`, `release_publication_expected:false`, and `stage45Go:false` to the provenance JSON. This changes a real publication permission boundary, not just a new test fixture. The current protected `main` is **NOT updated** until an independently reviewed PR is safely promoted; the old workflow on `main` remains in its previous state until then.

## Operational artifact-consumer tool

`tools/enterprise/e9_release/e9_hosted_image_verifier.py` is an offline, Python-standard-library verifier for a previously built/obtained **hosted** Docker-save artifact bundle. Default execution is plan-only and reads no files.

Expected local directory, with **exactly** these five files from a corresponding workflow run:

- `aetheris-ai-runtime-<full-commit-sha>.tar.gz`
- `aetheris-ai-runtime-<full-commit-sha>.tar.gz.sha256`
- `runtime-image-id.txt`
- `runtime-workspace-write-probe.txt`
- `runtime-image-provenance.json`

Example, after verifying the source of a GitHub Actions artifact and extracting locally into a private directory:

```sh
python tools/enterprise/e9_release/e9_hosted_image_verifier.py
python tools/enterprise/e9_release/e9_hosted_image_verifier.py \
  --verify-local --artifact-dir /absolute/private/image-evidence \
  --revision 0123456789abcdef0123456789abcdef01234567
```

The revision above is an **illustrative SHA**, not a real verified release.

For `--verify-local`, the verifier requires an absolute directory; rejects unlisted entries, symlinks, special files, inappropriate image identities, corrupt/oversize evidence, archive traversal, duplicate archive members, bad Docker config/manifest, mismatched image IDs or checksums. It **streams** the gzip archive and validates the actual Docker image's config JSON digest, `RepoTags`, expected SHA labels, repository label, configured non-root user and referenced layers. It independently checks the archive SHA-256 and parses the hosted-build metadata with all approval/GO flags forced false.

A successful result is `HOSTED_IMAGE_INTEGRITY_VERIFIED_RELEASE_NO_GO`, **not** authorization to publish. It proves locally supplied artifacts are internally consistent, not that GitHub actually built them. Same-account manipulation of all inputs, unsigned metadata, artifact storage compromise or provenance forgery can reproduce a consistent bundle. Real independent SLSA attestations, trusted builder identity, vulnerability/secret scanning, a full verified SBOM, dependency/source integrity, signed audit custody, real runtime image tests, owner-device hardware and all release authorizations are separate blockers.

## Verified CI requirements

The E9 workflow `.github/workflows/runtime-e9-hosted-integrity.yml` runs negative unit tests on Linux and Windows, plus a distinct **actual Docker build/export/load** on Ubuntu. The hosted image in that test is a tiny first-party `FROM scratch` fixture with a genuine non-root executable that writes/removes a test file in its image workspace, no third-party base image, network, secret or production code. It then verifies its real Docker-save tar/config/labels/checksums and preserves an explicitly unapproved synthetic artifact in CI. A fixture pass **does not** claim that the actual production runtime image or physical PC passed. Use the real original `ai-runtime-image-provenance.yml` build's downloadable artifact, once available on the reviewed revision, to check actual runtime-image integrity.

Existing E8 Mission Review, E7 specialist delegation, E4/E5/E6 drafts, Runtime #23, Platform authorization, deployment and protected `main` are left unchanged by the feature's code changes.

**Stop rule:** This completes a useful E9 publication-control and hosted-image integrity capability on a draft branch. E9 acceptance remains OPEN: independently trusted build signing/attestation, comprehensive SBOM and dependency audit, authentic integrated security/tenancy, incident/recovery/endurance tests, real hardware QA, and explicit authenticated owner production authorization. No release, XERJ contribution, production deployment or Stage45 GO is implied.
