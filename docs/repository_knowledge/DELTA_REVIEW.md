# Current worktree delta review

**Repository revision:** `eeccc677107f69039dbf71053c18a9038d4b5ff6` (`main`)
**Observed worktree:** 166 changed/untracked paths at task start; all pre-existed this investigation and remain user-owned.
**Review date:** 2026-10-27.
**Method:** static reading and source/diff inspection only; no application, test, script, build, emulator, benchmark, training, or inference execution. “Deep” means a complete small file or focused changed-code region, not an end-to-end audit of every changed file.

## Prior evidence disposition

| Artifact | Disposition | Reason / reuse boundary |
|---|---|---|
| `docs/REPO_CODEBASE_MAP.md` | Reusable with delta review | Detailed implementation map begun 2026-10-03. Its scope notes say an inventory is not proof every line has been inspected; changed/new paths postdate its snapshot. |
| `docs/REPO_MEMORY_MAP.md` | Reusable with delta review | Detailed documentation/navigation and evidence map at 2026-10-03; current docs include subsequent user changes. |
| `docs/AUDIT_LEDGER.md` | Partial, historical | Its latest reconciliation is 2026-10-02; preserve as provenance, not current source truth. |
| `docs/audits/2026-09-20_whole_software_closure.md` | Historical | Exact-head workflow/build/test claims do not transfer to the current dirty tree. Its external gate distinctions remain useful. |
| `README.md`, `docs/ARCHITECTURE.md`, canonical design/evidence specs | Reusable current-intent sources with source delta check | These describe intended contracts and architecture; changed source still requires correlation. |

## Workstreams and evidence actually gathered

| Workstream | Scope read | Result | Coverage limit |
|---|---|---|---|
| Documentation/history | README, `AUDIT_LEDGER.md`, `RELEASE_READINESS.md`, `RELEASE_GOVERNANCE_EVIDENCE.md`, 2026-09-20 closure audit, headings and status of both prior maps | Recovered goals, established current-vs-historical authority, release blockers and no-training applicability | Not a byte-for-byte reread of all 191 tracked docs; prior memory map remains the full navigation index |
| Kotlin/Android delta | Changed/new production Kotlin, manifest/resources and related test sources, with caller search for relevant APIs | Save-repair launch gate statically connects to both activities; score multiplier API exists without a production caller; feedback synchronization, compact layout/locale changes, backup rules and string-resource changes are present | New/small test files read in full; 15 modified test files read at changed-hunk level; no tests executed |
| Script/release implementation delta | All 54 changed/new production Python and shell paths had changed-hunk review with relevant contracts/callers; core shared reader and publishers received deeper review | Shared no-follow/stable-read and atomic-publication mechanisms are present across evidence/release consumers; one raw screenshot canonicality mismatch confirmed from existing repository contract evidence and corrected in TASK-A-026 | No changed production script was run; reviews are delta-scoped, not a full reread of every baseline file |
| Python script-test source | All 60 changed/new `scripts/test_*.py` files read in full across two independent path partitions | Source specifies synthetic, adversarial, schema, candidate-binding, publication, rollback and CLI contracts across tooling areas | 938 test method definitions are source-only; none were executed |

### Deeply inspected script paths

- `scripts/stable_file_reader.py`
- `scripts/strict_json.py`
- `scripts/build_declared_dependency_inventory.py`
- `scripts/build_resolved_dependency_sbom.py`
- `scripts/build_stable_release_evidence_index.py`
- `scripts/publish_device_acceptance_aggregate.py`
- `scripts/collect_installed_candidate_identity.py`
- `scripts/curate_store_screenshots.py`
- `scripts/publish_screenshot_capture_set.py`
- `scripts/verify_release_source_assets.py`
- `scripts/release_artifact_verifier.py`
- `scripts/prepare_play_release.py`
- `scripts/capture_store_screenshots.sh`

The script delta contains 114 changed/new paths (52 production Python modules, 2 shell launchers, 60 test-as-source Python files). The initial pass reviewed 13 central implementation paths; follow-ups reviewed changed hunks and surrounding contracts/callers for every production script path. All 60 script test files were read fully. This is broad changed-tree coverage, not a full-file reread of every baseline implementation or runtime validation.

### Kotlin/Android observations

Changed regions were inspected in the Manifest; Journal/Main activity; `GameStateManager`, `HapticManager`, `InputHandler`, `LeitmotifManager`, `SaveIntegrityManager`, `SfxManager`; `FeedbackSettingsPanel`, `GardenScreen`, `HUD`, `MainMenuScreen`, `MathUtils`; and changed strings. The entire new `SaveIntegrityStartupGate`, backup/data-extraction XML files, `PendingCaptureMediaPublisher`, and eight new Kotlin test files were read. The 15 modified test files had their changed hunks and relevant contracts read; no test was run.

