package com.yourname.forest_run.engine

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log

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
 *  - Tempo scaling: playback speed scales linearly with scroll speed.
 *    Formula: speed = 1.0 + (scrollSpeed - BASE) / BASE * 0.8, clamped 1.0..1.8
 *  - Graceful degradation: if an audio file is missing, that state plays silence.
 *
 * All MediaPlayer instances are created lazily and released on destroy().
 * Call [destroy] from MainActivity.onDestroy() and Activity.onPause() to
 * prevent resource leaks.
 */
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
        previousState = currentState
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

        applyTempoToPlayer(newPlayer, currentSpeed)

        val oldPlayer = activePlayer
        fadingPlayer  = oldPlayer
        activePlayer  = newPlayer

        crossFade(oldPlayer, newPlayer)
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
        val base     = GameConstants.BASE_SCROLL_SPEED.toDouble()
        val speed    = (1.0 + (scrollSpeed - base) / base * 0.8).coerceIn(1.0, 1.8).toFloat()
        if (speed == currentSpeed) return
        currentSpeed = speed
        activePlayer?.let { applyTempoToPlayer(it, speed) }
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
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun crossFade(from: MediaPlayer?, to: MediaPlayer) {
        stopFade()
        fadeThread = Thread {
            for (step in 0..FADE_STEPS) {
                val t      = step.toFloat() / FADE_STEPS
                val volIn  = t
                val volOut = 1f - t
                try {
                    to.setVolume(volIn, volIn)
                    from?.setVolume(volOut, volOut)
                } catch (_: IllegalStateException) { break }
                Thread.sleep(FADE_STEP_MS)
            }
            try {
                from?.stop()
                from?.release()
            } catch (_: Exception) {}
            if (fadingPlayer === from) fadingPlayer = null
        }.also { it.isDaemon = true; it.start() }
    }

    private fun stopFade() {
        fadeThread?.interrupt()
        fadeThread = null
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
