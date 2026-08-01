# Physical-Device and Store Acceptance Evidence

Forest Run is not release-ready until one frozen `main` candidate is proven on representative physical hardware, installed through the internal store track, visually reviewed, and checked against the current store-policy declarations.

This document defines the evidence contract enforced by [`scripts/validate_device_acceptance.py`](../scripts/validate_device_acceptance.py). The validator is deliberately fail-closed. It checks provenance, completeness, thresholds, file hashes, and approvals; it does **not** manufacture evidence or replace human/device testing.

## 1. Candidate invariants

Every bundle must identify exactly one candidate:

- repository: `Anurag9000/Forest_Run`;
- branch: `main`;
- application ID: `com.anurag9000.forestrun`;
- full lowercase 40-hex commit SHA;
- positive version code;
- signed artifact path and SHA-256, verified against the actual file beside the manifest;
- signing-certificate SHA-256;
- successful install from the internal store track;
- delivered package name, version, artifact digest, and certificate digest matching the candidate.

Every device session must repeat the same commit, artifact, version, signing, and internal-store installation identity. Mixing local APKs, different commits, or different bundles in one acceptance set is rejected.

## 2. Minimum device classes

The release owner must freeze the exact matrix in the manifest. The recommended minimum is:

| Class | Acceptance purpose |
|---|---|
| `older_phone` | CPU/GPU, memory, storage, thermal, and lifecycle pressure |
| `midrange_phone` | Primary representative Android experience |
| `high_refresh_phone` | Frame pacing and input feel at 90/120 Hz |
| `cutout_phone` | Safe-content transform, touch mapping, and readability |
| `tablet` | Unusual aspect, scaling, density, and large-screen composition |

A class is covered only when its required number of complete sessions passes. Device metadata includes manufacturer, model, build fingerprint, SDK, RAM, refresh rate, pixel dimensions, density, tablet classification, and cutout presence.

## 3. Required scenario surface

The recommended release matrix includes:

- `ordinary_play_15m`: natural play through speed/biome escalation;
- `all_entities`: all 19 entity families and their telegraphs/outcomes;
- `bloom_dense`: Bloom activation/conversion during dense hazards;
- `lifecycle_recovery`: pause/resume, recreation, background/foreground, repeated launch, and recovery;
- `settings_accessibility`: audio, haptic, and reduced-motion persistence/enforcement;
- `garden_transactions`: purchases, wardrobe, return moment, navigation, and persistence;
- `ghost_persistence`: best-run publication, process relaunch, disk reload, and readable playback.

Every required scenario must be marked passed and reference at least one unique evidence file. A path cannot be shared between two results.

## 4. Evidence-file integrity

Each final evidence reference is an object:

```json
{
  "path": "evidence/midrange_phone/ordinary-play/profile.json",
  "sha256": "<lowercase 64-hex digest>"
}
```

Paths must be normalized, relative, forward-slash paths. Absolute paths, parent traversal, home expansion, duplicates, missing files, and SHA-256 mismatches are rejected. Files are resolved relative to the manifest directory.

Raw evidence should include, as applicable:

- performance-report JSON;
- frame timeline or system trace;
- memory/GC/thermal observations;
- logcat and crash/ANR evidence;
- installation/package/certificate output;
- screenshots or screen recordings;
- scenario checklist and tester notes.

Do not commit private device identifiers, signing secrets, store credentials, or unrelated personal data.

## 5. Frozen thresholds

Thresholds are manifest inputs and must be frozen before final capture. They are not universal numbers that the validator invents. At minimum the policy defines:

- maximum p95 frame time;
- maximum p99 frame time;
- maximum slow-frame ratio;
- maximum peak proportional-set-size memory;
- maximum crashes;
- maximum ANRs;
- minimum session duration.

The p99 limit cannot be lower than the p95 limit. Ratios must be within `[0, 1]`; all metrics must be finite and nonnegative. A session exceeding any threshold fails the whole bundle.

Thresholds should first be derived from exploratory measurements across the intended device floor, then frozen for a fresh candidate run. Do not tune a threshold after seeing a failing final session merely to obtain a pass.

## 6. Manual acceptance and approvals

Every session records an explicit result for:

- touch controls;
- safe-content readability;
- audio;
- haptics;
- reduced motion;
- lifecycle recovery;
- artwork and animation.

Allowed states are `pass` or a justified `not_applicable`. The final bundle additionally requires `approved` decisions for:

- visual presentation;
- store metadata;
- privacy;
- data safety;
- content rating;
- target audience;
- current store policy.

At least two distinct named reviewers are required for final visual/store approval.

## 7. Deterministic manifest compilation

Use [`scripts/compile_device_acceptance.py`](../scripts/compile_device_acceptance.py) to build the final manifest from a human-entered draft. In the draft:

- keep candidate identity, certificate, version, signed status, and internal-track installation facts explicit;
- record the package, version, artifact digest, and certificate digest captured from the internal-store delivery path;
- record every session's captured commit, artifact digest, version, certificate, signing state, and installation path;
- record all device/scenario/performance/manual-review facts;
- list each scenario's `evidence_files` as plain relative path strings;
- do not type the candidate-file or raw-evidence-file SHA-256 values that the compiler derives from local bytes.

The compiler:

1. hashes the actual candidate artifact and writes that digest only to the candidate artifact field;
2. preserves the independently captured store-delivery and per-session build identity exactly as entered;
3. hashes every raw evidence file;
4. invokes the strict validator, which rejects any store/session identity that differs from the candidate;
5. transactionally publishes the final manifest and optional summary, restoring previous outputs if publication is interrupted.

The compiler deliberately does **not** copy candidate identity into store or session records. Doing so could conceal a stale local APK, a different internal-track artifact, or a mixed-device candidate set.

The draft, final manifest, summary, artifact, and evidence tree share one root directory so all relative paths remain stable. The final manifest and summary must not overwrite the draft or each other.

```bash
python scripts/compile_device_acceptance.py \
  release-evidence/device-acceptance-draft.json \
  release-evidence/device-acceptance.json \
  --summary-output release-evidence/device-acceptance-summary.json
```

Compilation cannot turn a failed scenario, exceeded threshold, incomplete device class, missing approval, or incorrect identity into a pass. It derives only the candidate-file and raw-evidence-file hashes; all observed store/session identity remains independent evidence.

## 8. Independent validation

The compiled bundle can be checked again independently:

```bash
python scripts/validate_device_acceptance.py \
  release-evidence/device-acceptance.json \
  --summary-output release-evidence/device-acceptance-summary.json
```

Exit code `0` means only that the declared bundle is internally consistent, complete under its frozen policy, and cryptographically bound to existing evidence files. It does not independently prove that screenshots are aesthetically acceptable, tester statements are truthful, or store declarations are legally sufficient. Those remain accountable human approvals.

## 9. Release decision

A release candidate may advance only when:

1. exact-SHA host and connected validation are green;
2. this physical-device/store bundle validates;
3. raw evidence has been manually inspected;
4. material performance or correctness findings are repaired and the full candidate matrix is rerun;
5. the signed artifact, internal-store delivery, package, version, and certificate all match;
6. artwork, screenshots, metadata, privacy/data-safety/content-rating/target-audience, and current policy are approved;
7. the accepted commit is tagged without rewriting history.

Until then, the honest classification remains **feature-rich alpha**, not release candidate.
