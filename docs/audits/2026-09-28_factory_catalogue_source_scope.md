# Forest Run — Catalogue construction-branch test scope (2026-09-28)

Exact-head Android validation run 36347167727 stopped in host Python tooling with one failure: `test_factory_contains_one_branch_for_every_catalogued_type` expected 19 occurrences of `EntityType.` anywhere in `EntityFactory.kt`. The compact Bamboo admission helper legitimately added extra mentions outside the factory's exhaustive construction switch, so this whole-file lexical count no longer represented what the test claimed to measure.

The test now extracts only the `return when (type) {` construction body, verifies one branch for each of the 19 canonical types and an exact 19-member count there. Separate existing tests still verify class/sprite authority per branch. It also asserts the new surface-eligibility helpers are wired. No runtime, art, gameplay, catalogue metadata or costs change. Require the corrected exact-head Python/host and API-35 workflows before promoting automated closure.
