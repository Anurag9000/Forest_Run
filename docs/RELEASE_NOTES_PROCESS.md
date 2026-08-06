# Candidate-bound release notes

Forest Run keeps player-facing release notes separate from implementation logs,
audit history, and speculative roadmap items. Release notes describe only one
accepted, signed, store-bound candidate.

## Authorities

- `CHANGELOG.md` records notable source changes under `Unreleased` until a real
  candidate is accepted.
- `release/google-play/metadata/en-US/` contains store-facing title,
  descriptions, and `What's new` copy.
- the candidate build summary and release-evidence index bind the source SHA,
  artifacts, screenshots, metadata, acceptance evidence, and reviewers.

No document may claim publication merely because source validation or emulator
checks pass.

## Required release identity

A dated changelog entry and final `What's new` text require all of the following:

- exact `main` commit SHA;
- Android version name and monotonically increasing version code;
- signed, minified AAB SHA-256;
- signing certificate SHA-256 and approved custody record;
- successful exact-head host and connected validation;
- accepted physical-device, accessibility, performance, lifecycle, and ordinary
  play evidence for the same artifact;
- declared dependency inventory, resolved CycloneDX SBOM, vulnerability review,
  licence review, and approved asset-provenance registry;
- final store screenshots, graphics, metadata, privacy/Data Safety, content
  rating, target audience, countries/devices, and policy review;
- internal-track upload/installation receipt and update-path result;
- independent reviewer identities and approval date.

## Writing procedure

1. Freeze the candidate and prohibit source or store-copy changes during review.
2. Reconcile `Unreleased` against the exact diff from the previous accepted
   version. Remove internal refactors that have no player-visible or operational
   consequence.
3. State additions, changes, fixes, known limitations, migrations, and any
   compatibility impact accurately. Do not promise features that are merely
   staged or planned.
4. Write concise Play `What's new` copy from the verified player-visible subset.
5. Validate that screenshots and descriptions depict the accepted artifact and
   that quantities such as five biomes, Bloom at eight Seeds, and the
   run-to-Rest-to-Garden loop remain true.
6. Record version identity, artifact/certificate hashes, candidate SHA, evidence
   index, reviewers, and store receipt in the dated changelog entry.
7. Publish only after the release-evidence index independently verifies every
   referenced file and the rollout decision is approved.

## Failure and rollback

Any source, asset, dependency, version, signing, screenshot, metadata, policy,
or artifact change creates a new candidate and invalidates the old release-note
approval. A rejected or withdrawn candidate remains documented in audit/evidence
records but must not receive a normal released changelog heading.

A rollback or hotfix gets its own version code and candidate-bound evidence. Do
not edit a historical released entry to make a different artifact appear to be
the original release.
