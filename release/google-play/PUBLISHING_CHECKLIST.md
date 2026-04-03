# Google Play Publishing Checklist

Use this after the product is actually ready for release.

Packaging readiness is already handled in-repo by `scripts/prepare_play_release.py`. This checklist is only for the final screenshot + Play Console pass.

## Assets

- Feature graphic exists at `graphics/feature-graphic.png`
- Promo square exists at `graphics/promo-square.png`
- Store metadata exists under `metadata/en-US/`
- Raw device screenshots have been captured into `screenshots/raw/`
- Final screenshot set has been curated from the raw captures

## Build

- `scripts/prepare_play_release.py` passes
- `app/build/outputs/bundle/release/app-release.aab` exists
- version name/code are correct for the intended release

## Product Truth

- hardware checklist is complete
- performance audit is complete
- docs reflect the actual shipped state
- release copy matches the implemented canon:
  - `5` biomes
  - Bloom at `8` seeds
  - gesture-anywhere input
  - `run -> rest -> Garden -> run`

## Console Upload

- upload final `.aab`
- upload final screenshot set
- upload feature graphic
- paste title / short description / full description
- verify content rating, policy, and release-notes fields
- verify target countries/devices
- roll out release
