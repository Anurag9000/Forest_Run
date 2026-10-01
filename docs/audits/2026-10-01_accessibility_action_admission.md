# Forest Run — accessibility gameplay action admission follows Player stance (2026-10-01)

## Reproduced cross-layer defect

The PLAYING accessibility tree always advertised Jump, Long jump and Duck. The live semantic handler also returned success whenever gameplay input callbacks existed, without first asking whether the Player could accept the requested stance change.

That was mechanically unsafe for Jump. During JUMP_START/JUMPING, a second semantic Jump press is rejected by `Player.onJumpPressed`, but the handler still invoked `onJumpReleased`. Release is meaningful during ascent, so an accessibility activation that claimed to start a new jump could instead cut the already-active jump short. Duck similarly scheduled a delayed release even when its press could not own the current stance.

## Correction

`Player` now owns explicit `canStartJump` and `canStartDuck` capabilities, and its press methods consume those same predicates. JUMP_START intentionally remains duck-cancellable because that is the existing gesture-arbitration contract; established flight does not.

The live accessibility snapshot publishes those capabilities. Jump/Long jump/Duck virtual nodes are disabled and expose no semantic action when the current Player stance cannot admit them. The GameView action handlers independently preflight the same capability under `runtimeStateLock` before invoking any press/hold/release callback, so a stale accessibility node also fails closed.

Robolectric tests cover Player capability transitions and semantic-node admission. The permanent Python source contract protects preflight-before-callback ordering and the snapshot wiring.

## Evidence boundary

This establishes source/emulator action ownership. It does not substitute for real TalkBack/Switch Access navigation, physical control comfort, announcement quality or the required device matrix.
