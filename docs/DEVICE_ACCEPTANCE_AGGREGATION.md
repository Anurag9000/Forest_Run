# Physical Acceptance Aggregation and Candidate Comparison

The final physical-device manifest answers a binary question: does one exact signed candidate satisfy the frozen acceptance contract? Once a manifest passes that gate, `scripts/aggregate_device_acceptance.py` summarizes measurements by device class and can compare them with another independently validated candidate.

Aggregation is descriptive evidence. It does not weaken the acceptance validator, invent a performance budget, or declare a regression acceptable.

## Canonical entrypoint

Use the strict wrapper for operator work:

```bash
bash scripts/aggregate_device_acceptance_bundle.sh \
  release-evidence/candidate/device-acceptance.json \
  release-evidence/candidate/device-acceptance-aggregate.json \
  release-evidence/baseline/device-acceptance.json
```

The baseline argument is optional. The wrapper orders the gates as follows:

1. strict-parse candidate and baseline manifests;
2. require at least one valid nonempty schema-v2 deterministic trace in each manifest;
3. independently reconstruct each trace's scenario definition and input script from Kotlin source;
4. validate candidate, artifact, scenario, hashes, schedules, and actions;
5. run physical evidence aggregation;
6. strict-parse the published aggregate.

The Python core repeats the material security gates; they are not wrapper-only conveniences. Calling `aggregate_device_acceptance.py` directly still requires an accepted manifest, a nonempty exact trace, immutable manifest identity across acceptance/trace validation, and matching trace plus hardware matrices for candidate/baseline comparison.

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

## Immutable manifest snapshot

The Python core performs a stable bounded manifest read and records size, modification time, and inode identity. After the acceptance and trace validators finish, it reads the manifest again and requires:

- identical bytes;
- identical size;
- identical modification timestamp;
- identical inode.

A manifest changed between validation passes is rejected. The already validated parsed manifest is then used to derive the protected source set; the publisher does not reread a potentially different manifest to decide which files must be protected.

## Anonymized device and comparison identities

The aggregate never emits the raw build fingerprint as its comparison key. It derives two lowercase SHA-256 identifiers:

- `physical_device_id` covers normalized manufacturer, model, and build fingerprint only;
- `device_profile_id` additionally covers device class, SDK, RAM, refresh rate, width, height, density, tablet flag, and cutout flag.

Text fields are NFKC-normalized, trimmed, and case-folded before hashing. A refresh-rate or geometry change therefore keeps the same physical identity but changes the comparison profile. An OEM, model, or build-fingerprint change changes both identities.

For each device class the report retains:

- distinct anonymized physical-device IDs and count;
- distinct anonymized device-profile IDs;
- exact session count and session IDs.

A deterministic `comparison_matrix_sha256` covers every class's session count and sorted device-profile IDs.

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
- global minimum, mean, and maximum for:
  - p95 frame time;
  - p99 frame time;
  - slow-frame ratio;
  - peak PSS memory;
  - crashes;
  - ANRs;
- worst-case headroom against each frozen acceptance threshold;
- the same distributions for each required device class;
- anonymized physical-device IDs, profile IDs, and exact session IDs per class.

A negative threshold-headroom value should be impossible for a valid manifest because the validator rejects threshold failures. Headroom is retained to show how close the worst accepted session came to the frozen boundary.

## Baseline comparison

The candidate and baseline must expose:

- the same required device-class matrix;
- at least one exact trace each;
- the identical set of `(scenario, scenario_definition_sha256, trace_contract_sha256)` tuples;
- the same session count within each class;
- the same anonymized device-profile set within each class;
- the same resulting comparison-matrix SHA-256.

This prevents measured deltas from being confounded by different deterministic scenarios, encounter definitions, input schedules, device substitutions, OS/build changes, refresh configurations, memory tiers, or sample counts. The comparison reports mean and maximum deltas globally and per class and carries both the matched trace-contract set and comparison-matrix hash into the output.

For frame time, slow-frame ratio, memory, crashes, and ANRs:

- a positive delta is worse;
- a negative delta is better;
- zero means no measured change at the reported precision.

The tool deliberately does not decide how much positive change is acceptable. A regression tolerance, when justified, must be separately authored, reviewed, frozen before final measurement, and applied through an explicit release policy rather than silently embedded in aggregation code.

## Interpretation rules

1. Never compare an exploratory local APK with an accepted internal-track candidate.
2. Do not compare manifests whose device-class matrices differ.
3. Do not compare manifests whose exact trace-contract sets differ.
4. Do not compare manifests whose per-class session counts or anonymized profile sets differ.
5. Examine class-level values before relying on the global mean; an improvement on fast hardware can conceal a regression on the supported device floor.
6. Use maximum deltas to identify worst-case regressions and mean deltas to understand broad movement.
7. Review raw traces for material changes. Aggregation cannot explain whether a frame-time increase came from rendering, allocation/GC, I/O, audio, thermal throttling, or another measured subsystem.
8. A candidate that still passes its absolute thresholds may nevertheless regress relative to the accepted baseline and require investigation.
9. Do not rewrite an old accepted manifest. Preserve candidate evidence immutably so comparisons remain reproducible.

## Output and source integrity

The output is strict finite JSON and is published through a staged, flushed, atomic replacement. Invalid inputs produce a nonzero exit status and no new aggregate file.

Both the shell wrapper and the Python core reject candidate/baseline aliasing. The Python publisher additionally protects:

- candidate and baseline manifests;
- signed artifacts;
- every referenced raw evidence file;
- symlink-resolved path aliases;
- existing hard-link aliases.

The separation check runs before temporary publication and immediately before atomic replacement. Aggregate output cannot overwrite any validated source inode. The protected source set is carried from the same parsed manifest snapshot used to calculate the report.

The aggregate contains no user account, save history, relationship history, advertising identifier, or device serial. Raw manufacturer/model/build fields remain only in the accepted source manifest; the aggregate comparison surface uses anonymized SHA-256 identities.

## Release relationship

Aggregation adds a review layer after absolute acceptance:

1. validate the candidate manifest and exact trace contract;
2. aggregate candidate distributions and freeze its comparison-matrix hash;
3. validate and compare with the last accepted candidate using identical trace and hardware matrices;
4. investigate material regressions using raw evidence;
5. remediate and rerun the entire frozen matrix when necessary;
6. retain the final manifest, aggregate, comparison, exact traces, and raw evidence together.

A successful aggregate command does not make Forest Run release-ready by itself. Signing, internal delivery, physical testing, visual review, accessibility review, and current store-policy approval remain independent gates.
