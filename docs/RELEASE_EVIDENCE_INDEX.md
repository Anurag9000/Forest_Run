# Release evidence index

`build_release_evidence_index.py` creates one immutable catalogue over the files used to approve a Forest Run release candidate. It does not manufacture evidence and it does not convert a source-ready build into a release-ready build. Its purpose is to bind already-collected evidence to one exact `main` commit and make later substitution detectable.

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

## Example

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

Use an actual UTC generation time and the exact lowercase 40-character commit SHA being approved. Add one distinct `kind=relative/path` entry for every file that is part of the decision.

## Candidate binding

JSON evidence can bind itself explicitly through `candidateSha` or `candidate_sha`. Candidate and per-session build objects can use `commitSha` or `commit_sha`. Baseline comparison identities are deliberately not treated as candidate identities; a comparison report may legitimately contain a different baseline commit while still binding its candidate side to the release SHA.

Use `--require-bound-kind` for every JSON manifest or approval that is expected to carry its own candidate identity. Binary artifacts and images are normally bound transitively: their digest is recorded by a candidate-bound verifier or manifest, and both files are included in the final index.

## Fail-closed rules

The builder rejects:

- noncanonical or uppercase candidate SHAs;
- non-UTC or malformed generation timestamps;
- missing, empty, oversized, unsupported, nonregular, or escaping files;
- absolute paths, parent traversal, Windows separators, and NULs;
- symbolic-link evidence or a symbolic-link output;
- duplicate kinds, duplicate paths, and hard-linked aliases;
- output/evidence aliasing;
- malformed UTF-8 JSON;
- malformed or conflicting candidate identities;
- required candidate-bound evidence that is missing or unbound;
- more than 128 index entries.

Publication uses a temporary file, flushes and fsyncs it, atomically replaces the destination, and fsyncs the containing directory. A validation failure leaves no partial output.

## Review procedure

1. Freeze one exact `main` SHA.
2. Build, sign, deliver, capture, and test that same candidate.
3. Validate every individual evidence format with its owning verifier.
4. Build the release evidence index.
5. Independently review the index entry count, kinds, paths, candidate-bound count, and `evidenceSetSha256`.
6. Do not modify any indexed file after approval. Any necessary change creates a new candidate and a new evidence set.

The index is an integrity boundary, not an approval substitute. Physical-device sessions, subjective presentation review, signing custody, and store-console checks still require real people and real systems.