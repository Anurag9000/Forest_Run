# Forest Run — Recovery Evidence Maintenance Audit

Date: 2026-08-02  
Repository: `Anurag9000/Forest_Run`  
Canonical branch: `main`  
Starting head: `0cef4cc347e6a669b3156834ec604ff2da1a9df7`

## Objective

Close the persistence debt where Forest Run correctly retained corrupt or conflicting recovery evidence but exposed no deliberate inspection or remediation path.

The implementation had to preserve the existing fail-closed guarantees:

- never infer that unreadable evidence is safe to delete;
- never replay incrementing APIs when complete state snapshots exist;
- never let one recovery domain clear the other;
- never expose run or ghost payloads through support logs;
- never race active gameplay or ghost I/O with a support mutation;
- never turn remediation into an automatic release-build behavior.

## Implemented production surface

Added:

```text
RecoveryEvidenceDomain
RecoveryEvidenceState
RecoveryEvidenceSnapshot
RecoveryEvidenceReport
RecoveryDiscardDisposition
RecoveryDiscardResult
RecoveryEvidenceHandler
RecoveryEvidenceMaintenanceCoordinator
AndroidRecoveryEvidenceMaintenance
```

Production handlers:

```text
AndroidRunOutcomeEvidenceHandler
MaintenanceRunOutcomePersistenceSink
AndroidGhostPromotionEvidenceHandler
```

The maintenance coordinator requires exactly one handler for each domain:

```text
RUN_OUTCOME
GHOST_PROMOTION
```

## Operation separation

The API intentionally separates:

```text
inspect
safe recover
corrupt-evidence discard
unresolved-pending discard
```

### Safe recovery

Safe recovery leaves `CLEAN` and `CORRUPT` evidence unchanged. It retries `PENDING`, `BLOCKED`, and transient `IO_FAILURE` states through each domain's canonical recovery owner.

It never calls `clearEvidence()` merely because decoding or validation failed.

### Corrupt discard

`discardCorrupt(domain)` performs a fresh inspection and clears only confirmed `CORRUPT` evidence in the selected domain.

### Pending discard

`discardUnresolvedPending(domain)`:

1. performs a fresh inspection;
2. refuses clean, corrupt, or unreadable evidence;
3. retries canonical recovery;
4. returns `RECOVERED_INSTEAD` if recovery succeeds;
5. clears only evidence that remains confirmed `PENDING` or `BLOCKED`;
6. verifies the domain reports `CLEAN` afterward.

The destructive operation removes recovery evidence only. It does not reset current progression state or delete durable ghost artifacts.

## Domain isolation

The run-outcome handler deliberately does not construct `AndroidRunOutcomePersistenceSink` because that production adapter also initializes ghost recovery.

Instead, `MaintenanceRunOutcomePersistenceSink` exposes only the complete non-ghost recovery surfaces:

- forest mood state;
- return state;
- atomic summary and route snapshot;
- route-count reads.

Its ghost publication method returns false and is not reached by constructor recovery.

The ghost handler uses only the receipt store, ghost artifact adapter, and ghost recovery coordinator. It never opens the run-outcome SharedPreferences journal.

## Support output privacy

`RecoveryEvidenceReport.supportSummary()` contains only:

```text
domain name
state enum
fixed detail code
```

It excludes:

- scores and distances;
- run quotes;
- entity identity;
- timestamps and counters;
- ghost frames;
- fingerprints.

## Debug command surface

`MainActivity` now accepts the following debug-only intent extras:

```text
recovery_action = inspect | recover | discard_corrupt | discard_pending
recovery_domain = RUN_OUTCOME | GHOST_PROMOTION
```

Processing order during cold start:

```text
SaveIntegrityManager.repair
→ recovery maintenance command
→ FeedbackSettings initialization
→ release asset validation
→ GameView construction
```

The application must carry `ApplicationInfo.FLAG_DEBUGGABLE`. Release/non-debuggable builds reject the command before constructing maintenance handlers.

Both extras are removed in `finally`, including all rejection branches.

## Safety findings during implementation

### Finding 1 — expression exhaustiveness risk

The first run-handler implementation wrapped `inspect()` in a redundant type-test `when` expression. Focused compiler review showed this shape was unnecessary and potentially non-exhaustive.

Correction:

