# Forest Run — Per-band swept Orchid contact (2026-09-28)

## Defect and scope
The generic EntityManager sweep deliberately excludes the aggregate Vanilla Orchid rectangle because its marked thread must remain safe. The two physical upper/lower bands were only tested at frame endpoints. A valid 0.05s step at 2,000px/s moves a band 100px, while a falling Player can cross the upper band between samples. The horizontally clear first endpoint and vertically clear final endpoint can conceal a genuine simultaneous impact, allowing an incorrect mercy/clean-pass result.

## Correction and executable regression
Store the preceding rectangles for the two actual physical bands before every geometry update. When EntityManager and Player have real motion-sample admission, use the existing strict, same-time SweptCoreOverlap separately for each band. Preserve direct endpoint HIT priority, the genuinely open visual safe thread, and existing mercy/score/spawn values. Never sweep the aggregate box.

The new Robolectric integration exercises real production Orchid, Player and EntityManager owners at supported maximum speed, asserts the preceding upper-band snapshot and two disjoint endpoint pairs, and requires one final HIT with no mercy/clean-pass credit. Existing real-Player marked-thread and independent band tests remain. Exact resulting HEAD's Android and API-35 CI are the automated authority; this does not certify all multipart species, physical feel or external creative/release gates.
