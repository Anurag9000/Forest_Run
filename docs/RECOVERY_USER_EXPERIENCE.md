# Recovery evidence user experience

Forest Run maintains two independent recovery domains:

- run outcome progress stored through the non-ghost outcome journal;
- best ghost promotion stored through ghost receipt/manifest/artifact evidence.

These domains are intentionally not presented as one global transaction. A failure or repair in one domain does not authorize mutation of the other.

## Implemented source boundary

`RecoveryEvidencePresentation` maps internal evidence into privacy-safe rows containing only:

- the domain title;
- a stable state label;
- generic user-facing detail;
- severity;
- actions valid for that state.

Unknown internal detail strings are never copied into the user model. The presentation cannot expose run summaries, ghost frames, file paths, hashes, raw journal data, or exception messages.

`RecoveryEvidenceUserController` routes maintenance through `ApplicationPersistenceFacade`:

- inspect current evidence;
- retry safe recovery;
- discard corrupt evidence for one selected domain;
- retry and, only if still unresolved, discard pending evidence for one selected domain.

Destructive actions require explicit confirmation. An unavailable action is a no-op. Runtime failures return the previously built safe model rather than exception text or recovery payloads.

`RecoveryEvidenceDialogCoordinator` is the ordinary-player UI owner. `MainActivity` creates it after `GameView` is installed and posts `showIfNeeded()`. It is dismissed during Activity destruction.

## State and action policy

| State | User meaning | Available actions |
|---|---|---|
| `CLEAN` | No repair is needed | None |
| `PENDING` | Valid evidence is waiting to complete | Safe retry; confirmed discard unresolved pending |
| `BLOCKED` | A conflict or write failure prevented safe completion | Safe retry; confirmed discard unresolved pending |
| `CORRUPT` | Evidence cannot be safely applied | Confirmed discard corrupt |
| `IO_FAILURE` | Storage could not be read or updated | Safe retry |

A valid ghost manifest can be either clean or pending depending on whether the best-distance state still needs repair. User copy is therefore derived from both evidence state and detail code.

## Live dialog behavior

The dialog opens only when the refreshed privacy-safe model contains an actionable row. It never renders a healthy-only screen merely because maintenance exists.

For an actionable row:

- **Not now** dismisses without mutation.
- **Retry safely** calls the controller's canonical safe-recovery action.
- **Discard damaged data** is offered only when the row advertises a destructive action.

Selecting a destructive action does **not** execute it. The coordinator opens a second confirmation dialog that explains only the unresolved/damaged evidence for that item will be removed and that the action cannot be undone. The player can choose **Keep data** or **Discard**. Only the confirmed path calls `controller.perform(..., confirmed = true)`.

The controller re-inspects current evidence before every operation and rejects an action that is no longer valid. Unknown I/O state is never treated as permission to erase data. If execution fails, the player is told that existing data was left unchanged and can retry after restart or storage recovery.

When an operation succeeds or refreshes state, the coordinator finds the next actionable row. If none remains, the recovery UI closes naturally.

## Debug/support commands remain separate

The ordinary-player dialog does not replace the debuggable-only ADB maintenance surface documented in [`RECOVERY_EVIDENCE_MAINTENANCE.md`](RECOVERY_EVIDENCE_MAINTENANCE.md). Debug commands are useful for preparing/inspecting evidence during acceptance; the player-facing dialog is the normal recovery path.

Both surfaces use the same fail-closed domain maintenance rules. Neither turns the two persistence domains into one global transaction.

## Automated coverage

Coverage includes:

- privacy-safe state/detail/action mapping in `RecoveryEvidencePresentationTest`;
- action availability, pre-action reinspection, confirmation requirements, safe retry, discard results, and failure behavior in `RecoveryEvidenceUserControllerTest`;
- Activity attachment/lifecycle, facade ownership, safe retry, privacy boundaries, and two-step destructive confirmation in `scripts/test_recovery_evidence_user_ui_contract.py`;
- the deeper namespace, run-outcome, ghost-promotion, corruption, retry, and targeted-discard suites documented in `RECOVERY_EVIDENCE_MAINTENANCE.md`.

## Release rule

The end-user recovery UI is **implemented in source**. It is no longer correct to describe recovery maintenance as debug/support-only.

Release acceptance still requires preparing representative `PENDING`, `BLOCKED`, `CORRUPT`, and storage-failure states on the exact signed candidate and physically verifying:

- dialog readability and safe copy;
- focus/TalkBack order and action announcements;
- cancellation and second confirmation;
- lifecycle/recreation/process-death behavior;
- successful safe retry and confirmed discard;
- preservation of unrelated live state and the other recovery domain.

Source completion does not substitute for that candidate-bound human/device evidence.
