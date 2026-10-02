# Forest Run — transient SFX and haptic lifecycle ownership (2026-10-03)

## Reproduced ownership gap

The Activity pause path stopped the GameThread and paused Leitmotif music, but already-playing SoundPool effects and an in-flight vibration remained outside that lifecycle boundary. The in-game Audio toggle likewise prevented future SFX through FeedbackSettings but did not stop a bark/hit/Bloom effect that had already started. These are transient foreground cues; continuing them after backgrounding or after an explicit Audio-off action violates the same feedback ownership already applied to music.

## Correction

SfxManager now records positive SoundPool stream ids in a small bounded ledger (four times the eight-stream hardware limit). Every play is serialized with pool destruction so a released generation cannot leave a stale stream id for a replacement pool. `stopActivePlayback()` drains that ledger and best-effort stops each recent stream. It is called on GameView pause and whenever audio is disabled. The pause path also cancels the current haptic pattern. Resume restarts only persistent music; it deliberately does not revive a stale transient hit/bark/vibration after an arbitrary background interval. The loaded SoundPool itself stays alive, so re-enabling audio can play fresh effects without asset reload.

The ledger is bounded because SoundPool exposes no effect-completion callback. JVM tests cover invalid ids, eviction, one-shot drain and invalid capacity. The existing Python audio-lifecycle contract now locks pause ownership, no transient resume, Audio-off cancellation and the SFX drain boundary.

## Boundary

This is source/lifecycle correctness, not proof of device mixer latency, OEM vibration behavior, loudness balance or human audio/haptic quality. Those remain physical/human acceptance work.
