# Physical Acceptance Aggregation and Candidate Comparison

The final physical-device manifest answers a binary question: does one exact signed candidate satisfy the frozen acceptance contract? Once a manifest passes that gate, `scripts/aggregate_device_acceptance.py` summarizes measurements by device class and can compare them with another independently validated candidate.

Aggregation is descriptive evidence. It does not weaken the acceptance validator, invent a performance budget, or declare a regression acceptable.

## Canonical entrypoint

Use the fail-closed wrapper for operator work:

```bash
bash scripts/aggregate_device_acceptance_bundle.sh \
  release-evidence/candidate/device-acceptance.json \
  release-evidence/candidate/device-acceptance-aggregate.json \
  release-evidence/baseline/device-acceptance.json
```

The baseline argument is optional.

The wrapper performs a two-phase publication sequence:

1. strict-parse the candidate and optional baseline manifests;
2. require at least one valid, nonempty schema-v2 deterministic trace in every manifest;
3. independently reconstruct trace scenario definitions and input scripts from Kotlin source;
4. run physical evidence aggregation into a unique same-directory staged file, never directly into the final path;
5. strict-parse the staged aggregate;
6. validate the staged aggregate with `validate_device_acceptance_aggregate.py`, an independent consumer-side contract validator;
7. revalidate candidate and baseline manifests, signed artifacts, every evidence digest, and every exact trace after staging has completed;
8. run a second physical-acceptance pass after exact trace validation so every artifact and evidence digest is rehashed at the last semantic boundary;
9. bind the staged candidate and optional baseline identities back to those final manifest validations;
10. perform a final bounded SHA-256 verification of every signed artifact and evidence file against its manifest digest, capturing each stable device, inode, size, and modification timestamp from that same hash pass;
11. reread the staged aggregate and require identical bytes, identity, parsed payload, and independent-validation summary;
12. recheck every protected-source snapshot and staged/output alias boundary;
13. atomically replace the final output and fsync its parent directory.

Every validation, identity, snapshot, and alias failure occurs before atomic replacement, so those failures leave the previous final report untouched. The wrapper exits nonzero and its cleanup trap removes the staged file. A low-level filesystem failure during or after `os.replace` is different: the destination may already be visible even if directory `fsync` fails, so operators must inspect the final path and rerun the complete gate. The dedicated publisher API intentionally leaves a failed staged file in place when called directly so it can be inspected; the canonical wrapper owns cleanup policy.

## Layered implementation

The aggregation path deliberately separates producer, independent validator, and publisher responsibilities.

### Producer

`scripts/aggregate_device_acceptance.py`:

- strictly parses and accepts every manifest;
- requires exact deterministic traces in the Python core, not only in the wrapper;
- verifies manifest bytes, size, modification time, and inode remain stable across acceptance and trace validation;
- derives the anonymized hardware matrix and metric distributions;
- compares only exact trace and hardware matrices;
- refuses candidate/baseline aliases;
- prevents its output from overwriting manifests, artifacts, or evidence files;
- emits strict finite JSON through a flushed atomic replacement.

### Independent aggregate validator

`scripts/validate_device_acceptance_aggregate.py` does not import or trust the producer. It independently validates:

- exact root, candidate, class, metric, distribution, trace, and comparison keys;
- schema version and `status = valid`;
- canonical application ID;
- lowercase commit, artifact, certificate, matrix, physical-device, and device-profile digests;
- the mandatory five-class device matrix;
- sorted, unique session, physical-device, and profile identifiers;
- per-class counts and total session consistency;
- `minimum <= mean <= maximum` for every finite nonnegative distribution;
- weighted global means and global extrema reconstructed from class summaries;
- nonnegative absolute-threshold headroom;
- a freshly reconstructed `comparison_matrix_sha256`;
- nonempty, sorted, unique exact trace contracts;
- trace/evidence count consistency;
- baseline matrix, trace, class, delta, and interpretation bindings.

Unknown fields fail closed. This makes accidental producer schema drift and forged post-processing visible before publication.

### Final publisher

