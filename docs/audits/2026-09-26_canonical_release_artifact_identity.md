# Forest Run — Canonical release artifact identity (2026-09-26)

## Examined contract

The canonical `prepare_main_release.sh` wrapper invokes `prepare_play_release.py`, then `verify_release_summary.py`. Its producer records the release bundle under `app/build/outputs/bundle/release/app-release.aab`, the R8 mapping under `app/build/outputs/mapping/release/mapping.txt`, and the exact 15 resource stems in `REQUIRED_AUDIO`.

## Source-addressable gap

The summary verifier previously accepted any repository-local regular files with matching reported hashes as `bundle` and `r8_mapping`; it also accepted any 15 distinct nonblank strings as audio names. The JSON evidence thus did not independently enforce the producer's canonical asset identities. It additionally accepted release summary files reached through final-component symbolic links, despite rejecting symlinks for reported artifact paths.

## Change and regression coverage

The verifier requires the two canonical build paths after safe normalized-path admission and verifies the files as before. The audio set must exactly equal the producer's required resource names. A test binds these identifiers to the release preparer's literal catalogue, so either owner changing requires an intentional synchronized update. Summary files must be regular non-symlink files. Regression coverage includes alternative same-content file paths, false audio names, and symlinked human/machine summaries. Existing Windows-separator canonical build paths remain supported.

This strengthens the summary's consistency check; it does not substitute for `prepare_play_release.py`'s independent bundle structure/signature inspection or for genuine production signing and Play delivery evidence.

## Validation boundary

Consult the workflow at the exact new commit for host and API-35 results. This record does not assert them in advance.
