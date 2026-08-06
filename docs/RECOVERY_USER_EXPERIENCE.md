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

`RecoveryEvidenceUserController` routes four operations through `ApplicationPersistenceFacade`:

- inspect current evidence;
- retry safe recovery;
- discard corrupt evidence for one selected domain;
- retry and, only if still unresolved, discard pending evidence for one selected domain.

Destructive actions require explicit confirmation. An unavailable action is a no-op. Runtime failures return the previously built safe model rather than exception text or recovery payloads.

## State and action policy

| State | User meaning | Available actions |
|---|---|---|
| `CLEAN` | No repair is needed | None |
| `PENDING` | Valid evidence is waiting to complete | Safe retry; confirmed discard unresolved pending |
| `BLOCKED` | A conflict or write failure prevented safe completion | Safe retry; confirmed discard unresolved pending |
| `CORRUPT` | Evidence cannot be safely applied | Confirmed discard corrupt |
| `IO_FAILURE` | Storage could not be read or updated | Safe retry |

A valid ghost manifest can be either clean or pending depending on whether the best-distance state still needs repair. User copy is therefore derived from both evidence state and detail code.

## Remaining live UI work

The privacy-safe model and action controller are implemented and tested, but no ordinary-player screen currently renders them. A future Settings/Support panel must:

1. call `refresh()` when opened;
2. display both domains independently;
3. show only actions supplied by each row;
4. require a second explicit confirmation step before either discard action;
5. preserve focus and announce the refreshed state after an operation;
6. never render internal detail codes, paths, hashes, summaries, frames, or exception messages;
7. remain accessible with TalkBack, large text, and reduced motion;
8. record no analytics or remote data unless privacy policy and Data Safety declarations are deliberately changed.

## Release rule

A recovery UI must not be described as complete until its live panel is wired, destructive confirmation is physically tested, lifecycle/process-death behavior is verified, and TalkBack acceptance is recorded on the exact signed candidate.
