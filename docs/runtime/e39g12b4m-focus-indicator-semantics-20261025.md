# E3.9G-12B4M — Visible link vs visible keyboard-focus indicator

**25 October 2026.** Offline, trusted-fixture, real Chromium negative-control investigation. **No new model inference, untrusted model HTML rendering, policy approvals or release certification.**

## Research finding and reason to investigate
Genuine B4L trial [PR #196](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/196), [Actions run 40164465244](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40164465244), exact head `cf433308851c9fcbef0ca2bde467b4a49a56c7ad`: 5/5 jobs green, two real Qwen2.5-Coder 7B inferences with source-only selector hints, **1/2** exact-source policy accepted and after-browser verified (reduced-motion). The keyboard-focus patch was **policy rejected**, not browser tested. Its real archived model response targeted the correct existing rule but proposed `.skip:focus{top:0;}` rather than reviewed `.skip:focus{top:16px;}`.

- B4L artifact `b4l-untrusted-genuine-qwen7b-hinted-replies`, Actions artifact ID `12587299418`.
- The genuine focus reply SHA-256 is `51d092cbb0181fa010fe656382833ab8762a15d152aec297ac2e8d8fb29645d6`; the original broken focus HTML SHA-256 is `385d3fac74d775586bb389beb3068e6e34a7fb0e08f8d854c80cb55b433b290b`.
- B4M does not resubmit or execute that model reply. It recreates **only the exact CSS literal** `top:0` against the reviewed static local source, under no arbitrary-file arguments.

The older browser assertion checked that the focused link rectangle appeared inside the viewport. That does not guarantee the **focus indicator** around the link is itself fully on screen. On the reviewed fixture, `outline:3px solid` with `outline-offset:4px` extends **7 pixels beyond** the element. If the link is at `top:0`, its indicator is clipped at the upper viewport boundary. This is an independent semantic concern beyond byte-exact answer matching.

## Five carefully scoped controls

| Static trusted variant | Focused link in viewport | Entire 3px + 4px keyboard indicator inside viewport | Prior B4J policy |
| --- | --- | --- | --- |
| Broken `top:-120px` | no | no | denied |
| Archived real model suggestion `top:0` | expected yes | expected no | denied |
| Near-miss `top:6px` | expected yes | expected no | denied |
| Semantic alternative `top:8px` | expected yes | expected yes | **not approved** by frozen B4J |
| Previously reviewed reference `top:16px` | expected yes | expected yes | approved on prior B4L motion-only path; for focus this is authorized **if generated** |

**Verified by real Chromium, not just inferred:** [exact run 40165193685](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40165193685) succeeded **3/3 CI jobs** (Windows, Ubuntu, real Chromium). The **25/25 controlled browser observations** measured all five CSS variants at 320/390/768/1280/1920px. `top:0` and `top:6px` left the focus outline clipped; `top:8px` and `top:16px` kept the 3px+4px outline inside the viewport. An alternative with browser-visible focus still receives **no patch-policy approval** from B4M. Neither B4J nor the historical B4L 1/2 score changed.

## Actual test execution
`tools/enterprise/e3_factory/e39g12b4m_focus_ring_chromium.mjs` hardcodes the fixture and only these five source literals. At 320, 390, 768, 1280 and 1920 px, Playwright Chromium must observe keyboard Tab focus, `:focus-visible`, outline width/offset, full element bounds **plus outline halo bounds**, overflow, page errors and blocked external requests. It captures 25 evidence screenshots/hashes and a diagnostic report. JavaScript and service workers are disabled; page navigation uses `about:blank` and `page.setContent`, and there is no model-call or external site loading path. The CI artifact is public synthetic test evidence, not private client data.

The static regressions also verify source digest, the actual previously rejected reply digest, one-rule-only mutation, no arbitrary node/shell/network operations, frozen B4J rules and default-off agent skills. Windows/Ubuntu checks are independent of the real Chromium Ubuntu job.

## Quality/architecture implications
- **Never weaken or retroactively rescore** B4L or the frozen exact-source B4J allowlist. Its historical result remains **1/2 verified**.
- A future, separately reviewed semantic-equivalence policy could admit safe alternatives only when the isolated patch executor proves no other change, a *full focus indicator visibility* invariant succeeds at realistic screen sizes, and accessibility/keyboard/regression acceptance is independently inspected. This proof by itself does **not** approve new arbitrary CSS, HTML or Javascript edits.
- Accessibility success on one synthetic focus control does not prove WCAG 2.2 AA across sites. Broader screen-reader use, reverse keyboard navigation, contrast, high zoom, scrolling and focus-obscured tests are separate.
- The eventual independent human-held-out benchmark still needs private newly curated challenges, robust site-specific policies, no-golden model inputs and real after-browser evaluations. **E3 remains active, E4–E9 and all nine release gates remain pending; Stage45 stays NO_GO.**
