# Forest Run — Release & Validation

Forest Run is a feature-rich alpha with the primary correctness-remediation pass implemented. A release candidate must still pass connected-device, physical-hardware, signed-artifact, performance, settings/accessibility, and store-acceptance gates.

## Canonical Build Commands

CI runs with Java 21 because the API 36 Robolectric test runtime requires it. Android source and target compatibility remain Java 17.

```bash
# Compile debug and release variants
bash gradlew compileDebugKotlin compileDebugUnitTestKotlin compileReleaseKotlin

# JVM/Robolectric invariant suite
bash gradlew testDebugUnitTest

# Static Android checks
bash gradlew lintDebug lintRelease

# Debug application and instrumentation APKs
bash gradlew assembleDebug assembleDebugAndroidTest

# Minified, resource-shrunk release bundle and R8 mapping
bash gradlew bundleRelease

# Requires an emulator or physical device
bash gradlew connectedDebugAndroidTest
```

Expected outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Android test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt`

A checklist item is complete only when the corresponding command or deterministic assertion passes on the audited branch head. Building an instrumentation APK is not the same as executing connected tests.

## Current Project Settings

| Setting | Current repository value | Remaining release action |
|---|---:|---|
| Application ID / namespace | `com.anurag9000.forestrun` | Preserve permanently after first store upload |
| Min SDK | API 24 | Confirm supported-device policy |
| Compile / target SDK | API 36 | Reverify immediately before upload |
| Android Gradle Plugin | 8.13.2 | Keep wrapper/toolchain compatibility pinned |
| Gradle wrapper | 8.13 | Wrapper validation runs in CI |
| CI Java runtime | 21 | Required by API 36 Robolectric tests |
| Source/target bytecode | Java 17 | Supported by current Android toolchain |
| Manifest orientation | fixed `landscape` | Validate across phones, tablets, cutouts, and rotation lifecycle |
| Essential UI safe area | aspect-preserving system-bar/cutout transform | Accept visually and interactively on representative hardware |
| Release minification | R8 + resource shrinking; renamed app classes verified from mapping | Smoke-test the actual signed artifact |
| Release signing | external environment/Gradle properties supported | Supply real upload-key credentials securely |

Required signing properties or environment variables:

- `FOREST_RUN_KEYSTORE`
- `FOREST_RUN_STORE_PASSWORD`
- `FOREST_RUN_KEY_ALIAS`
- `FOREST_RUN_KEY_PASSWORD`

The default Play release script refuses unsigned upload preparation unless `--allow-unsigned` is explicitly used for a non-upload dry run.

## Correctness Gate

### Implemented and automatically validated

- [x] Quick taps receive upward jump velocity
- [x] Hold duration controls jump height monotonically
- [x] Swipe-down is classified before a jump begins
- [x] Cancelled gestures cannot create phantom actions
- [x] Menu, Garden, dying, game-over, and restart input cannot mutate gameplay state
- [x] Render-thread shutdown is bounded and interruption-safe
- [x] Bloom is orthogonal to locomotion and preserves airborne physics
- [x] `GameStateManager` owns the authoritative Bloom clock
- [x] Active-Bloom rewards cannot reset the Bloom timer
- [x] Every entity receives at most one terminal encounter outcome
- [x] Collision arbitration precedes pass rewards
- [x] Collision severity is deterministic: hit → stumble → mercy
- [x] Collision queries are presentation- and mutation-free
- [x] Only the selected overlap receives terminal effects
- [x] Mercy is awarded once per encounter
- [x] Bloom conversion excludes clean-pass, unique-action, and orb rewards
- [x] Hedgehog contact resolves terminally as a nonlethal stumble
- [x] Seed Orbs are staged ahead of the player and cleaned up off-screen
- [x] Unsafe entity pooling remains disabled until complete reset contracts exist
- [x] Deterministic scenarios cannot write permanent encounter, pass, hit, spare, summary, ghost, best-distance, friendship, or high-score history
- [x] Persistent clean passes and resolved encounters are recorded centrally and once
- [x] Relationship familiarity alone is capped at Recognition; Trust and Bond require positive outcomes and hits delay progression
- [x] Garden spending cannot be overwritten by stale lifetime-seed state
- [x] Reused `singleTask` intents apply new deterministic scenarios
- [x] Activity/audio/haptic teardown and recreation paths are bounded
- [x] Eagle targeting follows the live player before lock and preserves an escape grace window
- [x] Decorative flora/tree sway no longer shifts invisible collision geometry
- [x] Random encounter pacing uses world-space separation rather than elapsed time
- [x] Garden particles advance while Garden is active
- [x] Return moments are consumed on visible Garden entry, not hidden preload
- [x] Daily return logic uses the local calendar day
- [x] Sanctuary-derived counts are clamped non-negative
- [x] The willow menu ritual resets on return home
- [x] Game-over composition and persistence reads are not rebuilt every draw frame
- [x] Dialogue/flavor queues are bounded, wrapped, deduplicated, and screen-clamped
- [x] `SoundPool` readiness, recreation, and failure diagnostics are explicit
- [x] Adaptive-music writes are throttled and crossfade ownership is deterministic
- [x] Garden catalogue, stats, last-run, wardrobe, and run-button regions are non-overlapping at compact, standard, and large landscape sizes
- [x] Best-run ghost capture detaches in O(1), publishes to playback memory immediately, and persists atomically off the render thread
- [x] Corrupt, oversized, truncated, trailing, non-finite, invalid-state, or non-monotonic ghost files are rejected safely
- [x] Menu, Garden, HUD, debug controls, and rest UI share one aspect-preserving safe-content transform with inverse touch mapping

### Still required before release

- [ ] Validate safe-content behavior and density scaling on representative cutout, unusual-aspect, phone, and tablet hardware
- [ ] Add reduced-motion, audio, and haptic user settings
- [ ] Profile and remove material per-frame allocations or emitter churn found on hardware
- [ ] Verify broader save migration, SharedPreferences corruption recovery, and forward compatibility
- [ ] Decide whether fixed landscape and the remaining procedural scenic layers are final product choices

## Automated-Test Gate

### Covered

- [x] Tap, hold, swipe-down, cancellation, and silent-reset gesture arbitration
- [x] Jump-force clamping and monotonic hold scaling
- [x] Instantiated airborne Player preserves ascent, gravity, fall, landing, and state through Bloom
- [x] Active Bloom rewards do not restart Bloom
- [x] Mercy resolves once even when collision checks repeat
- [x] One selected terminal result per entity
- [x] Lethal overlap outranks simultaneous stumble and mercy
- [x] Collision resolves before pass detection
- [x] Debug entities do not write permanent counters
- [x] All 19 concrete entity types record exactly one ordinary clean pass and encounter
- [x] Bloom conversion cannot stack pass, unique-action, and orb rewards
- [x] Stale game state cannot refund Garden spending
- [x] Run reset reloads externally changed seed currency
- [x] Garden card and wardrobe hit targets use the production layout plan
- [x] Garden layout regions do not overlap at 1280×720, 1920×1080, or 2560×1440
- [x] Local-day and visible-entry return-moment behavior
- [x] Menu ritual reset behavior
- [x] Dialogue/flavor queue bounds and wrapping
- [x] Sprite decode, frame divisibility, and sane-dimension contracts
- [x] Final debug application identity
- [x] Familiarity-only, hit-only, Trust-recovery, and Bond-depth relationship progression
- [x] Detached ghost buffers remain stable while a new recording starts
- [x] Atomic ghost round trips and malformed-file rejection
- [x] Safe-content identity, asymmetric-cutout, round-trip, edge-clamp, and pathological-inset geometry
- [x] R8 mapping contains actually renamed Forest Run classes

### Still needed

- [ ] Execute `connectedDebugAndroidTest` on an emulator and physical device
- [ ] Add a deterministic interruption test around the real `GameThread`/`GameView` shutdown boundary if feasible without instrumentation
- [ ] Add broader SharedPreferences save-corruption and migration fixtures
- [ ] Add signed-release installation and launch smoke tests

## Asset and Runtime Gate

- [x] Non-debug runtime validation fails when required sprites, mandatory audio, or fonts are absent
- [x] Sprite sheets must decode, divide by frame count, and remain within sane source dimensions
- [x] Generated placeholder sprites are prohibited in non-debug execution
- [x] Optional Bloom-ready/convert/fade sounds use explicit fallback behavior and do not masquerade as mandatory assets
- [x] Sound playback waits for successful sample loading
- [x] Debug and release lint pass
- [x] Debug and release Kotlin compilation pass
- [x] Debug and instrumentation APK assembly pass
- [x] The minified, resource-shrunk unsigned release AAB builds successfully
- [x] Unused Gson packaging and package-wide application keep rules are removed
- [x] CI verifies that release mapping contains obfuscated application classes and uploads the mapping artifact
- [ ] Install and smoke-test the minified release on hardware
- [ ] Supply real signing credentials and verify the signed AAB/APK
- [ ] Profile frame time, memory, GC, audio threads, I/O, and long-run stability
- [ ] Validate all artwork and animation frame counts visually, including the Wolf sheet
- [ ] Approve procedural scenic layers as final art direction or replace them

## Device Acceptance Checklist

A feature is not complete until its deterministic scenario and ordinary-play check pass on representative hardware. Validate at least one constrained/older device, one current mid-range device, and one high-refresh device; include a cutout or unusual-aspect device where possible.

### Core Flow

| Scenario | Acceptance criterion |
|---|---|
| `OPENING_READABILITY` | First 20–30 seconds teach duck, tap jump, hold jump, and spacing without gesture conflicts |
| `BLOOM_SHOWCASE` | Activation, invincibility, preserved locomotion, conversion rules, HUD state, audio, haptics, and expiration are unmistakable |
| `GHOST_READABILITY` | Ghost never resembles a broken duplicate, obscures the runner, or hitches on save |
| `REST_LOOP` | Failure, rest summary, fade, Garden return, currency, and next-run reset remain coherent |
| Garden ordinary flow | Catalogue, stats, narrative, wardrobe, particles, back gesture, and run button remain readable and tappable |
| Safe-content flow | Essential UI and mapped tap regions remain inside cutouts/system bars without distorting readability |
| Lifecycle recovery | Background/resume, process recreation, repeated intents, and surface recreation preserve coherent state |

### Flora and Trees

| Scenario | Acceptance criterion |
|---|---|
| `CACTUS_READ` | Silhouette and jump timing are immediate and fair |
| `LILY_GLOW` | Glow and seed-lure identity remain visible on a phone |
| `HYACINTH_BRUSH` | Brush, stumble/debuff, mercy, and clean pass are distinct |
| `EUCALYPTUS_WHIP` | Telegraph precedes danger by a fair interval |
| `ORCHID_WINDOW` | Both safe windows read without trial-and-error |
| `WILLOW_CURTAIN` | Scenic obstruction remains playable |
| `JACARANDA_PETALS` | Petal pressure is intentional rather than visual noise |
| `BAMBOO_GAP` | Precision gap is readable at all supported speeds |
| `CHERRY_GUST` | Actual mechanic and visual cue agree |

### Birds and Animals

| Scenario | Acceptance criterion |
|---|---|
| `DUCK_TEACH` | Lane and duck timing are unmistakable |
| `TIT_WAVE` | Flock reads as a coherent rhythm pattern |
| `CHICKADEE_SWERVE` | Motion is lively but predictable enough to be fair |
| `OWL_DIVE` | Alert, glow, trajectory, and collision timing agree |
| `EAGLE_MARK` | Reticle follows the live target, locks clearly, and preserves the intended escape window |
| `CAT_KINDNESS` | Pass, mercy, hit, reward, spare, and exit are exclusive and legible |
| `FOX_MIRROR` | Mirrored motion and outcome feel intentional |
| `WOLF_CHARGE` | Howl, charge, dust, collision, mercy, spare, and sprite frames remain distinct |
| `HEDGEHOG_DEBUFF` | Debuff never later becomes a clean-pass reward for the same encounter |
| `DOG_HAZARD` | Projectile lifecycle and collision timing are fair |
| `DOG_BUDDY` | Buddy mode is clearly harmless and persistent behavior remains correct |

## Store Asset Pipeline

```bash
python3 -m pip install -r scripts/requirements.txt
python3 scripts/generate_store_assets.py
bash scripts/capture_store_screenshots.sh
python3 scripts/curate_store_screenshots.py
python3 scripts/prepare_play_release.py
```

### Automated pipeline protections

- [x] Python dependencies are declared
- [x] Screenshot capture does not use hard-coded local SDK/JDK/ADB paths
- [x] Explicit device serial selection is supported
- [x] App state is force-stopped/reset between deterministic captures
- [x] Empty, invalid, portrait, stale, exact-duplicate, near-duplicate, low-variance, and suspicious edge-band screenshots are rejected
- [x] Generated graphics dimensions and manifest hashes are verified
- [x] Metadata files are non-empty and screened for placeholders
- [x] Final application ID is accepted; known placeholder IDs are rejected
- [x] Mandatory and optional audio resources are distinguished correctly
- [x] Default upload preparation requires all external signing credentials
- [x] Java 21 is enforced for the API 36 unit-test gate
- [x] Release lint, tests, bundle build, and effective R8 mapping verification are part of CI

### Manual/store work still required

- [ ] Capture and curate at least four final screenshots on accepted hardware
- [ ] Manually verify that every screenshot depicts the intended scenario and contains no system overlays
- [ ] Finalize title, descriptions, icon/graphics, privacy/data-safety answers, content rating, and store declarations
- [ ] Build with the real upload key and verify signing certificates
- [ ] Upload to an internal testing track and install through the store path
- [ ] Revalidate current Android and Play requirements immediately before submission

## Release Exit Criteria

Forest Run may be called a release candidate only when:

1. the exact candidate commit passes debug/release compile, unit tests, debug/release lint, debug APK, instrumentation APK, effective R8 mapping, and minified AAB gates;
2. connected instrumentation tests execute successfully;
3. no known P0/P1 gameplay, persistence, lifecycle, or packaging defect remains;
4. the signed, minified artifact passes installation, smoke, and long-run testing;
5. deterministic scenarios and ordinary play pass on representative physical hardware;
6. frame-time, memory, I/O, audio, and save behavior meet measured acceptance thresholds;
7. settings/accessibility and safe-area behavior are accepted;
8. store screenshots, metadata, policy, privacy, and data-safety requirements are reviewed against current authoritative rules.
