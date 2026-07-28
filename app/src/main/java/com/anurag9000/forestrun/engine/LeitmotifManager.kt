package com.anurag9000.forestrun.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlin.math.abs

internal data class LeitmotifPlaybackProfile(
    val tempo: Float,
    val targetVolume: Float,
    val motifSignature: LeitmotifSignature
)

internal data class BloomMusicSignature(
    val secondsRemaining: Float,
    val conversions: Int
)

internal data class LeitmotifSignature(
    val motifLabel: String,
    val leadPresence: Float,
    val pulsePresence: Float,
    val warmth: Float,
    val shimmer: Float,
    val cadenceLift: Float
)

private val defaultBloomMusicSignature = BloomMusicSignature(
    secondsRemaining = GameConstants.BLOOM_DURATION_S,
    conversions = 0
)
private val menuLeitmotifProfile = LeitmotifPlaybackProfile(
    tempo = 0.94f,
    targetVolume = 0.48f,
    motifSignature = LeitmotifSignature(
        motifLabel = "Garden Hush",
        leadPresence = 0.34f,
        pulsePresence = 0.20f,
        warmth = 0.84f,
        shimmer = 0.46f,
        cadenceLift = 0.26f
    )
)
private val restLeitmotifProfile = LeitmotifPlaybackProfile(
    tempo = 0.92f,
    targetVolume = 0.44f,
    motifSignature = LeitmotifSignature(
        motifLabel = "Lantern Recovery",
        leadPresence = 0.30f,
        pulsePresence = 0.16f,
        warmth = 0.72f,
        shimmer = 0.22f,
        cadenceLift = 0.18f
    )
)

/** Thread-safe MediaPlayer state machine for the game's musical layers. */
@SuppressLint("StaticFieldLeak", "DiscouragedApi")
object LeitmotifManager {
    private const val TAG = "LeitmotifMgr"
    const val CROSS_FADE_MS = 1500L
    private const val FADE_STEP_MS = 50L
    private const val FADE_STEPS = (CROSS_FADE_MS / FADE_STEP_MS).toInt()
    private const val PARAMETER_UPDATE_INTERVAL_NS = 100_000_000L
    private const val BLOOM_SIGNATURE_SECONDS_EPSILON = 0.08f
    private const val TEMPO_EPSILON = 0.0125f
    private const val VOLUME_EPSILON = 0.015f

    enum class MusicState { MENU, PLAYING_1, PLAYING_2, PLAYING_3, BLOOM, REST }

    private val audioLock = Any()

    private var currentState: MusicState = MusicState.MENU
    private var previousState: MusicState? = null
    private var activePlayer: MediaPlayer? = null
    private var fadingPlayer: MediaPlayer? = null
    private var fadeThread: Thread? = null
    private var fadeGeneration = 0L

    private var currentSpeed = 1f
    private var currentScrollSpeed = GameConstants.BASE_SCROLL_SPEED
    private var currentTargetVolume = 0.48f
    private var lastParameterUpdateNs = 0L
    private val tempoEvaluationThrottle = EvaluationThrottle(PARAMETER_UPDATE_INTERVAL_NS)
    private val bloomEvaluationThrottle = EvaluationThrottle(PARAMETER_UPDATE_INTERVAL_NS)
    private var currentMotifSignature = menuLeitmotifProfile.motifSignature
    private var bloomMusicSignature = defaultBloomMusicSignature

    private var ctx: Context? = null

    private val stateToResName = mapOf(
        MusicState.MENU to "music_garden",
        MusicState.PLAYING_1 to "music_run_1",
        MusicState.PLAYING_2 to "music_run_2",
        MusicState.PLAYING_3 to "music_run_3",
        MusicState.BLOOM to "music_bloom",
        MusicState.REST to "music_rest"
    )

    fun init(context: Context) {
        synchronized(audioLock) {
            ctx = context.applicationContext
        }
        // currentState starts as MENU, but no MediaPlayer exists yet. The
        // transition method therefore checks both state and player presence.
        transitionTo(MusicState.MENU)
    }