- `app/src/main/java/com/anurag9000/forestrun/SaveIntegrityStartupGate.kt:12-34` gates initialization when save repair returns `WRITE_FAILED`; `MainActivity.kt:60-70,118-121` and `ForestJournalActivity.kt:59-83` connect the decision to app initialization/rendering. `SaveIntegrityManager.kt:84` repairs `best_run_distance`. Source wiring is statically observed; behavior was not executed.
- `app/src/main/java/com/anurag9000/forestrun/engine/GameStateManager.kt:52-53,192-197,231-254,391-398` contains a duration-based score multiplier path. Static caller search found no production call passing the relevant arguments. This is dormant/unintegrated API surface; no explicit requirement for its use was established, so no implementation gap is asserted.
- `app/src/main/java/com/anurag9000/forestrun/utils/MathUtils.kt:32-42` computes normalization using `Double` intermediate arithmetic for finite endpoints. Test source covers extreme float ranges; no test execution claim.
- `app/src/main/AndroidManifest.xml:7-20` refers to backup/data-extraction and resizability policy; `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` define exclusions. `allowBackup` remains false according to inspected manifest. These are source observations only.
- `HapticManager`, `SfxManager`, and `LeitmotifManager` contain synchronization/lifecycle changes. `HUD`, `GardenScreen` and `MainMenuScreen` contain compact layout/locale changes; Journal and gate feedback strings are resource-backed. Consult the current `symbol_inventory.json` entries for the reviewed symbols and source lines.
- Modified/new regression sources cover startup gating, save repair, scoring, feedback lifecycle, HUD bounds/timing/locale, pointer ordering, and helper behavior. They are test specifications in source, not evidence of passing results.

### Android test-source coverage and contracts

The current test delta was read as source evidence, not executed:

- `ApplicationIdentityTest.kt` checks variant package identity, game category, landscape activity, backup flags and XML exclusions for cloud, device transfer and legacy backup.
- `GameStateManagerTest.kt` specifies immediate ordinary bonus points, later multiplier scoring, timed expiration, zero-duration boost persistence until reset, distance scoring through the final update, and invalid-duration rejection. No production caller passes the optional timed-boost arguments.
- `InputHandlerTest.kt` adds locale-aware hold text and multi-pointer order cases: secondary pointer movement cannot steal the primary gesture; primary release ends its gesture exactly once while another pointer remains.
- `HapticManagerTest.kt` checks cue/cancel synchronization against the lifecycle monitor; `SfxManagerTest.kt` asserts playback entrypoints are synchronized.
- `SaveIntegrityManagerTest.kt` specifies repair of malformed `best_run_distance` before typed read; `SaveIntegrityStartupGateTest.kt` specifies fail-closed startup on `WRITE_FAILED` and continuation for current/migrated/future-compatible saves.
- `CatTest.kt` specifies safe-departure cleanup after passing the authored wave distance.
- `FeedbackSettingsPanelLayoutTest.kt`, `HudTimeBoundaryTest.kt` and `MainMenuScreenTest.kt` specify compact-layout non-overlap, seed/heart separation, wrapping for eight authored guidance cues, and menu title/narrative/arrival row spacing.
- `ForestGardenHistoryTest.kt` specifies canonical Garden projection, affordability thresholds, complete catalogue, bounded corrupt progress/negative Seeds and invalid projection rejection. `MathUtilsTest.kt` specifies interpolation, clamping, normalization, mapping, smooth-step, trigonometry and finite float-extreme behavior.
- `BambooEncounterInstrumentedTest.kt` checks passage/stalk geometry plus representative collision/render behavior; `BitmapHelperInstrumentedTest.kt` checks placeholder strip dimensions, frame colors and brightness variation.
- `PendingCaptureMediaPublisherTest.kt` checks successful pending-row publication, output/insert failures, deletion cleanup and suppressed cleanup errors. `HardwareCoreFlowCaptureTest.kt` now captures `GameView` frames, rejects blank captures and uses MediaStore pending rows with late-callback bitmap ownership; setup removes rows in its dedicated capture path.
- `HardwareInputLatencyProfileTest.kt` dispatches directly to `GameView` on the UI thread and labels its metric as app-path-to-posted-frame latency, excluding Android input dispatch and touch-to-photon components. It uses `DOG_BUDDY` to keep the run live and records state/sample metadata.
- `MainActivityInstrumentedTest.kt` waits for the runtime producer to stop before mutating owners and excludes Bamboo only if the actual surface cannot support it. `LifecycleSettingsInstrumentedTest.kt` checks that `finish()` is accepted (`isFinishing`) rather than waiting for singleTask destruction.
- `ForestJournalLifecycleInstrumentedTest.kt` checks the localized Journal return label and accessible description. `RuntimeAssetValidatorInstrumentedTest.kt` specifies release asset validation under a non-debuggable context; it remains an unexecuted contract.

