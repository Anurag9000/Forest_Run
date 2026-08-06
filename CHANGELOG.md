# Changelog

All notable Forest Run changes are recorded here. This repository uses an `Unreleased` section until one exact signed candidate has completed physical-device, accessibility, store-delivery, policy, and evidence acceptance.

## Unreleased

### Added

- deterministic root accessibility actions for Menu/Rest navigation and run controls;
- a pure semantic accessibility tree for Menu, Settings, live Run, Garden, and Rest surfaces;
- a rate-limited accessibility announcement policy that prioritizes surface and Bloom changes without frame-driven score chatter;
- exhaustive lazy collision-outcome dispatch wired into the live run loop;
- a shared live collision-effect adapter that preserves coordinator-owned ordering;
- a reconciled run-session transition table and fail-closed effect coordinator;
- an application persistence facade that preserves independent durability domains;
- privacy-safe recovery presentation and a confirmation-gated user action controller;
- an exhaustive structural catalogue for all nineteen encounter families;
- descriptor-bound stable release-evidence snapshots and independent post-publication verification;
- a candidate-bound declared direct-dependency inventory;
- a packaged Android native-library/page-size risk verifier wired to the release AAB build;
- source-backed privacy, dependency-provenance, accessibility, recovery, content, and security/licensing governance documentation.

### Changed

- validation actions use maintained Node-runtime generations while preserving read-only exact-SHA behavior;
- release-evidence indexing rejects path aliasing, hard-link reuse, symlink traversal, and mutation during review;
- dependency declarations have one pinned authoritative source with no stale parallel catalogue;
- terminal, stumble, and mercy collision source contracts now follow dispatcher ownership while retaining original ordering and persistence guarantees;
- run-session effects no longer duplicate run-start music and now model restart initialization explicitly.

### Fixed

- malformed or stale accessibility, persistence, release-evidence, dependency, collision, recovery, and encounter-catalogue test fixtures now fail at their owning boundaries without weakening production behavior;
- pending valid ghost manifests now produce pending-recovery copy rather than a false healthy message.

## Release-note rule

A dated/versioned section must not be added until the accepted candidate is frozen. At that point, copy only verified player-visible changes from `Unreleased`, record the exact version name/code, `main` SHA, signed artifact SHA-256, certificate SHA-256, known limitations, and matching Play `What's new` text. The final changelog entry is part of the candidate-bound release evidence.
