# Forest Run — Release and Validation

Forest Run is a **feature-rich alpha**. Primary gameplay, encounter, persistence, lifecycle, UI, asset, build, and automated-validation defects have been remediated, but a release candidate requires one frozen `main` SHA with complete automated, physical-device, performance, signed-artifact, visual, and store evidence.

A feature is not complete merely because code exists. A gate is complete only when its evidence is tied to the exact candidate commit.

## 1. Candidate discipline

For every release-candidate attempt:

1. Freeze one `main` commit SHA without creating a release branch or pull request.
2. Run permanent read-only CI on that SHA.
3. Archive host and connected-test artifacts.
4. Build the signed minified artifact from that SHA.
5. Install and smoke-test the signed artifact.
6. Run deterministic and ordinary-play acceptance on representative hardware.
7. Capture performance evidence and compare it with written thresholds.
8. Approve artwork, screenshots, metadata, and policy declarations.
9. Perform a final independent code/diff audit.
10. Only then tag that exact frozen `main` SHA as the accepted candidate.

Any source, asset, dependency, build, or documentation change invalidates candidate evidence and starts this sequence again.

## 2. Canonical commands

CI uses Java 21 because the API 36 Robolectric runtime requires it. Android source/target bytecode remains Java 17.

```bash
# Compile all relevant source sets
bash gradlew \
  compileDebugKotlin \
  compileDebugUnitTestKotlin \
  compileReleaseKotlin \
  compileDebugAndroidTestKotlin

# JVM/Robolectric invariants
bash gradlew testDebugUnitTest

# Android static analysis
bash gradlew lintDebug lintRelease

# Debug app and instrumentation APKs
bash gradlew assembleDebug assembleDebugAndroidTest

# Minified/resource-shrunk unsigned release bundle and R8 mapping
bash gradlew bundleRelease

# Emulator or device required
bash gradlew connectedDebugAndroidTest

# Representative physical-device profiling
bash scripts/collect_performance_profiles.sh
```

Expected build outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `app/build/outputs/bundle/release/app-release.aab`
- `app/build/outputs/mapping/release/mapping.txt`

## 3. Current project settings

| Setting | Repository value | Remaining release action |
|---|---:|---|
| Application ID / namespace | `com.anurag9000.forestrun` | Preserve permanently after first store upload |
| Debug application ID | `com.anurag9000.forestrun.debug` | Keep isolated from release data |
| Min SDK | API 24 | Confirm supported-device policy |
| Compile / target SDK | API 36 | Reverify against current Play requirements before upload |
| Android Gradle Plugin | 8.13.2 | Keep wrapper/toolchain compatibility pinned |
| Gradle wrapper | 8.13 | Validate wrapper on every candidate |
| CI Java runtime | 21 | Preserve while API 36 Robolectric requires it |
| Android bytecode | Java 17 | Preserve current toolchain compatibility |
| Orientation | Fixed landscape | Accept across phones/tablets/cutouts or change product policy |
| Release optimization | R8 minification + resource shrinking | Smoke-test actual signed artifact |
| Release signing | External credentials supported | Supply upload-key credentials and verify certificates |
| Store Python dependency | `Pillow==11.3.0` | Revalidate only when intentionally updating the pipeline |
| Ghost binary format | v2 magic/version + stable state codes | Preserve legacy reader and version before future format changes |

Release-signing inputs:

- `FOREST_RUN_KEYSTORE`
- `FOREST_RUN_STORE_PASSWORD`
- `FOREST_RUN_KEY_ALIAS`
- `FOREST_RUN_KEY_PASSWORD`

No key material belongs in source control.

## 4. Permanent CI contract

Permanent CI must remain:

- read-only (`contents: read`);
- checked out at the exact event SHA;
- without persisted Git credentials;
- free of source-transform/bootstrap scripts;
- free of commit/push steps;
- split into host/release and connected-emulator jobs.

### Host/release job

The exact candidate must pass:

- [x] package and source-layout contract checks;
- [x] absence of temporary patch scripts and tracked diagnostics;
- [x] debug/release/unit/instrumentation compilation;
- [x] complete JVM/Robolectric suite;
- [x] debug and release lint;
- [x] debug application APK assembly;
- [x] instrumentation APK assembly;
- [x] minified/resource-shrunk unsigned AAB build;
- [x] non-empty R8 mapping;
- [x] proof that application classes are actually renamed;
- [x] strict sprite/audio/font runtime contracts;
- [x] source contracts for collision, telemetry, save repair, ghost persistence, and settings.

### Connected emulator job

The exact candidate must execute—not merely assemble—the connected suite:

