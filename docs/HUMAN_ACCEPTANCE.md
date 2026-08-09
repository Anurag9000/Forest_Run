# Human gameplay, accessibility, and presentation acceptance

Forest Run separates **measured physical-device acceptance** from **human acceptance**. `device-acceptance.json` proves candidate identity, device/scenario coverage, performance thresholds, coarse session checks, signed/internal-store identity, and file integrity. It cannot prove that a jump feels responsive, an Eagle telegraph is fair, TalkBack focus order is understandable, a ghost remains readable in a dense hazard, or final animation/audio/haptic presentation is acceptable.

The human layer is therefore a separate candidate-bound evidence contract implemented by:

```text
scripts/compile_human_acceptance.py
scripts/validate_human_acceptance.py
```

Neither script can manufacture a pass. The compiler hashes existing evidence and preserves entered judgments; the validator rejects incomplete, failed, stale, aliased, or candidate-mismatched evidence.

## Dependency on physical acceptance

A human manifest references one already-valid physical `device-acceptance.json` by relative path and SHA-256. The validator revalidates that physical manifest, then requires every human review to name a real `device_acceptance_session_id` and the matching device class.

The same candidate must match across both layers:

- repository and `main` branch;
- application ID;
- full commit SHA;
- version code;
- signed artifact SHA-256;
- upload-certificate SHA-256 for the submitted AAB;
- app-signing-certificate SHA-256 for the Play-delivered APK.

A human review cannot be attached to a stale local APK, an emulator-only run, or a different device session.

## Required physical coverage

Human acceptance must cover all five release device classes represented by the physical manifest:

- `older_phone`;
- `midrange_phone`;
- `high_refresh_phone`;
- `cutout_phone`;
- `tablet`.

There may be multiple reviews per class. At least one complete review is required for every class.

## Required gameplay checks

Every review records an explicit result for every gameplay criterion:

- `touch_latency`;
- `short_jump_feel`;
- `hold_jump_feel`;
- `swipe_down_duck`;
- `gesture_cancellation`;
- `all_entity_telegraphs_hitboxes_outcomes`;
- `high_speed_encounter_combinations`;
- `bloom_hazard_readability`;
- `rest_restart_continuity`;
- `safe_content_and_system_bars`;
- `text_contrast_readability`;
- `garden_wardrobe_continuity`;
- `ghost_readability`;
- `relationship_progression_cadence`.

These are human acceptance observations. Automated collision, input, safe-area, Garden, relationship, and ghost tests remain necessary but cannot substitute for them.

## Required accessibility checks

Every review also records:

- `talkback_focus_order`;
- `labels_and_state_descriptions`;
- `semantic_action_reliability`;
- `settings_toggles`;
- `playing_controls`;
- `garden_plants`;
- `wardrobe`;
- `rest_flow`;
- `recovery_dialogs`;
- `announcement_cadence`;
- `lifecycle_resume`;
- `large_text_and_display_scale`;
- `cutout_and_aspect_variants`;
- `audio_coexistence`;
- `reduced_motion`;
- `switch_access`.

The review records the TalkBack version and Switch Access version/context used. `switch_access` may be `not_applicable` when Switch Access is outside the accepted product matrix; that decision remains explicit rather than silently omitted. Cutout/aspect testing may be not applicable on a non-cutout review but **must pass on the `cutout_phone` review**.

## Required presentation checks

Every review records:

- `artwork_animation`;
- `wolf_animation`;
- `procedural_scenery`;
- `fixed_landscape_composition`;
- `audio_balance_latency`;
- `haptic_intensity_cadence`;
- `reduced_motion_presentation`.

Haptic presentation may be `not_applicable` only where the reviewed hardware genuinely has no applicable haptic surface. Other presentation checks must pass.

## States and evidence

A criterion may be only:

- `pass`; or
- an explicitly permitted `not_applicable`.

There is deliberately no `waived`, `mostly_pass`, or implicit missing state. A failed criterion keeps the candidate blocked and should lead to remediation plus a fresh affected acceptance run.

Every review includes at least one evidence file. Suitable files include tester notes, screenshots, screen recordings, accessibility recordings, device observations, and approved review exports. Final manifests store normalized relative paths and SHA-256 digests. Path traversal, duplicate paths, physical-file reuse, symlink aliases, missing files, digest mismatch, and files changing during validation are rejected.

Do not place unrelated personal information, account credentials, raw signing secrets, or private device identifiers in the evidence bundle.

## Draft and compilation

The draft uses plain relative paths so reviewers do not manually type file hashes. Example shape:

```json
{
  "generated_at_utc": "2026-08-09T18:00:00Z",
  "candidate": {
    "repository": "Anurag9000/Forest_Run",
    "branch": "main",
    "application_id": "com.anurag9000.forestrun",
    "commit_sha": "<40-hex>",
    "version_code": 1,
    "artifact_sha256": "<64-hex>",
    "upload_certificate_sha256": "<64-hex>",
    "app_signing_certificate_sha256": "<64-hex>"
  },
  "device_acceptance": "device-acceptance.json",
  "reviews": [
    {
      "review_id": "midrange-review-1",
      "device_acceptance_session_id": "<real session id>",
      "device_class": "midrange_phone",
      "reviewer": "reviewer-name",
      "talkback_version": "<observed version>",
      "switch_access_version": "<observed version or not_applicable>",
      "started_at_utc": "2026-08-09T16:00:00Z",
      "completed_at_utc": "2026-08-09T16:30:00Z",
      "gameplay_checks": {"touch_latency": "pass"},
      "accessibility_checks": {"talkback_focus_order": "pass"},
      "presentation_checks": {"artwork_animation": "pass"},
      "evidence_files": ["human/midrange/review-notes.txt"],
      "notes": "Observed review notes."
    }
  ],
  "final_review": {
    "decision": "approved",
    "reviewers": ["release-owner", "independent-reviewer"],
    "reviewed_at_utc": "2026-08-09T17:30:00Z",
    "notes": "Final human acceptance rationale."
  }
}
```

The abbreviated check objects above illustrate shape only. The real compiler/validator requires every exact criterion listed in this document.

Compile and validate:

```bash
python3 scripts/compile_human_acceptance.py \
  release/evidence/human-acceptance-draft.json \
  release/evidence/human-acceptance.json \
  --summary-output release/evidence/human-acceptance-summary.json

python3 scripts/validate_human_acceptance.py \
  release/evidence/human-acceptance.json \
  --summary-output release/evidence/human-acceptance-summary.json
```

The draft, device manifest, human evidence, final manifest, and summary must live under one evidence root so relative paths remain stable.

## Final reviewer rule

The final human decision must be `approved` and name at least two distinct reviewers, case-insensitively. Each final reviewer must have authored at least one device review. This prevents a final approval list containing names that never participated in the observed human matrix.

A valid human manifest means only that the declared reviews are complete, internally consistent, candidate-bound, and file-bound. It does not prove tester honesty or replace expert judgment. Those remain accountable human responsibilities.