All 60 changed/new Python test files were read in full (28 `a`–`m` paths and 32 `n`–`z` paths); 938 test method definitions were identified from source. Eight new Kotlin test files were read in full and 15 modified Kotlin test files at changed-hunk level. None were executed.

### Script/release observations

- `scripts/stable_file_reader.py` is a new shared filesystem evidence-safety layer. Key symbols include descriptor-held no-follow traversal (`open_directory_descriptor`), safe directory creation, guarded atomic writes/replacement/publication, directory publication/rollback, and bounded regular-file reads with identity/metadata rechecks and SHA-256 (`consume_regular_file`). The source is about 2.5k lines and is a shared dependency for multiple evidence consumers. Its filesystem/concurrency/rollback/platform semantics have not been runtime verified here.
- `scripts/strict_json.py:20-196` provides bounded UTF-8 JSON parsing, duplicate-key and non-finite rejection, depth/size/integer limits, exact-type recursive equality, and stable-file intake.
- `build_declared_dependency_inventory.py` adds comment/string-aware Kotlin Gradle parsing and stable reads; `build_resolved_dependency_sbom.py` rejects `FAILED` report lines and uses stable input; `build_stable_release_evidence_index.py` constrains paths and binds copied file identities/hashes.
- `publish_device_acceptance_aggregate.py` revalidates source/manifest snapshots and compares staged output; `collect_installed_candidate_identity.py` stages and atomically publishes candidate identity evidence; screenshot capture/curation validates raw bytes and candidate/scenario metadata before staged publication.
- `release_artifact_verifier.py`, `verify_release_source_assets.py`, `prepare_play_release.py` and `capture_store_screenshots.sh` add stricter archive, asset, audio/font, output-path and cleanup rules. Static presence does not establish an actual package, screenshot capture, device collection, or release result.
- `compile_installed_identity_matrix.py` now parses, validates and hashes the same bounded device-manifest bytes before atomic matrix/summary publication; `validate_installed_candidate_identity.py` requires exact digest/path spelling and validates supplied objects against source bytes. Play delivery/readiness consumers reuse validated exact-byte identities.
- `validate_manifest_scenario_traces.py` reads an acceptance manifest through the stable reader and validates every admitted scenario trace against candidate commit/artifact identity; aggregation and publication paths consume this wrapper. `validate_scenario_trace.py` preserves lexical source roots and no longer normalizes expected commit/artifact hashes before checking authored scenario/action contracts.
- `validate_play_delivery_evidence.py`, `validate_release_governance.py` and `validate_release_readiness.py` strengthen exact path/hash, reviewer/time, HTTPS/privacy, vulnerability-reporting and cross-layer candidate/certificate/index binding. These remain structural admission rules; validators cannot supply real Play, human, legal or governance evidence.
- `verify_android_page_size_package.py` freezes and hashes the package, bounds and validates ZIP members and detects ELF content even without a `.so` suffix. Its documented `no-native-code` result is package-inspection evidence, not device runtime compatibility.
- `verify_raw_screenshot_set.py` hardens stable reads, inventory and candidate/device/session/scenario binding. One detail was reconciled: the raw verifier now rejects surrounding whitespace and uppercase hashes in line with adjacent digest writers and the historical contract. TASK-A-026 records the source correction; runtime behavior remains unverified.
- `verify_release_summary.py`, `verify_store_graphics.py`, `verify_store_listing_parity.py`, `verify_store_metadata.py` and `verify_strict_json_evidence.py` use bounded exact-byte reads and safer candidate/path admission. The summary verifier may retain up to its 512 MiB bound per fact in memory; no memory profile was taken. Recursive JSON discovery still uses filesystem discovery methods before the selected files pass through the strict no-follow reader; static review did not establish a runtime bypass.
- `write_screenshot_capture_evidence.py` validates image/candidate/device metadata and UTC timestamps before atomic sidecar publication. `visual_baseline_provenance.py` binds baseline screenshots/manifests to the candidate but explicitly does not establish visual approval. `import_final_assets.py` now uses context-managed image data, proportional integer slicing that preserves source remainder pixels, validates mapping parameters and atomically publishes PNGs; no import was run.
- The `prepare_main_release.sh` caller orders candidate checks, store inputs, graphics/metadata checks and summary verification. `run_all_training.py` now removes a stale prior certificate before re-auditing, so old PASS evidence cannot survive a failed new audit; no workflow was run.

