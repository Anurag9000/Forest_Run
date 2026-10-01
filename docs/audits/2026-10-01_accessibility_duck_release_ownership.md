# Forest Run — delayed accessibility Duck release ownership (2026-10-01)

## Reproduced ownership defect

The live accessibility Duck action pressed the real input owner and then posted a 280 ms delayed release. That callback only rechecked whether gameplay input was currently accepted. It did not prove that the Duck stance it was about to release was still the one created by that accessibility activation.

A concrete interleaving was therefore possible: accessibility Duck A starts; A is released early by another input; Duck B starts; A's old delayed callback fires while the run is still PLAYING and releases B. A run/session transition could likewise leave a stale callback alive into a later live session.

## Repair

Use the existing identity-based `LatestRequestGate` as the delayed-release owner. The accessibility action invokes the real Duck press first, then creates a release token. Any subsequent Duck press/release cancels that token. Every accepted run/session transition also cancels it. The delayed callback may release only when its token is still current, gameplay still accepts input, and the Player is actually DUCKING; it cancels ownership before invoking the real release callback.

This preserves the existing 280 ms semantic Duck duration, Player stance rules, input telemetry, and shared input callbacks. It does not synthesize touch coordinates or create a second gameplay path.

## Regression

The permanent accessibility source contract now requires token acquisition after the press and token/DUCKING checks before the delayed release. `LatestRequestGateTest` already proves newer requests and explicit cancellation permanently invalidate older identity tokens. Exact-head Android/JVM/API-35 CI remains execution authority.

## Boundary

This closes asynchronous source ownership. It does not replace real TalkBack/Switch Access timing, physical-device comfort, or human accessibility acceptance.
