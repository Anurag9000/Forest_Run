# Forest Run — Exact durable high-score ownership across multiple record levels (2026-09-28)

## Remaining race after the equal-score repair

The first tie-ownership correction stored a Boolean saying that a run had published *some* new high. That is not enough over a long run. Run A can publish 500 (Boolean=true), later reach 900 locally, while Run B publishes 900 first. Run A's terminal summary then sees an equal durable 900 but the old Boolean still grants it NEW HIGH even though its only owned publication was 500.

A separate load-before-save cannot close the race either: two threads can both observe 500 before attempting the same 900 candidate.

## Correction

`SaveManager.publishHighScoreIfBetter` performs the monotonic compare/write and returns the ownership decision under the existing shared preference monitor. `saveHighScore` remains source-compatible and delegates to it. `GameStateManager` now remembers the exact durable score value it successfully published, not a Boolean. A tied terminal preview is NEW HIGH only when this run owns that exact current durable value; an unsaved value strictly above durable remains provisionally new.

Regressions cover:
- monotonic publisher return semantics;
- two simultaneous equal candidates producing exactly one owner;
- a run that owns 500 but loses a later 900 tie to another run;
- existing stale-equal and repeated-save ownership behavior.

This is process-local SharedPreferences record ownership, not distributed/cloud leaderboard consensus.
