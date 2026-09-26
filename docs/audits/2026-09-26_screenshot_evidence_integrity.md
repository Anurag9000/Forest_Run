# Forest Run — Screenshot evidence intake hardening (2026-09-26)

## Scope

Follow-on to the candidate-bound release-summary audit. This change traces the screenshot pipeline from captured PNG sidecar through finalization, raw-session verification, curation manifest/session verification, and curated store evidence. The prior release wrapper and its product/runtime owners remain unchanged.

## Confirmed gap and remediation

Four independent screenshot-evidence read paths still used Python's permissive `json.loads` rather than the repository's strict bounded reader: the capture sidecar loader, the raw capture-session verifier, the finalizer's preliminary sidecar inspection, and the curated manifest/session verifier. Duplicate keys could be silently overwritten and nonstandard non-finite tokens admitted on these paths. The capture sidecar and curation manifest also lacked a strict input-byte bound at their parser boundary.

These entry points now use `strict_json.load_file` with strict object admission, bounded bytes (64 KiB per sidecar/session; 256 KiB per curation manifest), and domain-specific error translation. This reuses the checked file-descriptor reader rather than introducing another parser. Regression tests exercise duplicate keys, non-finite tokens, oversized sidecars, and the rule that an ambiguous sidecar cannot publish a capture-session receipt.

This is evidence validation, not evidence creation. No screenshots, device reports, APKs, release summaries, rights approvals, or physical measurements are manufactured by this change.

## Verification boundary

The exact resulting commit and its workflow checks must be consulted before claiming host or connected-test PASS. Physical, human, creative, signing, privacy-hosting, and Play gates remain open until genuine candidate-bound evidence exists.