- [x] API 35 emulator boot and installation;
- [x] exactly fourteen ordinary connected tests;
- [x] zero failures;
- [x] zero errors;
- [x] zero skips;
- [x] lifecycle pause/resume and Surface recreation;
- [x] repeated `singleTask` intents;
- [x] settings recreation;
- [x] corrupt-save recovery;
- [x] gameplay input;
- [x] Garden transactions;
- [x] entity, biome, Bloom, collision, and safe-content flows;
- [x] asynchronous ghost disk reload.

`@LargeTest` hardware capture/profile suites are compiled but intentionally excluded from ordinary emulator CI.

## 5. Gameplay correctness gate

Implemented and automatically covered:

- [x] quick taps receive upward velocity;
- [x] hold duration monotonically controls retained jump height;
- [x] swipe-down is classified before jump commitment;
- [x] gesture cancellation cannot produce phantom actions;
- [x] inactive screens/states cannot mutate gameplay input;
- [x] Bloom is orthogonal to locomotion;
- [x] `GameStateManager` owns one authoritative Bloom clock;
- [x] active Bloom rewards do not restart the timer;
- [x] one terminal outcome per entity;
- [x] collision arbitration precedes pass resolution;
- [x] deterministic severity `HIT > STUMBLE > MERCY`;
- [x] collision probes are mutation/presentation-free;
- [x] only the selected overlap receives effects;
- [x] mercy resolves once;
- [x] Hedgehog resolves terminally as a nonlethal stumble;
- [x] Bloom conversion excludes ordinary pass, unique-action, and Orb rewards;
- [x] Seed Orbs are staged ahead and cleaned up off-screen;
- [x] unsafe entity pooling remains disabled;
- [x] Eagle follows the live player before lock and preserves escape grace;
- [x] Cat exit/outcome paths are mutually coherent;
- [x] flora/tree sway no longer displaces invisible collision geometry;
- [x] mercy safe windows preserve custom asymmetric geometry without temporary `RectF` allocations;
- [x] encounter pacing uses world-space separation rather than elapsed time.

Still requiring physical/ordinary-play acceptance:

- [ ] touch latency and gesture comfort;
- [ ] high-refresh input behavior;
- [ ] high-speed encounter combinations;
- [ ] fairness after sustained difficulty growth;
- [ ] every entity telegraph/hitbox/outcome agreement;
- [ ] Bloom audiovisual clarity without obscuring hazards.

## 6. Persistence, progression, and Garden gate

Implemented and automatically covered:

- [x] deterministic scenarios cannot contaminate permanent score, encounter, relationship, summary, Garden, best-distance, or ghost history;
- [x] clean passes and resolved encounters are recorded centrally and once;
- [x] familiarity alone cannot reach Trust/Bond;
- [x] positive outcomes advance relationships and hits delay them;
- [x] Garden spending cannot be overwritten by stale run state;
- [x] run reset reloads externally changed Seed currency;
- [x] sanctuary counts are clamped non-negative;
- [x] return moments are consumed on visible Garden entry;
- [x] daily return logic uses local calendar day;
- [x] willow ritual resets on returning home;
- [x] Garden particles advance while Garden is active;
- [x] Garden visual and touch regions share one tested layout plan;
- [x] save data is schema-versioned;
- [x] known corrupt values are repaired;
- [x] incomplete summaries are rejected;
- [x] counters/writes are clamped or saturating;
- [x] unknown preference keys are preserved;
- [x] newer-schema primary data is preserved through compatibility storage.

Still requiring physical/ordinary-play acceptance:

- [ ] Garden density/readability on phones and tablets;
- [ ] touch-target comfort;
- [ ] long-session currency/progression balance;
- [ ] return-moment emotional repetition and cadence;
- [ ] wardrobe and visitor presentation.

## 7. Ghost gate

Implemented and automatically covered:

- [x] 30 Hz recording instead of render-rate recording;
- [x] twenty-minute bounded recording capacity;
- [x] O(1) detached best-run snapshot;
- [x] immediate in-memory publication;
- [x] dedicated-worker disk persistence;
- [x] atomic writes;
- [x] corrupt, truncated, trailing, oversized, non-finite, invalid-state, and non-monotonic rejection;
- [x] binary format v2 magic and version headers;
- [x] stable persisted state codes independent of enum order;
- [x] legacy count/raw-ordinal file reads;
- [x] unknown future-version rejection without destructive rewrite;
- [x] Activity-recreation disk reload.

Still requiring physical/ordinary-play acceptance:

- [ ] save latency under sustained device load;
- [ ] playback readability near dense hazards;
- [ ] long-best-run disk size and I/O behavior;
- [ ] process-death/relaunch behavior on representative OEM devices.

## 8. Lifecycle, UI, accessibility, audio, and haptic gate

Implemented and automatically covered:

