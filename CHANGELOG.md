# Changelog

All notable Forest Run changes are recorded here. This repository uses an `Unreleased` section until one exact signed candidate has completed physical-device, accessibility, store-delivery, policy, provenance, and evidence acceptance.

## Unreleased

### Added

- a real custom-Canvas virtual-node `AccessibilityNodeProvider` on the installed `GameView`, with stable semantic descendants, focus, bounded layout-derived regions, checkable state, and direct semantic actions without synthetic coordinate touch dispatch;
- deterministic root accessibility actions for Menu/Rest navigation and run controls;
- a pure semantic accessibility tree for Menu, Settings, live Run, Garden, and Rest surfaces;
- a fail-closed semantic action router and rate-limited announcement policy that prioritize meaningful state changes without frame-driven score chatter;
- exhaustive lazy collision-outcome dispatch wired into the live run loop;
- a shared live collision-effect adapter and exact-marker adoption contract that preserve coordinator-owned ordering;
- a reconciled run-session transition table, fail-closed effect coordinator, live effect adapter, and exact-marker adoption contract;
- an application persistence facade that preserves independent durability domains;
- privacy-safe recovery presentation, confirmation-gated user actions, discard-result mapping, and a live recovery dialog entry point;
- an exhaustive structural catalogue for all nineteen encounter families;
- descriptor-bound stable release-evidence snapshots and independent post-publication verification;
- candidate-bound declared direct-dependency inventory and resolved CycloneDX 1.6 dependency evidence;
- a packaged Android native-library/page-size risk verifier wired to the release AAB build;
- complete source-asset provenance coverage, strict schema/adversarial validation, and a release-blocking approval mode;
- source-backed privacy, dependency, accessibility, recovery, content, security, supply-chain, and asset-provenance governance documentation.

### Changed

- validation actions use maintained Node-runtime generations while preserving read-only exact-SHA behavior;
- the canonical host workflow builds declared and resolved dependency evidence before compilation and packaging;
- release preparation now fails before store graphics or metadata generation while any packaged source asset lacks reviewed provenance and distribution approval;
- release-evidence indexing rejects path aliasing, hard-link reuse, symlink traversal, and mutation during review;
- dependency declarations have one pinned authoritative source with no stale parallel catalogue;
- terminal, stumble, and mercy collision source contracts now follow dispatcher ownership while retaining original ordering and persistence guarantees;
- run-session effects no longer duplicate run-start music, now model menu-to-Garden and restart initialization explicitly, and have one planned state-publication boundary;
- debug scenario/autostart state publication now routes through the same typed session owner, with an accepted idempotent debug request distinguished from an invalid stale ordinary no-op;
- recovery maintenance now distinguishes completed discard, recovery instead of deletion, no-longer-applicable races, and I/O failure.

### Fixed

- virtual accessibility actions no longer attempt framework event emission while Android accessibility is disabled, so direct provider routing remains safe in installed-app tests and non-service contexts;
- repeated `singleTask` debug scenario intents no longer reject an already-published PLAYING/PLAYING state;
- malformed or stale accessibility, persistence, release-evidence, dependency, collision, recovery, provenance, and encounter-catalogue fixtures now fail at their owning boundaries without weakening production behavior;
- pending valid ghost manifests now produce pending-recovery copy rather than a false healthy message;
- failed destructive recovery maintenance no longer reports completion to the user;
- recovery dialogs never expose journal payloads, ghost frames, local paths, hashes, or exception messages.

### Validation status

- source-bearing checkpoint `414bf30b36ce051f0d5ef75f6143ed6bf8fa5884` passed Android validation run `31297723150`, including the full host compilation/JVM-Robolectric/lint/APK/androidTest APK/AAB/R8/package-shape/source-immutability job and API-35 connected behavior/source immutability;
- later documentation-only heads must still pass their own canonical exact-head workflow before being called validated;
- physical-device, TalkBack/Switch Access, performance, signing, store-delivery, licence, vulnerability, policy, and final independent-review gates remain separate and unresolved;
- every current asset is registry-covered, but all provenance rules remain intentionally `review-required`, so strict release preparation is expected to fail until real review is recorded.

## Release-note rule

A dated/versioned section must not be added until the accepted candidate is frozen. At that point, copy only verified player-visible changes from `Unreleased`, record the exact version name/code, `main` SHA, signed artifact SHA-256, certificate SHA-256, known limitations, dependency/SBOM evidence, asset-provenance approval, reviewer identities, and matching Play `What's new` text. The final changelog entry is part of the candidate-bound release evidence.
