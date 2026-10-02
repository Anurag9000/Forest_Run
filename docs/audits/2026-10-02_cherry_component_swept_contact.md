# Forest Run — Cherry Blossom component swept contact (2026-10-02)

## Reproduced source gap

Forest Run admits recovery frames up to 50 ms and world speed up to 2,000 px/s, so a scroll-bound encounter may move about 100 px in one legal simulation interval. Cherry Blossom's trunk is narrower than that displacement. Its collision query checked the trunk and branch only at the final endpoint; the generic EntityManager sweep deliberately excludes multi-part trees because sweeping the aggregate tree bounds would falsely fill real empty space. Therefore a narrow solid component could be wholly to the right of the Player before the update and wholly to the left afterward while physically crossing the Player between samples, then later receive MERCY/CLEAN_PASS.

## Correction

Cherry Blossom snapshots its actual trunk and branch rectangles immediately before movement and applies the existing exact same-time `SweptCoreOverlap` solver to each solid component whenever both EntityManager and Player have valid motion samples. The public aggregate encounter box and decorative storm veil are never swept, so the empty lower side and mercy presentation are not converted into hidden solid geometry. Endpoint collision, mercy padding, scoring, art, sway and pass bounds are unchanged.

A Robolectric regression derives the real Player's FALLING collision dimensions, positions that physically sized body below the branch but inside the trunk's vertical span, and uses EntityManager at the max supported scroll speed/max admitted frame. To keep the swept-contact invariant independent of mutable production-art aspect ratios, the regression uses a valid minimum-width SpriteSheet for the Cherry Blossom; production art may legitimately be wide enough that endpoint overlap alone prevents a full tunnel. The test proves the combined falling-body + minimum legal trunk width is smaller than the legal 100 px displacement, stages that trunk wholly right at the previous endpoint and wholly left at the current endpoint, and requires the same-time component sweep to produce one terminal HIT with no mercy or clean-pass reward. The collision implementation remains exercised against the actual Player body dimensions and authored Cherry Blossom geometry rules.

## Boundary

This closes Cherry Blossom component tunneling only. Willow/Jacaranda have authored full-width crouch exemptions that require their safe lane to be preserved at the same instant before any analogous component sweep is added. Human readability, physical-device fairness and final art approval remain external.
