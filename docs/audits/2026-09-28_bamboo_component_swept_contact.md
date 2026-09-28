# Forest Run — Physical Bamboo stalk sweeps retain the safe tunnel (2026-09-28)

Bamboo's outer encounter rectangle includes a deliberately traversable vertical opening and inter-stalk gaps, so the generic entity sweep correctly excludes it. Its ten actual top/bottom stalk rectangles, however, previously had endpoint-only HIT queries. At the legal 0.05s frame and 2,000px/s, 100px world translation plus falling Player movement can conceal real same-time stalk contact.

Retain a reusable preceding rectangle for each physical component before scroll/sway, and query the established strict same-time SweptCoreOverlap for each top/bottom component under the manager/Player valid-history gates. The aggregate, guide, and tunnel remain non-solid. No geometry, random range, spawn admission, rewards or visual tuning changes.

Manager-integrated Robolectric cases prove a first-stalk contact with all previous/current endpoints clear, and separately prove no invented HIT for a full body in the open vertical tunnel. Existing sampled five-stalk traversal and compact-device admission tests remain. Exact-head Android host/API35 CI is required; this is not all-mixed-sequence or human/creative acceptance.
