# Release evidence index

`build_release_evidence_index.py` creates one immutable catalogue over the files used to approve a Forest Run release candidate. `verify_release_evidence_index.py` independently reconstructs and verifies that catalogue from the published files. Neither tool manufactures evidence or converts a source-ready build into a release-ready build.

## Why this exists

The release process produces evidence through several independent pipelines:

- signed AAB/APK verification;
- internal-store delivery verification;
- physical-device acceptance sessions;
- performance profiles;
- screenshot capture and curation;
- graphics generation;
- manual visual, audio, haptic, accessibility, privacy, metadata, audience, content-rating, and store-policy approvals.

Without a final index, each file can be internally valid while the release folder as a whole remains vulnerable to omission, accidental mixing of candidates, path aliasing, or replacement after review. The index records the exact path, byte count, SHA-256 digest, candidate-binding status, and discovered candidate identities for every selected file. It also hashes the canonical entry list into `evidenceSetSha256`.

The builder is deliberately not the final authority over its own output. The independent verifier parses the published index with strict JSON, validates its exact schema, reconstructs every entry from the filesystem, recomputes all hashes and counts, and rereads the index after evidence hashing to detect replacement during verification.

## Build example

```bash
python3 scripts/build_release_evidence_index.py \
  --root . \
  --candidate-sha "$CANDIDATE_SHA" \
  --generated-at-utc "2026-08-06T06:00:00Z" \
  --entry signed_bundle=release/evidence/app-release.aab \
  --entry artifact_verification=release/evidence/artifact-verification.json \
  --entry device_acceptance=release/evidence/device-acceptance.json \
  --entry device_aggregate=release/evidence/device-acceptance-aggregate.json \
  --entry screenshot_manifest=release/google-play/screenshots/screenshot_manifest.json \
  --entry graphics_manifest=release/google-play/graphics/graphics_manifest.json \
  --entry policy_approval=release/evidence/policy-approval.json \
  --require-bound-kind artifact_verification \
  --require-bound-kind device_acceptance \
  --require-bound-kind device_aggregate \
  --require-bound-kind screenshot_manifest \
  --require-bound-kind graphics_manifest \
  --require-bound-kind policy_approval \
  --output release/evidence/release-evidence-index.json
```

Use an actual canonical UTC generation time with second precision and the exact lowercase 40-character commit SHA being approved. Add one distinct `kind=relative/path` entry for every file that is part of the decision.

## Mandatory independent verification

Run the independent verifier after publication and before human approval:

```bash
python3 scripts/verify_release_evidence_index.py \
  release/evidence/release-evidence-index.json \
  --root . \
  --expected-candidate-sha "$CANDIDATE_SHA" \
  --require-bound-kind artifact_verification \
  --require-bound-kind device_acceptance \
  --require-bound-kind device_aggregate \
  --require-bound-kind screenshot_manifest \
  --require-bound-kind graphics_manifest \
  --require-bound-kind policy_approval
```

A valid result is emitted as a compact JSON summary containing:

- exact candidate SHA;
- canonical generation timestamp;
- entry and candidate-bound counts;
- total evidence bytes;
- reconstructed evidence-set SHA-256;
- SHA-256 of the index itself.

The verifier does not import the builder. Shared format rules are implemented independently so a builder defect cannot automatically validate itself.

## Candidate binding

JSON evidence can bind itself explicitly through `candidateSha` or `candidate_sha`. Candidate and per-session build objects can use `commitSha` or `commit_sha`. Baseline comparison identities are deliberately not treated as candidate identities; a comparison report may legitimately contain a different baseline commit while still binding its candidate side to the release SHA.

Use `--require-bound-kind` for every JSON manifest or approval that is expected to carry its own candidate identity. Binary artifacts and images are normally bound transitively: their digest is recorded by a candidate-bound verifier or manifest, and both files are included in the final index.

## Builder fail-closed rules

The builder rejects:

- noncanonical or uppercase candidate SHAs;
- non-UTC or malformed generation timestamps;
- missing, empty, oversized, unsupported, nonregular, or escaping files;
- absolute paths, parent traversal, Windows separators, and NULs;
- symbolic-link evidence, symbolic-link outputs, and symlinked output parents;
- duplicate kinds, duplicate paths, hard-linked evidence aliases, and output/evidence hard-link aliases;
- output paths outside the selected evidence root;
- malformed UTF-8 JSON and malformed or conflicting candidate identities;
- required candidate-bound evidence that is missing or unbound;
- more than 128 index entries.

Publication uses a temporary file, flushes and fsyncs it, rechecks output path and inode separation, atomically replaces the destination, and fsyncs the containing directory. A validation failure leaves no partial output.

## Independent verifier fail-closed rules

The verifier rejects:

- a missing, empty, oversized, nonregular, symlinked, escaping, or changing index;
- duplicate index keys, extra or missing schema fields, noncanonical values, and non-finite JSON;
- wrong schema version, candidate, timestamp, counts, ordering, or evidence-set digest;
- duplicate kinds, duplicate paths, duplicate physical files, and index/evidence hard-link aliasing;
- missing, symlinked, escaping, changing, or oversized evidence;
- mismatched byte counts, SHA-256 digests, candidate bindings, or candidate-bound flags;
- missing or unbound required evidence kinds;
- replacement of the index during the complete verification interval.

## Remaining bounded hardening

The index path and each evidence file itself are rejected when symbolic links. A future hardening tranche should also reject symbolic links in evidence parent directories and bind JSON candidate parsing to the exact same descriptor snapshot used for hashing. The independent verifier already detects final metadata or digest disagreement, so these are defense-in-depth improvements rather than a claim that current forged evidence can pass final verification.

## Review procedure

1. Freeze one exact `main` SHA.
2. Build, sign, deliver, capture, and test that same candidate.
3. Validate every individual evidence format with its owning verifier.
4. Build the release evidence index.
5. Run `verify_release_evidence_index.py` with the exact candidate and every required bound kind.
6. Record the verifier's `indexSha256` and `evidenceSetSha256` in the final approval record.
7. Independently review entry count, kinds, paths, candidate-bound count, and reviewer coverage.
8. Do not modify any indexed file after approval. Any necessary change creates a new candidate and a new evidence set.

The index is an integrity boundary, not an approval substitute. Physical-device sessions, subjective presentation review, signing custody, and store-console checks still require real people and real systems.
