# Forest Run source-completion delta — 2026-10-28

> Chronological append-only investigation log. Each earlier subsection is a snapshot from the time it was written; later candidate sections supersede earlier “current” wording. The latest source section is at the end. Do not treat historical run-in-progress text as current CI state.

## Current source and audio delta — 2026-10-28

- At the time of this initial note, `main` was bac310001d48629d4542c3ed75b65d1fb5d241ad, which removed a stale Duck quack-SFX comment without behavior change. This is a historical snapshot, superseded by later candidate sections.
- The bac310 applicability/certificate runs and Android run are historical. TASK-C-016 was subsequently repointed to exact 8f9340d run 40623906250; its host job passed and API35 connected job was still in progress at the latest poll.
- All 15 Ogg files were probed and decoded, then current payload hashes were checked against the review; all remain unchanged. Static call paths and the stale Duck note/history are reconciled in [audio_asset_and_wiring_review_2026-10-28.md](audio_asset_and_wiring_review_2026-10-28.md). No audio was listened to. Sample analysis does not establish mix, loop seam, true peak, creative approval, provenance, or licensing.
- No physical-device access is available. Physical performance/acceptance and human/external release gates remain pending; hosted emulator evidence is not a substitute.
- The previous exact-tested source candidate `113a4ebdf671dc16470b18de0d4dff7685230045` passed Android host/release/lint/package and API35 connected checks; this result is predecessor evidence, not exact-bac310 Android evidence.

## Repository identity and scope

- Verified source candidate: commit `8c335aef232ee8f164847631a74d1abb7409dd86`, pushed to canonical `main` after baseline `946130c19878f99f1c9ee21506fb89e1ab940525`; the exact-SHA workflow ran on this source candidate.
- The reviewed source, test and documentation delta was committed and pushed to canonical `main`. The tracked worktree is clean. Pre-existing untracked `.coverage`, `.training_control/`, `artifacts/`, `docs/REPOSITORY_COMPLETE_KNOWLEDGE_MAP_20261027.md`, and `docs/repository_knowledge/current_worktree_delta_20261027/` remain preserved and outside the commit.
- Host tests/build outputs were produced in `/var/tmp/forest-run-main-20261028-check`, a checksum-matched copy of the authored source snapshot. Its checksum-aware comparison found no authored-file differences from the candidate.

## Historical source delta and static disposition (8c335aef snapshot)

- **Wolf atlas/runtime mismatch:** the authored source atlas is a 4×2 layout with eight poses. The importer emits eight horizontal cells to `app/src/main/assets/sprites/animals/wolf_8frames.png`; SHA-256 is `bd9f60a4a85cec937b0df2c7709d2962ece8deda1cab246c71f77d4b2ec19225`. `AssetPaths.Animals.WOLF`, `SpriteManager`, importer output, tests, docs and packaged asset now agree on eight frames. The earlier four-frame conclusion is superseded. This does not establish human animation approval or provenance.
- **Lifecycle/input delta:** `GameView` cancels active gesture/duck ownership when paused or when its Surface is destroyed, and gates gameplay input on lifecycle and Surface readiness. Focused Kotlin/source-contract regressions were added. An independent static review found no confirmed regression in the changed region.
- **Release docs:** `release/google-play/README.md` names `bash scripts/prepare_main_release.sh` as the supported operator entrypoint and describes `prepare_play_release.py` only as an internal helper. Contract testing protects operator-facing release documentation.
- **Python compatibility:** `verify_release_source_assets.py` handles Python runtimes without `ast.TypeAlias`; a regression test covers the compatibility path.
- **Training applicability:** the no-trainable-surface checks guard against false interpretation of ordinary game/release identifiers. No ML functionality or fake training jobs were added; training remains N/A for this game.
- The old checked-in `release/google-play/BUILD_SUMMARY.md` is absent on current main; the release README identifies summary output as generated candidate evidence.

## Historical exact-candidate verification (8c335aef snapshot)

