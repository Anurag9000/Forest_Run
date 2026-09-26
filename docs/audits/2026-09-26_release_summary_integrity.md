# Forest Run — Release summary integrity continuation (2026-09-26)

## Baseline and scope

Investigation started on canonical `main` at `bcf88c749917161cedb709405fda1612cd72c439`. The complete Git tree has 813 tracked files. This focused follow-on traces the canonical operator entry point `scripts/prepare_main_release.sh`, its generated machine and human summaries, `scripts/verify_release_summary.py`, and the already-existing `scripts/strict_json.py` security boundary. It is not a second claim of full product acceptance.

## Confirmed source-level gap

The wrapper verifies `build_summary.json` after preparing a candidate, but the summary verifier parsed it with Python's permissive `json.loads`. Consequently ambiguous duplicate keys and non-standard `NaN`/`Infinity` tokens were not rejected at that boundary. Python also treats `bool` as an `int`, allowing boolean evidence in selected numeric fields; an object in the audio list could raise an uncaught `TypeError` rather than the verifier's diagnostic `ReleaseSummaryError`. Blank matching version names were also possible.

## Change

The final verifier now uses the repository's existing bounded, strict, inode-checked JSON file reader with its 2 MiB limit and normalizes parser failures to `ReleaseSummaryError`. Exact integer admission applies to version codes, R8 counts, byte counts, and metadata character counts. Audio evidence requires 15 distinct nonblank strings. The release identity requires a nonblank version name. The generated summary schema and canonical release entry point do not change.

The regression suite exercises duplicate keys, non-finite JSON tokens, boolean numeric fields, unhashable/non-string audio, blank version names, and boolean artifact facts alongside the pre-existing valid and malformed cases.

## Evidence boundaries

The changes and tests in this commit are source-level remediation, not evidence of a completed device run or signed release. Treat the host/Android workflow result for the exact resulting commit as the automated validation authority. Hardware, human creative/accessibility, legal/provenance, signing, privacy-hosting, and Play delivery gates remain external and must not be inferred from this change.
