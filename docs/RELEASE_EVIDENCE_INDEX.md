# Release evidence index

Forest Run uses three cooperating tools for the final evidence-set integrity boundary:

```text
build_stable_release_evidence_index.py
build_release_evidence_index.py
verify_release_evidence_index.py
```

The stable wrapper is the canonical operator entrypoint. It freezes every evidence input through one descriptor-backed snapshot, delegates format construction to the builder, confirms the original files did not change, publishes atomically, and then invokes the independently implemented verifier. The builder and verifier remain separately callable for focused testing and forensic review.

None of these tools manufactures evidence or turns a source-ready build into a release-ready build.

## Why this exists

The release process produces evidence through independent pipelines:

- signed AAB/APK verification;
- declared and resolved dependency provenance;
- internal-store delivery verification;
- physical-device acceptance sessions;
- performance profiles;
- screenshot capture and curation;
- graphics and metadata generation;
- manual visual, audio, haptic, accessibility, privacy, audience, content-rating, and store-policy approvals.

Without a final index, each file can be internally valid while the folder remains vulnerable to omission, candidate mixing, path aliasing, hard-link reuse, replacement during review, or inconsistent reads while a file changes. The index records the exact path, byte count, SHA-256 digest, candidate-binding status, and discovered candidate identities for every selected file. It hashes the canonical entry list into `evidenceSetSha256`.

## Canonical stable build and verification

```bash
python3 scripts/build_stable_release_evidence_index.py \
  --root . \
  --candidate-sha "$CANDIDATE_SHA" \
  --generated-at-utc "2026-08-06T06:00:00Z" \
  --entry signed_bundle=release/evidence/app-release.aab \
  --entry artifact_verification=release/evidence/artifact-verification.json \
  --entry declared_dependencies=release/evidence/declared-dependency-inventory.json \
  --entry sbom=release/evidence/sbom.cdx.json \
  --entry device_acceptance=release/evidence/device-acceptance.json \
  --entry device_aggregate=release/evidence/device-acceptance-aggregate.json \
  --entry screenshot_manifest=release/google-play/screenshots/screenshot_manifest.json \
  --entry graphics_manifest=release/google-play/graphics/graphics_manifest.json \
  --entry policy_approval=release/evidence/policy-approval.json \
  --require-bound-kind artifact_verification \
  --require-bound-kind declared_dependencies \
  --require-bound-kind sbom \
  --require-bound-kind device_acceptance \
  --require-bound-kind device_aggregate \
  --require-bound-kind screenshot_manifest \
  --require-bound-kind graphics_manifest \
  --require-bound-kind policy_approval \
  --output release/evidence/release-evidence-index.json
```

Use the exact lowercase 40-character candidate SHA and an actual canonical UTC timestamp with second precision. Add one unique `kind=relative/path` entry for every file used in the release decision.

The command succeeds only after independent verification of the published output. It prints the reconstructed entry count, candidate SHA, and evidence-set digest.

## Optional independent review invocation

A second operator or review environment should still run the verifier directly:

```bash
python3 scripts/verify_release_evidence_index.py \
  release/evidence/release-evidence-index.json \
  --root . \
  --expected-candidate-sha "$CANDIDATE_SHA" \
  --require-bound-kind artifact_verification \
  --require-bound-kind declared_dependencies \
  --require-bound-kind sbom \
  --require-bound-kind device_acceptance \
  --require-bound-kind device_aggregate \
  --require-bound-kind screenshot_manifest \
  --require-bound-kind graphics_manifest \
  --require-bound-kind policy_approval
```

A valid result includes:

- exact candidate SHA;
- canonical generation timestamp;
- entry and candidate-bound counts;
- total evidence bytes;
- reconstructed `evidenceSetSha256`;
- SHA-256 of the index itself.

The verifier does not import the builder or stable wrapper. Shared format rules are independently implemented so one construction defect cannot automatically validate itself.

## Stable snapshot guarantees

For each input, the stable wrapper:

1. rejects unsafe, absolute, parent-traversing, Windows-separated, or NUL-containing paths;
2. rejects symbolic links in the file or any existing parent component;
3. opens the file without following its final link where the platform supports `O_NOFOLLOW`;
4. verifies a regular file and bounded nonzero size;
5. records device, inode, mode, size, modification time, and change time;
6. reads exactly the descriptor-reported length and rejects growth or truncation;
7. rechecks descriptor metadata after the read;
8. hashes and parses the same frozen bytes in a private snapshot tree;
9. reopens the original and confirms identity, metadata, bytes, and digest before publication;
10. publishes with the existing root, symlink, inode, and atomic-replacement protections;
11. independently verifies the resulting index against the original evidence;
12. removes the index if independent verification fails.

This closes the earlier defense-in-depth gaps where candidate parsing and hashing could observe separate reads or a parent-directory symlink could redirect an evidence path.

## Candidate binding

JSON evidence can bind itself explicitly through `candidateSha` or `candidate_sha`. Candidate and per-session build objects can use `commitSha` or `commit_sha`. Baseline comparison identities are deliberately excluded from candidate identity; a comparison report may contain a different baseline while binding its candidate side to the release SHA.

Use `--require-bound-kind` for every JSON manifest or approval expected to carry its own candidate identity. Binary artifacts and images are normally bound transitively: their digest is recorded by a candidate-bound verifier or manifest, and both files are indexed.

## Builder and publication fail-closed rules

The construction path rejects:

- noncanonical candidate SHAs or timestamps;
- missing, empty, oversized, unsupported, nonregular, or escaping files;
- symbolic-link files and symbolic-link parent components;
- duplicate kinds, paths, physical files, and output/evidence aliases;
- malformed UTF-8 JSON or conflicting candidate identities;
- missing or unbound required kinds;
- more than 128 entries;
- any source mutation between snapshot and publication;
- any independently reconstructed metadata or digest disagreement.

Publication uses a temporary file, flushes and fsyncs it, rechecks output path and inode separation, atomically replaces the destination, and fsyncs the containing directory. Failure leaves no trusted partial output.

## Independent verifier fail-closed rules

The verifier rejects:

- a missing, empty, oversized, nonregular, symlinked, escaping, or changing index;
- duplicate JSON keys, extra or missing schema fields, noncanonical values, or non-finite JSON;
- wrong schema version, candidate, timestamp, counts, ordering, or evidence-set digest;
- duplicate kinds, paths, physical files, and index/evidence aliases;
- missing, symlinked, escaping, changing, or oversized evidence;
- mismatched bytes, SHA-256, candidate bindings, or candidate-bound flags;
- missing or unbound required evidence kinds;
- replacement of the index during the full verification interval.

## Review procedure

1. Freeze one exact clean `main` SHA.
2. Build, sign, deliver, capture, and test that candidate only.
3. Validate every evidence format with its owning verifier.
4. Build the declared inventory, resolved SBOM, licence review, and vulnerability report for the same candidate.
5. Run `build_stable_release_evidence_index.py` with every selected file.
6. Have an independent reviewer run `verify_release_evidence_index.py` separately.
7. Record `indexSha256` and `evidenceSetSha256` in the final approval record.
8. Review entry count, kinds, paths, candidate-bound count, and reviewer coverage.
9. Do not modify an indexed file after approval. Any required change creates a new candidate and evidence set.

The index is an integrity boundary, not an approval substitute. Physical-device sessions, subjective presentation review, signing custody, legal decisions, and store-console checks still require real people and real systems.
