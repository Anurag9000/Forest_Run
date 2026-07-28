# Forest Run — Release & Validation

Forest Run is a feature-rich alpha. A release candidate must pass code-correctness, automated-test, asset, packaging, performance, and real-device gates. Hardware validation is necessary, but it is not the only work remaining.

## Build Commands

```bash
# Unit tests
bash gradlew testDebugUnitTest

# Debug APK
bash gradlew assembleDebug

# Instrumented test APK
bash gradlew assembleDebugAndroidTest

# Connected device tests
bash gradlew connectedDebugAndroidTest

# Static Android checks
bash gradlew lintDebug
```

Expected outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Android test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

A checklist item is complete only when the command actually passes on the audited commit. Documentation, source inspection, or a previously passing build is not sufficient evidence.

## Current Project Settings

| Setting | Current repository value | Release action |
|---|---:|---|
| Application ID | `com.anurag9000.forestrun` | Replace placeholder identity |
| Min SDK | API 24 | Revalidate against supported-device policy |
| Compile/Target SDK | API 34 | Revalidate against current Android and store requirements |
| Java/Kotlin JVM | 17 | Verify clean build on documented toolchain |
| Manifest orientation | `landscape` | Decide whether fixed or sensor landscape is intended |
| Release minification | Enabled | Test the actual minified release artifact |
| Release signing | Not explicitly configured | Add secure release signing outside source control |

## Correctness Gate

### Core repairs implemented on `agent/fix-core-gameplay-invariants`

- [x] Quick taps receive upward jump velocity
- [x] Hold duration controls jump height
- [x] Swipe-down is classified before any jump starts
- [x] Bloom no longer replaces airborne locomotion
- [x] GameStateManager is the authoritative Bloom clock
- [x] Active-Bloom rewards cannot reset the Bloom timer
- [x] One terminal outcome is assigned per entity encounter
- [x] Collision results are resolved before clean-pass rewards
- [x] Collision severity is deterministic: hit → stumble → mercy
- [x] Mercy is awarded once per encounter
- [x] Bloom conversion is exclusive of clean-pass/unique-action/orb rewards
- [x] Seed Orbs are staged ahead of the player after a clean pass
- [x] Unsafe entity pooling is disabled pending complete reset contracts
- [x] Debug scenario spawns do not increment persistent encounter history
- [x] Persistent clean passes are recorded centrally
- [x] Cat spare-exit distance logic is valid
- [x] Garden spending cannot be overwritten by stale lifetime-seed state
- [x] Reused `singleTask` Activity intents apply the requested debug scenario
- [x] Activity teardown cancels haptics and avoids an initial duplicate-thread start

### Still required before release

- [ ] Build the repair branch and fix every compilation or lint failure
- [ ] Run all unit tests and connected tests on the exact release candidate
- [ ] Gate gameplay input so Menu, Garden, dying, game-over, and restart states cannot mutate Player or opening-guide state
- [ ] Replace interrupted infinite-retry thread joining with bounded, interruption-safe shutdown
- [ ] Ensure collision probing has no presentation side effects for unselected entities
- [ ] Define complete reset contracts before reconsidering any entity pooling
- [ ] Audit all 19 entities for telegraph, movement, collision, mercy, pass, spare, Bloom, persistence, debug, and off-screen cleanup invariants
- [ ] Rework relationship progression so appearances alone cannot create trust milestones
- [ ] Make Eagle targeting use the live player trajectory and validate mark retention
- [ ] Align sway visuals and collision geometry for flora and trees
- [ ] Convert spawn pacing from purely time-based intervals to validated world-space separation
- [ ] Make Garden unlock particles update while the Garden is active
- [ ] Prevent Garden return moments from being consumed during hidden preload
- [ ] Use local calendar dates rather than elapsed UTC-day buckets for daily greetings
- [ ] Clamp all Garden sanctuary counts to non-negative values
- [ ] Reset the willow sit/stand menu ritual whenever returning home
- [ ] Remove per-frame recomputation and persistence reads from GameOver drawing
- [ ] Bound dialogue/flavor queues and implement measured text wrapping/screen clamping
- [ ] Add density, safe-area, reduced-motion, audio, and haptic settings
- [ ] Make ghost recording duration suitable for intended endless runs and remove synchronous death-frame hitch risk
- [ ] Fix SoundPool load readiness, lifecycle recreation, and missing-asset reporting
- [ ] Throttle adaptive music parameter writes and make crossfade lifecycle deterministic

## Automated-Test Gate

Existing and newly added tests do not yet cover the whole engine. Required invariant tests include:

