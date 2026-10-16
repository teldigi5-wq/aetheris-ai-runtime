# Stage 46.25 — Remove production-side JVM worker symbol dependencies

**OPEN DRAFT / UNMERGED. All 13 Stage45 release blockers remain active. Production, real microphone, desktop actions and owner-PC pilot: NO_GO.**

## Motivation

In Stage 46.24 a physically separate Maven worker JAR was verified on hosted Linux and Windows using two canonical synthetic Java sources. However, two orchestrator-side classes referenced the worker symbol with compile-time Java references:

- `SyntraSyntheticHostLimits.command` called `SyntraSyntheticPcmWorkerMain.class.getName()`.
- `SyntraSyntheticProcessSpeechBoundary.spawnAndReview` referenced `SyntraSyntheticPcmWorkerMain.MAGIC` and `.PREFIX`.

The worker classes were already excluded from the normal Spring Boot JAR by Stage46.23, but remained in the orchestrator's main source set, so these symbolic source dependencies were a barrier to moving their source into the standalone fixture module.

## Incremental code change

Stage 46.25 changes **only two existing runtime-side Java classes**, and adds test/security evidence:

1. `SyntraSyntheticHostLimits` uses a fixed, compile-time `FIXED_PCM_WORKER_CLASS` string naming the explicitly allowlisted inert fixture. The class name cannot be chosen from input, model output, an environment variable or a file. The trusted fixture remains absent from the normal distributable Spring Boot JAR, so unapproved activation must reject the missing child rather than enabling another executable.
2. `SyntraSyntheticProcessSpeechBoundary` uses frozen synthetic wire constants `FIXED_PCM_MAGIC = 0x53463431` and `FIXED_PCM_PREFIX = "SYNTRA46_10_V1|"`. These previously existed as constants in the worker class; the new parent literals preserve the same bounded binary request and fixed output protocol. No user text or real speech/desktop data is used.
3. New JUnit `SyntheticWorkerProtocolContractTest` compares the parent-side class name, magic and protocol prefix against the **canonical test-only worker implementation** during tests and preserves the fixed child command. It also asserts there is no implicit Spring annotation on either worker.
4. New `stage46_worker_source_migration_preflight.py` evaluates both actual compiled Java `javap -verbose` constant pools and rejects a real `Class` constant reference to either reviewed worker entry point in the two runtime-side orchestrator classes. A source-level safety check alone is insufficient; the bytecode-level check confirms the production-side class loader is not required to link the missing worker class during startup.
5. The new adversarial Python tests detect accidental `.class` and constant references, changed protocol values, forged empty `javap` output, reintroduced ordinary-JAR worker entrypoints and release-authority confusion.

This is a **symbolic coupling refactor**, not a runtime sandbox, not a network egress proof and not a worker source migration. The two canonical worker `.java` files still live under `orchestrator-service/src/main/java` temporarily; the independent worker module recompiles them from that location. Seven other synthetic helpers still live in the normal orchestrator JAR.

## Exact-HEAD validation

CI must build the default Spring Boot JAR and independent test-only module, confirm both worker classes remain absent from the production archive, run `javap -verbose` on the compiled parent classes and compare against forbidden JVM `Class` constant-pool references, then run JUnit and adversarial policy tests. Hosted Windows Server 2022 must continue testing the actual restricted token and Job Object using `synthetic-worker-lab/target/classes`, plus AppContainer regression. Full orchestrator, workstation-agent and Stage45 policy gates must pass.

Test reproduction from a trusted repository checkout:

```bash
./mvnw -B -f synthetic-worker-lab/pom.xml -DskipTests clean package
./mvnw -B -f orchestrator-service/pom.xml -DskipTests clean package
python3 tools/security/stage46_worker_source_migration_preflight.py --jar-worker-mains 0
python3 -m unittest discover -s tools/security -p test_stage46_worker_source_migration_preflight.py -v
./mvnw -B -f orchestrator-service/pom.xml -Dtest=SyntheticWorkerProtocolContractTest test
```

**Never** interpret a passing protocol/bytecode test as voice activation permission. A subsequent separately reviewed stage may move the original two worker sources into the test-only module and update the normal compilation/test classpath, but only when full Windows/Linux regressions and existing release blockers remain intact. No branch merge, installer, artifact publication, microphone, physical owner-PC action or firewall setting is touched here.
