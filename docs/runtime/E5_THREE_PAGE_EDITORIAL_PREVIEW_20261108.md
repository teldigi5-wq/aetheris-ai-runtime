# E5 three-page editorial website preview

**8 November 2026 — stacked DRAFT feature, not merged or deployed. Stage45 NO_GO.**

This increment delivers a functional offline **three-page website** from supplied editorial content, rather than repeating the public six-case E5 layout benchmark. It compiles home, project and contact pages with keyboard navigation, accessible landmarks, responsive typography, local-only assets, a mailto contact path and reproducible SHA-256 evidence.

The builder is at tools/enterprise/e3_factory/e5_editorial_multipage.py. It reuses the existing reviewed e5_structured_layout_preview.build to enforce the trusted E3.9H script/styles fingerprints, the brief-brand match, safe copy and finite HTML/CSS composition. Human content is **not** sent to a model. The project and contact pages are generated only from escaped validated text; there are no remote assets, trackers, forms, untrusted JavaScript or deployment operations.

## Input contract

The tool reads four files only from the existing target/e5 directory: creative-brief.json, copy-proposal.json, layout-proposal.json and the new editorial-content.json. The fourth file has exact fields:

- version: integer 1
- brandLabel: exact match to the approved E5 brief and copy
- project: eyebrow, title, summary, process, result — non-empty bounded text
- contact: headline, message, email — bounded content; address validated for a safe mailto link

A fictional example for editorial-content.json:

    {
      "version": 1,
      "brandLabel": "Prototype Atelier",
      "project": {
        "eyebrow": "Selected concept",
        "title": "Material and Motion",
        "summary": "A fictional editorial study of proportion, typography and intentional transitions.",
        "process": "The project explores a bounded visual vocabulary and keyboard-first navigation.",
        "result": "A reproducible static prototype for human evaluation, not a delivered client result."
      },
      "contact": {
        "headline": "Discuss a creative brief",
        "message": "This review page exposes a static mail link without forms or analytics.",
        "email": "studio@example.invalid"
      }
    }

The content is not authenticated or independently fact-checked. The contact address above is an invalid example domain, not a live support address. The builder refuses unexpected fields, duplicate JSON keys, remote URLs, unsafe controls, missing inputs, unsafe output paths and overwrites.

## Running offline

From the repository root, prepare the four exact inputs under target/e5 and run:

    python tools/enterprise/e3_factory/e5_editorial_multipage.py

The single output is target/e5/editorial-three-page-review.zip, created once with an exclusive private file mode on POSIX. Extract locally and open index.html, then use navigation to reach work.html and contact.html. The ZIP includes a deterministic manifest identifying every asset digest and source-content hash, with no owner/release approval.

CI uses a separate synthetic example and tests **two distinct visual themes**, plus six genuine Chromium navigation paths (desktop, mobile, reduced motion). It verifies the inherited E5 unit tests, original code provenance, resource allowlists, all assets and manifest hashes, keyboard focus, no horizontal overflow and zero external requests. The tests do not measure real AI creative quality, real user acceptance, Windows device performance or genuine model inference.

**Boundaries:** No customer data, claimed success stories, real deployed site, cloud model use, real email service, owner approval, licensed real imagery, production integration, XERJ contribution or Stage45 release. Existing E4 quota PR #287, Runtime #23 draft stack, E6 work and protected main remain unchanged. E5 remains NOT CERTIFIED.
