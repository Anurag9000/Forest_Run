package com.yourname.forest_run.engine

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.annotation.SuppressLint
import android.util.Log

internal data class LeitmotifPlaybackProfile(
    val tempo: Float,
    val targetVolume: Float
)

/**
 * Leitmotif Audio Manager — Phase 20.
 *
 * Music state machine:
 *   MENU      → music_garden     (slow solo piano)
 *   PLAYING_1 → music_run_1      (drum beat only)        0–500m
 *   PLAYING_2 → music_run_2      (bass + flute melody)   500m–1500m
 *   PLAYING_3 → music_run_3      (full orchestral)       1500m+
 *   BLOOM     → music_bloom      (triumphant swell)      Bloom invincibility
 *   REST      → music_rest       (slow music box)
 *
 * Features:
 *  - Smooth crossfade between any two tracks over [CROSS_FADE_MS] ms.
 *  - State-shaped playback profiles: each state resolves to a target volume and tempo.
 *    Run layers scale with scroll speed, Bloom peaks hardest, and menu/rest remain softer.
 *  - Graceful degradation: if an audio file is missing, that state plays silence.
 *
 * All MediaPlayer instances are created lazily and released on destroy().
 * Call [destroy] from MainActivity.onDestroy() and Activity.onPause() to
 * prevent resource leaks.
 */
@SuppressLint("StaticFieldLeak", "DiscouragedApi")
object LeitmotifManager {

    private const val TAG = "LeitmotifMgr"
    const val CROSS_FADE_MS = 1500L     // 1.5s crossfade
    private const val FADE_STEP_MS = 50L
    private const val FADE_STEPS = (CROSS_FADE_MS / FADE_STEP_MS).toInt()

    // ── Music state ───────────────────────────────────────────────────────
    enum class MusicState { MENU, PLAYING_1, PLAYING_2, PLAYING_3, BLOOM, REST }

    private var currentState: MusicState = MusicState.MENU
    private var previousState: MusicState? = null

    // ── Players ───────────────────────────────────────────────────────────
    private var activePlayer: MediaPlayer?  = null
    private var fadingPlayer: MediaPlayer?  = null

    // ── Fade thread ───────────────────────────────────────────────────────
    private var fadeThread: Thread? = null

    // ── Tempo ─────────────────────────────────────────────────────────────
    private var currentSpeed: Float = 1f
    private var currentScrollSpeed: Float = GameConstants.BASE_SCROLL_SPEED
    private var currentTargetVolume: Float = 0.48f

    // ── Context (application context, no leak) ───────────────────────────
    private var ctx: Context? = null

    // ── Resource map ─────────────────────────────────────────────────────
    // Loaded lazily from res/raw/. Keys must match file names without extension.
    private val stateToResName = mapOf(
        MusicState.MENU      to "music_garden",
        MusicState.PLAYING_1 to "music_run_1",
        MusicState.PLAYING_2 to "music_run_2",
        MusicState.PLAYING_3 to "music_run_3",
        MusicState.BLOOM     to "music_bloom",
        MusicState.REST      to "music_rest"
    )

