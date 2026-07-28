#!/usr/bin/env python3
"""Wire display-cutout/system-bar insets into GameView's logical UI space."""

from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: {label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_activity() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/MainActivity.kt")
    replace_once(
        path,
        '''import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
''',
        '''import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
''',
        "AndroidX inset imports",
    )
    replace_once(
        path,
        '''        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        RuntimeAssetValidator.validateRelease(this)
        gameView = GameView(this)
        setContentView(gameView)
        gameView.post {
''',
        '''        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        RuntimeAssetValidator.validateRelease(this)
        gameView = GameView(this)
        setContentView(gameView)
        configureSafeAreaInsets()
        gameView.post {
''',
        "edge-to-edge setup",
    )
    replace_once(
        path,
        '''    private fun hideSystemUI() {
''',
        '''    private fun configureSafeAreaInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(gameView) { _, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            gameView.setSafeAreaInsets(
                left = safe.left,
                top = safe.top,
                right = safe.right,
                bottom = safe.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(gameView)
    }

    private fun hideSystemUI() {
''',
        "safe-area listener",
    )


def patch_game_view() -> None:
    path = Path("app/src/main/java/com/anurag9000/forestrun/engine/GameView.kt")
    replace_once(
        path,
        '''    var screenHeight: Int = 0
        private set

    // -----------------------------------------------------------------------
    // Lifecycle
''',
        '''    var screenHeight: Int = 0
        private set
    private var safeAreaInsets = SafeAreaInsets()
    @Volatile
    private var safeContentTransform = SafeContentTransform.create(1, 1)

    // -----------------------------------------------------------------------
    // Lifecycle
''',
        "safe transform state",
    )
    replace_once(
        path,
        '''            lastTouchX = event.getX(idx)
            lastTouchY = event.getY(idx)

            if (acceptsGameplayInput()) {
''',
        '''            lastTouchX = event.getX(idx)
            lastTouchY = event.getY(idx)
            val logicalTouch = safeContentTransform.toLogical(lastTouchX, lastTouchY)

            if (acceptsGameplayInput()) {
''',
        "inverse touch mapping",
    )
    replace_once(
        path,
        '''                    debugEncounterOverlay?.handleTap(lastTouchX, lastTouchY)?.let { action ->
''',
        '''                    debugEncounterOverlay?.handleTap(logicalTouch.x, logicalTouch.y)?.let { action ->
''',
        "debug overlay touch mapping",
    )
    replace_once(
        path,
        '''                        appState == AppGameState.MENU && ::mainMenuScreen.isInitialized ->
                            mainMenuScreen.onTap(lastTouchX, lastTouchY)
                        appState == AppGameState.GARDEN && ::gardenScreen.isInitialized ->
                            gardenScreen.onTap(lastTouchX, lastTouchY)
''',
        '''                        appState == AppGameState.MENU && ::mainMenuScreen.isInitialized ->
                            mainMenuScreen.onTap(logicalTouch.x, logicalTouch.y)
                        appState == AppGameState.GARDEN && ::gardenScreen.isInitialized ->
                            gardenScreen.onTap(logicalTouch.x, logicalTouch.y)
''',
        "menu and Garden touch mapping",
    )
    replace_once(
        path,
        '''        screenWidth  = width
        screenHeight = height

        // Phase 5: GameStateManager first (owns scroll speed)
''',
        '''        screenWidth  = width
        screenHeight = height
        rebuildSafeContentTransform()

        // Phase 5: GameStateManager first (owns scroll speed)
''',
        "surface-created safe transform",
    )
    replace_once(
        path,
        '''    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth  = width
        screenHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
''',
        '''    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth  = width
        screenHeight = height
        rebuildSafeContentTransform()
    }

    fun setSafeAreaInsets(left: Int, top: Int, right: Int, bottom: Int) {
        safeAreaInsets = SafeAreaInsets(left, top, right, bottom)
        rebuildSafeContentTransform()
    }

    private fun rebuildSafeContentTransform() {
        safeContentTransform = SafeContentTransform.create(
            surfaceWidth = screenWidth.takeIf { it > 0 } ?: width,
            surfaceHeight = screenHeight.takeIf { it > 0 } ?: height,
            insets = safeAreaInsets
        )
    }

    private inline fun drawInSafeContent(canvas: Canvas, drawBlock: () -> Unit) {
        val transform = safeContentTransform
        val checkpoint = canvas.save()
        canvas.translate(transform.contentLeft, transform.contentTop)
        canvas.scale(transform.scale, transform.scale)
        canvas.clipRect(
            0f,
            0f,
            transform.logicalWidth.toFloat(),
            transform.logicalHeight.toFloat()
        )
        try {
            drawBlock()
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
''',
        "safe transform lifecycle and canvas helper",
    )
    replace_once(
        path,
        '''        if (appState == AppGameState.MENU) {
            if (::mainMenuScreen.isInitialized) mainMenuScreen.draw(canvas)
            return
        }

        // Phase 23: GARDEN renders plant meta-loop
        if (appState == AppGameState.GARDEN) {
            if (::gardenScreen.isInitialized) gardenScreen.draw(canvas)
            return
        }
''',
        '''        if (appState == AppGameState.MENU) {
            if (::mainMenuScreen.isInitialized) {
                drawInSafeContent(canvas) { mainMenuScreen.draw(canvas) }
            }
            return
        }

        // Phase 23: GARDEN renders plant meta-loop
        if (appState == AppGameState.GARDEN) {
            if (::gardenScreen.isInitialized) {
                drawInSafeContent(canvas) { gardenScreen.draw(canvas) }
            }
            return
        }
''',
        "menu and Garden safe drawing",
    )
    replace_once(
        path,
        '''            hud.draw(
                canvas = canvas,
                state = gameState,
                bloomPresentation = BloomPresentation.hudPresentation(
                    bloomMeter = gameState.bloomMeter,
                    seedTarget = gameState.bloomSeedTarget,
                    isActive = gameState.isBloomActive,
                    secondsRemaining = gameState.bloomSecondsRemaining,
                    totalConversions = gameState.bloomConversionsThisRun,
                    burstConversions = bloomLastBurstConversions,
                    recentAfterglow = bloomAfterglow
                ),
                openingCue = openingCue
            )
''',
        '''            drawInSafeContent(canvas) {
                hud.draw(
                    canvas = canvas,
                    state = gameState,
                    bloomPresentation = BloomPresentation.hudPresentation(
                        bloomMeter = gameState.bloomMeter,
                        seedTarget = gameState.bloomSeedTarget,
                        isActive = gameState.isBloomActive,
                        secondsRemaining = gameState.bloomSecondsRemaining,
                        totalConversions = gameState.bloomConversionsThisRun,
                        burstConversions = bloomLastBurstConversions,
                        recentAfterglow = bloomAfterglow
                    ),
                    openingCue = openingCue
                )
            }
''',
        "HUD safe drawing",
    )
    replace_once(
        path,
        '''        ) {
            val director = encounterDirector
            debugEncounterOverlay?.draw(
                canvas = canvas,
                director = director ?: return,
                biomeLabel = entityManager.biomeManager.currentBiome.displayName,
                activeEntityCount = entityManager.activeEntities.size,
                bloomText = "${gameState.bloomMeter}/${gameState.bloomSeedTarget}",
                mercyHearts = gameState.mercyHearts,
                kindnessChain = gameState.kindnessChain,
                bloomConversions = gameState.bloomConversionsThisRun
            )
        }
''',
        '''        ) {
            val director = encounterDirector
            if (director != null) {
                drawInSafeContent(canvas) {
                    debugEncounterOverlay?.draw(
                        canvas = canvas,
                        director = director,
                        biomeLabel = entityManager.biomeManager.currentBiome.displayName,
                        activeEntityCount = entityManager.activeEntities.size,
                        bloomText = "${gameState.bloomMeter}/${gameState.bloomSeedTarget}",
                        mercyHearts = gameState.mercyHearts,
                        kindnessChain = gameState.kindnessChain,
                        bloomConversions = gameState.bloomConversionsThisRun
                    )
                }
            }
        }
''',
        "debug overlay safe drawing",
    )
    replace_once(
        path,
        '''            if (::gameOverScreen.isInitialized && ::gameState.isInitialized) {
                gameOverScreen.draw(
                    canvas = canvas,
                    summary = currentRunSummary ?: gameState.buildRunSummary(
                        lastKiller = PersistentMemoryManager.getLastKiller(context),
                        restQuote = currentRestQuote
                    ),
                    isRecovering = runState == RunState.DYING,
                    recoveryProgress = if (runState == RunState.DYING) runResetManager.dyingFraction else 1f
                )
            }
''',
        '''            if (::gameOverScreen.isInitialized && ::gameState.isInitialized) {
                drawInSafeContent(canvas) {
                    gameOverScreen.draw(
                        canvas = canvas,
                        summary = currentRunSummary ?: gameState.buildRunSummary(
                            lastKiller = PersistentMemoryManager.getLastKiller(context),
                            restQuote = currentRestQuote
                        ),
                        isRecovering = runState == RunState.DYING,
                        recoveryProgress = if (runState == RunState.DYING) runResetManager.dyingFraction else 1f
                    )
                }
            }
''',
        "game-over safe drawing",
    )


def main() -> None:
    patch_activity()
    patch_game_view()


if __name__ == "__main__":
    main()
