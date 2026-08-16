# Forest Run — Candidate-Bound Store Evidence

Store files under `release/` are intentionally generated/manual evidence. They are not trusted merely because they exist. Every accepted graphic, metadata file, screenshot, and release summary must be tied to the exact clean canonical `origin/main` commit being prepared.

The human-authored source of truth for public title/description copy is [`STORE_LISTING.md`](STORE_LISTING.md). Candidate metadata may carry evidence around those bytes, but it must not silently become a second copy authority.

## 1. Freeze the candidate

Start from a clean named `main` worktree synchronized with the canonical repository:

```bash
CANDIDATE_SHA="$(bash scripts/verify_origin_main.sh)"
python3 scripts/verify_main_candidate.py \
  --root . \
  --expected-sha "${CANDIDATE_SHA}"
```

Any source, asset, dependency, documentation, configuration, or script commit changes the candidate SHA and invalidates all candidate-bound generated evidence below.

## 2. Verify checked-in runtime assets

```bash
python3 scripts/verify_release_source_assets.py --root .
```

This preflight performs bounded structural validation rather than magic-byte checks:

- PNG signature, chunk order, CRCs, IHDR geometry, bounded zlib decoding, scanline length, and filename-declared frame divisibility;
- SFNT offset table, bounded unique table directory, required font tables, `head` magic, glyph count, cmap records, and name records;
- Ogg page boundaries, page CRCs, logical-stream sequence, BOS/EOS ownership, and Vorbis/Opus identification;
- RIFF/WAVE chunk and format/data validation;
- MP3 ID3 bounds and a valid MPEG audio-frame header;
- M4A/ISO-BMFF box bounds with required `ftyp`, `moov`, and `mdat` boxes;
- exact agreement between the authored asset catalogue, runtime release validator, and release-required audio list.

A decoded image or playable sound still requires manual artistic/audio approval on hardware.

## 3. Generate and verify store graphics

Install the pinned dependency first:

```bash
python3 -m pip install -r scripts/requirements.txt
```

Generate graphics into a staging directory and atomically publish the completed set:

```bash
python3 scripts/generate_store_assets.py \
  --candidate-sha "${CANDIDATE_SHA}"
```

The generated manifest records:

- schema version;
- exact candidate SHA;
- generator path and hash;
- font and selected sprite source paths, sizes, and hashes;
- exact output filenames, dimensions, modes, sizes, and hashes.

Verify independently:

```bash
python3 scripts/verify_store_graphics.py \
  --root . \
  --graphics-dir release/google-play/graphics \
  --candidate-sha "${CANDIDATE_SHA}"
```

The verifier rejects stale source evidence, wrong candidate identity, missing or extra files, duplicate manifest entries, unreadable PNGs, wrong dimensions/modes, and changed hashes. A failed regeneration preserves the previous published directory instead of exposing a partial set.

## 4. Project, finalize, and verify metadata

The public title, short description, and full description are authored first in `docs/STORE_LISTING.md` under their three canonical `text` fences. The checked-in Play projections are exactly:

```text
release/google-play/metadata/en-US/title.txt
release/google-play/metadata/en-US/short-description.txt
release/google-play/metadata/en-US/full-description.txt
```

Do not independently rewrite one of those files during candidate preparation. First verify that the three projection files are byte-for-text identical to the canonical Markdown blocks and that both sides satisfy the strict UTF-8/Unicode/whitespace boundary:

```bash
python3 scripts/verify_store_listing_parity.py \
  --listing-source docs/STORE_LISTING.md \
  --metadata-dir release/google-play/metadata/en-US
```

The parity verifier fails closed on missing or duplicate canonical headings, wrong or unclosed fence types, BOM/CR/non-NFC listing source, malformed metadata, trailing-newline drift, or any title/short/full-description text difference.

Only after parity succeeds, finalize their candidate evidence manifest:

```bash
python3 scripts/verify_store_metadata.py \
  --metadata-dir release/google-play/metadata/en-US \
  --candidate-sha "${CANDIDATE_SHA}" \
  --finalize
```

Then verify without mutation:

```bash
python3 scripts/verify_store_metadata.py \
  --metadata-dir release/google-play/metadata/en-US \
  --candidate-sha "${CANDIDATE_SHA}"
```

The metadata boundary requires:

- exact expected file set;
- UTF-8 without BOM;
- NFC-normalized Unicode;
- LF line endings;
- no control characters, leading/trailing whitespace, trailing line whitespace, excessive blank lines, or one-line field wrapping;
- bounded nontrivial lengths;
- no known TODO/template markers;
- candidate-bound per-file character, byte, line-count, and SHA-256 evidence.

`prepare_main_release.sh` reruns canonical listing parity before it accepts the candidate metadata manifest, so direct manual finalization cannot bypass the canonical public-copy ownership rule in the release path.

For final release evidence, preserve `metadata_manifest.json` together with the exact same `title.txt`, `short-description.txt`, and `full-description.txt` bytes. Index the manifest as candidate-bound kind `store_metadata`. `validate_release_readiness.py` path-admits all four files, reruns `verify_store_metadata.py` semantics, and proves that the exact verified manifest is the exact one named by the final release-evidence index.

Automated checks cannot approve truthfulness, localization quality, trademark usage, policy compliance, or whether current store limits have changed. Those remain manual current-policy gates.

## 5. Capture and curate screenshots

Capture eight deterministic scenarios from a freshly built exact-candidate debug APK:

```bash
bash scripts/capture_store_screenshots.sh
```

For every image, capture verifies the exact scenario readiness marker, process liveness, and the expected resumed Activity before and after `screencap`. The strict writer validates the full PNG and atomically records candidate, APK, device, package, Activity, scenario, timing, dimensions, and image hash.

The session finalizer revalidates the complete raw image/sidecar set, requires unique scenarios and one shared identity, and atomically writes `raw/capture-session.json` only after every capture succeeds.

Curate only after capture succeeds:

```bash
python3 scripts/curate_store_screenshots.py
```

Final verification requires:

```bash
python3 scripts/verify_curated_screenshot_set.py \
  --screenshot-root release/google-play/screenshots \
  --candidate-sha "${CANDIDATE_SHA}"
```

The final verifier checks complete PNG structure and compressed data, exact manifest membership, unique titles/scenarios/images, per-image sidecars, raw session evidence, and shared candidate/APK/device/package/Activity identity. A failed curation does not replace the prior accepted final set.

## 6. Prepare release evidence

Use only the canonical wrapper:

```bash
bash scripts/prepare_main_release.sh
```

Before invoking the large Play preparer, the wrapper independently verifies:

1. canonical repository identity and exact `origin/main` synchronization;
2. clean named `main` candidate identity;
3. checked-in runtime assets;
4. candidate/source-bound store graphics;
5. exact parity between `docs/STORE_LISTING.md` and the three Play metadata text files;
6. candidate-bound store metadata.

The Play preparer then verifies curated screenshots, project identity, bundle structure, R8 output, and signing evidence. The wrapper finally rechecks both local `main` and canonical `origin/main` against the frozen candidate SHA.

## 7. Evidence invalidation

Regenerate or refinalize evidence whenever any relevant input changes:

- any commit: graphics and metadata candidate manifests, screenshots, build summaries;
- canonical store-listing or metadata text change: parity must be restored and metadata manifest refinalized;
- generator/font/selected sprite change: graphics;
- APK or capture-script change: screenshots and capture session;
- curation manifest or final image change: curated screenshot verification;
- build/signing/version/dependency change: artifact and release summary;
- store policy or product claims change: manual review and, where needed, canonical listing/metadata/screenshots.

A passing automated verifier proves internal consistency and candidate binding. It does not replace visual approval, physical-device review, signed installation, internal-track delivery, or current store-policy review.
