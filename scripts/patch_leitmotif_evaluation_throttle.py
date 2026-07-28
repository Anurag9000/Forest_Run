#!/usr/bin/env python3
"""Throttle derived music profiles before they allocate on the game loop."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/engine/LeitmotifManager.kt")

    replace_once(
        path,
        '''internal data class LeitmotifSignature(
    val motifLabel: String,
    val leadPresence: Float,
    val pulsePresence: Float,
    val warmth: Float,
    val shimmer: Float,
    val cadenceLift: Float
)

/** Thread-safe MediaPlayer state machine for the game's musical layers. */
''',
        '''internal data class LeitmotifSignature(
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
''',
        "fixed profile singletons",
    )

    replace_once(
        path,
        '''    private const val PARAMETER_UPDATE_INTERVAL_NS = 100_000_000L
    private const val TEMPO_EPSILON = 0.0125f
    private const val VOLUME_EPSILON = 0.015f
''',
        '''    private const val PARAMETER_UPDATE_INTERVAL_NS = 100_000_000L
    private const val BLOOM_SIGNATURE_SECONDS_EPSILON = 0.08f
    private const val TEMPO_EPSILON = 0.0125f
    private const val VOLUME_EPSILON = 0.015f
''',
        "Bloom evaluation epsilon",
    )

    replace_once(
        path,
        '''    private var currentTargetVolume = 0.48f
    private var lastParameterUpdateNs = 0L
    private var currentMotifSignature = LeitmotifSignature(
        motifLabel = "Garden Hush",
        leadPresence = 0.34f,
        pulsePresence = 0.22f,
        warmth = 0.82f,
        shimmer = 0.44f,
        cadenceLift = 0.28f
    )
    private var bloomMusicSignature = BloomMusicSignature(
        secondsRemaining = GameConstants.BLOOM_DURATION_S,
        conversions = 0
    )
''',
        '''    private var currentTargetVolume = 0.48f
    private var lastParameterUpdateNs = 0L
    private val tempoEvaluationThrottle = EvaluationThrottle(PARAMETER_UPDATE_INTERVAL_NS)
    private val bloomEvaluationThrottle = EvaluationThrottle(PARAMETER_UPDATE_INTERVAL_NS)
    private var currentMotifSignature = menuLeitmotifProfile.motifSignature
    private var bloomMusicSignature = defaultBloomMusicSignature
''',
        "evaluation gates and cached initial state",
    )

    replace_once(
        path,
        '''            if (newState == MusicState.BLOOM) {
                bloomMusicSignature = BloomMusicSignature(
                    secondsRemaining = GameConstants.BLOOM_DURATION_S,
                    conversions = 0
                )
            }
''',
        '''            if (newState == MusicState.BLOOM) {
                bloomMusicSignature = defaultBloomMusicSignature
            }
''',
        "cached Bloom reset on transition",
    )

    replace_once(
        path,
        '''            currentMotifSignature = newProfile.motifSignature
            lastParameterUpdateNs = System.nanoTime()
            applyTempoToPlayer(newPlayer, newProfile.tempo)
''',
        '''            currentMotifSignature = newProfile.motifSignature
            val transitionNowNs = System.nanoTime()
            lastParameterUpdateNs = transitionNowNs
            tempoEvaluationThrottle.tryAcquire(transitionNowNs, force = true)
            bloomEvaluationThrottle.tryAcquire(transitionNowNs, force = true)
            applyTempoToPlayer(newPlayer, newProfile.tempo)
''',
        "mark evaluation gates on transition",
    )

    replace_once(
        path,
        '''    fun updateTempo(scrollSpeed: Float) {
        synchronized(audioLock) {
            currentScrollSpeed = scrollSpeed.coerceAtLeast(1f)
            val profile = buildLeitmotifPlaybackProfile(
                currentState,
                currentScrollSpeed,
                bloomMusicSignature
            )
            currentMotifSignature = profile.motifSignature
            applyProfileIfNeededLocked(profile, force = false)
        }
    }
''',
        '''    fun updateTempo(scrollSpeed: Float) {
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
''',
        "tempo profile throttle before allocation",
    )

    replace_once(
        path,
        '''    fun playBloom() {
        synchronized(audioLock) {
            bloomMusicSignature = BloomMusicSignature(
                secondsRemaining = GameConstants.BLOOM_DURATION_S,
                conversions = 0
            )
        }
        transitionTo(MusicState.BLOOM)
    }
''',
        '''    fun playBloom() {
        synchronized(audioLock) {
            bloomMusicSignature = defaultBloomMusicSignature
        }
        transitionTo(MusicState.BLOOM)
    }
''',
        "cached Bloom start signature",
    )

    replace_once(
        path,
        '''    fun updateBloomSignature(secondsRemaining: Float, conversions: Int) {
        synchronized(audioLock) {
            val previous = bloomMusicSignature
            bloomMusicSignature = BloomMusicSignature(
                secondsRemaining = secondsRemaining.coerceIn(
                    0f,
                    GameConstants.BLOOM_DURATION_S
                ),
                conversions = conversions.coerceAtLeast(0)
            )
            if (currentState != MusicState.BLOOM) return

            val profile = buildLeitmotifPlaybackProfile(
                MusicState.BLOOM,
                currentScrollSpeed,
                bloomMusicSignature
            )
            currentMotifSignature = profile.motifSignature
            applyProfileIfNeededLocked(
                profile,
                force = previous.conversions != bloomMusicSignature.conversions
            )
        }
    }
''',
        '''    fun updateBloomSignature(secondsRemaining: Float, conversions: Int) {
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
''',
        "Bloom profile throttle before allocation",
    )

    replace_once(
        path,
        '''            currentMotifSignature = buildLeitmotifPlaybackProfile(
                MusicState.MENU,
                GameConstants.BASE_SCROLL_SPEED
            ).motifSignature
            bloomMusicSignature = BloomMusicSignature(
                secondsRemaining = GameConstants.BLOOM_DURATION_S,
                conversions = 0
            )
            previousState = null
''',
        '''            currentMotifSignature = menuLeitmotifProfile.motifSignature
            bloomMusicSignature = defaultBloomMusicSignature
            tempoEvaluationThrottle.reset()
            bloomEvaluationThrottle.reset()
            previousState = null
''',
        "cached destroy reset",
    )

    replace_once(
        path,
        '''    private fun applyProfileIfNeededLocked(
        profile: LeitmotifPlaybackProfile,
        force: Boolean
    ) {
''',
        '''    private fun applyProfileIfNeededLocked(
        profile: LeitmotifPlaybackProfile,
        force: Boolean,
        nowNs: Long
    ) {
''',
        "profile application timestamp argument",
    )

    replace_once(
        path,
        '''        val now = System.nanoTime()
        if (!force && now - lastParameterUpdateNs < PARAMETER_UPDATE_INTERVAL_NS) return
        lastParameterUpdateNs = now
''',
        '''        if (!force && nowNs - lastParameterUpdateNs < PARAMETER_UPDATE_INTERVAL_NS) return
        lastParameterUpdateNs = nowNs
''',
        "reuse evaluation timestamp",
    )

    replace_once(
        path,
        '''internal fun buildLeitmotifPlaybackProfile(
    state: LeitmotifManager.MusicState,
    scrollSpeed: Float,
    bloomSignature: BloomMusicSignature = BloomMusicSignature(
        secondsRemaining = GameConstants.BLOOM_DURATION_S,
        conversions = 0
    )
): LeitmotifPlaybackProfile {
''',
        '''internal fun buildLeitmotifPlaybackProfile(
    state: LeitmotifManager.MusicState,
    scrollSpeed: Float,
    bloomSignature: BloomMusicSignature = defaultBloomMusicSignature
): LeitmotifPlaybackProfile {
''',
        "cached default Bloom signature",
    )

    replace_once(
        path,
        '''        LeitmotifManager.MusicState.MENU -> LeitmotifPlaybackProfile(
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

        LeitmotifManager.MusicState.REST -> LeitmotifPlaybackProfile(
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
''',
        '''        LeitmotifManager.MusicState.MENU -> menuLeitmotifProfile

        LeitmotifManager.MusicState.REST -> restLeitmotifProfile
''',
        "cached Menu and Rest profiles",
    )


if __name__ == "__main__":
    main()
