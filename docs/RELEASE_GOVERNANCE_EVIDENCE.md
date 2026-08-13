# Candidate-bound release governance evidence

Forest Run treats legal, store, security, licensing, presentation, provenance, and final release decisions as accountable human decisions—not values that build tooling may infer. The repository nevertheless enforces a strict evidence envelope around those decisions so the final release cannot silently omit a domain, mix candidates, reuse stale files, or claim approval without named reviewers.

The source tooling is:

```text
scripts/compile_release_governance.py
scripts/validate_release_governance.py
```

The compiler derives hashes from existing files and preserves entered decisions. The validator verifies completeness, identity, timestamps, file integrity, cross-binding, and reviewer separation. Neither tool decides that a licence is legally sufficient, that a Play declaration is correct, or that a vulnerability review found no material risk.

## Prerequisites

A governance manifest is downstream of four independently valid principal manifests:

1. `device-acceptance.json`;
2. `installed-identity-matrix.json`, with one measured Play-delivered APK identity per physical session;
3. `play-delivery.json`, with the external internal-track upload/install/update record bound to that matrix; and
4. `human-acceptance.json` referencing the same physical candidate.

The validator revalidates all four layers and rejects disagreement in:

- repository/branch/application ID;
- candidate commit SHA;
- version code;
- signed artifact SHA-256;
- upload-certificate SHA-256 for the submitted AAB;
- app-signing-certificate SHA-256 for the Play-delivered APK;
- device-acceptance digest referenced by human acceptance.

Governance therefore cannot be approved against one artifact while physical, installed-package, Play-delivery, or human evidence describes another. The Play-delivery validator explicitly requires the `internal` track plus upload/release/tester-install/update assertions and hashes the corresponding external Play Console/receipt evidence; it never infers a track from the package installer.

## Mandatory external conditions

The manifest must state a real public HTTPS privacy-policy URL with no embedded credentials. It also requires `private_vulnerability_reporting_enabled: true`.

These fields are deliberate release gates. The compiler does not publish a privacy policy and does not enable GitHub private vulnerability reporting. The repository owner must perform those external actions first and retain evidence.

## Required decision domains

Every domain is mandatory and must be explicitly `approved`, with a named reviewer, UTC review time, and nonblank rationale:

- `security_disclosure`;
- `software_licensing`;
- `creative_asset_licensing`;
- `dependency_licensing`;
- `dependency_vulnerability`;
- `dependency_verification`;
- `privacy_policy`;
- `data_safety`;
- `content_rating`;
- `target_audience`;
- `store_policy`;
- `visual_artwork_animation`;
- `store_screenshots_graphics`;
- `audio_presentation`;
- `haptic_presentation`;
- `reduced_motion_presentation`;
- `accessibility_presentation`;
- `orientation_policy`;
- `signed_artifact_provenance`;
- `release_notes`.

There is no pending/waived fallback in a valid final governance manifest. If a domain is unresolved, the release stays blocked.

## Required evidence kinds

The governance manifest also hashes one unique file for every required evidence kind:

- `artifact_verification`;
- `resolved_sbom`;
- `dependency_license_report`;
- `dependency_vulnerability_report`;
- `dependency_verification_report`;
- `installed_identity_matrix`;
- `play_delivery_record`;
- `asset_provenance`;
- `security_reporting_record`;
- `license_decision_record`;
- `third_party_notices`;
- `privacy_policy_snapshot`;
- `data_safety_record`;
- `content_rating_record`;
- `target_audience_record`;
- `store_policy_review`;
- `visual_approval`;
- `screenshot_graphics_approval`;
- `audio_haptic_approval`;
- `accessibility_approval`;
- `signed_artifact_provenance`;
- `release_notes`;
- `changelog`;
- `store_whats_new`.

These files may be generated reports, exported console records, reviewed Markdown/text records, screenshots, or other appropriate immutable evidence. The validator does not prescribe the legal meaning of a file; it enforces that the named evidence exists, is unique, is stable during validation, and matches its recorded SHA-256.

Path traversal, symbolic-link aliases, hard-link reuse, duplicate paths, missing files, and digest changes are rejected.

## Release-note identity

The `release_notes` evidence must be readable UTF-8 text and contain the exact:

- candidate commit SHA;
- artifact SHA-256;
- upload-certificate SHA-256;
- app-signing-certificate SHA-256;
- version code;
- version name.

This prevents release notes copied from another candidate from satisfying the final evidence contract merely because a reviewer marked the release-notes domain approved.

## Draft compilation

In a draft:

- `device_acceptance` is the relative path to the compiled physical manifest;
- `human_acceptance` is the relative path to the compiled human manifest;
- each `evidence.<kind>` is a relative path string;
- all decisions, reviewers, URLs, identity fields, and final approval facts are human-entered.

Compile and independently revalidate:

```bash
python3 scripts/compile_release_governance.py \
  release/evidence/release-governance-draft.json \
  release/evidence/release-governance.json \
  --summary-output release/evidence/release-governance-summary.json

python3 scripts/validate_release_governance.py \
  release/evidence/release-governance.json \
  --summary-output release/evidence/release-governance-summary.json
```

The compiler does not overwrite the draft. Manifest and summary publication is transactional within the draft directory.

## Final decision rule

The final decision must:

- be `approved`;
- name a release owner;
- name a distinct independent reviewer;
- use a review time no earlier than any domain decision and no later than manifest generation;
- provide a nonblank final rationale;
- use people who each owned at least one recorded governance-domain decision.

This is an accountability rule, not a substitute for legal counsel or store review.

## Relationship to the final release-evidence index

The compiled `release-governance.json` is itself candidate-bound evidence and belongs in the final release-evidence index alongside physical acceptance, human acceptance, artifact verification, dependency/SBOM evidence, screenshots/graphics manifests, and the signed artifact. The individual governance evidence files should also be indexed when they materially participate in the go/no-go decision.

A valid governance manifest still does **not** mean the release has happened. The exact signed artifact must have been delivered through the accepted store path, the evidence must be independently reviewed, and the final candidate/tag decision must follow the release procedure.
