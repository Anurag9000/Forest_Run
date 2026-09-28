# Forest Run — Immediate input-stance collision geometry (2026-09-28)

## Cross-layer finding

InputHandler and authored actions enter before GameView's Player.update / EntityManager collision sweep. Previously Player.onDuckPressed/onDuckReleased/onJumpPressed transitioned state without refreshing the hitbox until the next Player.update. That update captured the old physical rectangle into previousHitbox before rebuilding the new stance. The swept collision then treated an instantaneous action accepted before the frame as though the old stance persisted through part of the frame, producing a possible false hit against a low flyer even when both real duck-stance samples are clear. The same stale-presentation window applied to jump-start scaling and immediate reset/Rest state changes.

## Change and test

Publish the current foot-anchored hitbox at the end of Player.transitionTo. Input changes are thus visible to collision queries immediately, and the next admitted update samples the correct post-input starting rectangle. Keep Player.update's post-physics hitbox refresh (for continuous y movement), all physics velocities, jump/duck timings, encounter geometry, and sweep algorithm unchanged. One Robolectric regression verifies immediate duck/release/jump geometry and that the previous sample matches the admitted input stance. Another moves an actual-size narrow flyer horizontally through the old upright-only vertical band and asserts no false swept contact after a pre-frame duck while proving the stale old sample would invent contact.

Exact new HEAD's Android/JVM/connected workflow is required for execution evidence. This is an input-sample ownership fix, not a certification of all mixed encounters or real-device control comfort.
