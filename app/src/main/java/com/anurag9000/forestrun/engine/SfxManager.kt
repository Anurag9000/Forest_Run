package com.anurag9000.forestrun.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/** Low-latency short-effect playback with explicit generation-safe load readiness. */
object SfxManager {
    internal enum class BloomSfxEvent { READY, CONVERT, FADE }

    internal data class BloomSfxProfile(
        val volume: Float,
        val rate: Float
    )

    private const val TAG = "SfxManager"
    private const val MAX_STREAMS = 8
    private const val PRIORITY = 1
    private const val NO_LOOP = 0
    private const val RATE_1X = 1f

    @Volatile
    private var pool: SoundPool? = null
    private val sampleReadiness = SoundSampleReadiness()

    private var idJump = 0
    private var idLand = 0
    private var idSeedPing = 0
    private var idBark = 0
    private var idScreech = 0
    private var idHowl = 0
    private var idBloomActivate = 0
    private var idBloomReady = 0
    private var idBloomConvert = 0
    private var idBloomFade = 0
    private var idMercyMiss = 0
    private var idHit = 0

    @android.annotation.SuppressLint("DiscouragedApi")
    @Synchronized
    fun init(context: Context) {
        // Surface recreation must not leak the previous native SoundPool or
        // start loading the same samples a second time.
        if (pool != null) return

        val appContext = context.applicationContext
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val newPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
        val generation = sampleReadiness.beginGeneration()

        newPool.setOnLoadCompleteListener { callbackPool, sampleId, status ->
            // The callback may arrive after destroy/reinitialization. Both the
            // captured pool identity and generation must still own readiness.
            if (pool !== callbackPool) return@setOnLoadCompleteListener
            when (sampleReadiness.complete(generation, sampleId, status)) {
                SoundSampleReadiness.CompletionResult.READY -> Unit
                SoundSampleReadiness.CompletionResult.FAILED ->
                    Log.e(TAG, "SFX sample failed to load: id=$sampleId status=$status")
                SoundSampleReadiness.CompletionResult.STALE -> Unit
            }
        }
        pool = newPool

        val resources = appContext.resources
        val packageName = appContext.packageName

        fun load(name: String): Int {
            val resourceId = resources.getIdentifier(name, "raw", packageName)
            if (resourceId == 0) {
                Log.e(TAG, "Required SFX resource missing: res/raw/$name")
                return 0
            }
            val sampleId = newPool.load(appContext, resourceId, PRIORITY)
            if (sampleId == 0) Log.e(TAG, "SoundPool rejected SFX resource: $name")
            return sampleId
        }

        idJump = load("sfx_jump")
        idLand = load("sfx_land")
        idSeedPing = load("sfx_seed_ping")
        idBark = load("sfx_bark")
        idScreech = load("sfx_screech")
        idHowl = load("sfx_howl")
        idBloomActivate = load("sfx_bloom")
        idBloomReady = load("sfx_bloom_ready")
        idBloomConvert = load("sfx_bloom_convert")
        idBloomFade = load("sfx_bloom_fade")
        idMercyMiss = load("sfx_mercy_miss")
        idHit = load("sfx_hit")
    }

    private fun play(id: Int, volume: Float = 1f, rate: Float = RATE_1X) {
        if (!FeedbackSettings.audioEnabled || !sampleReadiness.isReady(id)) return
        val activePool = pool ?: return
        val safeVolume = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val safeRate = rate.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: RATE_1X
        runCatching {
            activePool.play(
                id,
                safeVolume,
                safeVolume,
                PRIORITY,
                NO_LOOP,
                safeRate
            )
        }.onFailure { error ->
            Log.w(TAG, "SFX playback failed for sample id=$id", error)
        }
    }

    fun playJump() = play(idJump, 0.7f)
    fun playLand() = play(idLand, 0.8f)
    fun playSeedPing() = play(idSeedPing, 0.9f)
    fun playBark() = play(idBark, 1.0f)
    fun playScreech() = play(idScreech, 1.0f)
    fun playHowl() = play(idHowl, 1.0f)
    fun playBloomActivate() = play(idBloomActivate, 1.0f)

    fun playBloomReady() {
        val profile = buildBloomSfxProfile(BloomSfxEvent.READY, 0)
        play(readySampleOrFallback(idBloomReady, idSeedPing), profile.volume, profile.rate)
    }

    fun playBloomConvert(conversionsInBurst: Int) {
        val profile = buildBloomSfxProfile(BloomSfxEvent.CONVERT, conversionsInBurst)
        play(readySampleOrFallback(idBloomConvert, idSeedPing), profile.volume, profile.rate)
    }

    fun playBloomFade(conversionsInBurst: Int) {
        val profile = buildBloomSfxProfile(BloomSfxEvent.FADE, conversionsInBurst)
        play(
            readySampleOrFallback(idBloomFade, idBloomActivate),
            profile.volume,
            profile.rate
        )
    }

    fun playMercyMiss() = play(idMercyMiss, 0.6f)
    fun playHit() = play(idHit, 1.0f)

    private fun readySampleOrFallback(primaryId: Int, fallbackId: Int): Int =
        chooseReadySample(
            primaryId = primaryId,
            fallbackId = fallbackId,
            primaryReady = sampleReadiness.isReady(primaryId),
            fallbackReady = sampleReadiness.isReady(fallbackId)
        )

    @Synchronized
    fun destroy() {
        // Invalidate callbacks before releasing the native pool so no delayed
        // completion can repopulate readiness for this or a replacement pool.
        sampleReadiness.invalidate()
        val oldPool = pool
        pool = null
        runCatching { oldPool?.setOnLoadCompleteListener(null) }
        runCatching { oldPool?.release() }
        idJump = 0
        idLand = 0
        idSeedPing = 0
        idBark = 0
        idScreech = 0
        idHowl = 0
        idBloomActivate = 0
        idBloomReady = 0
        idBloomConvert = 0
        idBloomFade = 0
        idMercyMiss = 0
        idHit = 0
    }
}

internal fun chooseReadySample(
    primaryId: Int,
    fallbackId: Int,
    primaryReady: Boolean,
    fallbackReady: Boolean
): Int = when {
    primaryId > 0 && primaryReady -> primaryId
    fallbackId > 0 && fallbackReady -> fallbackId
    else -> 0
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
