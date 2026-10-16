# Stage 46.24 — Standalone, dependency-free synthetic worker test module

**OPEN DRAFT, UNMERGED. This is an isolated CI lab artifact, not a signed release or a completed source-code relocation. Physical Windows owner-PC pilot and production remain NO_GO.**

## Motivation, evidence and boundaries

Stage 46.22 physically inspected the default Spring Boot archive and confirmed **nine synthetic Java helpers** were packaged, including two fixed executable subprocess main classes. Stage 46.23 changed the ordinary Boot JAR so it includes **zero of those two worker mains** while preserving both in the orchestrator's compiled test classpath. That build-only reduction passed the full Java and Windows regressions.

A direct move of the worker `.java` files is not yet safe: `SyntraSyntheticHostLimits` and `SyntraSyntheticProcessSpeechBoundary` reference one worker's Java class constants/name; the 111 targeted Java tests and the Windows Job/reduced-token harnesses depend on fixed classpaths. Relocation is planned, **not claimed** in this stage.

## Implemented incremental architecture

`synthetic-worker-lab/pom.xml` is a **separate, independently invoked Maven module with no parent POM, no declared dependencies and no aggregator/reaction link**. It is not a dependency of the Spring Boot orchestrator. Its configured Java 21 compiler and JAR include-list allow **exactly two canonical reviewed sources** from `orchestrator-service/src/main/java`, without duplicating or forking either source:

- `SyntraSyntheticPcmWorkerMain.java`: fixed 16 kHz synthetic PCM input and deterministic VOICE/SILENCE fixture outcomes; never real microphone, STT, TTS, model or OS command
- `SyntraSyntheticJobProbeMain.java`: fixed EXIT, FAIL, CHECK_ENV, STALL, TRY_CHILD test modes for the Windows Job Object's actual child/limit evidence

The Maven JAR contains only those two compiled classes plus standard archive directories/manifest, rather than the 75 transitive Spring Boot libraries or 1,084 shaded workstation classes observed previously. The module uses pinned Maven compiler, JAR, install and deploy plugin versions. Its install and deploy plugins specify `skip=true`; this is an additional safeguard, not a signed producer identity. No packaging rule adds this module to the default orchestrator application.

The existing Stage46.23 `synthetic-worker-lab` **Maven profile** of the orchestrator and the new `synthetic-worker-lab/` **standalone directory** are distinct. The older profile remains available as a backwards-compatibility fixture; the new separate module is tested independently in CI, without passing a test JAR into the Spring application.

## Proof required before calling the standalone module working

The dedicated exact-HEAD GitHub Actions workflow builds the standalone lab module **without building the orchestrator first**, then packages the default Spring Boot JAR independently. The new `stage46_independent_worker_module_gate.py` rejects any unexpected dependencies, extra source inclusion, changed Maven module IDs, default reactor wiring, ability to install/deploy the lab, unapproved JAR payload, duplicate ZIP entry, sensitive private keys or a reintroduced worker main in the ordinary Boot JAR.

A fixed synthetic subprocess check runs `java -cp synthetic-worker-lab/target/classes` with its sole allowed probe modes, then sends exactly 640 synthetic zero PCM bytes in the 16 kHz fixed binary protocol and requires `SYNTRA46_10_V1|SILENCE`. No user recording or device is accessed.

A **second, Windows Server 2022 CI job** builds the standalone module on Windows and supplies **`synthetic-worker-lab/target/classes` directly** to the unchanged native `Test-SyntraWindowsReducedTokenLab.ps1` and `Test-SyntraWindowsJobLab.ps1`. Its actual kernel Job limits, constrained environment, reduced process token, child-creation denial, timeouts and kill-on-close must remain passing. The preexisting native AppContainer laboratory regression is also run.

Separate CI jobs preserve all 111 targeted voice/STOP/owner tests (which are a subset of the full orchestrator tests), full orchestrator/workstation JUnit regressions and Stage45's **13 immutable production release blockers**. All binary outputs stay ephemeral within hosted CI; there is no release, upload or distribution.

## Reproduction on trusted disposable CI

```bash
./mvnw -B -f synthetic-worker-lab/pom.xml -DskipTests clean package
./mvnw -B -f orchestrator-service/pom.xml -DskipTests clean package
python3 tools/security/stage46_independent_worker_module_gate.py
python3 -m unittest discover -s tools/security -p test_stage46_independent_worker_module_gate.py -v
```

Windows only on **hosted disposable CI**: `Test-SyntraWindowsJobLab.ps1` and `Test-SyntraWindowsReducedTokenLab.ps1` each receive `-ClassesPath ./synthetic-worker-lab/target/classes`.

## Open issues / proper next source-migration milestone

This stage achieves an **independent build and test execution boundary**, not independent original source ownership or a production isolation guarantee. Both fixed source files are still in `orchestrator-service/src/main/java`, and seven other reviewed synthetic helpers remain in its distribution JAR. A subsequent controlled source migration must decouple hardcoded protocol symbols/references, move the canonical source files into the standalone module, update JUnit + Windows classpaths, explicitly check JAR exclusions and old Stage46 policy expectations, then rerun a complete merged candidate and hardware consent review.

The standalone module is also **not** a Windows network-egress sandbox, authenticated WFP process-denial proof, signed artifact, STT/TTS voice engine, owner voice authenticator or autonomous desktop agent. Physical owner-PC pilot, protected branch merging and production authorization remain **NO_GO** with all thirteen Stage45 blockers active.
