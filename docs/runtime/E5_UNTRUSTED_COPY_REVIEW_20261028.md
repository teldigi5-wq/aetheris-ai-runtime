# E5 — bounded untrusted-copy website review preview (28 October 2026)

**Draft E5 substage following PR #203. No autonomous website generation, model calls, owner approval, publication or Stage45 GO.**

## Existing verified state
PR #203 packages a reproducible, human-coded E3.9H three-asset WebGL2 reference. Real Chromium checks exercised the extracted ZIP on mobile/desktop/reduced-motion. That is a QA foundation, not a model-designed customer website.

## New capability in this candidate
The isolated `e5_untrusted_copy_preview.py` reads a LOCAL input only from `target/e5/copy-proposal.json` and produces a review ZIP at `target/e5/untrusted-copy-preview.zip`. The source application is still the exact checked human-coded HTML/CSS/JS reference. An input from a human or a model is **untrusted** and carries no permissions.

It accepts **only** this typed JSON object, with bounded human-readable strings:
```json
{
  "version": 1,
  "brandLabel": "Prototype Atelier",
  "headlineLines": ["Quiet materials.", "Purposeful motion."]
}
```
The example is synthetic, not a factual claim about a real customer.

- Exact field set, version 1, two headline lines, per-field character ceilings and 1400-byte request cap; rejects duplicate JSON keys, invalid Unicode, invisible control characters, URL markup, HTML tags, extra privileges and malformed arrays.
- Only replaces the existing reference site's brand-label and two hero-text nodes. The WebGL2 `scene.js`, `styles.css`, research disclaimers, accessibility landmarks and reduced-motion behavior remain byte-for-byte unchanged.
- Independent `validate_source()` applies the inherited reference asset/remote-source restrictions. The preview ZIP has stable file ordering, pinned timestamps and fixed SHA-256 integrity records. It embeds the canonical accepted proposal for audit. Repeated builds must be bitwise identical.
- Manifest explicitly uses `modelAuthored: null`, because the provenance of the proposal's **copy** has NOT been proven, while `siteCodeProvenance` identifies the trusted human-coded reference. It declares `ownerApproved:false`, `deploymentAuthorized:false`, `productionReady:false`, `runtimeReleaseStatus:NO_GO`. It must **never** be called an AI-designed production website.
- CI runs new negative schema tests, reproducibility/hash tests, extracts the ZIP, and uses **real Chromium** desktop/mobile/reduced-motion to validate visible copy, unchanged code hashes, no external requests, no browser errors, no unexpected horizontal overflow and the existing static fallback.

No provider/cloud network call, tool execution, external URL, image license, private key, real customer content, database change, production deployment, branch merge, or new runtime agent is part of this candidate.

## Remaining E-series work
E3.9H model-authored cinematic quality and device GPU tests remain outstanding. E4 requires real model reliability/consent, quota and budget accounting. **E5 is not complete**: safe human-approved brand inputs, original design direction, genuine model-authored output, independent technical and aesthetic acceptance, WCAG 2.2 AA audits, preview/production boundaries and signed release approvals are still missing. E6–E9 and nine separate Stage45 production attestations also remain unfinished. Protected main unchanged. Keep this PR stacked and DRAFT.
