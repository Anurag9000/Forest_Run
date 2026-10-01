# Forest Run — semantic gameplay action availability invalidation (2026-10-01)

## Reproduced accessibility state-publication gap

The accessibility semantic snapshot derives Jump/Long jump/Duck enablement from the live Player stance. The provider therefore returns correct state when queried. However, ordinary Player transitions such as jump launch, landing, Duck press and Duck release did not emit a virtual-node content-change event. Only larger surface/settings transitions called `notifyAccessibilityTreeChanged`.

A service that cached the node after it became disabled could therefore receive no signal when landing or release made that action available again. Handler-side preflight prevented unsafe stale actions, but fail-closed execution is not sufficient for discoverability.

## Repair

Track only the two semantic capabilities (Jump and Duck), not raw animation state. The delayed-Duck token also remains owned when a redundant press is rejected while already DUCKING; only a press that can actually start a new Duck cancels that owner. At the end of each live simulation update, compare the current capabilities with the previously published baseline. A Jump capability change invalidates both Jump and Long jump nodes; a Duck capability change invalidates only Duck. Unchanged JUMP_START/JUMPING/APEX/FALLING states do not create redundant accessibility events. A full semantic-tree notification resets the baseline, and disabling accessibility clears it.

The tracker is pure and independently unit-tested for baseline establishment, one-capability changes, unchanged-state suppression and reset behavior. The permanent live-provider source contract verifies the three targeted node invalidations and capability predicates.

## Boundary

This supplies the Android content-change signal required for services to refresh semantic actions. It does not certify TalkBack/Switch Access focus behavior or timing on real devices; that remains part of human/physical acceptance.