- [x] tap, hold, swipe-down, and cancel gesture arbitration
- [x] jump-force clamping and monotonic hold scaling
- [x] active Bloom rewards do not restart Bloom
- [x] stale GameStateManager cannot refund a Garden purchase
- [ ] airborne Bloom preserves jump/fall/land behavior in an instantiated Player test
- [ ] mercy can fire only once for an entity
- [ ] an entity can receive only one terminal outcome
- [ ] lethal overlap outranks simultaneous stumble/mercy overlap
- [ ] collision is evaluated before pass resolution
- [ ] debug encounters never change persistent counters
- [ ] clean pass persistence increments exactly once for every entity type
- [ ] Bloom conversion cannot stack clean-pass, unique-action, and orb rewards
- [ ] Seed Orb spawn/scroll geometry leaves a reachable collection window
- [ ] Garden purchase and run-earned currency remain consistent across lifecycle transitions
- [ ] menu ritual resets after returning from Garden/rest
- [ ] daily return moments are consumed only when visibly entering Garden
- [ ] repeated debug launch intents switch scenarios reliably
- [ ] render-thread shutdown terminates under interruption

## Asset and Runtime Gate

- [ ] Fail release builds when required sprites, audio, or fonts are absent
- [ ] Validate sprite frame dimensions and frame-count divisibility
- [ ] Prohibit generated placeholder sprites in release artifacts
- [ ] Replace remaining procedural/placeholder scenic layers with approved final assets or explicitly accept them as art direction
- [ ] Verify every sound is loaded before first playback and expose missing assets in debug diagnostics
- [ ] Exercise the minified release build, not only debug
- [ ] Profile allocations, frame time, memory, audio threads, and I/O during long runs
- [ ] Remove known draw/update allocations and unbounded queues
- [ ] Verify save migration/corruption handling and recovery behavior

## Device Acceptance Checklist

A feature is not complete until its deterministic scenario and ordinary-play check pass on physical devices. Validate at least one low-end, one representative mid-range, and one high-refresh Android device.

### Core Flow

| Scenario | Acceptance criterion |
|---|---|
| `OPENING_READABILITY` | First 20–30 seconds teach duck, tap jump, hold jump, and spacing without gesture conflicts |
| `BLOOM_SHOWCASE` | Bloom activation, invincibility, preserved locomotion, conversion rules, HUD state, and expiration are unmistakable |
| `GHOST_READABILITY` | Ghost never resembles a broken duplicate or obscures the live runner |
| `REST_LOOP` | Failure, rest summary, fade, Garden return, currency, and next-run reset remain coherent |

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
| `EAGLE_MARK` | Reticle follows the live target and survives until the intended attack decision |
| `CAT_KINDNESS` | Pass, mercy, hit, reward, spare, and exit are exclusive and legible |
| `FOX_MIRROR` | Mirrored motion and outcome feel intentional |
| `WOLF_CHARGE` | Howl, charge, dust, collision, mercy, and spare remain distinct |
| `HEDGEHOG_DEBUFF` | Debuff can never later become a clean-pass reward for the same encounter |
| `DOG_HAZARD` | Projectile lifecycle and collision timing are fair |
| `DOG_BUDDY` | Buddy mode is clearly harmless and persistent behavior remains correct |

## Store Asset Pipeline

```bash
python3 scripts/generate_store_assets.py
bash scripts/capture_store_screenshots.sh
python3 scripts/curate_store_screenshots.py
python3 scripts/prepare_play_release.py
```

Before relying on these scripts:

- [ ] Declare Python dependencies and supported versions
- [ ] Remove hard-coded local SDK/JDK/ADB paths
- [ ] Support explicit device serial selection
- [ ] Force-stop or otherwise reset app state between scenarios
- [ ] Verify each requested scenario became active before capture
- [ ] Reject stale, duplicate, blank, portrait, system-bar, and wrong-scenario screenshots
- [ ] Require a non-empty approved screenshot set
- [ ] Validate package ID, versioning, signing, bundle contents, required assets, and minified-runtime smoke test
- [ ] Revalidate all current Play Console requirements at release time

## Release Exit Criteria

Forest Run may be called a release candidate only when:

1. the exact candidate commit builds cleanly;
2. all required automated tests and lint checks pass;
3. no known P0/P1 gameplay, persistence, lifecycle, or packaging defect remains;
4. release assets are strict-validated rather than silently replaced;
5. the signed, minified release artifact passes smoke and long-run tests;
6. all deterministic scenarios pass on representative physical hardware;
7. store metadata and policy requirements are verified against current authoritative sources.
