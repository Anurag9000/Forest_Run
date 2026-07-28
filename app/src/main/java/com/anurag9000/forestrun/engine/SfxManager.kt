package com.anurag9000.forestrun.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/** Low-latency short-effect playback with explicit load readiness. */
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

    private var pool: SoundPool? = null
    private val readySamples = ConcurrentHashMap.newKeySet<Int>()

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

        newPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                readySamples.add(sampleId)
            } else {
                readySamples.remove(sampleId)
                Log.e(TAG, "SFX sample failed to load: id=$sampleId status=$status")
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
        if (id == 0 || id !in readySamples) return
        pool?.play(
            id,
            volume.coerceIn(0f, 1f),
            volume.coerceIn(0f, 1f),
            PRIORITY,
            NO_LOOP,
            rate.coerceIn(0.5f, 2f)
        )
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
        play(if (idBloomReady != 0) idBloomReady else idSeedPing, profile.volume, profile.rate)
    }

    fun playBloomConvert(conversionsInBurst: Int) {
        val profile = buildBloomSfxProfile(BloomSfxEvent.CONVERT, conversionsInBurst)
        play(if (idBloomConvert != 0) idBloomConvert else idSeedPing, profile.volume, profile.rate)
    }

    fun playBloomFade(conversionsInBurst: Int) {
        val profile = buildBloomSfxProfile(BloomSfxEvent.FADE, conversionsInBurst)
        play(
            if (idBloomFade != 0) idBloomFade else idBloomActivate,
            profile.volume,
            profile.rate
        )
    }

    fun playMercyMiss() = play(idMercyMiss, 0.6f)
    fun playHit() = play(idHit, 1.0f)

    @Synchronized
    fun destroy() {
        pool?.release()
        pool = null
        readySamples.clear()
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
