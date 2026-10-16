# Stage 46.21 — Synthetic source boundary and minimal integration-candidate review

**STATUS: OPEN DRAFT / SYNTHETIC TEST REVIEW ONLY. NO MERGE, RELEASE, OWNER-PC TEST OR PRODUCTION DEPLOYMENT. ALL THIRTEEN STAGE 45 BLOCKERS ACTIVE; NO_GO.**

## Source-grounded finding

The accumulated security stages **46.6–46.20** are contained in **15 stacked draft PRs #72–#86**. Their exact PR patches were inspected before writing this review. Stage 46.20's exact-HEAD CI checked **317 changed paths** against protected `main` with a conflict-free Git merge-tree simulation; this was not an approved integration or deployed release. Stage 46.21 reviews the **actual compiled source inventory**, not just GitHub branch names.

Nine experimental/synthetic Java classes from PRs #72–#78 and the earlier synthetic audio-gate stage are located under `orchestrator-service/src/main/java/io/aetheris/orchestrator/voice/`. **These classes are compiled in the main orchestrator build; the directory alone does not prevent them from being included in a normal runtime JAR.** A missing Spring annotation does not independently guarantee unreachable execution. The gate protects against several specific unexpected wiring patterns but is not a full bytecode/call-graph or dynamic security audit.

## Curated file classification

| Inventory group | Observed purpose | Production conclusion |
|---|---|---|
| Nine `SyntraOffline*`, `SyntraVoiceReadOnly*` and `SyntraSynthetic*` Java helpers in `src/main/java` | Simulated artifact review, bounded voice-state and process/STOP fixtures | **Compiled with main runtime; manual packaging/isolation review REQUIRED** |
| Java `src/test/java` voice and owner-bound tests | JVM unit and synthetic subprocess regression evidence | Test sources, not proof of production safety |
| `workstation-agent/packaging/stage46/*` | Windows native Job Object, AppContainer, reduced-token, WFP and unsigned worker build fixtures | **CI laboratory only**, not endorsed for an end-user installer |
| `tools/security/stage46_*.py` | Non-authorizing evidence parsing and release NO_GO policies | CI review tools, not active enforcement in deployed host |
| `tools/security/test_stage46_*.py` | Adversarial policy contract tests | Review/test scope only |
| `.github/workflows/runtime-stage46-*.yml` | Hosted Windows/Linux synthetic test execution | CI-only workflows; must not be treated as runtime policy |
| `docs/runtime/stage46-*` | Implementation notes, design constraints and signed worker release checklists | Documentation, not verified production implementation |
| `workstation-agent/packaging/install.ps1` and orchestrator Maven assembly | Existing production-adjacent packaging/launcher surfaces | **Still require independent packaging, startup and transitive dependency review** |

Specific compiled Java inventory is machine-pinned in [`stage46-minimal-integration-scope.json`](stage46-minimal-integration-scope.json), including **two main-entrypoint child-process fixtures**:

- `SyntraSyntheticPcmWorkerMain.java`
- `SyntraSyntheticJobProbeMain.java`

Seven additional reviewed classes are `SyntraOfflineSpeechArtifactReview`, `SyntraOfflineVoiceTurnCoordinator`, `SyntraVoiceReadOnlyConversationBridge`, `SyntraSyntheticAsyncVoiceBoundary`, `SyntraSyntheticAudioGate`, `SyntraSyntheticHostLimits`, and `SyntraSyntheticProcessSpeechBoundary`.

### New mandatory automated detection

`tools/security/stage46_integration_surface_gate.py` traverses the actual orchestrator Java source tree and existing PowerShell packaging scripts. It requires the nine-class reviewed inventory **without silently adding newly introduced synthetic helpers**, detects Spring runtime component/controller/service/bean registration or unsafe audio/native access inside those classes, enforces exactly the two known child-process entry points, and checks non-laboratory `src/main/java` code for references to the reviewed synthetic class names. Existing workstation packaging scripts outside `stage46` cannot call the experimental scripts/worker names without failing CI.

These are **conservative lexical checks**, not proof that a class is unreachable through reflection, dependency injection from external libraries, Java serialization, bytecode instrumentation, classpath scanning outside the reviewed scope or an attacker-supplied launcher. The observed inventory always reports `compiled_on_main_classpath=true` and `excluded_from_production_artifact=false`. Any reported warning must be reviewed and not waived without a new explicitly approved design. The gate always returns `decision=NO_GO`.

Adversarial tests deliberately inject malicious Spring annotations, previously unapproved entry points, Java sound/desktop imports, production-class callers, installer script references, fabricated review flags and modified manifest counts. A passing CI result confirms only the stated source-review contract.

## Minimal future integration candidate — NOT CREATED HERE

1. **Extract only genuinely working code**, keeping production APIs narrow and owner-bound. Preserve existing authorization, STOP, signed-audit and model/voice consent semantics. Avoid bundling experimental Windows WFP tools as real network protection.
2. **Separate synthetic fixture entry points from runtime distribution**, potentially moving synthetic-only workers to test-scoped modules and supplying explicit fixed command wiring in CI. This requires an independent build/compatibility review: simply moving files risks breaking the currently passing Java and Windows harness. No file moves were made in this stage.
3. **Inspect the actual packaged artifact** using an approved build reproducibility and dependency inventory test, including JAR contents, service-provider files, Spring registrations, transitive native libraries, OS script entry points and Windows startup behavior. This stage's source scan does **not** inspect the final JAR; packaging evidence remains a blocker.
4. **Review both sides of the application boundary**: existing host installer, orchestrator Maven dependency graph, signed release manifests and the real owner/STOP lifecycle. Do not activate any microphone, model, desktop action or WFP policy before corresponding Stage45 blockers close with independently validated evidence.
5. **Security review and controlled consolidation**: classify the 15 accumulated draft PRs into production functionality, tests, lab fixtures and documentation. Seek explicit independent architectural and security signoff on a minimized patch set, then create a new integration candidate and run protected-branch CI, OS-specific tests, backup/recovery and source provenance checks. Never auto-merge just because a simulated Git merge was conflict-free.

## Reproduce source review

```bash
python3 tools/security/stage46_integration_surface_gate.py
python3 -m unittest discover -s tools/security -p test_stage46_integration_surface_gate.py -v
python3 tools/security/stage45_pre_pilot_gate.py
```

The exact-HEAD CI also reruns existing Windows process-isolation tests, targeted synthetic voice/STOP cases, complete orchestrator/workstation JUnit tests and Stage46.20's read-only main merge-tree analysis.

**Limitations remain explicit:** Main-classpath presence, untrusted unsigned binaries, no independent provenance, uncorrelated WFP events, absent real microphone/STT/TTS and unresolved owner, restore, host/OS isolation, STOP and independent review are incompatible with approval for production or a physical Windows-PC pilot. **No deployment, merging, model download, installation, hardware testing, firewall changes or autonomous desktop commands were performed.**
