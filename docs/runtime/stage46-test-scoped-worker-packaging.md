# Stage 46.23 — Opt-in, nonattached synthetic worker fixture packaging

**OPEN DRAFT, UNMERGED. No production speech engine, owner-PC pilot, installation, release, firewall changes or microphone access. All 13 Stage45 blockers remain NO_GO.**

## Source-grounded packaging change

Stage 46.22's exact CI build demonstrated that the ordinary Spring Boot orchestrator JAR contained **nine synthetic voice-related Java helper classes, including both standalone worker `main` entry points**. This was a genuine packaging exposure finding, not just source-code speculation.

This stage changes only `orchestrator-service/pom.xml` packaging, not the behavior of compiled Java classes, libraries, existing Spring registrations, workstation startup or Windows/Java tests.

- The pinned Apache `maven-jar-plugin:3.5.1` `default-jar` execution explicitly **excludes two specific class files** from the ordinary orchestrator JAR before Spring Boot repackage: `SyntraSyntheticPcmWorkerMain.class` and `SyntraSyntheticJobProbeMain.class`. Both remain in the **compiled `target/classes` directory** to preserve JUnit synthetic worker/STOP, Linux host-limits and hosted Windows reduced-token/Job Object/AppContainer tests.
- The new Maven **opt-in profile `synthetic-worker-lab`** creates `orchestrator-service-0.2.0-SNAPSHOT-synthetic-worker-lab-only.jar` containing **only those two fixed class files**, when explicitly built using `-Psynthetic-worker-lab`. Its execution uses **`<attach>false</attach>`** with a plugin that supports that setting, so the lab fixture is not attached to the Maven project for install/deploy. The profile is **not active by default**. The CI job never uploads/publishes an artifact or runs Maven install/deploy.
- The new `tools/security/stage46_test_scoped_worker_packaging_gate.py` checks both **actual packaged JAR ZIP inventories**, current POM configuration, all nine original main source names, the preserved two compiled classes, and the absence of a lab-classifier JAR after the normal default build. A real physical release is neither tested nor authorized.
- The main Boot JAR is expected to retain the other **seven synthetic helper classes**. This is **not full sandboxing** or complete experimental code removal, and direct use of the remaining process boundary outside its test classpath can now reject a worker request when the child class is absent. No real speech pipeline is enabled or approved.
- Adversarial Python tests reject a worker main reappearing inside the standard JAR, inclusion of unapproved fixture bytecode, missing compiled testing fixtures, Maven profile default activation, attached fixture artifacts, sensitive resources, forged release approval and malicious archive entries.

## Why this avoids destabilizing verified tests

The test fixtures stay in the same `src/main/java` source set **temporarily**, so the target Java test classpath and existing Windows `-ClassesPath orchestrator-service/target/classes` remain unchanged. The production JAR is deliberately a narrower delivery surface than Maven's test compilation output.

This is a **build-time packaging separation**, not a fully independent Maven module. A true independent worker module/test source migration must be designed and regression-tested before moving or deleting these two classes. Nothing in this stage creates a signed, runnable owner-PC app, a full Windows sandbox or a verified voice system.

## Exact-commit CI verification requirements

On Linux hosted CI:
```bash
./mvnw -B -f orchestrator-service/pom.xml -DskipTests clean package
# The default build must NOT create any *synthetic-worker-lab-only.jar
./mvnw -B -f orchestrator-service/pom.xml -Psynthetic-worker-lab -DskipTests package
python3 tools/security/stage46_test_scoped_worker_packaging_gate.py \
  --spec docs/runtime/stage46-minimal-integration-scope.json \
  --pom orchestrator-service/pom.xml \
  --orchestrator-jar orchestrator-service/target/orchestrator-service-0.2.0-SNAPSHOT.jar \
  --lab-fixture-jar orchestrator-service/target/orchestrator-service-0.2.0-SNAPSHOT-synthetic-worker-lab-only.jar \
  --compiled-classes-dir orchestrator-service/target/classes \
  --default-build-fixture-present false
```

Other independent CI jobs must preserve the existing 111 targeted synthetic voice/STOP/owner tests, full 685-case orchestrator suite (the 111 are included), workstation JUnit, real hosted Windows Job Object/reduced-token/AppContainer tests, Stage46.21 Java/installer inventory, and Stage45's immutable **13 release blockers**.

## No release implications

An unattached opt-in classified archive is still an **unsigned untrusted local test artifact**. No independently approved signing chain, provenance, verified Windows WFP egress isolation, actual real microphone/STT/TTS, durable owner identity, desktop automation, OS/stop certification, release artifact or independent security signoff. An unapproved packaging-classpath change **must not** automatically merge to protected main. This PR remains **OPEN DRAFT and UNMERGED; production and physical Windows-PC pilot: NO_GO.**

Apache Maven reference: <https://maven.apache.org/plugins/maven-jar-plugin/jar-mojo.html> — the jar plugin's `attach` option (supported from 3.5.0) controls whether classifier artifacts become installation/deployment attachments.
