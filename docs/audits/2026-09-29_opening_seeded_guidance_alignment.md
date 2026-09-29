# Forest Run — Opening cue/encounter semantic alignment (2026-09-29)

## Finding

Normal runs seed Duck first. The canonical encounter design defines Duck as the low-flyer ducking lesson, but the HUD previously prioritized a tap/jump cue throughout the first approach. The tutorial therefore taught a different response from the first guaranteed encounter.

## Change

During the existing 6.75-second random-spawn lockout, an unseen duck input now receives `Duck The Low Flyer` with the explicit swipe-down instruction. Once duck is observed, the existing tap and hold progression continues; the later duck cue remains a fallback for players who reach the later guided window without recording the input. No encounter order, timing, collision geometry, input mechanics, random-pool membership or progression values change.

A Robolectric cross-owner regression seeds the real normal opening, proves its first entity is Duck, and requires the initial guidance cue to teach the same action. Unit coverage retains tap, hold, late-duck, route, expiry and malformed-time cases.

## Boundary

This is source-level tutorial consistency, not human/device onboarding acceptance.
