# Creative asset provenance inventory

Forest Run must distinguish **byte identity** from **creative/legal approval**.
The repository can prove which creative bytes ship with a candidate. It cannot,
by itself, prove who created those bytes, whether a license is valid, or whether
an approver accepted their use.

## Candidate-bound inventory

Generate an inventory from the exact clean candidate checkout:

```bash
python3 scripts/build_creative_asset_inventory.py \
  --root . \
  --candidate-sha "$(git rev-parse HEAD)" \
  --output build/release-evidence/creative-assets.unverified.json
```

The inventory includes every regular file below:

- `app/src/main/assets/` (runtime sprites and font), and
- `app/src/main/res/raw/` (runtime music and SFX).

Every entry is bound by repository-relative path, byte count, SHA-256 digest,
category, and explicit unresolved review state. The aggregate
`inventorySha256` also binds the candidate commit and ordered entry set.

The builder first runs the checked-in release-source asset verifier and runtime
asset ownership audit. Orphaned, missing, malformed, symlinked, or unexpected
byte-identical runtime assets therefore fail before an inventory is emitted.

## What the generated inventory does **not** prove

The generated values are deliberately:

- `provenanceStatus = "unverified"`, and
- `licenseReviewStatus = "required"`.

Do not replace those values merely to make a release gate green. A later human
or organizational review must maintain separate signed/controlled evidence for
creator/source, acquisition date, license/permission, applicable attribution,
reviewer, and review timestamp, and must bind that evidence to the exact
`candidateCommit`, per-file SHA-256 values, and `inventorySha256`.

Five bird base/flying source pairs are currently known byte-identical aliases.
`scripts/audit_runtime_asset_references.py` allowlists those exact pair
memberships so existing aliases are not mislabeled as newly introduced
accidental duplicates. The allowlist is **not** a statement that the creative
choice or license has been reviewed. If authored flying art later becomes
distinct, the audit accepts that improvement automatically.

## Release interpretation

A reproducible inventory is source-complete evidence of **what bytes ship**.
Creative provenance and license approval remain external governance evidence
until a qualified reviewer supplies and binds them. Never infer approval from a
successful Android build, a matching hash, a Git commit, or the existence of a
file in this repository.
