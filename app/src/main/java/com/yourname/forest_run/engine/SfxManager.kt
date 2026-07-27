package com.yourname.forest_run.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * SFX Manager — Phase 20.
 *
 * Wraps [SoundPool] for low-latency short sound effects.
 * All sounds loaded from res/raw/. Missing files are silently skipped.
 *
 * Usage (after init(context)):
 *   SfxManager.playJump()
 *   SfxManager.playLand()
 *   SfxManager.playSeedPing()
 *   SfxManager.playBark()
 *   SfxManager.playScreech()
 *   SfxManager.playHowl()
 *   SfxManager.playBloomActivate()
 *   SfxManager.playMercyMiss()
 *   SfxManager.playHit()
 */
object SfxManager {

    internal enum class BloomSfxEvent { READY, CONVERT, FADE }

    internal data class BloomSfxProfile(
        val volume: Float,
        val rate: Float
    )

    private const val TAG       = "SfxManager"
    private const val MAX_STREAMS = 8
    private const val PRIORITY  = 1
    private const val NO_LOOP   = 0
    private const val RATE_1X   = 1f

    private var pool: SoundPool? = null

    // SFX IDs (0 = not loaded)
    private var idJump          = 0
    private var idLand          = 0
    private var idSeedPing      = 0
    private var idBark          = 0
    private var idScreech       = 0
    private var idHowl          = 0
    private var idBloomActivate = 0
    private var idBloomReady    = 0
    private var idBloomConvert  = 0
    private var idBloomFade     = 0
    private var idMercyMiss     = 0
    private var idHit           = 0

    // ── Init ──────────────────────────────────────────────────────────────

    @android.annotation.SuppressLint("DiscouragedApi")
    fun init(context: Context) {
        destroy()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        pool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attrs)
            .build()

        val res = context.resources
        val pkg = context.packageName

        fun load(name: String): Int {
            val id = res.getIdentifier(name, "raw", pkg)
            if (id == 0) { Log.w(TAG, "SFX not found: $name"); return 0 }
            return pool?.load(context, id, PRIORITY) ?: 0
        }

        idJump          = load("sfx_jump")
        idLand          = load("sfx_land")
        idSeedPing      = load("sfx_seed_ping")
        idBark          = load("sfx_bark")
        idScreech       = load("sfx_screech")
        idHowl          = load("sfx_howl")
        idBloomActivate = load("sfx_bloom")
        idBloomReady    = load("sfx_bloom_ready")
        idBloomConvert  = load("sfx_bloom_convert")
        idBloomFade     = load("sfx_bloom_fade")
        idMercyMiss     = load("sfx_mercy_miss")
        idHit           = load("sfx_hit")
    }

    // ── Playback helpers ─────────────────────────────────────────────────

    private fun play(id: Int, volume: Float = 1f, rate: Float = RATE_1X) {
        if (id == 0) return
        pool?.play(id, volume, volume, PRIORITY, NO_LOOP, rate)
    }

    // ── Public API ────────────────────────────────────────────────────────

    fun playJump()          = play(idJump,          0.7f)
    fun playLand()          = play(idLand,          0.8f)
    fun playSeedPing()      = play(idSeedPing,      0.9f)
    fun playBark()          = play(idBark,          1.0f)
    fun playScreech()       = play(idScreech,       1.0f)
    fun playHowl()          = play(idHowl,          1.0f)
    fun playBloomActivate() = play(idBloomActivate, 1.0f)
    fun playBloomReady() {
        val profile = buildBloomSfxProfile(BloomSfxEvent.READY, 0)
        play(if (idBloomReady != 0) idBloomReady else idSeedPing, profile.volume, profile.rate)
    }
    fun playBloomConvert(conversionsInBurst: Int) {
        val profile = buildBloomSfxProfile(BloomSfxEvent.CONVERT, conversionsInBurst)
        play(if (idBloomConvert != 0) idBloomConvert else idSeedPing, profile.volume, profile.rate)
    }
    fun playBloomFade(conversionsInBurst: Int) {
        val profile = buildBloomSfxProfile(BloomSfxEvent.FADE, conversionsInBurst)
        play(if (idBloomFade != 0) idBloomFade else idBloomActivate, profile.volume, profile.rate)
    }
    fun playMercyMiss()     = play(idMercyMiss,     0.6f)
    fun playHit()           = play(idHit,           1.0f)

    // ── Lifecycle ─────────────────────────────────────────────────────────

    fun destroy() {
        pool?.release()
        pool = null
    }
}

internal fun buildBloomSfxProfile(
    event: SfxManager.BloomSfxEvent,
    conversionsInBurst: Int
): SfxManager.BloomSfxProfile {
    val burstLift = conversionsInBurst.coerceAtLeast(0).coerceAtMost(6)
    return when (event) {
        SfxManager.BloomSfxEvent.READY -> SfxManager.BloomSfxProfile(
            volume = 0.72f,
            rate = 1.12f
        )
        SfxManager.BloomSfxEvent.CONVERT -> SfxManager.BloomSfxProfile(
            volume = (0.70f + burstLift * 0.05f).coerceAtMost(1f),
            rate = (1.02f + burstLift * 0.035f).coerceAtMost(1.28f)
        )
        SfxManager.BloomSfxEvent.FADE -> SfxManager.BloomSfxProfile(
            volume = (0.62f + burstLift * 0.03f).coerceAtMost(0.86f),
            rate = (0.90f + burstLift * 0.02f).coerceAtMost(1.08f)
        )
    }
}