The principal delta theme is race/symlink/TOCTOU resistance, strict evidence intake, source/candidate binding, and atomic publication. It creates a foundational verification dependency, not a measured performance bottleneck or proven defect.

## Confirmed findings, gaps and unresolved questions

1. **Reusable maps do not cover today’s dirty delta.** The delta paths need targeted inspection records; do not repeat untouched prior subsystem audits.
2. **New safety/publication APIs have source evidence but no current runtime evidence.** This is a verification gap, not evidence that the APIs fail.
3. **Many modified/new tests are source-only evidence here.** Prior historical test-suite results do not validate the changed working tree.
4. **The timed score multiplier has no discovered production invocation.** Whether this represents intentional unused general-purpose API or unfinished intended gameplay behavior is unresolved because no explicit requirement was found.
5. **External release gates remain unresolved by design.** Physical device/human acceptance, creative/rights, signed candidate/Play, hosted privacy and policy/security/governance decisions require actual external evidence and accountable owners.
6. **Current dirty script-surface inspection is broad but delta-scoped.** All 60 changed/new Python test files were read fully and all 54 production script/shell paths had changed-hunk review with caller/contract context. No baseline production file was reread end-to-end solely for this delta, and no production script was executed.
7. **Raw screenshot canonicality mismatch corrected statically.** Repository-local prior contract notes and adjacent writer/parser/finalizer sources require exact unpadded strings and lowercase hexadecimal hashes. `TASK-A-026` updates `verify_raw_screenshot_set.py` accordingly. No behavior was executed; runtime verification remains open.

## Next actions and current state

The original delta review was read-only; that restriction is historical. The user later authorized source completion and applicable verification. `TASK-A-035` closed the statically identified OWL_DIVE input-path gap, and EVID-072 records Python/JVM suite, lint, and APK assembly evidence on the isolated merged candidate. The original dirty worktree remains preserved.

- Use `execution/NEXT_ACTIONS.md` and the master ledger for current work, rather than replaying this historical read-only queue.
- `TASK-C-007`–`TASK-C-011` have partial suite/build evidence; connected/device-specific criteria remain open. `TASK-C-012` concurrency-specific validation and `TASK-C-013` candidate-bound OWL_DIVE screenshot proof remain deferred/blocked.
- Keep external release tasks separately owned and candidate-bound. Source automation does not substitute for human, legal, physical, signing, policy or store evidence.
- Reuse completed `TASK-C-005` static contract reconciliation and `TASK-A-026` source correction unless a source or requirement delta invalidates their evidence.



## Follow-up historical and ledger reconciliation

Three bounded read-only follow-ups checked prior architecture status chronology, current source seams, and ledger schema consistency. `docs/repository_knowledge/HISTORICAL_ARCHITECTURE_RECONCILIATION.md` records the complete crosswalk. The Aug 6 audit's collision dispatcher, live-effect adapters, run-session coordinator and persistence facade were closed in Aug 9; the structural encounter catalogue and ordinary-player recovery UI were also closed in bounded forms. Sep 20/27 evidence does not reopen those source tasks. The broader data-oriented authoring proposal remains optional and unselected. Current source code was read at the relevant symbols and call paths; no tests/build/runtime were run.

The master ledger now links every goal to requirements, requirements to capabilities, and capabilities back to requirements. Algorithm records link to selected source symbols, method/variant/setting summaries, and components. Each task carries separate investigation, implementation/investigation, evidence, and execution states. The state history keeps legacy events immutable and uses stable event IDs for newly appended records. The file inventory includes file types and stable SHA-256 fingerprints for 167 dirty regular-file records, summarized by a deterministic worktree snapshot ID; symlinks/nonregular files are not followed.


## Follow-up: authorized source completion and verification (2026-10-27)

The preceding review was performed under a read-only scope and is retained as historical evidence. The user later authorized source completion and verification. `TASK-A-035` adds a `TAP_JUMP` to the `OWL_DIVE` deterministic script at 1.80 seconds because the named `07-owl.png` capture previously launched without the visible-player jump required by Owl. Full suite/build evidence and remaining device/external limits are recorded in `verification_delta_2026-10-27.md` and EVID-072. The original dirty checkout remains preserved.