- [x] bounded interruption-safe render-thread shutdown;
- [x] caller interruption restoration;
- [x] stale-render suppression after stop;
- [x] repeated launch-intent handling with latest-intent ownership;
- [x] explicit audio/haptic teardown;
- [x] cached game-over composition;
- [x] bounded/wrapped/deduplicated/clamped dialogue and flavour queues;
- [x] aspect-preserving safe-content transform;
- [x] inverse touch mapping;
- [x] persistent reduced-motion setting;
- [x] persistent audio setting;
- [x] persistent haptic setting;
- [x] manager-boundary enforcement of those settings;
- [x] explicit `SoundPool` readiness/failure handling;
- [x] deterministic adaptive-music crossfade ownership;
- [x] throttled repeated music parameter writes;
- [x] finite and capped Menu, HUD, Rest, and Player presentation clocks.

Still requiring hardware acceptance:

- [ ] cutouts/system bars/unusual aspect ratios;
- [ ] phone/tablet density and typography;
- [ ] reduced-motion adequacy without losing telegraphs;
- [ ] SFX/music loudness and latency;
- [ ] crossfade behavior on lifecycle transitions;
- [ ] haptic intensity/differentiation across OEMs;
- [ ] toggles remain reachable and immediate before/within a run.

## 9. Performance gate

Implemented instrumentation:

- [x] fixed primitive timing ring buffers;
- [x] update/render/processing duration recording in the real game thread;
- [x] mean, p50, p95, p99, maximum, and slow-frame ratio snapshots;
- [x] Java heap observations;
- [x] deterministic JSON reports;
- [x] physical `OPENING_READABILITY` profiling scenario;
- [x] physical `BLOOM_SHOWCASE` profiling scenario;
- [x] device/build metadata collection;
- [x] `gfxinfo`, `meminfo`, and display diagnostics collector;
- [x] local generated evidence excluded from source control.

Evidence still required:

- [ ] constrained/older supported phone;
- [ ] current mid-range phone;
- [ ] high-refresh phone;
- [ ] cutout/unusual-aspect device;
- [ ] tablet if supported;
- [ ] ordinary-play long-run profile;
- [ ] allocation/GC trace beyond heap snapshots;
- [ ] audio-thread trace;
- [ ] ghost-save I/O duration evidence;
- [ ] thermal/battery degradation evidence;
- [ ] written p95/p99/slow-frame/memory thresholds;
- [ ] remediation and remeasurement of every material hotspot.

See `docs/PERFORMANCE.md`.

## 10. Asset and runtime gate

Implemented programmatic contracts:

- [x] required sprites, mandatory audio, and fonts must exist outside debug;
- [x] required raw resources must contain readable data;
- [x] sprite sheets must decode;
- [x] dimensions must be sane;
- [x] atlas width must divide by declared frame count;
- [x] generated placeholder sprites are prohibited outside debug;
- [x] debug placeholder construction is overflow-safe and allocation-bounded;
- [x] optional Bloom sounds have explicit fallback behavior;
- [x] mandatory and optional audio are distinguished;
- [x] playback waits for successful sample loading.

Manual visual/audio work still required:

- [ ] inspect every sprite and atlas frame;
- [ ] verify transparent edges and scaling;
- [ ] verify animation cadence;
- [ ] verify collision silhouette agreement;
- [ ] inspect Wolf sheet and charge sequence;
- [ ] inspect costume overlays and ghost visuals;
- [ ] approve procedural scenic layers or replace them;
- [ ] verify all mandatory/optional audio on hardware.

## 11. Device acceptance scenarios

A deterministic scenario is accepted only after both the scripted path and ordinary play succeed on representative hardware.

### Core flows

- [ ] `OPENING_READABILITY`: tap, hold, duck, cancellation, spacing, text, and first encounters are self-explanatory;
- [ ] `BLOOM_SHOWCASE`: activation, locomotion, conversion, expiry, HUD, audio, haptics, and visual intensity agree;
- [ ] `GHOST_READABILITY`: ghost remains legible without obscuring the runner/hazards or hitching on save;
- [ ] `REST_LOOP`: death, summary, fade, Garden, currency, and reset remain coherent;
- [ ] Garden ordinary flow: catalogue, stats, narrative, wardrobe, back, particles, and run button;
- [ ] safe-content flow: UI and mapped touches remain inside safe bounds;
- [ ] feedback controls: settings take effect immediately without altering physics/telegraphs;
- [ ] save recovery: legacy/corrupt/future data behavior remains coherent;
- [ ] lifecycle recovery: background/resume, process recreation, repeated intents, and Surface recreation.

### Flora and trees

- [ ] `CACTUS_READ`
- [ ] `LILY_GLOW`
- [ ] `HYACINTH_BRUSH`
- [ ] `EUCALYPTUS_WHIP`
- [ ] `ORCHID_WINDOW`
- [ ] `WILLOW_CURTAIN`
- [ ] `JACARANDA_PETALS`
- [ ] `BAMBOO_GAP`
- [ ] `CHERRY_GUST`