`scripts/publish_device_acceptance_aggregate.py` accepts a staged aggregate and performs the final publication transaction. It:

- rejects staged or output symlinks;
- requires staged and output paths to be distinct and in the same directory;
- rejects candidate/baseline path or inode aliasing;
- independently re-runs physical acceptance and exact trace validation after staging;
- immediately runs physical acceptance a second time after trace validation, rehashing the signed artifact and every evidence file at the final semantic boundary;
- proves both acceptance passes resolve the same candidate summary and that the manifest remains byte- and inode-identical;
- proves the staged candidate commit/artifact match the final candidate manifest;
- proves baseline presence and commit/artifact identity match the supplied final baseline manifest;
- performs a final bounded SHA-256 pass over every signed artifact and evidence file, compares each digest with the accepted manifest, and captures the stable device, inode, size, and modification timestamp returned by that same pass;
- carries the already confirmed manifest identity into the same protected-source snapshot set;
- rereads the staged aggregate and requires the exact bytes, inode identity, parsed payload, and independent-validation summary to remain unchanged during source validation;
- rejects staged/output resolved-path, symbolic-link, and hard-link aliases to protected sources;
- rechecks every protected-source snapshot and alias boundary immediately before replacement;
- atomically moves the already validated staged inode into the final path;
- fsyncs the destination directory.

This closes the canonical publication windows in which either a source or the staged report could otherwise be modified after validation but before replacement. The final artifact/evidence snapshot is not an unverified stat sample: it is emitted by the last digest-matching hash pass. Subsequent stat checks are the final race detector before replacement.

## Preconditions

Both the candidate and optional baseline must be complete manifests accepted by `scripts/validate_device_acceptance.py`. The strict path verifies:

- canonical repository, branch, application ID, commit, version, certificate, and signed artifact identity;
- internal-store delivery identity;
- the mandatory five-class physical-device matrix;
- the mandatory seven-scenario surface;
- thresholds, durations, manual checks, reviewers, and approvals;
- every referenced artifact and evidence-file digest;
- unique physical devices and non-aliased evidence files;
- at least one exact deterministic trace with a nonempty authored input script;
- `scenario_definition_sha256` and `trace_contract_sha256` reconstructed from `EncounterDirector.kt` and `DebugScenarioScript.kt`;
- exact event count, authored schedule, action, sequence, and lateness arithmetic.

An invalid, trace-free, mixed-build, forged-trace, or tampered manifest is rejected before any distribution is calculated. Recomputing the manifest evidence digest after changing a trace action does not bypass the source-reconstructed semantic check.

## Immutable manifest reads

The producer and final publisher perform stable bounded manifest reads. Each records device/inode identity, size, and modification timestamp, reads the complete bytes, then rechecks identity. After acceptance and trace validation, the manifest is read again and must still be byte-identical.

The parsed, validated manifest determines the protected source set. Publication never rereads a different manifest merely to decide what must be protected.

## Anonymized device and comparison identities

The aggregate never emits the raw build fingerprint as its comparison key. It derives two lowercase SHA-256 identifiers:

- `physical_device_id` covers normalized manufacturer, model, and build fingerprint only;
- `device_profile_id` additionally covers device class, SDK, RAM, refresh rate, width, height, density, tablet flag, and cutout flag.

Text fields are NFKC-normalized, trimmed, and case-folded before hashing. A refresh-rate or geometry change therefore keeps the same physical identity but changes the comparison profile. An OEM, model, or build-fingerprint change changes both identities.

For each device class the report retains:

- distinct anonymized physical-device IDs and count;
- distinct anonymized device-profile IDs;
- exact session count and session IDs;
- per-metric distributions.

A deterministic `comparison_matrix_sha256` covers every class's session count and sorted device-profile IDs. The independent validator reconstructs this hash rather than trusting the serialized value.

## Candidate-only report

```bash
bash scripts/aggregate_device_acceptance_bundle.sh \
  release-evidence/device-acceptance.json \
  release-evidence/device-acceptance-aggregate.json
```

The report contains:

