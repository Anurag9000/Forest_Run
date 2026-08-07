# Accessibility architecture and acceptance

Forest Run renders its product UI through one custom Canvas/SurfaceView surface. Accessibility therefore requires explicit semantics rather than relying on standard Android widgets.

## Implemented source boundary

`GameView` is now the live accessibility host. It exposes `GameAccessibilityNodeProvider` directly from the custom game surface; `MainActivity` no longer attaches a root delegate and accessibility navigation no longer synthesizes fixed-coordinate touch events.

The live stack is deliberately layered:

1. `GameAccessibilitySemantics` builds an immutable semantic tree from presentation facts.
2. `GameAccessibilityActionRouter` rejects stale IDs, disabled nodes, unsupported actions, malformed semantic snapshots, and handler failures.
3. `LiveGameAccessibilityActions` maps stable node IDs to typed runtime owners.
4. `GameAccessibilityGeometry` maps those stable IDs to the same settings/Garden layout planners used by the Canvas UI and to bounded semantic regions for run/Rest controls.
5. `GameAccessibilityNodeProvider` exposes the current tree as Android virtual descendants, owns accessibility focus, publishes click/focus events, and fails closed when the current tree no longer contains an ID.
6. `AccessibilityAnnouncementPolicy` coalesces spoken state changes into surface/Bloom transitions and time-spaced distance milestones.
7. `GameView` supplies the exact live snapshot, transforms logical bounds through `SafeContentTransform`, routes persistence mutations through the application persistence facade, and publishes semantic-tree changes after successful state mutations.

The source now provides stable IDs independent of draw/list order, explicit focus order, Menu/Settings/Playing/Garden/Rest surfaces, labels/state descriptions/enabled state/actions/live-region intent, all nine Garden plants with affordability, all eight wardrobe styles with equipped/available/unlock-requirement state, comfort settings, run status, and authored Rest summary/quote state.

### Runtime action ownership

The provider does not dispatch synthetic touch coordinates. Essential actions route to existing owners:

- the Menu primary action preserves the willow sit/stand/ready ritual through `MainMenuScreen.performAccessibilityPrimaryAction()`;
- Menu→Garden, Garden→run, Garden→home, terminal Rest continuation, and all other top-level routes remain owned by `RunSessionTransitionCoordinator` via typed `RunSessionEvent` values;
- reduced motion, audio, and haptic writes go through the shared `ApplicationPersistenceFacade`, which delegates to `FeedbackSettings`;
- jump, long-jump, and duck use the same guarded `InputHandler` callbacks already wired to player/game-state ownership;
- Garden plant spending goes through the shared facade to `GardenPurchaseManager.purchaseNext`, so accessibility cannot bypass the canonical economy or atomic purchase boundary;
- wardrobe selection goes through the shared facade to `CostumeManager.equip`, so locked styles remain non-actionable and the persisted active costume remains authoritative.

The accessibility-only Settings sub-surface describes the comfort controls already visible on the Menu; opening it changes semantic focus scope rather than inventing a second visual modal.

### Truthful transitional state

The Rest surface is exposed during the DYING/GAME_OVER/RESTARTING sequence so the authored terminal summary remains readable, but `REST_CONTINUE` is actionable only while `runState == GAME_OVER`. During the death-recovery interval it is disabled and described as recovering, preventing assistive technology from skipping the canonical death timing.

### Announcement cadence

`GameView` samples accessibility announcement state only while Android accessibility and touch exploration are enabled, and only once every 30 rendered frames. `AccessibilityAnnouncementPolicy` then applies the stricter semantic cadence:

- announce an initial/surface transition;
- prioritize Bloom, Garden-progression, wardrobe-unlock, and feedback-setting state changes immediately;
- coalesce ordinary Playing distance into 100 m buckets with a 10 s minimum routine interval;
- ignore frame-driven score churn and unchanged semantic snapshots.

The game therefore never calls `announceForAccessibility` for every rendered frame or score increment. Real TalkBack testing must still judge whether the resulting cadence is understandable and non-intrusive on actual devices.

## Automated coverage

Source, JVM, Robolectric, and connected-device contracts now cover:

- semantic-tree validation and stable focus order;
- stale/disabled/unsupported action rejection;
- virtual-node creation, root descendants, text search, focus/clear-focus, click routing, checkable settings state, and bounded geometry;
- canonical Garden plant and wardrobe semantics;
- disabled Rest continuation before GAME_OVER;
- typed runtime action dispatch;
- throttled/coalesced accessibility announcement policy;
- permanent absence of the legacy root accessibility delegate and accessibility-specific synthetic `MotionEvent` dispatch.

These checks prove source integration; they do **not** prove human screen-reader usability.

## Physical acceptance

Automated JVM/Robolectric and emulator checks cannot substitute for real assistive-technology review. The frozen signed candidate must be tested with current TalkBack on representative phones and a tablet for:

- discoverability from first launch;
- logical focus order and no focus traps;
- correct labels and state descriptions;
- activation of every actionable control;
- willow ritual progression and Menu/Garden/Rest routing;
- jump/hold/duck reliability and understandable feedback;
- Garden purchase affordability and post-purchase focus behavior;
- wardrobe selection/equipped state and locked-style descriptions;
- Rest summary readability, DYING lockout, and restart routing;
- reduced-motion, audio, and haptic setting changes;
- lifecycle, rotation/size recreation, process death, and restored/cleared focus;
- cutout, unusual-aspect, large-font, display-size, and high-refresh devices;
- speech volume and game audio coexistence;
- no excessive live-region or announcement chatter.

Record device, Android version, TalkBack version, locale, display/font scale, scenario, result, reviewer, and evidence digest in the final candidate-bound acceptance bundle.

## Release-blocking rule

The virtual-node provider and coalesced announcement policy are live in source, so those former source blockers are closed. Forest Run must still **not** be described as fully screen-reader accepted until representative TalkBack review has passed on the exact signed release candidate and its evidence is bound into the final acceptance bundle.
