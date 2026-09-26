# Forest Run — Authored bird warning sounds (2026-09-26)

The master content audit calls for verifying that species-specific sounds are actually reachable, including Eagle/Owl screech. `SfxManager` required and loaded `sfx_screech`, but review of GameView, EntityManager, the five bird implementations, Dog and Wolf found no production invocation of `playScreech`. The Eagle initializer even retained a planned screech comment without an event.

The existing one-time presentation owners were used instead of adding polling or a duplicated sound-state machine: Eagle's `announceTarget()` already guards `targetAnnounced` and now emits the screech when its marked corridor is announced; Owl's first jump-triggered `hasWarned` alert now emits the screech with the visual warning and before its delayed dive. Idle, every-frame updates and repeated dive calls do not emit screeches. The existing `SfxManager` applies audio preference and asynchronous sample readiness.

A Python source/wiring contract verifies both one-shot owners, only Owl/Eagle using the screech among species, Dog/Wolf retaining their own cue, and required sample/readiness authority. Owl/Eagle runtime state tests remain separate. Hardware audibility, level/mix, scene timing feel and final creative approval are still external.