```text
val after = inspect()
→ exhaustive when(after.state)
```

### Finding 2 — read failure could authorize deletion

The first `discardUnresolvedPending` draft allowed an `IO_FAILURE` result to fall through to `clearEvidence()`.

That violated fail-closed behavior because unreadable evidence is unknown, not confirmed pending.

Correction:

```text
before IO_FAILURE  → IO_FAILURE, no retry, no clear
after-retry IO_FAILURE → IO_FAILURE, no clear
```

The pure policy test and source contract lock this rule.

### Finding 3 — live safe recovery could race ghost I/O

The first Activity hook allowed `recover` through `onNewIntent` while a reused `singleTask` Activity was live.

Although recovery is non-destructive in intent, it mutates receipts and journals. Ghost receipt recovery could race the active single-worker transaction, and run-outcome recovery could race a terminal commit.

Correction:

```text
live reused Activity → inspect only
cold onCreate        → inspect, recover, discard_corrupt, discard_pending
```

Recover and discard commands now reject with `reason=active_session` before constructing maintenance.

## Tests added

### Pure policy

`RecoveryEvidenceMaintenanceCoordinatorTest` covers:

- missing/duplicate domain-handler rejection;
- independent domain inspection;
- payload-free support summary;
- clean/corrupt safe-recovery skipping;
- retry of pending/transient states;
- selected-domain corrupt discard;
- pending evidence refusal by corrupt discard;
- recovery before pending deletion;
- confirmed blocked evidence deletion;
- no deletion after read failure;
- failed clear reporting.

### Android integration

`RecoveryEvidenceMaintenanceIntegrationTest` covers:

- clean installation inspection;
- complete run-outcome journal recovery;
- corrupt run journal retention under safe retry;
- explicit corrupt run journal removal;
- conflicting valid journal retry before evidence-only discard;
- preservation of conflicting live state;
- matching ghost receipt distance repair;
- corrupt ghost receipt isolation and removal.

### Source contracts

Added:

- `test_recovery_evidence_maintenance_contract.py`;
- `test_recovery_maintenance_launch_contract.py`.

They lock operation separation, domain isolation, no-delete-on-I/O, support-summary privacy, debug gating, cold-start ordering, inspection-only live intents, domain validation, and one-shot extras.

## Focused validation performed

### Kotlin policy harness

Compiled and executed the policy core with recording handlers.

Passed:

```text
recovery evidence maintenance policy checks passed
```

Covered:

- constructor cardinality;
- independent safe retry;
- corrupt-only deletion;
- recover-before-discard;
- confirmed blocked discard;
- I/O fail-closed behavior.

### Production-shaped compilation

Compiled the Android maintenance handler and sink surface against focused production-shaped stubs.

This checked:

- cross-package internal imports;
- `RecoverableRunOutcomePersistenceSink` conformance;
- summary snapshot ownership;
- ghost receipt disposition mapping;
- constructor and method signatures.

### Launch harness

Compiled and executed the maintenance intent parser and dispatcher.

Passed:

```text
recovery maintenance launch checks passed
```

Covered:

- live inspection;
- live mutation rejection;
- cold-start domain routing;
- non-debuggable rejection before maintenance construction;
- one-shot extra removal.

### Exact diff inspection

`MainActivity` changes were limited to:

- two imports;
- maintenance constants;
- one cold-start call;
- one `onNewIntent` call;
- command parsing, logging, domain parsing, and debug gating helpers.

No game-loop, rendering, input, collision, lifecycle shutdown, audio, haptic, safe-area, scenario, or Surface code changed.

## Evidence boundary

Not executed in an exact-head Android Gradle environment:

- complete JVM/JUnit suite;
- Robolectric suite;
- Android lint;
- debug/release builds;
- connected emulator;
- physical-device ADB command acceptance.

The connector exposed no successful exact-head status or workflow evidence during this tranche. These absences are not treated as green CI.

## Remaining limitations

- remediation is debug/support tooling, not an end-user settings UI;
- release builds intentionally reject maintenance intents;
- evidence discard can preserve a live state that is semantically wrong but cannot be safely inferred from conflicting evidence;
- no signed diagnostic export or cryptographic evidence identity exists;
- active save/ghost namespace switching while a maintenance instance exists remains unsupported;
- physical-device ADB acceptance remains outstanding.
