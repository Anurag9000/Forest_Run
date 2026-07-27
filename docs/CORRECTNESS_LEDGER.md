
# Correctness ledger

## Fixed in the correctness overhaul

- Quick taps always launch.
- Swipe-down no longer commits a jump first.
- Bloom is independent of locomotion and has one timer.
- Seeds and conversions cannot silently restart Bloom.
- Every entity has one terminal encounter outcome.
- Collision priority is deterministic and precedes pass rewards.
- Mercy is awarded once per entity.
- Unsafe entity pooling is removed.
- Clean passes are persisted centrally.
- Debug-spawn encounters do not persist encounter/pass progression.
- Seed Orbs spawn in a reachable forward lane.
- Garden purchases cannot be overwritten by stale game-state currency.
- Garden return moments are previewed until the Garden is actually entered.
- Garden unlock particles advance; plant cards use a compact grid.
- Menu ritual resets on return.
- Dialogue wraps and transient flavour text is bounded.
- Game thread shutdown is bounded and loop failures are logged.
- Screenshot intents are handled through `onNewIntent`.
- Release target moves to API 36 with a modern supported toolchain.
- CI runs unit tests and lint.

## Still requires physical evidence

- Low-, mid- and high-refresh device frame pacing.
- All nineteen entity hitboxes and telegraphs on phone screens.
- Haptic intensity and audio mix on real hardware.
- Long-run memory/thermal profiling.
- Store screenshot visual review and Play Console signing/upload acceptance.

No documentation may call the game release-ready until those checks are recorded with device/build evidence.