- Full Python scripts suite on the checksum-matched source copy: **1,395 passed**, exit 0, 18.379 seconds (EVID-104).
- Android host suite/build on the same copy: **1,303 JVM/Robolectric tests**, zero failures/errors/skips; Kotlin compilation, debug/release lint, debug/instrumentation APK and release AAB packaging succeeded. `lintRelease bundleRelease` and a sequential `lintDebug` invocation explicitly reported `BUILD SUCCESSFUL` (EVID-105).
- Exact source candidate `8c335aef232ee8f164847631a74d1abb7409dd86` hosted Android workflow [40602123824](https://github.com/Anurag9000/Forest_Run/actions/runs/40602123824) completed successfully. Host/release/lint/package job `122532575053` and API35 connected job `122534456541` both passed; the connected step runs ordinary instrumentation and the repository's process-death prepare/force-stop/no-PID/restore flow. Uploaded artifacts: host artifact `12778759135` (17,810,799 bytes) and connected artifact `12779308471` (713,891 bytes).
- Exact-SHA no-trainable-surface audit [40602123869](https://github.com/Anurag9000/Forest_Run/actions/runs/40602123869) and estate certificate workflow [40602123856](https://github.com/Anurag9000/Forest_Run/actions/runs/40602123856) also completed successfully.
- Documentation-only descendant `f00f78e820ae2a158ef420d634f7c410a27ef2b3` then passed run [40604093279](https://github.com/Anurag9000/Forest_Run/actions/runs/40604093279) on exact SHA: host job `122539158093` (7m05s) and API35 connected job `122541808355` (7m31s) both succeeded. The uploaded host artifact `12780112471` is 17 MB with digest `1e7998dd14059596cb3bc4fcf51089350f386d8f44c40e38053a471ac1e71a7a`; connected artifact `12780856634` is 661 KB with digest `458808cf3482b8af9de9a77cf1a4b90b08305cb912920364bd982d92b874d`. Training applicability/certificate workflows `40604093598` and `40604093353` passed on the same tree. Application source is unchanged from `8c335aef` (EVID-111/EVID-112).
- Detailed hosted artifacts could not be downloaded from this environment: GitHub's artifact endpoint returned HTTP 401 without authenticated access. Therefore this record uses the directly observable exact-SHA job/step conclusions and artifact metadata; it does not invent test counts from those remote artifacts. The local exact-source Python and JVM counts above remain independently recorded.
- Previous local connected attempts on API36.1 SwiftShader exited 143 before assertions (EVID-106); they are historical environment failures and are superseded for the exact candidate by successful hosted API35 evidence EVID-109.

## Remaining work and limits

- `TASK-C-014` is complete for the exact source delta: candidate identity and all required hosted host/API35 connected outcomes are verified (EVID-108–EVID-112).
- Candidate-bound `OWL_DIVE` screenshot capture and visual inspection remain under `TASK-C-013`; no authorized device/adb was available locally. Physical performance/device acceptance, human gameplay/accessibility/creative approval, provenance/licensing, signing, Play delivery, privacy/security/store governance and accountable release approval remain external/device-dependent gates. They must be satisfied with real evidence.
- This delta does not claim all physical, human, legal, store or production-release acceptance is complete.

## Provenance

Verified source candidate: `8c335aef232ee8f164847631a74d1abb7409dd86`; host copy and Python coverage: `/var/tmp/forest-run-main-20261028-check` and `/var/tmp/forest-run-python-current20261028.coverage`; prior local connected logs: `/var/tmp/forest-run-connected-current20261028.log` and `/var/tmp/forest-run-connected-current-retry2-20261028.log`. Hosted run and artifact IDs are listed above and in `execution/EVIDENCE_INDEX.json`.


### Subsequent exact-head capture correction and verification (2026-10-28)

Commit `871467c2437a9949da6bf9c0119a31c405d7fa42` fixes the screenshot helper's fullscreen-prompt bounds parsing after the first hosted attempt exposed a Python syntax failure. A regression contract executes the embedded helper against valid and malformed bounds. The full Python scripts suite passed 1,398 tests locally; shell syntax and `git diff --check` passed. Exact-SHA Android run [40610098124](https://github.com/Anurag9000/Forest_Run/actions/runs/40610098124) passed host/release/lint/package and API35 connected jobs. The connected JUnit report passed 28/28 tests. The canonical screenshot workflow captured eight images and the raw-set verifier confirmed candidate `871467c…`, APK SHA-256 `0d51293fc8a544f891a9091097dfb79c23354afb65f7ed5ca6ea7c8e180a0806`, and device `emulator-5554`. The `07-owl.capture.json` sidecar identifies `OWL_DIVE` and its readiness marker; inspected `07-owl.png` visibly shows Owl alert rings (image SHA-256 `59c017ff6ea79716448789173b07bc082e91e572cb447f8e95d644b09b363895`). Downloaded artifact digest matches GitHub metadata. Training applicability and estate-certificate workflows [40610098264](https://github.com/Anurag9000/Forest_Run/actions/runs/40610098264) and [40610098198](https://github.com/Anurag9000/Forest_Run/actions/runs/40610098198) passed.

This closes `TASK-C-013` for hosted API35 candidate capture and visible-state evidence. It does not establish physical/OEM presentation, human gameplay/accessibility/creative acceptance, store curation, provenance/licensing, or production release. Those remain under the external B/D/E tasks.


## WOLF_CHARGE deterministic-sequence completion delta

A fresh scenario trace found WOLF_CHARGE could not reach its documented spare: clean debug reset left mercy at zero, while Wolf.resolveSpecialSafeDeparture requires eight hearts. The scenario had no scripted jumps through its two charge windows. The delta sets a validated run-local starting-mercy fixture only for WOLF_CHARGE, applies it after reset through the debug launch route, schedules held jumps at 9.60/9.95 and 14.50/14.85 seconds, and includes it in scenario fingerprint format v2. Ordinary-run eligibility and mercy near-miss history remain unchanged.

WolfScenarioIntegrationTest drives the actual encounter director, entity manager, player and collision arbitration for 20 seconds at 60 Hz, records both Wolves' states and requires each to traverse HOWLING → CHARGING → SPARED; it also requires two spares, no hits/ordinary clean passes, and zero persistent Wolf encounter/spare records. A companion test checks reset and saved high-score/lifetime-Seed isolation. The independent Python source parser binds initial mercy into scenario canonicalization.

The source delta was committed and pushed as `113a4ebdf671dc16470b18de0d4dff7685230045`. Local verification performed on the staged-equivalent contents before commit: full Python suite 1,398 passed; JDK 21 JVM/Robolectric suite 1,307 passed, 0 failures/errors/skips; focused Wolf integration 2 passed; scenario source contract 10 passed; Kotlin compile, debug/release lint, debug/instrumentation APK and release AAB bundle tasks succeeded. An old local-test APK initially contained the pre-rename Wolf path; rerunning only packageDebugUnitTestForUnitTest refreshed it without deleting build state, after which the asset contract and full suite passed. EVID-114 records the evidence limits. Exact-SHA Android run `40620167730` passed host/release/lint/package and API35 connected validation (28/28 connected tests, eight verified screenshots, and process-death restore); applicability and estate certificate workflows passed.


The current candidate `113a4ebdf671dc16470b18de0d4dff7685230045` was pushed to `main`. Exact-SHA no-trainable-surface run [40620167767](https://github.com/Anurag9000/Forest_Run/actions/runs/40620167767) and estate certificate [40620167515](https://github.com/Anurag9000/Forest_Run/actions/runs/40620167515) passed. Android validation [40620167730](https://github.com/Anurag9000/Forest_Run/actions/runs/40620167730) passed host/release/lint/package (EVID-115) and API35 connected job 122594376073 (EVID-116): 28/28 tests passed, process-death recovery succeeded, and eight raw candidate-bound screenshots verified. Artifacts: host 12787496440 and connected 12787029693. Earlier 8c/871 workflow evidence does not establish this source change.


### Exact-source closeout

On `113a4ebdf671dc16470b18de0d4dff7685230045`, host/release/lint/package and API35 connected jobs both passed in [run 40620167730](https://github.com/Anurag9000/Forest_Run/actions/runs/40620167730). The connected report completed 28/28 tests (0 skipped, 0 failed), the process-death test passed the force-stop/fresh-process restoration flow, and eight screenshots were verified against the candidate, APK SHA-256 `6cba144842fdc2f5077e49345d92ebc4a601d7cc2fb2ec3b3a78b6068da0c5ea` and `emulator-5554`. No-trainable-surface and estate certificate workflows [40620167767](https://github.com/Anurag9000/Forest_Run/actions/runs/40620167767) and [40620167515](https://github.com/Anurag9000/Forest_Run/actions/runs/40620167515) passed. See EVID-114–EVID-116. These emulator/source results do not establish physical-device, human, legal, Play, or production release acceptance.

### Documentation-only HEAD reconciliation

A fresh fetch on 2026-10-28 confirmed local `main` and `origin/main` at documentation-only commit `be5b402ddf4efdaaf0c3bde09093379bda15b5e9`; application source remains exact-tested commit `113a4ebdf671dc16470b18de0d4dff7685230045`. On `be5b402`, no-trainable-surface workflow `40622204348` and estate-certificate workflow `40622204354` passed; Android validation `40622204411` was still in progress at last observation. The master map and ledger now distinguish current documentation HEAD, verified application source, and the pending exact-HEAD Android result. Existing human/device/legal/Play/governance tasks remain external evidence gates.

## EAGLE_MARK scenario-to-mechanic integration delta

A bounded scenario review found that `EAGLE_MARK`'s authored director and four timed jump actions were checked separately from Eagle lock behavior. This did not prove that the scenario failed, but left its named target-mark/lock beat without an integration assertion. `EagleScenarioIntegrationTest` now advances the actual `EncounterDirector`, `DebugScenarioScript`, `Player`, `EntityManager` update and collision path for 20 seconds at 60 Hz. It observes the two authored Eagle objects and requires both to announce and lock targets, confirms the four-event trace satisfies `DeterministicScenarioReplayContract`, and confirms debug execution does not write the ordinary persistent Eagle encounter count.

Focused command `./gradlew testDebugUnitTest --tests com.anurag9000.forestrun.engine.EagleScenarioIntegrationTest` passed 1 test with no skips/failures/errors on Kakarot using Java 17 and Robolectric SDK35 (EVID-123). The full Python scripts suite passed 1,398 tests in 24.091 seconds (EVID-124). The combined local Gradle compile/test/lint/package command did not complete: repository Robolectric tests requesting SDK36 require Java21, while this host only has Java17; failures occurred at sandbox creation before test methods (EVID-125). Do not classify that as a product defect or as a full-suite pass.

The change remains an uncommitted source delta on top of `8f9340d431f7abb84719aef0a5b1174cafb0c437` at the time this paragraph was added. Exact-SHA hosted Java21 host/API35 validation is tracked by TASK-C-017. The previous exact-candidate run 40623906250 is for 8f9340d only: its host job passed, while API35 connected validation was still running at the last poll; it cannot validate this new test. REQ-020 remains partial at the whole scenario-catalogue level until the other authored scenario claims are dispositioned. Physical readability, human acceptance and external release gates remain separate.

## Selected authored-scenario integration delta

A real-path test found that `CACTUS_READ` scheduled both held jumps after the cactus hitboxes reached the Player. In the prior schedule (3.18/3.48 and 5.06/5.36 seconds), both encounters resolved `HIT`. `DebugScenarioScript` now schedules the pairs at 2.40/2.70 and 4.30/4.60 seconds. `NamedFloraTreeScenarioIntegrationTest` drives the director, timed script, Player, `EntityManager`, and collision arbitration; it now observes two clean passes and zero hits. Its other assertions cover ordered flora/tree showcase content, Orchid/Bamboo safe geometry, and selected flora/tree presentation paths. The focused class passed 5/5 under Java 21/Robolectric SDK35 (EVID-126).

Additional focused tests cover Cat kindness, Fox mirror jump, Hedgehog stumble/debuff, Dog hazard and buddy modes (5/5, EVID-127); Owl alert-to-dive and Chickadee seeded altitude/replay consistency (2/2, EVID-128). These are bounded scenario checks and do not close the entire authored catalogue. Exact-SHA hosted verification for the current delta remains pending under TASK-C-018. No physical device access is available; these tests do not establish physical readability, hardware acceptance, creative approval, or release readiness.

## Full local verification after scenario delta

Using `/opt/android-studio/jbr` (Java 21), the requested combined local Gradle gate passed: debug/release/instrumentation Kotlin compile, full `testDebugUnitTest`, debug/release lint, debug and AndroidTest APK assembly, and release AAB bundle. The XML aggregate reports 1,320 tests, zero failures/errors/skips. The full Python scripts suite passed 1,398 tests (24.733s). See EVID-129 and EVID-130. These are local checks against the uncommitted delta; hosted exact-SHA CI and connected API35 execution are still required. Physical/human acceptance remains unavailable/pending, and no release acceptance is inferred.

## Exact-SHA early workflow results

After commit/push to `d0eb2f6953951ab29f0a262977a86d0f097b2846`, the no-trainable-surface workflow `40626476537` (job `122612483127`) and estate certificate workflow `40626476460` (job `122612483046`) passed for that exact SHA (EVID-131/EVID-132). Android validation run `40626476441` was pending at the latest poll; do not claim exact candidate Android/connected validation until its terminal jobs and artifacts are inspected.


## BLOOM_SHOWCASE window correction and selected scenario coverage (local delta)

A new real-path simulation found that the scenario's authored summary (“convert the full lane”) was not true at the existing six-second Bloom duration. Duck and Weeping Willow did not clear the Player before the timer expired, so ordinary collision resumed and they resolved as HIT. The correction moves only those scenario origins closer and staggers the five authored types; it does not extend or restart Bloom. `ShowcaseScenarioIntegrationTest` now requires Cactus, Lily, Cat, Duck and Willow to each resolve exactly once as `BLOOM_CONVERTED` before expiry, checks one conversion Seed per encounter, and checks that ordinary pass/hit/spare rewards are not co-awarded. EVID-134 records the reproduced source defect and passing regression.

Three new selected scenario test classes cover full Bloom conversion, GhostPlayer advancement in the opted-in readability scenario, the bounded REST_LOOP forced terminal fixture, actual ordered BIRD/ANIMAL showcase rosters and selected Wolf/Eagle states, plus DUCK_TEACH/TIT_WAVE roster dispatch and debug isolation. The two bird scenarios are intentionally manual; their response mechanics are not falsely attributed to unscripted automated inputs. These tests do not close the other authored scenarios or establish visual/hardware acceptance.

After the final source/test delta, `python3 -m unittest discover -s scripts -p 'test_*.py'` passed 1,398 tests (EVID-135). The full Java21 Gradle compile/test/lint/package command completed BUILD SUCCESSFUL with 1,327 JVM/Robolectric tests, zero failures/errors/skips, and all requested APK/AAB tasks successful (EVID-136). The delta is not yet committed; exact-SHA hosted Android validation is pending. GitHub public API requests were rate-limited during this check, so the preceding d0eb2f69 run is not substituted for current-candidate evidence. No physical-device access is available.


The final static ledger self-audit passed (EVID-137): unique IDs/paths, task/evidence references, acyclic dependencies, inventory coverage counts, selected Markdown links, and JSONL parsing all reconcile; `git diff --check` is clean. This checks documentation structure, not every recorded technical claim.

## Pushed scenario candidate `e1e8501451d17996469fc82973076ed0d1fb9d22`

- The reviewed source, tests and knowledge-ledger changes were committed directly to `main` and pushed; `git ls-remote origin refs/heads/main` confirmed the same SHA. Pre-existing untracked `.coverage`, `.training_control/`, `artifacts/`, `docs/REPOSITORY_COMPLETE_KNOWLEDGE_MAP_20261027.md`, and `docs/repository_knowledge/current_worktree_delta_20261027/` were preserved and excluded.
- Exact-SHA public workflow listing showed Android validation run [40628306759](https://github.com/Anurag9000/Forest_Run/actions/runs/40628306759) and training-control applicability run [40628306963](https://github.com/Anurag9000/Forest_Run/actions/runs/40628306963) in progress. Estate certificate run [40628306741](https://github.com/Anurag9000/Forest_Run/actions/runs/40628306741) completed. This was an in-progress snapshot; terminal job outcomes have not yet been verified. See EVID-138 / TASK-C-018.
- Local Python and Java21 Gradle suites passed before commit (EVID-135/136). Their local evidence is distinct from hosted exact-SHA results. Physical-device access is unavailable; broad scenario-catalogue, human, legal, Play and production gates remain unresolved.

## Accessibility subtree-query correction and integrated local validation

- Static delta review found that `GameAccessibilityNodeProvider.findAccessibilityNodeInfosByText` ignored its `virtualViewId` argument. The current semantic accessibility tree is flat: host has semantic children, leaves have no children. A leaf-root query therefore must not return sibling matches. The method now returns an empty result for non-host roots; host-root matching remains unchanged.
- Added `GameAccessibilityNodeProviderTest.textSearchIsScopedToTheRequestedVirtualNodeSubtree`, covering both a matching host query and an empty leaf query. The focused test passed (EVID-140).
- After integration, full Python passed 1,398 tests (EVID-141), and the full Java21 Gradle compile/test/lint/package gate passed 1,328 tests with zero failures/errors/skips (EVID-142). This is local evidence, not exact-SHA hosted or physical TalkBack acceptance.
- Accessibility code/test changes and ledger updates are not yet committed at this log entry; exact-candidate hosted validation must be repeated after the new source commit. Physical-device access remains unavailable.

## Accessibility delta pushed at `bef4ab354a013892dd0d0289ec3616a20ef0d2d4`

- The provider fix, regression test, and synchronized documentation/ledger delta were committed directly to `main` and pushed; local, origin-tracking, and remote branch SHA matched. Pre-existing untracked workspace state was preserved and excluded (EVID-145).
- Exact-SHA Android validation run [40629702414](https://github.com/Anurag9000/Forest_Run/actions/runs/40629702414) was pending; applicability [40629702454](https://github.com/Anurag9000/Forest_Run/actions/runs/40629702454) and estate certificate [40629702394](https://github.com/Anurag9000/Forest_Run/actions/runs/40629702394) were in progress at the latest listing (EVID-146). Do not claim success before terminal jobs/artifacts are inspected.
- Local integrated checks remain Python 1,398/1,398 and Java21 Gradle 1,328/1,328 plus compile/lint/package (EVID-141/142). Physical-device access remains unavailable; broader scenario and external human/release gates remain open.


## GHOST_READABILITY production-path verification delta

- All 28 scenarios were reconciled against current production definitions, tests, instrumentation routes and prior evidence (EVID-147). This exposed a test gap, not a confirmed gameplay defect: the Robolectric test drove a separate GhostPlayer, while the actual GameView capture lacked a ghost visibility assertion.
- Added a wait/assertion to `HardwareCoreFlowCaptureTest.captureGhostReadabilityHardwarePass` for the GameView-owned ghost to be loaded and reach positive visibility before capture. AndroidTest compilation passes; the `@LargeTest` itself remains unexecuted locally.
- Local regression: Python 1,398 passed; full Java21 Gradle compile/test/lint/package succeeded with 1,328 JVM/Robolectric tests and zero failures/errors/skips (EVID-148). The test-only delta needs a commit and exact-SHA hosted connected execution.
- Bounded reviews found no new source defect in input/Player physics (EVID-149), spawn/Seed Orb/Bloom (EVID-150), or Wolf/bird asset mapping (EVID-151). Human/device readability, pairwise fairness acceptance, creative approval and provenance remain unverified.


## Exact bef4ab candidate hosted verification — 2026-10-28

- The exact committed candidate `bef4ab354a013892dd0d0289ec3616a20ef0d2d4` completed Android validation run `40629702414`: host/release/lint/package job `122623118133` and API35 connected job `122625349202` both concluded success.
- Exact-SHA no-trainable-surface workflow `40629702454` (job `122622965372`) and estate certificate workflow `40629702394` (job `122622965322`) also concluded success. Evidence is recorded in `EVID-153`.
- This hosted candidate predates the uncommitted `TASK-A-047` production GameView ghost-visibility assertion. Commit that bounded test/docs delta and require a new exact-SHA connected run before claiming the assertion passed. The broader scenario catalogue remains partially dispositioned; physical/human acceptance and external release gates remain open.
