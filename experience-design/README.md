# Aetheris — Flagship Experience design reviews & safe journey handoff

**Purpose:** turn the Wayfarer-inspired design quality bar into reusable, review-only Aetheris knowledge and an independently testable offline state-handoff contract. This branch does **not** modify or deploy the actual Wayfarer Journey AI Vercel project. Wayfarer's production source is not the same as the separate `wayfarer-world-travel` repository.

## Three read-only experience skills

- `realistic-globe-experience-review`: Earth accuracy, real geography, cinematic but restrained camera movement, keyboard/touch controls, 2D fallback and GPU-budget review.
- `agent-journey-trust-review`: source timestamps, real tool status (not fake progress), estimates vs AI suggestions, verification labels, consent and multi-agent UI continuity.
- `arrival-companion-ux-review`: after-landing first-hour UX, practical travel evidence, responsive and offline assistance, source freshness and user approval.

These are added to Aetheris's **existing** strict Agent Skills manifest; it now contains thirteen `TEMPLATE_ONLY` definitions. They are not callable live agents and no new tool grants, credentials or network hosts were added.

## Connected journey contract — actual tested code, NOT deployed

The stdlib-only `connected-journey.mjs` reference module offers:

- `createDraft(...)`: validate a minimal travel brief and initialize stage `discover` with only `NOT_WIRED`, `NOT_AVAILABLE` and `NOT_ASSERTED` truth values.
- `appendEvidence(...)`: label provider-reported context separately from estimates and AI suggestions. Real provider responses require source URL and observation time; **they do not imply independently verified fares or rooms**.
- `advance(...)`: allow only Discover → Plan → Arrive → Explore, one step at a time, with explicit traveler confirmation. Arrival needs an approved itinerary reference and user-supplied checklist. No automatic booking or agent work.
- Strict field allowlists, bounded evidence, immutable return values and no local file, network or API dependencies.

This module is a reference pattern for future integration, not confirmation that Wayfarer has persisted synchronized data. It intentionally does not implement itinerary generation, global flight availability, payment, live hotel booking or maps.

## 12 external design/library candidates: NOT INSTALLED

A strictly curated source catalog records MapLibre, react-map-gl, deck.gl, react-globe.gl, React Three Fiber, Motion, GSAP, dnd-kit, Zod, TanStack Query, Playwright and axe-core.

- All are **research entries**, not fetched packages, runtime dependencies or already-available tools.
- Reported GitHub license metadata is not independent approval of the project, fonts, textures, tile providers or other assets.
- A globe must use correct geographic data and appropriately licensed Earth imagery rather than generic wireframes.
- Production map tiles, API keys, global inventory and model inference may have external provider limits/costs. An open-source library does not make its hosted data service unlimited or free.
- Any future tool integration needs an exact pinned revision, license and dependency review, permission boundaries, tests and owner approval.

## Flagship design acceptance criteria (Wayfarer inspiration, project separate)

1. **Discover / Globe:** meaningful destination exploration with semantic 2D list/map fallback, no invented destination ratings, reduced-motion and low-end device support.
2. **Plan / Agent Studio:** genuinely operational sources and tool calls (not hardcoded agent responses); reports preserve `provider observed`, `estimated`, `AI suggestion`, `unavailable` provenance. Editable itinerary and budget input should hand off under user control.
3. **Arrive / Arrival Companion:** consented itinerary, airport transfer research, nearby hotel/destination context, saved first-hour checklist and source freshness, graceful offline behavior.
4. **Explore:** continue without losing destination/route context, but do not claim that live providers are available when they're not.
5. **Design QA:** purposeful motion, readable booking comparisons, WCAG 2.2 AA target, keyboard access, mobile thumb use and real browser tests before launch.

## Offline CI

Run with a CI-capable machine without installing new npm packages:

    python scripts/validate_agent_skills.py
    python scripts/validate_flagship_sources.py
    python -m unittest discover -s tests -p 'test_flagship_design_safety.py' -v
    node --test tests/test_connected_journey.mjs

All current project checks and protected `main` review gates remain mandatory. The owner PC status stays `BLOCKED_PENDING_HARDWARE`.
