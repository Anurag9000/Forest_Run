# Physical Acceptance Aggregation and Candidate Comparison

The final physical-device manifest answers a binary question: does one exact signed candidate satisfy the frozen acceptance contract? Once a manifest passes that gate, `scripts/aggregate_device_acceptance.py` summarizes the measurements by device class and can compare them with another independently validated candidate.

Aggregation is descriptive evidence. It does not weaken the acceptance validator, invent a performance budget, or declare a regression acceptable.

## Preconditions

Both the candidate and optional baseline must be complete manifests accepted by `scripts/validate_device_acceptance.py`. The aggregator independently performs the same strict parsing and validation, including verification of:

- canonical repository, branch, application ID, commit, version, certificate, and signed artifact identity;
- internal-store delivery identity;
- the mandatory five-class physical-device matrix;
- the mandatory seven-scenario surface;
- thresholds, durations, manual checks, reviewers, and approvals;
- every referenced artifact and evidence-file digest;
- unique physical devices and non-aliased evidence files.

An invalid or tampered manifest is rejected before any distribution is calculated.

## Candidate-only report

```bash
python3 scripts/aggregate_device_acceptance.py \
  release-evidence/device-acceptance.json \
  --output release-evidence/device-acceptance-aggregate.json
```

The report contains:

- candidate commit, artifact, application, version, and certificate identity;
- total sessions and evidence files;
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
- distinct physical-device count and exact session IDs per class.

A negative threshold-headroom value should be impossible for a valid manifest because the validator rejects threshold failures. Headroom is retained in the aggregate to show how close the worst accepted session came to the frozen boundary.

## Baseline comparison

```bash
python3 scripts/aggregate_device_acceptance.py \
  release-evidence/candidate/device-acceptance.json \
  --baseline release-evidence/baseline/device-acceptance.json \
  --output release-evidence/candidate/device-acceptance-comparison.json
```

The candidate and baseline must expose the same required device-class matrix. The comparison reports mean and maximum deltas globally and per class.

For frame time, slow-frame ratio, memory, crashes, and ANRs:

- a positive delta is worse;
- a negative delta is better;
- zero means no measured change at the reported precision.

The tool deliberately does not decide how much positive change is acceptable. A regression tolerance, when justified, must be separately authored, reviewed, frozen before final measurement, and applied through an explicit release policy rather than silently embedded in aggregation code.

## Interpretation rules

1. Never compare an exploratory local APK with an accepted internal-track candidate.
2. Do not compare manifests whose device-class matrices differ.
3. Examine class-level values before relying on the global mean; an improvement on fast hardware can conceal a regression on the supported device floor.
4. Use maximum deltas to identify worst-case regressions and mean deltas to understand broad movement.
5. Review raw traces for material changes. Aggregation cannot explain whether a frame-time increase came from rendering, allocation/GC, I/O, audio, thermal throttling, or scenario variation.
6. A candidate that still passes its absolute thresholds may nevertheless regress relative to the accepted baseline and require investigation.
7. Do not rewrite an old accepted manifest. Preserve candidate evidence immutably so comparisons remain reproducible.

## Output integrity

The output is strict finite JSON and is published through a staged, flushed, atomic replacement. Invalid inputs produce a nonzero exit status and no aggregate file.

The aggregate contains no user account, save history, relationship history, advertising identifier, or device serial. Device identity is used only through the already approved manufacturer, model, and build-fingerprint fields present in the acceptance manifest.

## Release relationship

Aggregation adds a review layer after absolute acceptance:

1. validate the candidate manifest;
2. aggregate candidate distributions;
3. compare with the last accepted candidate when available;
4. investigate material regressions using raw evidence;
5. remediate and rerun the entire frozen matrix when necessary;
6. retain the final manifest, aggregate, comparison, and raw traces together.

A successful aggregate command does not make Forest Run release-ready by itself. Signing, internal delivery, physical testing, visual review, accessibility review, and current store-policy approval remain independent gates.