- candidate commit, artifact, application, version, and certificate identity;
- total sessions and evidence files;
- exact deterministic trace count;
- unique trace contracts, including scenario, scenario-definition SHA-256, and input-contract SHA-256;
- comparison-matrix SHA-256;
- session-duration minimum, mean, and maximum;
- global and per-class minimum, mean, and maximum for p95/p99 frame time, slow-frame ratio, peak PSS, crashes, and ANRs;
- worst-case headroom against each frozen acceptance threshold;
- anonymized physical-device IDs, profile IDs, and exact session IDs per class.

A negative threshold-headroom value should be impossible for a valid manifest because the validator rejects threshold failures. The independent aggregate validator also rejects negative serialized headroom.

## Baseline comparison

The candidate and baseline must expose:

- the same required device-class matrix;
- at least one exact trace each;
- the identical set of `(scenario, scenario_definition_sha256, trace_contract_sha256)` tuples;
- the same session count within each class;
- the same anonymized device-profile set within each class;
- the same resulting comparison-matrix SHA-256.

This prevents measured deltas from being confounded by different deterministic scenarios, encounter definitions, input schedules, device substitutions, OS/build changes, refresh configurations, memory tiers, or sample counts. The comparison reports mean and maximum deltas globally and per class.

For frame time, slow-frame ratio, memory, crashes, and ANRs:

- a positive delta is worse;
- a negative delta is better;
- zero means no measured change at the reported precision.

The fixed interpretation sentence is part of the validated schema. The tool deliberately does not decide how much positive change is acceptable. Any regression tolerance must be separately authored, reviewed, and frozen before final measurement.

## Interpretation rules

1. Never compare an exploratory local APK with an accepted internal-track candidate.
2. Do not compare manifests whose device classes, exact trace contracts, per-class session counts, or anonymized profile sets differ.
3. Examine class-level values before relying on the global mean; fast hardware can conceal a supported-device-floor regression.
4. Use maximum deltas for worst-case regressions and mean deltas for broad movement.
5. Review raw traces for material changes; aggregation cannot explain rendering, allocation/GC, I/O, audio, or thermal causes.
6. A candidate that passes absolute thresholds may still regress relative to the accepted baseline and require investigation.
7. Preserve accepted manifests, aggregates, traces, and raw evidence immutably.

## Test coverage

The host-side suites include:

- producer compatibility with the independent validator;
- candidate-only and baseline schema acceptance;
- unknown-field rejection;
- forged matrix-hash rejection;
- weighted-global-distribution rejection;
- identifier ordering, duplicate, count, and mandatory-class failures;
- negative headroom and impossible trace/evidence counts;
- baseline matrix, trace, identity, and semantic drift;
- duplicate-key strict JSON rejection;
- valid two-phase publication;
- evidence mutation after staging;
- non-trace evidence mutation during trace validation, caught by the post-trace acceptance rehash;
- evidence mutation after the second acceptance pass, caught by the digest-bound final snapshot;
- staged-report mutation during final source validation;
- protected-source mutation after the final snapshot;
- candidate and baseline identity substitution;
- output hard-link and symlink aliasing;
- cross-directory staging;
- candidate/baseline manifest aliasing;
- failed publication preserving the staged file in the direct publisher API.

## Release relationship

Aggregation adds a review layer after absolute acceptance:

1. validate the candidate manifest and exact trace contract;
2. aggregate to a staged report and freeze its comparison-matrix hash;
3. independently validate the aggregate schema and arithmetic;
4. revalidate all source evidence after staging and rehash it again after exact trace validation;
5. perform the final manifest-digest verification and capture source identities from that exact hash pass;
6. lock staged-report bytes plus the digest-bound protected-source snapshots;
7. publish atomically only when candidate/baseline identities and all final snapshots still match;
8. investigate material regressions using raw evidence;
9. retain the final manifest, aggregate, comparison, exact traces, and raw evidence together.

A successful aggregate command does not make Forest Run release-ready by itself. Signing, internal delivery, physical testing, visual review, accessibility review, and current store-policy approval remain independent gates.
