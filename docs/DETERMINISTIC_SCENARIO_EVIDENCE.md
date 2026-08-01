# Deterministic Scenario Trace Evidence

Forest Run's authored encounter scenarios are intended to reproduce the same input sequence and encounter setup across debugging, hardware validation, screenshot capture, and regression analysis. The deterministic trace system records the input actions that were actually dispatched, not merely the actions that were planned.

## Runtime ownership

`DebugScenarioScript` remains the sole deterministic input sequencer. Every prepared scenario now owns a bounded `DeterministicScenarioTraceRecorder` by default. Existing callers do not need to opt in.

For each successful dispatch the trace records:

- canonical scenario enum;
- zero-based sequence number;
- authored schedule time;
- actual dispatch time;
- input action;
- derived dispatch lateness.

An action is recorded only after the runtime dispatch callback returns successfully. If dispatch throws, the action remains pending and no false evidence event is emitted.

The recorder:

- has a fixed default capacity of 256 actions;
- refuses capacities above 4,096;
- never performs disk I/O;
- never owns gameplay state;
- fails closed after overflow;
- returns detached immutable snapshots;
- preserves the completed snapshot after a scenario is cleared.

## Authored input contract

Before a script is activated, `DebugScenarioInputContract` verifies:

- finite nonnegative schedule times;
- chronological ordering;
- maximum one-minute authored duration;
- no held-jump start while another held jump is active;
- no duck start while another duck is active;
- no jump/duck overlap;
- no unmatched end action;
- no held action left active at script completion.

Every checked-in scenario is covered by a catalogue-wide test.

## Candidate-bound evidence encoding

`DeterministicScenarioTraceEvidenceCodec` converts a complete replayable snapshot into strict JSON bound to:

- the exact lowercase 40-hex candidate commit;
- the exact lowercase 64-hex artifact digest;
- the UTC capture timestamp;
- the canonical scenario;
- the complete ordered event list.

Times are encoded as integer microseconds rather than floating-point decimals. This avoids device- or runtime-dependent spellings such as `0.1` versus `0.100000024`, making the JSON and SHA-256 byte-stable for the same input.

The encoder rejects:

- incomplete or overflowed snapshots;
- invalid candidate or artifact identifiers;
- negative capture timestamps;
- malformed or nonrepresentable timing;
- payloads above 256 KiB.

The evidence contains no account identifier, save history, relationship history, advertising identifier, device serial, or free-form player text.

## Atomic persistence

`DeterministicScenarioTraceEvidenceStore` verifies the payload SHA-256 before writing and uses Android `AtomicFile` so an interrupted replacement preserves the previous complete trace.

The filename is deterministic:

```text
scenario-trace-<scenario_name_lowercase>.json
```

A regular file cannot masquerade as the destination directory, and a forged evidence object with a mismatched digest is rejected without creating output.

## Independent validation

Validate an exported trace against the repository's canonical scenario catalogue:

```bash
python3 scripts/validate_scenario_trace.py \
  release-evidence/scenario-trace-cactus_read.json \
  --expected-sha <40-hex-main-commit> \
  --expected-artifact-sha256 <64-hex-signed-artifact-digest> \
  --expected-scenario CACTUS_READ
```

The validator strict-parses the file and checks:

- exact root and event keys;
- schema version;
- candidate and artifact binding;
- scenario membership in `EncounterDirector.kt`;
- declared and actual event counts;
- exact zero-based sequence;
- chronological schedule and dispatch time;
- dispatch not preceding schedule;
- exact lateness arithmetic;
- known input action vocabulary;
- maximum 4,096 events;
- stable payload SHA-256.

Duplicate JSON keys, nonfinite values, numeric overflow, excessive nesting, oversized files, and files that change while being read fail closed through the shared strict-JSON boundary.

## Soak and hardware workflow

1. Freeze one clean canonical `main` candidate and signed artifact.
2. Prepare the deterministic scenario.
3. Run the scenario on the target device.
4. Capture the trace snapshot after completion.
5. Encode it with the exact candidate and artifact identity.
6. Persist it atomically beside the performance, screenshot, log, and lifecycle evidence.
7. Run `validate_scenario_trace.py` with the expected candidate, artifact, and scenario.
8. Reference the validated trace file in the physical acceptance manifest.
9. Preserve the trace and raw evidence immutably with the accepted candidate.

A valid input trace proves which deterministic inputs were dispatched and when. It does not by itself prove correct collisions, rendering, audio, haptics, performance, or visual quality. Those remain separate evidence and review layers.
