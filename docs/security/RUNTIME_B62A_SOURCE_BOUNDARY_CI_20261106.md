# S-B62-A pinned source-only trust boundary review

Prepared 6 November 2026. **TEST ONLY. Stage45 NO_GO; XERJ HOLD.**

This isolated branch adds a dependency-free scanner for the prior source inventory. It pins Platform to 0f296079241ca0c498e52ad0bafe259229c10735 and uses the triggering Runtime commit. It inspects four Compose files, the Runtime API quarantine and default-disabled settings, and Helm non-local safety / ingress guards.

It also runs eight intentionally broken in-memory variations. Public or implicit Compose host binds, unknown long-form ports, missing Runtime 404, missing decoded pathInfo, unsafe workspace-write default, widened Helm backend ingress, or a removed Helm image guard MUST fail.

The emitted JSON is explicitly SOURCE_ONLY_PASS and unconditionally states deployment_verified=false, fencing_verified=false and api_authorization_certified=false. No deployed service, Pod, credential, database, TLS key, coordinator or host was contacted. The results cannot replace independent CNI validation, real physical fencing, privileged rollback tests, owner/project authorization, owner-PC evidence or independent signoff.

Only the two new source-review files and this explanatory document are changed. Protected main and upstream Runtime / Platform security PRs remain untouched. S-B62-A is still IN PROGRESS.

## 6 November follow-up: canonical Compose rendering

The original source-text check cannot by itself detect YAML aliases, long-form port
mappings, or override effects. The exact-head workflow now additionally runs
`docker compose config --no-interpolate --no-env-resolution --format json` on
all three Platform Compose deployment variants and on the full+override merge.
It checks each parsed published port's host IP must be `127.0.0.1`, rejects
host networking and privileged containers in the rendered services, denies
datastore host publication, and requires reviewed internal service bindings.
Eight in-memory mutations of the rendered models must fail; this includes a
missing host IP, a wildcard, an unauthorized datastore publication and a
widened implicit new port. Nothing is started, deployed, modified, or given
credentials. This is still SOURCE_ONLY, not a run-time reachability test or
production attestation. No YAML text check is substituted for actual CNI tests.
