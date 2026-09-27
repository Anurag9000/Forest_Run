# Forest Run — Duck's authored low flight is a real duck lesson (2026-09-27)

## Reproduced geometry discrepancy

The canonical Duck requirement is a low flyer with an actionable duck-under response. The production Duck set its sprite bottom to 70% of world groundY (groundY minus 30% groundY), while Player is a 100px foot-anchored body. At BALANCED groundY=885.6px this put the Duck's dangerous bottom around Y=611px, far above the standing Player's collision top around Y=796px. A player could stand still and receive an ordinary clean pass; the existing DuckTest manufactured tiny/relocated player hitboxes and thus never exercised real standing-versus-crouching geometry.

## Minimal behavior-preserving repair

Anchor Duck's sprite lower edge at groundY minus 60% of Player.BASE_HEIGHT instead of subtracting a varying fraction of the entire screen/ground. Its hitbox is inlaid from that visible sprite, so it overlaps the actual standing head/shoulder band and clears the actual foot-anchored 0.55-scale crouch. The previously authored yellow low-answer guide remains derived from the real same sprite placement and overlaps the crouched body. No random pacing, collision severity, scoring, animation, asset or art provenance changes.

Two Robolectric regressions use unmodified real Player hitboxes at 720/760/1080/1320/1440px heights: they verify standing HIT, ducked NONE, guide recognition; and scroll two real Duck instances across real standing/crouching players at 650 and 2,000px/s, requiring an actual dangerous standing crossing and uninterrupted safe crouch passage. The prior authored quack/answer probe test remains. Exact HEAD CI is the executable authority.

## Limits

This proves isolated Duck mechanics and the reference-size geometry, not all follow-on encounter recovery, human telegraph perception, device comfort, final creative approval or Play acceptance.
