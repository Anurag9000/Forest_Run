# Google Play Publishing Checklist

Use this only after the product and one exact candidate are actually ready for release. A green source build or emulator run does not make a store-ready release.

## Candidate identity

- exact `main` SHA is frozen and matches origin
- version name and monotonically increasing version code are approved
- signed, minified AAB SHA-256 is recorded
- signing certificate SHA-256 and credential-custody approval are recorded
- candidate build summary and release-evidence index reference the same SHA and artifact
- dated changelog entry and Play `What's new` text describe only this candidate

## Source, dependencies, and assets

- immutable source validation passes on the exact candidate SHA
- declared dependency inventory is present
- resolved CycloneDX SBOM is present
- vulnerability review records scanner, database timestamp, policy, findings, suppressions, and reviewer
- dependency and asset licence/attribution review is complete
- `asset-provenance.json` passes `validate_asset_provenance.py --require-approved`
- source assets pass structural validation
- Gradle wrapper/downloaded-artifact integrity and provenance have been independently reviewed

## Build and installation

- `scripts/prepare_main_release.sh` passes without bypasses
- `app/build/outputs/bundle/release/app-release.aab` exists and matches the recorded hash
- R8/minification and resource shrinking are verified
- package native-library/16 KB page-size inspection passes
- signed candidate installs and launches on the accepted physical device matrix
- internal-track upload, delivery receipt, clean install, upgrade, and rollback/hotfix procedure are verified

## Product and physical acceptance

- ordinary play and deterministic scenarios use the exact signed artifact
- older phone, midrange phone, high-refresh phone, cutout/unusual aspect device, and tablet evidence are complete
- controls, jump/hold/release/swipe, fairness, safe areas, text, contrast, reduced motion, audio, and haptics are accepted
- TalkBack and Switch Access navigation/actions/announcements are accepted
- lifecycle, interruption, process death, Garden, wardrobe, recovery, and ghost continuity are accepted
- p95/p99 frame time, slow frames, allocations/GC, PSS, I/O, audio thread, crashes/ANRs, thermal, battery, long-session, and dense Bloom/all-entity profiles pass
- final art/animation review, including the Wolf sheet, is approved

## Store assets and policy

- feature graphic exists at `graphics/feature-graphic.png`
- promo square exists at `graphics/promo-square.png`
- final candidate-bound screenshot set is curated from physical-device captures
- store title, short description, full description, and `What's new` text are approved
- privacy policy and Data Safety declarations match actual behavior
- content rating, target audience, ads declaration, countries/devices, and current Play policy review are complete
- store copy matches the implemented canon:
  - `5` biomes
  - Bloom at `8` Seeds
  - gesture-anywhere input
  - `run -> Rest -> Garden -> run`

## Console rollout

- upload the verified final AAB and confirm Play's processed artifact identity
- upload the approved screenshot set and graphics
- paste only the approved metadata and release notes
- confirm signing, integrity, policy, country/device, and rollout settings
- complete independent release review
- start the approved rollout and preserve receipts/evidence
- monitor crash/ANR, delivery, policy, and user-impact signals with an explicit pause/rollback owner
