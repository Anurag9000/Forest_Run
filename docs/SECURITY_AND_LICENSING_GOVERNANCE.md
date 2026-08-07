# Security, licensing, and disclosure governance

Forest Run is a public source repository, but public release governance is not complete until the repository owner makes and records the decisions below. This document is an explicit gate; it is not a substitute for those decisions.

## Vulnerability reporting

The repository now contains a deliberately conditional `SECURITY.md`. It tells reporters to use GitHub private vulnerability reporting **when that feature is available** and otherwise to contact the repository owner privately through the contact method on the owner's GitHub profile. That wording is an interim source-repository policy; it does not assert that GitHub private vulnerability reporting is enabled today.

GitHub private vulnerability reporting must be enabled and verified before a public release candidate is accepted. The accepted release policy must then name the actually enabled private reporting mechanism without conditional or aspirational wording.

Required owner action:

1. Open the repository Security settings.
2. Enable private vulnerability reporting.
3. Confirm that a test draft advisory can be created by an authorized maintainer.
4. Record the enabled state and reviewer in the final candidate approval evidence.
5. Update `SECURITY.md` so the accepted release policy points to the verified private-reporting path rather than the interim fallback.

Until that setting is enabled, repository documentation must not claim that GitHub private vulnerability reporting is active. Security reports must not be directed to public issues by default.

## Accepted-release `SECURITY.md` minimum contents

Before public distribution, the policy must state:

- which released versions are supported;
- the private reporting mechanism actually enabled for the repository;
- the scope covering game runtime, persistence, release tooling, evidence validators, build workflows, and store-delivery artifacts;
- information requested from reporters without requesting player secrets or unrelated personal data;
- acknowledgement, triage, remediation, and coordinated-disclosure expectations;
- treatment of duplicate, invalid, low-impact, and out-of-scope reports;
- safe-harbor language reviewed by the owner or counsel;
- an explicit prohibition on publishing active exploit details before a fix is available.

The current alpha policy is therefore useful for responsible contact but is not evidence that the final release reporting channel has been enabled or accepted.

## Source-code and creative-asset licensing

No software or asset licence is selected automatically by this repository. The owner must decide and document separate treatment where appropriate for:

- Kotlin, Python, shell, Gradle, and configuration source;
- original character, environment, Garden, wardrobe, UI, and promotional artwork;
- music, sound effects, and haptic compositions;
- fonts and third-party assets;
- screenshots, videos, store graphics, and written copy;
- contributions accepted from other people.

A software licence does not automatically grant rights to art, audio, trademarks, or promotional media. Conversely, an asset licence does not automatically license the source code.

## Licence decision record

Before public distribution, the final approval evidence must record:

```text
software_licence:
asset_licence:
audio_licence:
font_licence_and_attribution:
third_party_notices_path:
trademark_policy:
contribution_policy:
decision_owner:
legal_reviewer:
decision_date_utc:
candidate_sha:
```

Every selected licence text and notice file must be committed on `main`, reviewed against the resolved SBOM, and included in the final release-evidence index.

## Dependency and attribution review

The declared dependency inventory is not enough to determine licences. The release owner must review the resolved transitive graph and packaged artifact, then produce:

- machine-readable SBOM;
- licence identifiers and source links;
- required copyright and notice text;
- incompatible or unknown licence findings;
- remediation or replacement decisions;
- final third-party notices shipped with or linked from the application as required.

## Candidate-bound release notes

Release notes must be written only after one candidate is frozen and accepted. They must identify:

- version name and version code;
- exact `main` commit SHA;
- signed artifact SHA-256 and signing-certificate SHA-256;
- player-visible changes;
- accessibility and privacy behavior relevant to the release;
- migration or compatibility information;
- known limitations that affect users;
- store `What's new` text matching the uploaded candidate.

Do not copy release notes from a different candidate or publish placeholders. The final notes and changelog entry are candidate-bound evidence.

## Release-blocking rule

Public release remains blocked while any of these are unresolved:

- GitHub private vulnerability reporting has not been enabled and verified for the accepted candidate;
- `SECURITY.md` still uses an interim fallback instead of the verified accepted-release reporting path;
- code, assets, audio, fonts, or third-party content lack an owner-approved licence position;
- required notices are missing or disagree with the resolved SBOM;
- release notes are not bound to the exact accepted signed artifact;
- the final approval evidence does not identify the responsible owner and reviewer.
