# Changelog

All notable Forest Run changes are recorded here. This repository uses an `Unreleased` section until one exact signed candidate has completed physical-device, accessibility, store-delivery, policy, and evidence acceptance.

## Unreleased

### Added

- deterministic root accessibility actions for Menu/Rest navigation and run controls;
- a pure semantic accessibility tree for Menu, Settings, live Run, Garden, and Rest surfaces;
- exhaustive lazy collision-outcome dispatch and narrow collision-runtime effect ports;
- a pure run-session transition table with fail-closed invalid-event handling;
- an application persistence facade that preserves independent durability domains;
- descriptor-bound stable release-evidence snapshots and independent post-publication verification;
- a candidate-bound declared direct-dependency inventory;
- source-backed privacy, dependency-provenance, accessibility, and security/licensing governance documentation.

### Changed

- validation actions use maintained Node-runtime generations while preserving read-only exact-SHA behavior;
- release-evidence indexing rejects path aliasing, hard-link reuse, symlink traversal, and mutation during review;
- dependency declarations have one pinned authoritative source with no stale parallel catalogue.

### Fixed

- malformed or stale accessibility, persistence, release-evidence, and dependency test fixtures now fail at their owning boundaries without weakening production behavior.

## Release-note rule

A dated/versioned section must not be added until the accepted candidate is frozen. At that point, copy only verified player-visible changes from `Unreleased`, record the exact version name/code, `main` SHA, signed artifact SHA-256, certificate SHA-256, known limitations, and matching Play `What's new` text. The final changelog entry is part of the candidate-bound release evidence.
