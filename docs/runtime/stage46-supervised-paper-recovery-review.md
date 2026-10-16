# Stage 46.33 — Supervised, read-only PAPER recovery evidence review

**OPEN DRAFT · UNMERGED · NO_GO · ALL 13 Stage45 release blockers remain active.**

## Existing evidence and the gap closed

The Java paper-trading subsystem already stores a paper account, open positions, marked-equity history, an irreversible 8% drawdown halt, a durable offline synthetic message replay anchor, and a **read-only** externally presented checkpoint comparator. Stage46.32 demonstrated *fixture-based* detection of restored or missing replay anchors. The rollback signature key is **public CI test material**, not trusted external custody or a production owner identity.

Previously, a checkpoint match did not establish that the rest of the Java paper portfolio was internally consistent, nor that an observed market mark remained fresh. The new `PaperRecoveryReviewService` provides one bounded, **non-mutating** assessment across these existing repositories. It has no controller, no recovery-write method and no link to the Python Quant testnet execution loop.

## Implemented assessment

The service reads the existing `paper-default` synthetic state and reports the following independent conditions:

- Presented checkpoint HMAC and current replay anchor: signature match against a supplied **public test key**, missing/stale DB anchor, mismatched sequence or hash, restored old checkpoint, unexpected DB advancement. The trust root is **always labelled unverified**.
- Actual Java paper account: presence, fixed account identity, positive finite starting/current cash, finite realized P&L, valid timestamp.
- Latest persisted paper market-equity mark: observed no more than **120 seconds** before the independently supplied assessment clock; coherent cash, equity = cash + unrealized P&L, gross exposure and count.
- Actual open position set: no unsupported side, bad stops, non-finite entry/qty/notional, inconsistent `entry × quantity` arithmetic or excessive count. Their aggregate notional must agree with the mark.
- Persistent drawdown guard: noncorrupt state, **not latched**, actual marked-equity timestamp, marked equity agreement and bounded clock skew. A halt is **never cleared**, even when a subsequent portfolio valuation appears healthy.

A structurally consistent *synthetic* fixture is labelled `SYNTHETIC_EVIDENCE_CONSISTENT_REQUIRES_OWNER_REVIEW`, and `evidenceEligibleForIndependentHumanReview=true` only flags that a person may examine it. **This is not an approval**: even that case carries `INDEPENDENT_CHECKPOINT_CUSTODY_UNVERIFIED`. Every response always declares `stateMutated=false`, `drawdownCircuitReset=false`, `ownerApprovedRecovery=false`, `testnetOrdersAuthorized=false`, `liveMoneyAuthorized=false`, `productionAuthorized=false`, and `decision=NO_GO`.

Any missing/corrupt/unmatched material generates `SYNTHETIC_RECOVERY_BLOCKED` with explicit safe-to-display reason codes. No snapshot, balance, risk-policy, replay anchor, checkpoint, market mark or drawdown row is updated.

## Honest constraints

- **No atomic system-wide reconciliation.** The existing checkpoint comparator runs its own transaction; account, positions, mark and guard are read as a separate service evaluation. They are not held under a single serializable lock shared with trading decisions. A full transaction-serializable snapshot, multi-service locking, authenticated event transport, durable external anti-rollback anchor and backup/restore tests remain necessary.
- **No trusted authorization.** Existing synthetic receipt signatures and their keys are PUBLIC TEST FIXTURES. There is no owner authentication, external custody, HSM/secret provisioning, explicit authorized restoration consent or approval record.
- **No automatic recovery.** The service does not reset a latched risk circuit, clear open positions, change account equity, import Python Quant trading state, perform position reconciliation against Binance, send orders or certify profitable strategies.
- **No continuous market collection.** A 120-second freshness check only compares timestamps supplied to this service. It cannot establish the provenance of a market quote, prove time synchronization, reconcile slippage or eliminate survivorship bias. The actual owner PC and exchange API remain untouched.
- **No release authorization.** This is evidence collection for developer review, not launch readiness. Protected merge, model/device activation, autonomous desktop commands, live/testnet money, and the physical owner-PC pilot remain NO_GO.

## Reproduce in disposable CI

```bash
./mvnw -B -f orchestrator-service/pom.xml -Dtest=PaperRecoveryReviewIntegrationTest test
./mvnw -B -f orchestrator-service/pom.xml -Dtest=OfflinePaperRollbackCheckpointIntegrationTest,OfflinePaperReplayLedgerIntegrationTest test
python3 -m unittest discover -s aetheris-quant/tests -p test_offline_replay_checkpoint.py -v
```

Stage46.33 exact-HEAD CI also runs the complete orchestrator and workstation Java suites, prior Python cross-language paper/host-denial tests, hosted Windows reduced-token / Job Object / AppContainer labs, and all 13 immutable Stage45 production NO_GO blockers.

**Next sensible work:** lock and validate a truly atomic *paper-only* recovery candidate using a database snapshot, explicit external owner authorization, and independent audit evidence. Do not build or wire exchange execution until all release blockers and a separate financial-security review are resolved.