The Eucalyptus whip and Cherry gust are currently telegraph/presentation identities around collision geometry; they must not be accepted as external-force mechanics unless real force/timing behavior is implemented and tested.

### Birds and animals

- [ ] `DUCK_TEACH`
- [ ] `TIT_WAVE`
- [ ] `CHICKADEE_SWERVE`
- [ ] `OWL_DIVE`
- [ ] `EAGLE_MARK`
- [ ] `CAT_KINDNESS`
- [ ] `FOX_MIRROR`
- [ ] `WOLF_CHARGE`
- [ ] `HEDGEHOG_DEBUFF`
- [ ] `DOG_HAZARD`
- [ ] `DOG_BUDDY`

## 12. Signed-artifact gate

Still required:

- [ ] provision real upload key securely;
- [ ] build signed, minified release artifact;
- [ ] verify package ID/version/signing certificate;
- [ ] install the signed artifact directly;
- [ ] launch and complete core smoke flow;
- [ ] verify R8/resource shrinking did not break runtime lookups;
- [ ] run lifecycle and persistence smoke tests;
- [ ] run long-session stability test;
- [ ] upload to internal testing track;
- [ ] install through the store delivery path;
- [ ] compare store-delivered certificate/package/version with expected values.

## 13. Store asset pipeline

```bash
python3 -m pip install -r scripts/requirements.txt
python3 scripts/generate_store_assets.py
bash scripts/capture_store_screenshots.sh
python3 scripts/curate_store_screenshots.py
python3 scripts/prepare_play_release.py
```

Automated safeguards implemented:

- [x] dependency version is pinned;
- [x] no hard-coded local SDK/JDK/ADB paths;
- [x] explicit device serial selection;
- [x] application reset between deterministic captures;
- [x] rejection of empty/invalid/portrait/stale screenshots;
- [x] rejection of exact and near duplicates;
- [x] rejection of low-variance/suspicious edge-band captures;
- [x] generated graphic dimensions and hashes verified;
- [x] metadata non-empty and placeholder-screened;
- [x] final application ID accepted and known placeholders rejected;
- [x] signing inputs required by default for upload preparation;
- [x] Java/toolchain and Gradle release gates invoked by preparation.

Manual/store work still required:

- [ ] capture at least four final screenshots on accepted hardware;
- [ ] verify each screenshot depicts the intended scenario;
- [ ] verify no system/debug overlay appears;
- [ ] approve icon, feature graphic, and promotional graphics;
- [ ] finalize title, short description, and full description;
- [ ] finalize privacy policy decision;
- [ ] complete Play data-safety answers;
- [ ] complete content rating and target-audience declarations;
- [ ] revalidate permissions;
- [ ] revalidate current Android/Play requirements immediately before submission.

## 14. Architecture and final audit gate

Still required before declaring the original exhaustive mission complete:

- [ ] independently re-audit every changed file on the frozen candidate;
- [ ] inspect unchanged files reachable from changed code;
- [ ] verify tests are not reproducing implementation mistakes;
- [ ] verify every documentation statement against code and evidence;
- [ ] verify no temporary workflow/script/artifact remains;
- [ ] inspect all persistence reads/writes and migrations;
- [ ] inspect every entity state/outcome/reward path;
- [ ] inspect all lifecycle and thread transitions;
- [ ] inspect build/signing/R8/store scripts;
- [ ] review the complete candidate diff on `main` since the previous accepted release/tag;
- [ ] record any accepted architectural debt.

Known debt that may remain for a post-release-candidate refactor if behavior is stable:

- `GameView` is still a large coordinator;
- persistence ownership remains distributed across several managers;
- some entity uniqueness is presentation rather than additional physics.

These debts must be documented and bounded; they must not be falsely called resolved.

## 15. Release exit criteria

Forest Run may be called a release candidate only when all are true:

1. The exact frozen SHA passes host/release CI.
2. The exact frozen SHA passes the fourteen-test emulator gate with zero skips.
3. No known P0/P1 gameplay, persistence, lifecycle, packaging, or data-loss defect remains.
4. Representative physical-device deterministic and ordinary-play scenarios pass.
5. Written performance thresholds are met with archived evidence.
6. The signed minified artifact passes install, smoke, lifecycle, persistence, and long-run testing.
7. Safe-content, settings, audio, haptics, and reduced motion are accepted on hardware.
8. Artwork/animation/scenic direction is manually approved.
9. Screenshots, metadata, privacy, data safety, content rating, and current policy requirements are approved.
10. The final independent exhaustive audit and complete candidate-diff review are complete.

Until then, keep `main` untagged and describe the project as a feature-rich alpha.
