# Deterministic Scenario Trace Evidence

Forest Run's authored encounter scenarios are intended to reproduce the same input sequence and encounter setup across debugging, hardware validation, screenshot capture, and regression analysis. The deterministic trace system records the input actions that were actually dispatched, not merely the actions that were planned.

## Runtime ownership

`DebugScenarioScript` remains the sole deterministic input sequencer. Every prepared scenario owns a bounded `DeterministicScenarioTraceRecorder` by default; callers do not need to opt in.

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

## Authored input and replay contract

Before a script is activated, `DebugScenarioInputContract` verifies:

- finite nonnegative schedule times;
- chronological ordering;
- maximum one-minute authored duration;
- no held-jump start while another held jump is active;
- no duck start while another duck is active;
- no jump/duck overlap;
- no unmatched end action;
- no held action left active at script completion.

`DeterministicScenarioReplayContract` then requires an evidence snapshot to contain the complete authored script with exactly the same event count, schedule, action, scenario, and sequence. An incomplete trace or a well-formed substituted action is not evidence.

## Exact source fingerprints

`EncounterScenarioFingerprint` produces two SHA-256 identities:

1. `scenario_definition_sha256` covers the scenario name, title, summary, forced biome, Bloom-start policy, ghost-playback policy, and every ordered encounter step including timing, entity, offset, and variant.
2. `trace_contract_sha256` covers the scenario-definition hash plus every ordered deterministic input step, schedule, and action.

All Float timing and offset values are converted to integer microseconds or micro-pixels before hashing. JVM tests and the independent Python source parser assert the same fixed hashes for `CACTUS_READ`; either implementation drifting independently fails validation.

## Candidate-bound schema v2 encoding

`DeterministicScenarioTraceEvidenceCodec` converts a complete authored snapshot into strict schema-v2 JSON bound to:

- the exact lowercase 40-hex candidate commit;
- the exact lowercase 64-hex artifact digest;
- the UTC capture timestamp;
- the canonical scenario;
- `scenario_definition_sha256`;
- `trace_contract_sha256`;
- the complete ordered event list.

Times are encoded as integer microseconds rather than floating-point decimals. This avoids device- or runtime-dependent spellings such as `0.1` versus `0.100000024`, making the JSON and payload SHA-256 byte-stable for the same input.

The encoder rejects:

- incomplete, altered, or overflowed snapshots;
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

## Independent source reconstruction and validation

Validate an exported trace against the checked-in Kotlin definitions:

```bash
python3 scripts/validate_scenario_trace.py \
  release-evidence/scenario-trace-cactus_read.json \
  --expected-sha <40-hex-main-commit> \
  --expected-artifact-sha256 <64-hex-signed-artifact-digest> \
  --expected-scenario CACTUS_READ
```

`scripts/scenario_source_contract.py` independently parses `EncounterDirector.kt` and `DebugScenarioScript.kt`, emulates Kotlin Float32 canonicalization, and reconstructs both expected hashes and the exact input sequence. The strict validator checks:

- exact schema-v2 root and event keys;
- candidate and artifact binding;
- exact scenario-definition and trace-contract hashes;
- complete canonical event count;
- exact zero-based sequence;
- exact authored schedule and action at every index;
- chronological dispatch time;
- dispatch not preceding schedule;
- exact lateness arithmetic;
- known input action vocabulary;
- maximum 4,096 events;
- stable payload SHA-256.

Duplicate JSON keys, nonfinite values, numeric overflow, oversized integers, excessive nesting, oversized files, malformed Kotlin source, and files that change while being read fail closed.

## Acceptance-manifest integration

`validate_manifest_scenario_traces.py` validates every referenced `scenario-trace-*.json` file against the candidate commit and artifact and preserves both contract hashes in its summary.

The canonical entrypoints require at least one valid trace:

```bash
scripts/compile_device_acceptance_bundle.sh DRAFT FINAL SUMMARY
scripts/aggregate_device_acceptance_bundle.sh CANDIDATE REPORT [BASELINE]
```

Aggregation requires the same trace gate for both candidate and baseline. A manifest with ordinary hashed evidence but no exact deterministic trace cannot pass these operator paths.

## Soak and hardware workflow

1. Freeze one clean canonical `main` candidate and signed artifact.
2. Prepare a deterministic scenario.
3. Run the scenario on the target device.
4. Capture the trace snapshot after completion.
5. Encode it with the exact candidate and artifact identity.
6. Persist it atomically beside performance, screenshot, lifecycle, and log evidence.
7. Run `validate_scenario_trace.py` with the expected candidate, artifact, and scenario.
8. Reference the validated trace file in the physical acceptance manifest.
9. Compile the acceptance bundle through the canonical strict entrypoint.
10. Preserve the trace and raw evidence immutably with the accepted candidate.

A valid input trace proves which authored deterministic inputs were dispatched and when. It does not by itself prove correct collisions, rendering, audio, haptics, performance, or visual quality. Those remain separate evidence and review layers.
