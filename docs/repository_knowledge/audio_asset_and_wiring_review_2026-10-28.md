# Audio asset and runtime wiring review — 2026-10-28

## Scope and evidence

- Repository: `Anurag9000/Forest_Run`.
- Source revision inspected: `03f6b331a2f4ecacab6083c1d8d044b73ef3f12c`.
- Payload analysis: all 15 packaged Ogg files were probed and decoded; no decode failures or warnings were observed. Signal summaries were computed from decoded samples. This is asset-structure and signal evidence, not listening, music-mastering, or human acceptance.
- Runtime wiring: static inspection of `RuntimeAssetValidator.kt`, `LeitmotifManager.kt`, `SfxManager.kt`, `GameView.kt`, `MainActivity.kt`, `Player.kt`, `SeedOrbManager.kt`, and the Duck, Wolf, Dog, Owl, and Eagle entities. No application or tests were executed as part of the static wiring review.

## Packaged payloads

All files are Ogg Vorbis, stereo, 44.1 kHz. All decoded sample peaks were below 0 dBFS (range −10.20 to −0.80 dBFS); no sample clipping, nonfinite samples, decoder warnings, or long internal silence were observed. This was a sample-peak measurement, not an inter-sample true-peak measurement. Silence scanning used a −60 dBFS threshold and a 10 ms minimum window. Hashes are SHA-256 of the packaged files.

| File | Duration (s) | Sample peak (dBFS) | RMS (dBFS) | SHA-256 |
|---|---:|---:|---:|---|
| `music_bloom.ogg` | 0.390635 | −6.04 | −14.91 | `2c0b9dcc14bdae6d66ecb40d0351e61b4b2fc570e7b13821974e2057f07c93e7` |
| `music_garden.ogg` | 1.002358 | −5.09 | −15.13 | `3f8f2534a297d19386c233a7ee4fa8737cc40870dd206010263760e1983dde46` |
| `music_rest.ogg` | 0.887800 | −9.53 | −19.59 | `272b8160616d0f924f447df3f8543ab83440c5e41168107bd5abe1140894122b` |
| `music_run_1.ogg` | 1.375306 | −0.81 | −15.56 | `099b192f120f7eb7c24613be277428628b9ec9c81e1f8420254abce336fbe7e5` |
| `music_run_2.ogg` | 1.387256 | −0.80 | −15.81 | `891c2b48d3586744b38be18b8c5a35f46ac30fe06efb48f70f3f5528a0cec0c3` |
| `music_run_3.ogg` | 1.387256 | −1.45 | −14.55 | `21233e95b5e2c167293484c9906264c632a5f1fe45744d1d5b52ba1dfb0a18fb` |
| `sfx_bark.ogg` | 0.390635 | −10.20 | −20.57 | `f87605d2a3c717e6a26e584f57d24eac44e47f5a01470dad18809e5d6c9eb618` |
| `sfx_bloom.ogg` | 0.494172 | −6.12 | −17.51 | `b442ade3308a2bbda4b8c572c86f9f1071c7bdbcfb1e6f915d35c605b61bd475` |
| `sfx_hit.ogg` | 0.506054 | −4.20 | −15.58 | `ddca9df332d7cdab525bbbe62ca26e9d61aa64bfa626d9516d5c53eb91c45322` |
| `sfx_howl.ogg` | 0.932812 | −2.62 | −18.98 | `8799eb726d784ae0aa1a9df80648a873ac60015bc00aea12c8e2dd1431a2bd7d` |
| `sfx_jump.ogg` | 0.093832 | −1.54 | −23.99 | `59175ac17cd49a68dd736285738441287636112a84a6f7ce0d89921bda5a5360` |
| `sfx_land.ogg` | 0.144785 | −1.09 | −23.66 | `b9524658b52a99a20590f8feefc103088637fd13ea8ec35811aee785e1f2f330` |
| `sfx_mercy_miss.ogg` | 0.408390 | −4.70 | −15.06 | `dac403c193e8b6277375c25182ba75bb6868e0e453f0ac2de368d079016e0476` |
| `sfx_screech.ogg` | 0.284104 | −4.05 | −15.82 | `e23d8c9e971a4e34484bc834a3ec1d560811ec6f5edd5efa2c61e583c18c504a` |
| `sfx_seed_ping.ogg` | 1.757846 | −4.91 | −14.15 | `1010c9b566d6c090ff03a4aaaf7439e26eb4cc4f20373800d0b7e6a9057b795d` |