    fun transitionTo(newState: MusicState) {
        synchronized(audioLock) {
            if (newState == currentState && activePlayer != null) return

            val appContext = ctx ?: return
            val resourceName = stateToResName[newState] ?: return
            val resourceId = appContext.resources.getIdentifier(
                resourceName,
                "raw",
                appContext.packageName
            )
            if (resourceId == 0) {
                Log.e(TAG, "Required music resource is missing: $resourceName")
                return
            }

            val newPlayer = try {
                MediaPlayer.create(appContext, resourceId)?.apply {
                    isLooping = newState != MusicState.REST && newState != MusicState.BLOOM
                    setVolume(0f, 0f)
                    start()
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to create MediaPlayer for $resourceName", error)
                null
            } ?: return

            val oldState = currentState
            val oldPlayer = activePlayer
            val oldProfile = buildLeitmotifPlaybackProfile(
                oldState,
                currentScrollSpeed,
                bloomMusicSignature
            )

            if (newState == MusicState.BLOOM) {
                bloomMusicSignature = defaultBloomMusicSignature
            }
            val newProfile = buildLeitmotifPlaybackProfile(
                newState,
                currentScrollSpeed,
                bloomMusicSignature
            )

            previousState = oldState
            currentState = newState
            currentSpeed = newProfile.tempo
            currentTargetVolume = newProfile.targetVolume
            currentMotifSignature = newProfile.motifSignature
            val transitionNowNs = System.nanoTime()
            lastParameterUpdateNs = transitionNowNs
            tempoEvaluationThrottle.tryAcquire(transitionNowNs, force = true)
            bloomEvaluationThrottle.tryAcquire(transitionNowNs, force = true)
            applyTempoToPlayer(newPlayer, newProfile.tempo)

            activePlayer = newPlayer
            fadingPlayer = oldPlayer
            startCrossFadeLocked(
                from = oldPlayer,
                to = newPlayer,
                fromVolume = oldProfile.targetVolume,
                toVolume = newProfile.targetVolume
            )
        }
    }

    fun updateDistance(distanceM: Float) {
        synchronized(audioLock) {
            if (currentState == MusicState.BLOOM || currentState == MusicState.REST) return
        }
        transitionTo(runStateForDistance(distanceM))
    }

    /**
     * Called from the game loop. MediaPlayer parameters are changed at most ten
     * times per second and only after a meaningful tempo/volume delta.
     */
    fun updateTempo(scrollSpeed: Float) {
        synchronized(audioLock) {
            currentScrollSpeed = scrollSpeed.coerceAtLeast(1f)
            if (currentState == MusicState.BLOOM || currentState == MusicState.REST) return

            val nowNs = System.nanoTime()
            if (!tempoEvaluationThrottle.tryAcquire(nowNs)) return

            val profile = buildLeitmotifPlaybackProfile(
                currentState,
                currentScrollSpeed,
                bloomMusicSignature
            )
            currentMotifSignature = profile.motifSignature
            applyProfileIfNeededLocked(profile, force = false, nowNs = nowNs)
        }
    }

    fun playRest() = transitionTo(MusicState.REST)

    fun playBloom() {
        synchronized(audioLock) {
            bloomMusicSignature = defaultBloomMusicSignature
        }
        transitionTo(MusicState.BLOOM)
    }

    fun updateBloomSignature(secondsRemaining: Float, conversions: Int) {
        synchronized(audioLock) {
            if (currentState != MusicState.BLOOM) return

            val clampedSeconds = secondsRemaining.coerceIn(0f, GameConstants.BLOOM_DURATION_S)
            val clampedConversions = conversions.coerceAtLeast(0)
            val conversionChanged = clampedConversions != bloomMusicSignature.conversions
            val timeChanged = abs(clampedSeconds - bloomMusicSignature.secondsRemaining) >=
                BLOOM_SIGNATURE_SECONDS_EPSILON
            if (!conversionChanged && !timeChanged) return

            val nowNs = System.nanoTime()
            if (!bloomEvaluationThrottle.tryAcquire(nowNs, force = conversionChanged)) return

            bloomMusicSignature = BloomMusicSignature(
                secondsRemaining = clampedSeconds,
                conversions = clampedConversions
            )
            val profile = buildLeitmotifPlaybackProfile(
                MusicState.BLOOM,
                currentScrollSpeed,
                bloomMusicSignature
            )
            currentMotifSignature = profile.motifSignature
            applyProfileIfNeededLocked(
                profile = profile,
                force = conversionChanged,
                nowNs = nowNs
            )
        }
    }

    fun endBloom(distanceM: Float) {
        synchronized(audioLock) {
            bloomMusicSignature = BloomMusicSignature(
                secondsRemaining = 0f,
                conversions = 0
            )
        }
        transitionTo(runStateForDistance(distanceM))
    }

    fun playRunStart() = transitionTo(MusicState.PLAYING_1)

    fun pause() {
        synchronized(audioLock) {
            runCatching { activePlayer?.pause() }
            runCatching { fadingPlayer?.pause() }
        }
    }

    fun resume() {
        synchronized(audioLock) {
            runCatching { activePlayer?.start() }
            runCatching { fadingPlayer?.start() }
        }
    }

    fun destroy() {
        synchronized(audioLock) {
            stopFadeLocked()
            releasePlayer(activePlayer)
            releasePlayer(fadingPlayer)
            activePlayer = null
            fadingPlayer = null
            ctx = null
            currentScrollSpeed = GameConstants.BASE_SCROLL_SPEED
            currentTargetVolume = 0.48f
            currentSpeed = 1f
            lastParameterUpdateNs = 0L
            currentMotifSignature = menuLeitmotifProfile.motifSignature
            bloomMusicSignature = defaultBloomMusicSignature
            tempoEvaluationThrottle.reset()
            bloomEvaluationThrottle.reset()
            previousState = null
            currentState = MusicState.MENU
        }
    }

    internal fun currentMotifSignature(): LeitmotifSignature =
        synchronized(audioLock) { currentMotifSignature }

    private fun runStateForDistance(distanceM: Float): MusicState = when {
        distanceM < 500f -> MusicState.PLAYING_1
        distanceM < 1500f -> MusicState.PLAYING_2
        else -> MusicState.PLAYING_3
    }

    private fun applyProfileIfNeededLocked(
        profile: LeitmotifPlaybackProfile,
        force: Boolean,
        nowNs: Long
    ) {
        val tempoChanged = abs(profile.tempo - currentSpeed) >= TEMPO_EPSILON
        val volumeChanged = abs(profile.targetVolume - currentTargetVolume) >= VOLUME_EPSILON
        if (!force && !tempoChanged && !volumeChanged) return

        if (!force && nowNs - lastParameterUpdateNs < PARAMETER_UPDATE_INTERVAL_NS) return
        lastParameterUpdateNs = nowNs

        if (tempoChanged || force) {
            activePlayer?.let { applyTempoToPlayer(it, profile.tempo) }
            fadingPlayer?.let { fading ->
                val fadeState = previousState
                if (fadeState != null) {
                    val fadeProfile = buildLeitmotifPlaybackProfile(
                        fadeState,
                        currentScrollSpeed,
                        bloomMusicSignature
                    )
                    applyTempoToPlayer(fading, fadeProfile.tempo)
                }
            }
        }

        if ((volumeChanged || force) && fadeThread?.isAlive != true) {
            activePlayer?.let { setPlayerVolume(it, profile.targetVolume) }
        }

        currentSpeed = profile.tempo
        currentTargetVolume = profile.targetVolume
    }

    private fun startCrossFadeLocked(
        from: MediaPlayer?,
        to: MediaPlayer,
        fromVolume: Float,
        toVolume: Float
    ) {
        stopFadeLocked()
        val generation = ++fadeGeneration
        val thread = Thread({
            try {
                for (step in 0..FADE_STEPS) {
                    if (Thread.currentThread().isInterrupted) return@Thread
                    val fraction = step.toFloat() / FADE_STEPS
                    synchronized(audioLock) {
                        if (generation != fadeGeneration) return@Thread
                        setPlayerVolume(to, toVolume * fraction)
                        from?.let { setPlayerVolume(it, fromVolume * (1f - fraction)) }
                    }
                    Thread.sleep(FADE_STEP_MS)
                }
            } catch (_: InterruptedException) {
                // A newer transition owns the audio state now.
            } catch (error: Exception) {
                Log.w(TAG, "Crossfade ended early", error)
            } finally {
                synchronized(audioLock) {
                    releasePlayer(from)
                    if (fadingPlayer === from) fadingPlayer = null
                    if (fadeThread === Thread.currentThread()) fadeThread = null
                    if (generation == fadeGeneration) {
                        runCatching { setPlayerVolume(to, currentTargetVolume) }
                    }
                }
            }
        }, "LeitmotifFade-$generation")
        thread.isDaemon = true
        fadeThread = thread
        thread.start()
    }

    private fun stopFadeLocked() {
        fadeGeneration++
        fadeThread?.interrupt()
        fadeThread = null
    }

    private fun releasePlayer(player: MediaPlayer?) {
        if (player == null) return
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    private fun setPlayerVolume(player: MediaPlayer, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        player.setVolume(clamped, clamped)
    }

    @Suppress("DEPRECATION")
    private fun applyTempoToPlayer(player: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val params = player.playbackParams
            params.speed = speed.coerceIn(0.5f, 2f)
            player.playbackParams = params
        } catch (_: Exception) {
            // Some devices/codecs do not support PlaybackParams.
        }
    }
}

internal fun buildLeitmotifPlaybackProfile(
    state: LeitmotifManager.MusicState,
    scrollSpeed: Float,
    bloomSignature: BloomMusicSignature = defaultBloomMusicSignature
): LeitmotifPlaybackProfile {
    val base = GameConstants.BASE_SCROLL_SPEED
    val speedRatio = (scrollSpeed / base).coerceIn(0.75f, 2.0f)
    val speedLift = (speedRatio - 1f).coerceAtLeast(0f)
    val runTempo = (1f + ((scrollSpeed - base) / base) * 0.8f).coerceIn(1f, 1.8f)

    return when (state) {
        LeitmotifManager.MusicState.MENU -> menuLeitmotifProfile

        LeitmotifManager.MusicState.REST -> restLeitmotifProfile

        LeitmotifManager.MusicState.PLAYING_1 -> LeitmotifPlaybackProfile(
            tempo = runTempo,
            targetVolume = (0.66f + speedLift * 0.05f).coerceIn(0.55f, 0.78f),
            motifSignature = LeitmotifSignature(
                motifLabel = "First Footing",
                leadPresence = 0.42f,
                pulsePresence = (0.42f + speedLift * 0.10f).coerceIn(0.40f, 0.58f),
                warmth = 0.50f,
                shimmer = 0.18f,
                cadenceLift = 0.30f
            )
        )

        LeitmotifManager.MusicState.PLAYING_2 -> LeitmotifPlaybackProfile(
            tempo = (runTempo + 0.04f).coerceAtMost(1.8f),
            targetVolume = (0.78f + speedLift * 0.06f).coerceIn(0.68f, 0.90f),
            motifSignature = LeitmotifSignature(
                motifLabel = "Open Path",
                leadPresence = 0.58f,
                pulsePresence = (0.56f + speedLift * 0.08f).coerceIn(0.52f, 0.70f),
                warmth = 0.56f,
                shimmer = 0.30f,
                cadenceLift = 0.42f
            )
        )

        LeitmotifManager.MusicState.PLAYING_3 -> LeitmotifPlaybackProfile(
            tempo = (runTempo + 0.08f).coerceAtMost(1.8f),
            targetVolume = (0.90f + speedLift * 0.06f).coerceIn(0.82f, 0.98f),
            motifSignature = LeitmotifSignature(
                motifLabel = "Forest In Full",
                leadPresence = 0.74f,
                pulsePresence = (0.72f + speedLift * 0.06f).coerceIn(0.68f, 0.84f),
                warmth = 0.60f,
                shimmer = 0.42f,
                cadenceLift = 0.58f
            )
        )

        LeitmotifManager.MusicState.BLOOM -> LeitmotifPlaybackProfile(
            tempo = maxOf(
                1.08f,
                (
                    runTempo +
                        0.08f +
                        (bloomSignature.secondsRemaining / GameConstants.BLOOM_DURATION_S) * 0.08f +
                        bloomSignature.conversions.coerceAtMost(5) * 0.014f
                    ).coerceAtMost(1.8f)
            ),
            targetVolume = (
                0.88f +
                    (bloomSignature.secondsRemaining / GameConstants.BLOOM_DURATION_S) * 0.08f +
                    bloomSignature.conversions.coerceAtMost(5) * 0.015f
                ).coerceIn(0.88f, 1f),
            motifSignature = LeitmotifSignature(
                motifLabel = "Bloom Surge",
                leadPresence = (
                    0.78f +
                        (bloomSignature.secondsRemaining / GameConstants.BLOOM_DURATION_S) * 0.08f +
                        bloomSignature.conversions.coerceAtMost(5) * 0.02f
                    ).coerceIn(0.78f, 0.96f),
                pulsePresence = (
                    0.74f + bloomSignature.conversions.coerceAtMost(5) * 0.035f
                    ).coerceIn(0.74f, 0.96f),
                warmth = (
                    0.58f +
                        (bloomSignature.secondsRemaining / GameConstants.BLOOM_DURATION_S) * 0.08f
                    ).coerceIn(0.58f, 0.74f),
                shimmer = (
                    0.68f + bloomSignature.conversions.coerceAtMost(5) * 0.045f
                    ).coerceIn(0.68f, 0.94f),
                cadenceLift = (
                    0.66f + bloomSignature.conversions.coerceAtMost(5) * 0.04f
                    ).coerceIn(0.66f, 0.90f)
            )
        )
    }
}
