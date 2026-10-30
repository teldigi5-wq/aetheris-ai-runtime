# Enterprise E6 — dependency license-review evidence (draft, 30 October 2026)

**Scope:** Research-only Next.js / React Three Fiber / GSAP app under tools/enterprise/e6_studio/next-studio. No production distribution approval or independent legal certification.

## Recovered evidence and controls

- Parent PR #235 committed a reviewed npm lockfile, reproducible npm ci, CycloneDX v1.5 SBOM, npm high/critical audit, TypeScript/build and browser regressions. Exact-head CI passed: https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/41063046085.
- The previous CI SBOM contained 58 installed packages; the committed lockfile included 85 dependency entries (excluding the root). They have different coverage: the lock contains architecture/OS-specific optional dependencies not installed on Ubuntu CI.
- All 85 locked entries carry license metadata. Fourteen entries reference LGPL, primarily optional Sharp/libvips platform binaries. They require distribution-context review, not automatic rejection or a claim of infringement.
- gsap@3.15.0 has a custom Standard no-charge license string in the lockfile; its generated SBOM component omitted SPDX license metadata. Missing SPDX does not mean no license.
- New script scripts/report_dependency_licenses.py cross-references the actual installed CycloneDX component names/versions with the committed lock, rejects unknown/mismatched/duplicated packages, and generates target/e6-studio/supply-chain/license-review.json as a CI artifact.
- The report explicitly fixes ownerReleaseApproval=false, stage45Go=false and releaseStatus=REVIEW_REQUIRED. Passing metadata verification does not certify distribution license compliance.

## License primary sources

1. GreenSock standard license, terms and restrictions: https://gsap.com/standard-license . Official repository license notes: https://github.com/greensock/GSAP .
2. Sharp/libvips binary notices and bundled LGPL libraries: https://www.npmjs.com/package/@img/sharp-libvips-linux-x64 .

## Outstanding review

Review all actual target-distribution package/asset notices, platform-specific binary packages, obligations for shipping LGPL components, embedded or statically distributed code, commercial-use restrictions and any required third-party attributions. The CI SBOM alone does not describe every target platform. Independently approve release artifacts and licenses. No production merge, deployment, owner GPU certification or Stage45 release approval has occurred. Stage45 remains NO_GO.