Every packaged hash is unique. A pairwise comparison of equal-duration clips after mono 8 kHz resampling found no correlation above 0.5; this does not replace listening for perceptual similarity. No audio was listened to in this review.

Reproduction commands used per file (versions were not captured):

```bash
ffprobe -v error -show_format -show_streams -of json <path>
ffmpeg -hide_banner -nostats -v info -i <path> -af 'astats=metadata=1:reset=0' -f null -
ffmpeg -hide_banner -nostats -v info -i <path> -af 'astats=metadata=0:reset=0' -f null -
ffmpeg -hide_banner -nostats -v info -i <path> -af 'silencedetect=n=-60dB:d=0.01' -f null -
ffmpeg -hide_banner -v error -i <path> -f f32le -ac 1 -ar 8000 pipe:1
```

All 15 probe and decode invocations returned successfully. Decoder output went to FFmpeg's null sink or process memory; the review created no temporary files. Duplicate comparison captured the final command's float32 mono samples in memory, compared only equal-length arrays using zero-lag normalized dot product, and reported no pair above 0.5.

The music files are short: 0.39–1.39 s. Runtime loops Garden and the three run motifs; Bloom and Rest are one-shot transitions. This is consistent with short looping motifs/stingers, but the asset payload alone cannot establish that they are compositionally complete or artistically approved. Musicality, motif identity, loop seams, mix balance, repetition fatigue, device output, rights, and final creative approval remain unresolved external review items.

## Runtime ownership and semantic cue mapping

- `RuntimeAssetValidator.kt` lists the six music resources and nine SFX resources as required release assets. Bloom effect sounds are optional in `SfxManager`; missing ones fall back to Seed ping or Bloom activation sounds.
- `LeitmotifManager.kt` maps Menu to `music_garden`, run distance bands to `music_run_1..3`, Bloom to `music_bloom`, and Rest to `music_rest`. Garden and run motifs loop; Bloom and Rest do not. The manager owns MediaPlayer transitions, crossfades, volume/tempo, and player release. `GameView` changes music state and forwards distance/Bloom changes; pause/resume is wired through lifecycle, and `MainActivity.onDestroy` releases music and SFX managers. Audio preference is applied centrally through `FeedbackSettings`.
- `SfxManager.kt` loads sounds asynchronously, logs required-load failures, handles optional Bloom fallbacks, checks the central audio-enabled preference, synchronizes playback, bounds tracked streams, and stops/releases resources. `SoundSampleReadiness` prevents callbacks from a replaced pool from marking new samples ready.
- Semantic calls are connected: Player jump/land (`Player.kt`), Seed collection (`SeedOrbManager.kt`), Wolf howl (`Wolf.kt`), Dog hazard/buddy bark (`Dog.kt`), Owl actionable alert (`Owl.kt`), Eagle target lock (`Eagle.kt`), plus hit, mercy, and Bloom callbacks in `GameView.kt`.
- Duck currently uses an animated visual call/lane cue and one-time “Quack low.” / “Stay under.” dialogue, but has no dedicated quack SFX asset or playback call. Current `GAME_DESIGN.md` requires a clear lane and timing cue, which the visual/dialogue path supplies. Historical commit `c489889f` (“Finish duck quack payoff pass”) removed the Duck quack payoff from the active implementation TODO and describes the staged quack call and answer reward as completed. A stale Phase 20 source comment nevertheless still claimed that Duck should play a quack SFX. That comment was removed in the follow-up source delta; no gameplay behavior changed.

## Remaining acceptance

No source evidence in this review establishes final audio mix, cue recognition, music quality, SFX repetition fatigue, physical device audibility, rights, licensing, or human approval. Keep those under the existing external acceptance tasks. This report does not claim a missing quack-audio bug because the current canonical requirement is a timing cue and the documented visual/dialogue cue is wired.