    // ── Init ──────────────────────────────────────────────────────────────

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Transition to a new music state with a crossfade. */
    fun transitionTo(newState: MusicState) {
        if (newState == currentState) return
        val oldState = currentState
        previousState = oldState
        currentState  = newState

        val appCtx = ctx ?: return
        val resName = stateToResName[newState] ?: return
        val resId   = appCtx.resources.getIdentifier(resName, "raw", appCtx.packageName)
        if (resId == 0) {
            Log.w(TAG, "Audio file not found: $resName — playing silence")
            stopFade()
            activePlayer?.release()
            activePlayer = null
            return
        }

        val newPlayer = try {
            MediaPlayer.create(appCtx, resId)?.apply {
                isLooping = (newState != MusicState.REST && newState != MusicState.BLOOM)
                setVolume(0f, 0f)
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaPlayer for $resName", e)
            null
        } ?: return

        val oldProfile = buildLeitmotifPlaybackProfile(oldState, currentScrollSpeed)
        val newProfile = buildLeitmotifPlaybackProfile(newState, currentScrollSpeed)
        currentSpeed = newProfile.tempo
        currentTargetVolume = newProfile.targetVolume
        applyTempoToPlayer(newPlayer, newProfile.tempo)

        val oldPlayer = activePlayer
        fadingPlayer  = oldPlayer
        activePlayer  = newPlayer

        crossFade(
            from = oldPlayer,
            to = newPlayer,
            fromVolume = oldProfile.targetVolume,
            toVolume = newProfile.targetVolume
        )
    }

    /**
     * Update music layer based on run distance. Call from GameView.update() every frame.
     * Distance thresholds: 0..500m → layer 1, 500..1500m → layer 2, 1500m+ → layer 3.
     */
    fun updateDistance(distanceM: Float) {
        val target = when {
            distanceM < 500f  -> MusicState.PLAYING_1
            distanceM < 1500f -> MusicState.PLAYING_2
            else               -> MusicState.PLAYING_3
        }
        if (currentState == MusicState.BLOOM || currentState == MusicState.REST) return
        transitionTo(target)
    }

    /**
     * Apply tempo scaling based on scroll speed.
     * Called every few frames — MediaPlayer.PlaybackParams is cheap to update.
     */
    fun updateTempo(scrollSpeed: Float) {
        currentScrollSpeed = scrollSpeed
        val profile = buildLeitmotifPlaybackProfile(currentState, scrollSpeed)
        if (profile.tempo == currentSpeed && profile.targetVolume == currentTargetVolume) return
        currentSpeed = profile.tempo
        currentTargetVolume = profile.targetVolume
        activePlayer?.let {
            applyTempoToPlayer(it, profile.tempo)
            if (fadeThread == null) {
                setPlayerVolume(it, profile.targetVolume)
            }
        }
        fadingPlayer?.let { fading ->
            val fadeState = previousState ?: return@let
            val fadeProfile = buildLeitmotifPlaybackProfile(fadeState, scrollSpeed)
            applyTempoToPlayer(fading, fadeProfile.tempo)
        }
    }

    /** Transition to REST music. Call on HIT / run death. */
    fun playRest() {
        transitionTo(MusicState.REST)
    }

    /** Transition to BLOOM music. Automatically reverts to correct PLAYING layer on Bloom end. */
    fun playBloom() {
        transitionTo(MusicState.BLOOM)
    }

    /** Called when Bloom invincibility ends — reverts to the previous PLAYING layer. */
    fun endBloom(distanceM: Float) {
        currentState = MusicState.MENU  // force re-evaluate
        updateDistance(distanceM)
    }

    /** Called on run reset. */
    fun playRunStart() {
        currentState = MusicState.MENU  // force re-evaluate
        transitionTo(MusicState.PLAYING_1)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    fun pause() {
        activePlayer?.pause()
        fadingPlayer?.pause()
    }

    fun resume() {
        activePlayer?.start()
    }

    fun destroy() {
        stopFade()
        activePlayer?.release(); activePlayer = null
        fadingPlayer?.release(); fadingPlayer = null
        ctx = null
        currentScrollSpeed = GameConstants.BASE_SCROLL_SPEED
        currentTargetVolume = 0.48f
        currentSpeed = 1f
        previousState = null
        currentState = MusicState.MENU
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun crossFade(
        from: MediaPlayer?,
        to: MediaPlayer,
        fromVolume: Float,
        toVolume: Float
    ) {
        stopFade()
        val thread = Thread {
            try {
                for (step in 0..FADE_STEPS) {
                    val t      = step.toFloat() / FADE_STEPS
                    val volIn  = toVolume * t
                    val volOut = fromVolume * (1f - t)
                    try {
                        setPlayerVolume(to, volIn)
                        from?.let { setPlayerVolume(it, volOut) }
                    } catch (_: IllegalStateException) { break }
                    Thread.sleep(FADE_STEP_MS)
                }
            } catch (_: InterruptedException) {
                // Fade interrupted by a new transition
            }
            try {
                from?.stop()
                from?.release()
            } catch (_: Exception) {}
            if (fadingPlayer === from) fadingPlayer = null
            if (fadeThread === Thread.currentThread()) fadeThread = null
        }
        fadeThread = thread.also { it.isDaemon = true; it.start() }
    }

    private fun stopFade() {
        fadeThread?.interrupt()
        fadeThread = null
    }

    private fun setPlayerVolume(player: MediaPlayer, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        player.setVolume(clamped, clamped)
    }

    @Suppress("DEPRECATION")
    private fun applyTempoToPlayer(player: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player.playbackParams.setSpeed(speed)
                player.playbackParams = params
            } catch (_: Exception) { /* unsupported — ignore */ }
        }
    }
}

internal fun buildLeitmotifPlaybackProfile(
    state: LeitmotifManager.MusicState,
    scrollSpeed: Float
): LeitmotifPlaybackProfile {
    val base = GameConstants.BASE_SCROLL_SPEED
    val speedRatio = (scrollSpeed / base).coerceIn(0.75f, 2.0f)
    val speedLift = (speedRatio - 1f).coerceAtLeast(0f)
    val runTempo = (1f + ((scrollSpeed - base) / base) * 0.8f).coerceIn(1f, 1.8f)

    return when (state) {
        LeitmotifManager.MusicState.MENU -> LeitmotifPlaybackProfile(
            tempo = 0.94f,
            targetVolume = 0.48f
        )
        LeitmotifManager.MusicState.REST -> LeitmotifPlaybackProfile(
            tempo = 0.92f,
            targetVolume = 0.44f
        )
        LeitmotifManager.MusicState.PLAYING_1 -> LeitmotifPlaybackProfile(
            tempo = runTempo,
            targetVolume = (0.66f + speedLift * 0.05f).coerceIn(0.55f, 0.78f)
        )
        LeitmotifManager.MusicState.PLAYING_2 -> LeitmotifPlaybackProfile(
            tempo = (runTempo + 0.04f).coerceAtMost(1.8f),
            targetVolume = (0.78f + speedLift * 0.06f).coerceIn(0.68f, 0.90f)
        )
        LeitmotifManager.MusicState.PLAYING_3 -> LeitmotifPlaybackProfile(
            tempo = (runTempo + 0.08f).coerceAtMost(1.8f),
            targetVolume = (0.90f + speedLift * 0.06f).coerceIn(0.82f, 0.98f)
        )
        LeitmotifManager.MusicState.BLOOM -> LeitmotifPlaybackProfile(
            tempo = maxOf(1.12f, (runTempo + 0.10f).coerceAtMost(1.8f)),
            targetVolume = 1f
        )
    }
}
