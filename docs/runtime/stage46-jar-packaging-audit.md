# Stage 46.22 — Built JAR classpath and dependency packaging audit

**OPEN DRAFT; NO MERGE, NO PRODUCTION RELEASE, NO HARDWARE ACTIVATION.**

## Engineering question

Stage 46.21 proved that **nine synthetic voice-related Java classes**, including two executable `main(String[] args)` worker fixtures, exist in the orchestrator's **`src/main/java`** source set. That finding cannot be resolved by assuming the absence of a Spring `@Service` annotation prevents packaging or standalone execution. Stage 46.22 inspects the actual JARs built using **the repository's current Maven configuration** rather than claiming test-only separation that has not occurred.

The orchestrator currently uses Spring Boot 3.3.3 `spring-boot-maven-plugin`, Java 21, output timestamp `2020-01-01T00:00:00Z`, and a version `0.2.0-SNAPSHOT` executable archive. The workstation agent uses `maven-shade-plugin` with its main entry point `io.aetheris.workstation.AetherisHostAgent`; its fixture audit targets the `aetheris-host-agent.jar` shaded output. The precise Maven dependencies and compiled outputs must be observed in CI rather than guessed.

## Implemented tests and guardrails

The dedicated exact-commit GitHub Actions build produces both local JARs in **disposable hosted CI** using Maven `-DskipTests package`, then checks the archives with `tools/security/stage46_jar_packaging_evidence.py`. This is a **read-only** ZIP central-directory inspection: it computes hashes of each complete built archive, reads entry metadata, and **never extracts, executes, installs, deploys or uploads a binary**.

The gate requires:

1. Actual Spring Boot archive layout (`BOOT-INF/classes/`, `BOOT-INF/lib/`, `META-INF/MANIFEST.MF`) and shaded workstation host entry point. It measures the *actual nested dependency count* instead of inventing a claim.
2. All nine reviewed synthetic voice class files and the two fixed child-process `main` classes to remain discoverable **for an evidence-only baseline**. If packaging changes, CI must fail until the expected separation strategy is independently reviewed and explicitly updated. The gate deliberately reports `synthetic_classes_are_excluded_from_orchestrator_jar=false` and `production_authorized=false`. **This is an exposure finding, not approval to ship those classes.**
3. No checked test-only JUnit/Mockito/H2 dependency JARs, `*Test.class` bytecode, key/certificate private material, `.env` files or Windows lab native executable/script payloads in the inspected JAR entries. These are limited heuristics: not a comprehensive SBOM, vulnerability scan, key-leak detector or bytecode security audit.
4. ZIP entry schema controls reject duplicate entry names, path traversal, encrypted entries, unexpected input types and oversized declared uncompressed totals. A non-existent/corrupt JAR or invalid manifest cannot pass.
5. Adversarial Python unit tests verify test dependency leakage, explicit artifact exposure, malformed/altered archive entries, forged release claims and every non-authorization invariant. The immutable Stage45 13-blocker policy is checked separately.

## Separating synthetic functionality safely — next step, not executed

Moving these classes into `src/test/java` without a proper module/fixture plan may break the Stage46.10 subprocess classpath or the real Windows native Job Object tests. No class was relocated, excluded or deleted here.

The minimal-change approach for an independently approved follow-up is a dedicated **test-scoped synthetic voice worker module** that produces a test-only runnable artifact for Windows CI, with an explicit fixed classpath and opt-in fixture launcher. A reviewer must then validate that the normal Spring Boot orchestrator JAR contains no test-only main classes, review dependency and `META-INF/services` contents, and ensure all existing 111 targeted voice/owner/STOP tests, all full orchestrator tests, workstation regression and native Windows tests remain green. Until then, the runtime continues to compile synthetic classes, and the policy classifies them as **not release-approved**.

## Reproduction

```bash
./mvnw -B -f orchestrator-service/pom.xml -DskipTests package
./mvnw -B -f workstation-agent/pom.xml -DskipTests package
python3 tools/security/stage46_jar_packaging_evidence.py \
  --spec docs/runtime/stage46-minimal-integration-scope.json \
  --orchestrator-jar orchestrator-service/target/orchestrator-service-0.2.0-SNAPSHOT.jar \
  --workstation-jar workstation-agent/target/aetheris-host-agent.jar
python3 -m unittest discover -s tools/security -p test_stage46_jar_packaging_evidence.py -v
```

The complete Stage46.22 CI also reruns Stage46.21 production-source wiring review, Windows synthetic Job Object/reduced-token/AppContainer checks, targeted and complete Java orchestrator/workstation regressions, and a read-only `main` Git merge-tree simulation.

**The JARs are unsigned build evidence, not independently approved releases. No physical PC, microphone, network firewall, desktop agent, branch merge or production deployment is authorized. All 13 Stage45 release blockers and NO_GO decision remain active.**
