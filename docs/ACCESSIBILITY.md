# Accessibility architecture and acceptance

Forest Run renders its product UI through one custom Canvas/SurfaceView surface. Accessibility therefore requires explicit semantics rather than relying on standard Android widgets.

## Implemented source boundary

`ForestRunAccessibilityDelegate` is attached to the live game surface by `MainActivity` and exposes guarded root actions for:

- continue or restart;
- open Garden;
- return home;
- tap jump;
- hold jump;
- duck.

Menu/Garden/Rest navigation actions dispatch through the existing touch path. Jump and duck actions use the existing `InputHandler` callbacks. Existing `GameView` app-state and run-state admission therefore remains authoritative; accessibility cannot bypass gameplay, persistence, death, or screen-transition guards.

`GameAccessibilitySemantics` defines the deterministic virtual semantic tree that a future `AccessibilityNodeProvider` must expose. It provides:

- stable IDs independent of draw/list order;
- explicit focus order;
- Menu, Settings, Playing, Garden, and Rest surfaces;
- labels, state descriptions, enabled state, actions, and live-region intent;
- all nine Garden plant nodes and affordability state;
- reduced-motion, audio, and haptic setting state;
- run status including distance, score, Seeds, and Bloom state;
- authored Rest quote/summary with deterministic fallbacks;
- fail-closed validation for malformed counts and negative values.

The semantic model is pure and tested independently from Canvas animation and touch geometry.

## Remaining live integration

The current root action surface improves operability, but it is not complete TalkBack support. A safe exact patch of the large `GameView` owner is still required to expose current presentation facts and install a virtual-node provider that:

1. builds the semantic snapshot from the exact current app/run state;
2. maps each semantic node to transformed safe-content bounds;
3. routes semantic actions through the same guarded runtime paths;
4. publishes focus and content-change events only when meaningfully changed;
5. rate-limits live run announcements so score/distance updates do not overwhelm speech;
6. retains focus across reduced-motion changes, Garden purchases, and Rest transitions;
7. invalidates nodes safely after screen changes;
8. exposes settings, individual plants, wardrobe controls, Rest summary, and run controls as distinct virtual descendants.

Do not claim complete screen-reader accessibility until that provider is live and tested.

## Physical acceptance

Automated JVM/Robolectric and emulator checks cannot substitute for real assistive-technology review. The frozen signed candidate must be tested with current TalkBack on representative phones and a tablet for:

- discoverability from first launch;
- logical focus order and no focus traps;
- correct labels and state descriptions;
- activation of every actionable control;
- jump/hold/duck reliability and understandable announcements;
- Garden purchase affordability and post-purchase focus retention;
- wardrobe selection state;
- Rest summary readability and restart routing;
- reduced-motion, audio, and haptic setting changes;
- lifecycle, rotation/size recreation, process death, and restored focus;
- cutout, unusual-aspect, large-font, display-size, and high-refresh devices;
- speech volume and game audio coexistence;
- no excessive live-region chatter.

Record device, Android version, TalkBack version, locale, display/font scale, scenario, result, reviewer, and evidence digest in the final candidate-bound acceptance bundle.

## Release-blocking rule

Forest Run remains not fully screen-reader accessible while either of these is true:

- the semantic virtual-node provider is not live for essential controls and state;
- representative TalkBack acceptance has not been completed on the exact signed candidate.
