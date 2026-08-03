package com.anurag9000.forestrun.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.CostumeStyle
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.PlayerState
import com.anurag9000.forestrun.systems.FxPreset
import com.anurag9000.forestrun.systems.GhostFrame
import com.anurag9000.forestrun.systems.GhostPersistenceManager
import com.anurag9000.forestrun.systems.GhostPlayer
import com.anurag9000.forestrun.systems.GhostRecorder
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import com.anurag9000.forestrun.ui.GameOverScreen
import com.anurag9000.forestrun.ui.GardenScreen
import com.anurag9000.forestrun.ui.HUD
import com.anurag9000.forestrun.ui.MainMenuScreen
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.DebugEncounterOverlay
import com.anurag9000.forestrun.ui.DebugOverlayAction
import kotlin.math.hypot

private const val TAG = "ForestRun"

/**
 * The top-level game view.
 *
 * Phases implemented:
 *  - Phase 0: SurfaceView + GameThread scaffold
 *  - Phase 1: 60 FPS loop with nanosecond deltaTime
 *  - Phase 2: [InputHandler] wired + on-screen debug panel
 *  - Phase 3: [Player] physics, state machine, squash/stretch, hitbox
 *  - Phase 4: [ParallaxBackground] 4-layer scroll, floor line
 *  - Phase 5: [GameStateManager] scroll/score/seeds/bloom; [HUD] drawn last
 *  - Phase 6: [SpriteManager] loaded, passed to Player
 *  - Phase 12: [EntityManager] spawner + collision loop live
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    @Volatile
    internal var debugFrameCounter: Long = 0
    private val debugToolsEnabled =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // -----------------------------------------------------------------------
    // Engine
    // -----------------------------------------------------------------------
    private var gameThread: GameThread = GameThread(holder, this)

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------
    val inputHandler = InputHandler()
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // -----------------------------------------------------------------------
    // Phase 3: Player – initialized in surfaceCreated once we know screen size
    // -----------------------------------------------------------------------
    private lateinit var player: Player

    // -----------------------------------------------------------------------
    // Phase 4: Background
    // -----------------------------------------------------------------------
    private lateinit var parallaxBackground: ParallaxBackground

    // -----------------------------------------------------------------------
    // Phase 5: Game state + HUD
    // Phase 6: Sprite Manager
    // Phase 12: Entity Manager
    // Phase 17: GameOverScreen + run-state machine
    // -----------------------------------------------------------------------
    private lateinit var gameState: GameStateManager
    private lateinit var hud: HUD
    private lateinit var spriteManager: SpriteManager
    private lateinit var entityManager: EntityManager
    private lateinit var gameOverScreen: GameOverScreen

    // ── Run State (Phase 17) ──────────────────────────────────────────────
    @Volatile
    private var runState: RunState = RunState.PLAYING
    private val runResetManager    = RunResetManager()

    @Volatile
    internal var runMode: RunMode = RunMode.NORMAL
        private set

    // ── App Game State (Phase 22) ─────────────────────────────────────────
    /** Active screen state; Bloom and rest/death have separate owners. */
    @Volatile
    private var appState: AppGameState = AppGameState.MENU
    private lateinit var mainMenuScreen: MainMenuScreen
    private lateinit var gardenScreen: GardenScreen
    private var currentRestQuote: String = "The forest is waiting for a cleaner run."
    private var currentRunSummary: RunSummary? = null
    private var surfacedMercyTier = 0
    private var surfacedKindnessTier = 0
    private var surfacedCleanTier = 0
    private val encounterDirector = if (debugToolsEnabled) EncounterDirector() else null
    private var debugEncounterOverlay: DebugEncounterOverlay? = null
    private var pendingDebugLaunchIntent: Intent? = null
    private var debugScenarioVisualsEnabled = true
    private val debugScenarioScript = DebugScenarioScript()

    // Restart fade-to-black overlay
    private val restartFadePaint = Paint().apply { color = Color.BLACK }

    // Screen-flash overlay for MERCY_MISS (green border pulse)
    private var mercyFlashTimer = 0f
    private val mercyFlashDuration = 0.3f
    private val mercyFlashPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.argb(200, 60, 240, 80)
    }

    // Phase 13: Night/dusk ambient darkness overlay
    private val ambientOverlayPaint = Paint().apply { color = Color.BLACK }
    private var bloomScreenPulse = 0f
    private var bloomActivationFlash = 0f
    private var bloomAfterglowTimer = 0f
    private var bloomSessionConversionBase = 0
    private var bloomLastBurstConversions = 0
    private var bloomReadyAnnounced = false
    private var bloomLastAudioConversionCount = 0
    private var bloomPowerSurgeTimer = 0f
    private var bloomPowerTier = 0
    private var bloomPowerSurgeStrength = 0f
    private val bloomScreenPaint = Paint().apply {
        color = Color.argb(0, 255, 204, 96)
    }
    private val bloomGlowPaint = Paint().apply {
        color = Color.argb(0, 255, 228, 170)
    }
    private val bloomFramePaint = Paint().apply {
        color = Color.argb(0, 255, 232, 170)
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }
    private val bloomInnerFramePaint = Paint().apply {
        color = Color.argb(0, 255, 246, 214)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val bloomFlashPaint = Paint().apply {
        color = Color.argb(0, 255, 245, 220)
    }
    private val bloomAfterglowPaint = Paint().apply {
        color = Color.argb(0, 255, 220, 176)
    }
    private val debugHitboxPaint = Paint().apply {
        color = Color.argb(210, 255, 96, 96)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val debugPlayerHitboxPaint = Paint().apply {
        color = Color.argb(210, 110, 210, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val debugLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        typeface = Typeface.MONOSPACE
    }

    // ── Phase 19: Ghost Run ───────────────────────────────────────────────
    private val ghostRecorder = GhostRecorder()
    private val runOutcomePersistence =
        RunOutcomePersistenceCoordinator(AndroidRunOutcomePersistenceSink(context))
    private val terminalHitImpact = TerminalHitImpactCoordinator(
        effects = GameViewTerminalHitImpactEffects()
    )
    private val terminalHitOutcome = TerminalHitOutcomeCoordinator(
        relationshipRecorder = AndroidTerminalHitRelationshipRecorder(context),
        feedbackPresenter = AndroidTerminalHitFeedbackPresenter(context),
        restQuoteResolver = AndroidTerminalHitRestQuoteResolver(context),
        outcomeCommitter = runOutcomePersistence
    )
    private val nonTerminalCollisionOutcome = NonTerminalCollisionOutcomeCoordinator(
        effects = GameViewNonTerminalCollisionEffects(),
        relationshipRecorder = AndroidNonTerminalCollisionRelationshipRecorder(context),
        feedbackPresenter = AndroidNonTerminalCollisionFeedbackPresenter(context)
    )
    private val collisionOutcomeDispatcher = CollisionOutcomeDispatcher(
        terminalHitImpact = terminalHitImpact,
        terminalHitOutcome = terminalHitOutcome,
        nonTerminalCollisionOutcome = nonTerminalCollisionOutcome
    )
    private val ghostPlayer   = GhostPlayer()
    private val ghostHazardFocusRect = RectF()
    private val reusableGhostVisibilityContext = GhostPlayer.VisibilityContext(
        livePlayerX = 0f,
        livePlayerY = 0f,
        livePlayerWidth = Player.BASE_WIDTH,
        livePlayerHeight = Player.BASE_HEIGHT,
        nearbyHazardCount = 0,
        nearestHazardDistancePx = Float.POSITIVE_INFINITY
    )

    // -----------------------------------------------------------------------
    // Paint objects – created ONCE, never inside draw()
    // -----------------------------------------------------------------------
    private val bgPaint = Paint().apply {
        color = Color.BLACK; style = Paint.Style.FILL
    }
    private val cinematicOverlay = CinematicOverlayRenderer()
    private val reusableRunLighting = RunLightingIdentity()
    private val reusableRunCinematicProfile = CinematicPolishProfile()
    private val reusableBloomPowerState = BloomPowerPresentationState()
    private val reusableBloomHudPresentation = BloomHudPresentation()

    // -----------------------------------------------------------------------
    // Screen dimensions
    // -----------------------------------------------------------------------
    var screenWidth:  Int = 0
        private set
    var screenHeight: Int = 0
        private set
    private var safeAreaInsets = SafeAreaInsets()
    @Volatile
    private var safeContentTransform = SafeContentTransform.create(1, 1)

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    init {
        holder.addCallback(this)
        setOnTouchListener { view, event ->
            val idx = event.actionIndex.coerceAtLeast(0)
            lastTouchX = event.getX(idx)
            lastTouchY = event.getY(idx)
            val logicalTouch = safeContentTransform.toLogical(lastTouchX, lastTouchY)

            if (acceptsGameplayInput()) {
                if (debugToolsEnabled &&
                    event.actionMasked == android.view.MotionEvent.ACTION_UP
                ) {
                    debugEncounterOverlay?.handleTap(logicalTouch.x, logicalTouch.y)?.let { action ->
                        handleDebugOverlayAction(action)
                        return@setOnTouchListener true
                    }
                }
                inputHandler.onTouch(view, event)
            } else {
                inputHandler.cancelActiveGesture()
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    view.performClick()
                    when {
                        appState == AppGameState.MENU && ::mainMenuScreen.isInitialized ->
                            mainMenuScreen.onTap(logicalTouch.x, logicalTouch.y)
                        appState == AppGameState.GARDEN && ::gardenScreen.isInitialized ->
                            gardenScreen.onTap(logicalTouch.x, logicalTouch.y)
                        runState == RunState.GAME_OVER ->
                            runState = runResetManager.beginRestart()
                    }
                }
                true
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth  = width
        screenHeight = height
        rebuildSafeContentTransform()
        if (!::gameState.isInitialized) gameState = GameStateManager(context)
        if (!::parallaxBackground.isInitialized) parallaxBackground = ParallaxBackground(screenWidth, screenHeight)
        if (!::spriteManager.isInitialized) spriteManager = SpriteManager(context)
        if (!::entityManager.isInitialized) {
            entityManager = EntityManager(context, screenWidth.toFloat(), screenHeight.toFloat(), spriteManager)
        }
        FlavorTextManager.init(context)
        DialogueBubbleManager.init(context)
        if (!::gameOverScreen.isInitialized) gameOverScreen = GameOverScreen(context, screenWidth, screenHeight)
        if (!ghostPlayer.hasGhost) {
            val frames = SaveManager.loadGhostRun(context)
            if (frames.isNotEmpty()) ghostPlayer.load(frames)
        }
        LeitmotifManager.init(context)
        SfxManager.init(context)
        if (appState == AppGameState.MENU) LeitmotifManager.transitionTo(LeitmotifManager.MusicState.MENU)
        else LeitmotifManager.playRunStart()
        HapticManager.init(context)
        if (!::mainMenuScreen.isInitialized) {
            mainMenuScreen = MainMenuScreen(context, spriteManager, screenWidth, screenHeight)
            mainMenuScreen.onGardenTap = {
                if (::gardenScreen.isInitialized) gardenScreen.refresh()
                appState = AppGameState.GARDEN
            }
        }
        if (!::gardenScreen.isInitialized) {
            gardenScreen = GardenScreen(context, spriteManager, screenWidth, screenHeight)
            gardenScreen.onBack = {
                if (::mainMenuScreen.isInitialized) {
                    mainMenuScreen.resetRitual()
                    mainMenuScreen.refreshCopy()
                }
                appState = AppGameState.MENU
            }
            gardenScreen.onRun = {
                prepareFreshRun()
                appState = AppGameState.PLAYING
            }
            gardenScreen.load()
        }
        if (!::hud.isInitialized) hud = HUD(context, screenWidth, screenHeight)
        if (debugToolsEnabled && debugEncounterOverlay == null) debugEncounterOverlay = DebugEncounterOverlay(screenWidth)
        if (!::player.isInitialized) {
            player = Player(screenWidth, screenHeight, spriteManager, parallaxBackground.groundY)
            player.setCostume(CostumeManager.activeCostume(context))
            wirePlayerToInput()
        }
        gameThread.isRunning = true
        if (gameThread.state == Thread.State.NEW) gameThread.start()
        pendingDebugLaunchIntent?.let {
            pendingDebugLaunchIntent = null
            post { applyDebugLaunchIntent(it) }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
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
        canvas.clipRect(0f, 0f, transform.logicalWidth.toFloat(), transform.logicalHeight.toFloat())
        try { drawBlock() } finally { canvas.restoreToCount(checkpoint) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) { stopThread() }

    fun pause() {
        stopThread()
        LeitmotifManager.pause()
        if (::gameState.isInitialized && runMode.persistsProgress) gameState.save()
    }

    fun resume() {
        LeitmotifManager.resume()
        gameThread = GameThread(holder, this)
        if (holder.surface?.isValid == true) {
            gameThread.isRunning = true
            gameThread.start()
        }
        pendingDebugLaunchIntent?.let {
            pendingDebugLaunchIntent = null
            post { applyDebugLaunchIntent(it) }
        }
    }

    fun applyDebugLaunchIntent(intent: Intent?) {
        if (!debugToolsEnabled || intent == null) return
        val scenarioName = intent.getStringExtra(com.anurag9000.forestrun.MainActivity.EXTRA_DEBUG_SCENARIO)
        val requestedRunMode = intent.getStringExtra(com.anurag9000.forestrun.MainActivity.EXTRA_RUN_MODE)
        val autoStart = intent.getBooleanExtra(com.anurag9000.forestrun.MainActivity.EXTRA_DEBUG_AUTOSTART, false)
        if (scenarioName.isNullOrBlank() && !autoStart) return
        if (!::mainMenuScreen.isInitialized || !::entityManager.isInitialized ||
            !::player.isInitialized || !::gameState.isInitialized || holder.surface?.isValid != true
        ) {
            pendingDebugLaunchIntent = Intent(intent)
            postDelayed({ applyDebugLaunchIntent(intent) }, 100L)
            return
        }
        if (!scenarioName.isNullOrBlank()) {
            val director = encounterDirector ?: return
            val scenario = EncounterScenario.entries.firstOrNull { it.name == scenarioName } ?: return
            runMode = RunMode.forScenario(requestedRunMode)
            debugScenarioVisualsEnabled = false
            debugScenarioScript.prepare(scenario)
            if (scenario == EncounterScenario.GHOST_READABILITY) {
                ghostPlayer.load(listOf(
                    GhostFrame(0.00f, 520f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f),
                    GhostFrame(0.35f, 560f, 780f, PlayerState.JUMPING.ordinal, 0.96f, 1.04f),
                    GhostFrame(0.72f, 610f, 710f, PlayerState.APEX.ordinal, 0.92f, 1.08f),
                    GhostFrame(1.05f, 660f, 790f, PlayerState.FALLING.ordinal, 1f, 1f),
                    GhostFrame(1.42f, 720f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f),
                    GhostFrame(1.80f, 780f, 790f, PlayerState.JUMPING.ordinal, 0.96f, 1.04f),
                    GhostFrame(2.15f, 840f, 860f, PlayerState.RUNNING.ordinal, 1f, 1f)
                ), revealImmediately = true)
            }
            director.selectScenario(scenario)
            appState = AppGameState.PLAYING
            runState = RunState.PLAYING
            prepareEncounterScenario()
            return
        }
        if (autoStart) {
            runMode = RunMode.NORMAL
            debugScenarioVisualsEnabled = false
            debugScenarioScript.clear()
            prepareFreshRun()
            appState = AppGameState.PLAYING
            runState = RunState.PLAYING
        }
    }

    private fun stopThread() {
        if (!gameThread.requestStopAndAwait()) Log.w(TAG, "GameThread did not terminate within the 1 second shutdown bound")
    }

    private fun acceptsGameplayInput(): Boolean =
        appState == AppGameState.PLAYING && runState == RunState.PLAYING && ::player.isInitialized

    private fun wirePlayerToInput() {
        inputHandler.onJumpPressed = {
            if (acceptsGameplayInput()) {
                if (::gameState.isInitialized) gameState.recordJumpInput()
                player.onJumpPressed()
            }
        }
        inputHandler.onJumpHeld = { holdSec ->
            if (acceptsGameplayInput()) {
                if (::gameState.isInitialized) gameState.recordJumpHold(holdSec)
                player.onJumpHeld(holdSec)
            }
        }
        inputHandler.onJumpReleased = { holdSec ->
            if (acceptsGameplayInput()) {
                if (::gameState.isInitialized) gameState.recordJumpHold(holdSec)
                player.onJumpReleased(holdSec)
            }
        }
        inputHandler.onDuckPressed = {
            if (acceptsGameplayInput()) {
                if (::gameState.isInitialized) gameState.recordDuckInput()
                player.onDuckPressed()
            }
        }
        inputHandler.onDuckReleased = { if (acceptsGameplayInput()) player.onDuckReleased() }
    }

    fun update(deltaTime: Float) {
        if (!FrameInputAdmission.acceptsDelta(deltaTime)) return
        updateBounded(FrameInputAdmission.boundedDeltaSeconds(deltaTime))
    }

    private fun updateBounded(deltaTime: Float) {
        debugFrameCounter++
        CameraSystem.update(deltaTime)
        if (acceptsGameplayInput()) inputHandler.tick(deltaTime) else inputHandler.cancelActiveGesture()
        if (!::gameState.isInitialized) return
        if (!gameState.isBloomActive && gameState.bloomMeter >= gameState.bloomSeedTarget - 1 && !bloomReadyAnnounced) {
            bloomReadyAnnounced = true
            SfxManager.playBloomReady()
        } else if (!gameState.isBloomActive && gameState.bloomMeter < gameState.bloomSeedTarget - 1) bloomReadyAnnounced = false
        bloomScreenPulse = if (gameState.isBloomActive) bloomScreenPulse + deltaTime * 4.8f else 0f
        if (bloomActivationFlash > 0f) bloomActivationFlash = (bloomActivationFlash - deltaTime).coerceAtLeast(0f)
        if (bloomAfterglowTimer > 0f) bloomAfterglowTimer = (bloomAfterglowTimer - deltaTime).coerceAtLeast(0f)
        if (bloomPowerSurgeTimer > 0f) bloomPowerSurgeTimer = (bloomPowerSurgeTimer - deltaTime).coerceAtLeast(0f)
        if (appState == AppGameState.MENU) {
            if (::mainMenuScreen.isInitialized) {
                mainMenuScreen.update(deltaTime)
                if (mainMenuScreen.consumeStartRunRequest()) {
                    prepareFreshRun()
                    appState = AppGameState.PLAYING
                }
            }
            return
        }
        if (appState == AppGameState.GARDEN) {
            if (::gardenScreen.isInitialized) gardenScreen.update(deltaTime)
            return
        }
        when (runState) {
            RunState.DYING -> {
                val next = runResetManager.update(deltaTime, runState)
                if (next == RunState.GAME_OVER) runState = RunState.GAME_OVER
                ParticleManager.update(deltaTime)
                FlavorTextManager.update(deltaTime)
                DialogueBubbleManager.update(deltaTime)
                if (::gameOverScreen.isInitialized) gameOverScreen.update(deltaTime)
                return
            }
            RunState.GAME_OVER -> {
                if (::gameOverScreen.isInitialized) gameOverScreen.update(deltaTime)
                return
            }
            RunState.RESTARTING -> {
                val next = runResetManager.update(deltaTime, runState)
                restartFadePaint.alpha = runResetManager.restartFadeAlpha
                if (next == RunState.PLAYING && runResetManager.restartFadeAlpha >= 255) {
                    if (::entityManager.isInitialized && ::player.isInitialized && ::gameState.isInitialized) {
                        runResetManager.executeReset(gameState, entityManager, player)
                        ghostRecorder.reset()
                        reloadGhost()
                    }
                    if (::gardenScreen.isInitialized) gardenScreen.refresh()
                    appState = AppGameState.GARDEN
                    LeitmotifManager.transitionTo(LeitmotifManager.MusicState.MENU)
                    runState = RunState.PLAYING
                }
                return
            }
            RunState.PLAYING -> Unit
        }
        gameState.update(deltaTime)
        if (::parallaxBackground.isInitialized) {
            parallaxBackground.setBloomState(gameState.isBloomActive, bloomActivationFlash / 0.32f, bloomAfterglowFraction())
            parallaxBackground.update(deltaTime, gameState.scrollSpeed)
        }
        if (::hud.isInitialized) hud.update(deltaTime, gameState)
        if (::entityManager.isInitialized) {
            entityManager.biomeManager.update(gameState.distanceMetres)
            gameState.updatePacifistBiome(entityManager.biomeManager.currentBiome)
            if (::parallaxBackground.isInitialized) {
                val bm = entityManager.biomeManager
                parallaxBackground.applyBiomeScene(bm.currentBiome)
                parallaxBackground.applyBiomeColours(bm.currentSkyTop, bm.currentSkyBottom, bm.currentGround, bm.currentFoliage)
            }
        }
        if (!::player.isInitialized) return
        if (gameState.isBloomActive && !player.isInvincible) {
            player.activateBloom()
            bloomSessionConversionBase = gameState.bloomConversionsThisRun
            bloomLastBurstConversions = 0
            bloomLastAudioConversionCount = gameState.bloomConversionsThisRun
            bloomAfterglowTimer = 0f
            bloomPowerSurgeTimer = 0f
            bloomPowerTier = 0
            bloomPowerSurgeStrength = 0f
            bloomReadyAnnounced = false
            LeitmotifManager.playBloom()
            SfxManager.playBloomActivate()
            HapticManager.bloomSurge()
            CameraSystem.shakeBloom()
            bloomActivationFlash = 0.32f
            player.setBloomPowerPresentation(0.02f, 112)
            ParticleManager.emit(FxPreset.BLOOM_ACTIVATE, player.x + Player.BASE_WIDTH / 2f, player.y + Player.BASE_HEIGHT / 2f)
            ParticleManager.emit(FxPreset.BLOOM_WORLD_BURST, player.x + Player.BASE_WIDTH / 2f, player.y + Player.BASE_HEIGHT * 0.35f)
        } else if (!gameState.isBloomActive && player.isInvincible) {
            player.deactivateBloom()
            bloomLastBurstConversions = (gameState.bloomConversionsThisRun - bloomSessionConversionBase).coerceAtLeast(0)
            bloomAfterglowTimer = if (bloomLastBurstConversions >= 3) 1.8f else 1.2f
            bloomPowerSurgeTimer = 0f
            bloomPowerTier = 0
            bloomPowerSurgeStrength = 0f
            ParticleManager.emit(
                if (bloomLastBurstConversions > 0) FxPreset.BLOOM_WORLD_BURST else FxPreset.BLOOM_CONVERT,
                player.x + Player.BASE_WIDTH / 2f,
                player.y + Player.BASE_HEIGHT * if (bloomLastBurstConversions > 0) 0.38f else 0.44f
            )
            SfxManager.playBloomFade(bloomLastBurstConversions)
            LeitmotifManager.endBloom(gameState.distanceMetres)
        }
        if (gameState.isBloomActive) {
            val liveBurstConversions = (gameState.bloomConversionsThisRun - bloomSessionConversionBase).coerceAtLeast(0)
            val powerState = BloomPowerPresentation.resolveInto(reusableBloomPowerState, gameState.bloomSecondsRemaining, liveBurstConversions, bloomPowerSurgeFraction())
            player.setBloomPowerPresentation(powerState.playerScaleBoost, powerState.auraAlpha)
            bloomPowerTier = powerState.tier
            bloomPowerSurgeStrength = powerState.surgeStrength
            bloomLastBurstConversions = liveBurstConversions
            LeitmotifManager.updateBloomSignature(gameState.bloomSecondsRemaining, liveBurstConversions)
            val audioConversions = gameState.bloomConversionsThisRun
            if (audioConversions > bloomLastAudioConversionCount) {
                repeat(audioConversions - bloomLastAudioConversionCount) { offset ->
                    SfxManager.playBloomConvert((audioConversions - bloomSessionConversionBase - offset).coerceAtLeast(1))
                }
                bloomPowerSurgeTimer = 0.55f
                CameraSystem.shakeBloomChain(powerState.tier)
                ParticleManager.emit(
                    if (powerState.tier >= 2) FxPreset.BLOOM_WORLD_BURST else FxPreset.BLOOM_CONVERT,
                    player.x + Player.BASE_WIDTH / 2f,
                    player.y + Player.BASE_HEIGHT * if (powerState.tier >= 2) 0.32f else 0.38f
                )
                if (powerState.tier >= 3) bloomActivationFlash = maxOf(bloomActivationFlash, 0.18f)
                bloomLastAudioConversionCount = audioConversions
            }
        } else player.setBloomPowerPresentation(0f, 0)
        player.update(deltaTime, gameState.scrollSpeed)
        if (::entityManager.isInitialized) {
            entityManager.update(deltaTime, gameState, player, encounterDirector, runMode)
            val collision = entityManager.checkCollisions(player, gameState)
            if (collision != null) {
                val persistEncounter = collision.entity.shouldRecordPersistence && runMode.persistsProgress
                val completedHit = collisionOutcomeDispatcher.dispatch(
                    result = collision.result,
                    hit = {
                        HitCollisionDispatch(
                            persistEncounter = persistEncounter,
                            captureAfterImpact = {
                                val completedGhost = ghostRecorder.detachSnapshot()
                                val killerType = entityManager.entityTypeOf(collision.entity)
                                TerminalHitImpactCapture(
                                    killerType = killerType,
                                    biome = entityManager.biomeManager.currentBiome,
                                    presentation = TerminalHitPresentation(
                                        killerType = killerType,
                                        routeTier = gameState.pacifistRouteTier,
                                        playerX = player.x,
                                        playerY = player.y
                                    ),
                                    completedGhost = completedGhost
                                )
                            },
                            buildSummaryPreview = { killerType ->
                                gameState.buildRunSummary(lastKiller = killerType)
                            }
                        )
                    },
                    stumble = {
                        val killerType = entityManager.entityTypeOf(collision.entity)
                        val dominantColor = if (::entityManager.isInitialized) {
                            entityManager.biomeManager.currentFoliage
                        } else Color.rgb(255, 180, 200)
                        StumbleCollisionDispatch(
                            input = StumbleCollisionOutcome(
                                killerType = killerType,
                                routeTier = gameState.pacifistRouteTier,
                                playerX = player.x,
                                playerY = player.y,
                                dominantColor = dominantColor,
                                persistEncounter = persistEncounter
                            ),
                            deactivateEntity = {
                                collision.entity.isActive = false
                            }
                        )
                    },
                    mercyMiss = {
                        MercyMissCollisionOutcome(
                            entityType = entityManager.entityTypeOf(collision.entity),
                            routeTier = gameState.pacifistRouteTier,
                            mercyHearts = gameState.mercyHearts,
                            kindnessChain = gameState.kindnessChain,
                            playerX = player.x,
                            playerY = player.y
                        )
                    }
                )
                if (completedHit != null) {
                    currentRestQuote = completedHit.summary.restQuote
                    currentRunSummary = completedHit.summary
                    if (::gameState.isInitialized) runResetManager.triggerDeath(gameState)
                    runState = RunState.DYING
                }
            }
            if (mercyFlashTimer > 0f) mercyFlashTimer -= deltaTime
        }
        if (::player.isInitialized && runMode.recordsGhost) ghostRecorder.record(deltaTime, player)
        if (shouldDrawGhostPlayback()) ghostPlayer.update(deltaTime, ghostVisibilityContext())
        runDebugScenarioScript()
        if (::gameState.isInitialized) {
            LeitmotifManager.updateDistance(gameState.distanceMetres)
            LeitmotifManager.updateTempo(gameState.scrollSpeed)
            gameState.consumePacifistReward()?.let { reward ->
                gameState.addBonus(reward.points, reward.seeds)
                if (runMode.persistsProgress) reward.friendBiome?.let { PersistentMemoryManager.recordBiomeFriendship(context, it) }
                val rewardCue = PacifistPresentation.rewardCue(reward)
                ParticleManager.emit(FxPreset.MERCY_STARS, player.x + Player.BASE_WIDTH * 0.5f, player.y + Player.BASE_HEIGHT * 0.42f)
                DialogueBubbleManager.spawn(rewardCue.bubbleText, player.x + Player.BASE_WIDTH * 0.5f, player.y - 28f, rewardCue.fillColor, rewardCue.borderColor)
                FlavorTextManager.spawn(rewardCue.flavorText, player.x + Player.BASE_WIDTH * 0.18f, player.y - 6f, rewardCue.flavorColor, 1.2f, rewardCue.flavorSize)
            }
            if (gameState.consumeMilestone()) {
                HapticManager.mediumPulse()
                CameraSystem.addTrauma(0.3f)
                val milestoneCue = RunFlavorPresentation.milestoneCue(context, gameState.score, gameState.pacifistRouteTier, gameState.isNewHighScore)
                DialogueBubbleManager.spawn(milestoneCue.bubbleText, player.x + Player.BASE_WIDTH * 0.5f, player.y - 28f, milestoneCue.fillColor, milestoneCue.borderColor)
                FlavorTextManager.spawn(milestoneCue.flavorText, player.x + Player.BASE_WIDTH * 0.20f, player.y - 8f, milestoneCue.flavorColor, 1.1f, milestoneCue.flavorSize)
            }
            emitOrdinaryProgressCues()
        }
        FlavorTextManager.update(deltaTime)
        DialogueBubbleManager.update(deltaTime)
        ParticleManager.update(deltaTime)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (appState == AppGameState.MENU) {
            if (::mainMenuScreen.isInitialized) drawInSafeContent(canvas) { mainMenuScreen.draw(canvas) }
            return
        }
        if (appState == AppGameState.GARDEN) {
            if (::gardenScreen.isInitialized) drawInSafeContent(canvas) { gardenScreen.draw(canvas) }
            return
        }
        CameraSystem.applyTo(canvas) {
            if (::parallaxBackground.isInitialized) parallaxBackground.draw(canvas)
            if (::entityManager.isInitialized) {
                entityManager.draw(canvas)
                val bloomFrac = if (::gameState.isInitialized) gameState.bloomMeterFraction else 0f
                entityManager.drawOrbs(canvas, bloomFrac)
            }
            if (::spriteManager.isInitialized && shouldDrawGhostPlayback()) ghostPlayer.draw(canvas, spriteManager)
            if (::player.isInitialized) player.draw(canvas)
            if (debugToolsEnabled && debugScenarioVisualsEnabled && encounterDirector?.isScenarioActive == true) drawDebugScenarioLayer(canvas)
            DialogueBubbleManager.draw(canvas)
            FlavorTextManager.draw(canvas)
            ParticleManager.draw(canvas)
        }
        if (mercyFlashTimer > 0f) {
            mercyFlashPaint.alpha = ((mercyFlashTimer / mercyFlashDuration) * 200).toInt().coerceIn(0, 200)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mercyFlashPaint)
        }
        if (::entityManager.isInitialized) {
            val ambient = entityManager.biomeManager.ambientAlpha
            if (ambient > 0) {
                ambientOverlayPaint.alpha = ambient
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), ambientOverlayPaint)
            }
        }
        val bloomAfterglow = bloomAfterglowFraction()
        if (::gameState.isInitialized && (gameState.isBloomActive || bloomAfterglow > 0f)) {
            val pulse = 0.55f + 0.45f * kotlin.math.sin(bloomScreenPulse)
            val powerBoost = if (gameState.isBloomActive) bloomPowerSurgeStrength * (0.22f + bloomPowerTier * 0.08f) else 0f
            val bloomStrength = if (gameState.isBloomActive) 1f + powerBoost else 0.38f * bloomAfterglow
            bloomScreenPaint.alpha = (78f + 72f * pulse * bloomStrength).toInt().coerceIn(0, 255)
            bloomGlowPaint.alpha = (32f + 52f * pulse * (bloomStrength + powerBoost * 0.3f)).toInt().coerceIn(0, 255)
            bloomFramePaint.alpha = (160f + 82f * pulse * (bloomStrength + powerBoost * 0.45f)).toInt().coerceIn(0, 255)
            bloomInnerFramePaint.alpha = (120f + 92f * pulse * (bloomStrength + powerBoost * 0.55f)).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bloomScreenPaint)
            canvas.drawRect(14f, 14f, width.toFloat() - 14f, height.toFloat() - 14f, bloomGlowPaint)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bloomFramePaint)
            canvas.drawRect(22f, 22f, width.toFloat() - 22f, height.toFloat() - 22f, bloomInnerFramePaint)
            if (gameState.isBloomActive && bloomPowerTier >= 2) {
                bloomFlashPaint.alpha = (18f + 26f * bloomPowerSurgeStrength).toInt().coerceIn(0, 90)
                canvas.drawRect(34f, height * 0.12f, width.toFloat() - 34f, height * 0.88f, bloomFlashPaint)
            }
            if (!gameState.isBloomActive && bloomAfterglow > 0f) {
                bloomAfterglowPaint.alpha = (28f + 52f * bloomAfterglow).toInt().coerceIn(0, 120)
                canvas.drawRect(0f, height * 0.08f, width.toFloat(), height * 0.92f, bloomAfterglowPaint)
            }
        }
        if (bloomActivationFlash > 0f) {
            bloomFlashPaint.alpha = ((bloomActivationFlash / 0.32f) * 170f).toInt().coerceIn(0, 170)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bloomFlashPaint)
        }
        if (::gameState.isInitialized) {
            val motif = LeitmotifManager.currentMotifSignature()
            val nightFactor = if (::entityManager.isInitialized) (entityManager.biomeManager.ambientAlpha / 255f).coerceIn(0f, 1f) else 0f
            val runLighting = resolveRunLightingIdentity(reusableRunLighting, nightFactor, if (gameState.isBloomActive) 1f else bloomAfterglow * 0.55f)
            cinematicOverlay.draw(
                canvas,
                width.toFloat(),
                height.toFloat(),
                resolveCinematicPolishProfile(reusableRunCinematicProfile, CinematicScene.RUN, ((motif.cadenceLift + motif.shimmer) * 0.5f).coerceIn(0f, 1f), if (gameState.isBloomActive) 1f else bloomAfterglow * 0.55f),
                debugFrameCounter / 60f,
                runLighting.horizonGlowColor,
                0.47f
            )
        }
        if (::hud.isInitialized && ::gameState.isInitialized) {
            val openingCue = if (encounterDirector?.isScenarioActive == true && encounterDirector.activeScenario != EncounterScenario.OPENING_READABILITY) null else gameState.openingGuidanceCue
            drawInSafeContent(canvas) {
                hud.draw(canvas, gameState, BloomPresentation.resolveInto(reusableBloomHudPresentation, gameState.bloomMeter, gameState.bloomSeedTarget, gameState.isBloomActive, gameState.bloomSecondsRemaining, gameState.bloomConversionsThisRun, bloomLastBurstConversions, bloomAfterglow), openingCue)
            }
        }
        if (debugToolsEnabled && debugScenarioVisualsEnabled && ::entityManager.isInitialized && ::gameState.isInitialized &&
            debugEncounterOverlay != null && appState == AppGameState.PLAYING && runState == RunState.PLAYING
        ) {
            encounterDirector?.let { director ->
                drawInSafeContent(canvas) {
                    debugEncounterOverlay?.draw(canvas, director, entityManager.biomeManager.currentBiome.displayName, entityManager.activeEntities.size, "${gameState.bloomMeter}/${gameState.bloomSeedTarget}", gameState.mercyHearts, gameState.kindnessChain, gameState.bloomConversionsThisRun)
                }
            }
        }
        if (runState == RunState.GAME_OVER || runState == RunState.DYING) {
            if (::gameOverScreen.isInitialized && ::gameState.isInitialized) {
                drawInSafeContent(canvas) {
                    gameOverScreen.draw(canvas, currentRunSummary ?: gameState.buildRunSummary(PersistentMemoryManager.getLastKiller(context), currentRestQuote), runState == RunState.DYING, if (runState == RunState.DYING) runResetManager.dyingFraction else 1f)
                }
            }
        }
        if (runState == RunState.RESTARTING) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), restartFadePaint)
    }

    private fun prepareFreshRun() {
        runMode = RunMode.NORMAL
        encounterDirector?.stopScenario()
        debugScenarioScript.clear()
        if (!::entityManager.isInitialized || !::player.isInitialized || !::gameState.isInitialized) return
        runOutcomePersistence.resetForNewRun()
        player.setCostume(CostumeManager.activeCostume(context))
        runResetManager.executeReset(gameState, entityManager, player)
        entityManager.biomeManager.forceDebugBiome(null)
        entityManager.seedOpeningSequence()
        ghostRecorder.reset()
        reloadGhost()
        currentRestQuote = "The forest is waiting for a cleaner run."
        currentRunSummary = null
        resetBloomPresentationState()
        resetOrdinaryProgressCueState()
        LeitmotifManager.playRunStart()
    }

    private fun emitOrdinaryProgressCues() {
        if (!::gameState.isInitialized || !::player.isInitialized || !runMode.allowsOrdinaryProgressCues) return
        val mercyTier = progressTier(gameState.mercyHearts, 2, 4, 6)
        if (mercyTier > surfacedMercyTier) {
            surfacedMercyTier = mercyTier
            spawnOrdinaryProgressCue(RunFlavorPresentation.ordinaryProgressCue("mercy", gameState.mercyHearts, gameState.pacifistRouteTier))
        }
        val kindnessTier = progressTier(gameState.kindnessChain, 3, 5, 8)
        if (kindnessTier > surfacedKindnessTier) {
            surfacedKindnessTier = kindnessTier
            spawnOrdinaryProgressCue(RunFlavorPresentation.ordinaryProgressCue("kindness", gameState.kindnessChain, gameState.pacifistRouteTier))
        }
        val cleanTier = progressTier(gameState.cleanPassesThisRun, 4, 8, 12)
        if (cleanTier > surfacedCleanTier) {
            surfacedCleanTier = cleanTier
            spawnOrdinaryProgressCue(RunFlavorPresentation.ordinaryProgressCue("clean", gameState.cleanPassesThisRun, gameState.pacifistRouteTier))
        }
    }

    private fun spawnOrdinaryProgressCue(cue: RunFlavorCue) {
        DialogueBubbleManager.spawn(cue.bubbleText, player.x + Player.BASE_WIDTH * 0.5f, player.y - 30f, cue.fillColor, cue.borderColor)
        FlavorTextManager.spawn(cue.flavorText, player.x + Player.BASE_WIDTH * 0.18f, player.y - 10f, cue.flavorColor, 1.05f, cue.flavorSize)
    }

    private fun resetOrdinaryProgressCueState() {
        surfacedMercyTier = 0
        surfacedKindnessTier = 0
        surfacedCleanTier = 0
    }

    private fun progressTier(value: Int, firstThreshold: Int, secondThreshold: Int, thirdThreshold: Int): Int = when {
        value >= thirdThreshold -> 3
        value >= secondThreshold -> 2
        value >= firstThreshold -> 1
        else -> 0
    }

    private fun prepareEncounterScenario() {
        val director = encounterDirector ?: return
        if (!::entityManager.isInitialized || !::player.isInitialized || !::gameState.isInitialized) return
        runOutcomePersistence.resetForNewRun()
        if (runMode == RunMode.NORMAL) runMode = RunMode.DEBUG_SCENARIO
        val scenario = director.selectedScenario
        debugScenarioScript.prepare(scenario)
        player.setCostume(CostumeManager.activeCostume(context))
        runResetManager.executeReset(gameState, entityManager, player)
        entityManager.biomeManager.forceDebugBiome(scenario.forcedBiome)
        if (scenario.startsWithBloom) gameState.debugActivateBloom() else gameState.debugPrimeBloomMeter(0)
        ghostRecorder.reset()
        currentRestQuote = "Scenario verification active."
        currentRunSummary = null
        resetBloomPresentationState()
        resetOrdinaryProgressCueState()
        director.startSelectedScenario()
        if (scenario == EncounterScenario.REST_LOOP) entityManager.debugSpawnAt(EntityType.CACTUS, player.x + 14f)
        else if (scenario == EncounterScenario.WOLF_CHARGE) entityManager.debugSpawnAt(EntityType.WOLF, player.x + 520f)
        else if (scenario == EncounterScenario.EAGLE_MARK) entityManager.debugSpawnAt(EntityType.EAGLE, player.x + 420f)
        LeitmotifManager.playRunStart()
    }

    private fun handleDebugOverlayAction(action: DebugOverlayAction) {
        val director = encounterDirector ?: return
        when (action) {
            DebugOverlayAction.PREVIOUS -> director.previousScenario()
            DebugOverlayAction.NEXT -> director.nextScenario()
            DebugOverlayAction.TOGGLE_RUN -> if (director.isScenarioActive) prepareFreshRun() else prepareEncounterScenario()
        }
    }

    private fun drawDebugScenarioLayer(canvas: Canvas) {
        if (!::player.isInitialized || !::entityManager.isInitialized) return
        canvas.drawRect(player.hitbox, debugPlayerHitboxPaint)
        canvas.drawText("PLAYER", player.hitbox.left, player.hitbox.top - 8f, debugLabelPaint)
        for (entity in entityManager.activeEntities) {
            canvas.drawRect(entity.hitbox, debugHitboxPaint)
            canvas.drawText(entityManager.entityTypeOf(entity)?.name ?: "ENTITY", entity.hitbox.left, entity.hitbox.top - 8f, debugLabelPaint)
        }
    }

    private fun reloadGhost() {
        ghostPlayer.reset()
        val frames = GhostPersistenceManager.loadLatest(context)
        if (frames.isNotEmpty()) ghostPlayer.load(frames)
    }

    private fun resetBloomPresentationState() {
        bloomActivationFlash = 0f
        bloomAfterglowTimer = 0f
        bloomSessionConversionBase = 0
        bloomLastBurstConversions = 0
        bloomReadyAnnounced = false
        bloomLastAudioConversionCount = 0
        bloomPowerSurgeTimer = 0f
        bloomPowerTier = 0
        bloomPowerSurgeStrength = 0f
        if (::player.isInitialized) player.setBloomPowerPresentation(0f, 0)
    }

    private fun bloomAfterglowFraction(): Float = (bloomAfterglowTimer / 1.8f).coerceIn(0f, 1f)
    private fun bloomPowerSurgeFraction(): Float = (bloomPowerSurgeTimer / 0.55f).coerceIn(0f, 1f)

    private fun runDebugScenarioScript() {
        if (!debugToolsEnabled || !::gameState.isInitialized || !::player.isInitialized ||
            appState != AppGameState.PLAYING || runState != RunState.PLAYING
        ) return
        debugScenarioScript.advance(gameState.runTimeSeconds) { action ->
            when (action) {
                DebugScenarioAction.TAP_JUMP -> { player.onJumpPressed(); player.onJumpReleased(0f) }
                DebugScenarioAction.HOLD_JUMP_START -> player.onJumpPressed()
                DebugScenarioAction.HOLD_JUMP_END -> player.onJumpReleased(0.35f)
                DebugScenarioAction.DUCK_START -> player.onDuckPressed()
                DebugScenarioAction.DUCK_END -> player.onDuckReleased()
            }
        }
    }

    private fun shouldDrawGhostPlayback(): Boolean {
        if (!runMode.isDeterministic) return runMode.allowsDefaultGhostPlayback
        return encounterDirector?.activeScenario?.allowGhostPlayback == true
    }

    private fun ghostVisibilityContext(): GhostPlayer.VisibilityContext? {
        if (!::player.isInitialized) return null
        val liveHitbox = player.hitbox
        var nearbyHazardCount = 0
        var nearestHazardDistancePx = Float.POSITIVE_INFINITY
        if (::entityManager.isInitialized) {
            ghostHazardFocusRect.set(liveHitbox.left - Player.BASE_WIDTH * 1.4f, liveHitbox.top - Player.BASE_HEIGHT * 0.9f, liveHitbox.right + Player.BASE_WIDTH * 4.8f, liveHitbox.bottom + Player.BASE_HEIGHT * 0.9f)
            entityManager.activeEntities.forEach { entity ->
                if (!entity.isActive || entity.hitbox.isEmpty || !RectF.intersects(ghostHazardFocusRect, entity.hitbox)) return@forEach
                nearbyHazardCount++
                nearestHazardDistancePx = minOf(nearestHazardDistancePx, rectGapDistance(liveHitbox, entity.hitbox))
            }
        }
        return reusableGhostVisibilityContext.set(player.x, player.y, player.currentWidth, player.currentHeight, nearbyHazardCount, nearestHazardDistancePx)
    }

    private fun rectGapDistance(a: RectF, b: RectF): Float {
        val dx = when { a.right < b.left -> b.left - a.right; b.right < a.left -> a.left - b.right; else -> 0f }
        val dy = when { a.bottom < b.top -> b.top - a.bottom; b.bottom < a.top -> a.top - b.bottom; else -> 0f }
        return hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    private inner class GameViewTerminalHitImpactEffects : TerminalHitImpactEffectSink {
        override fun recordRunHit() { gameState.recordHit() }
        override fun suppressGhost(seconds: Float) { ghostPlayer.suppress(seconds) }
        override fun triggerPlayerRest() { player.triggerRest() }
        override fun shakeHit() { CameraSystem.shakeHit() }
        override fun playHit() { SfxManager.playHit() }
        override fun playRest() { LeitmotifManager.playRest() }
        override fun longPulse() { HapticManager.longPulse() }
    }

    private inner class GameViewNonTerminalCollisionEffects : NonTerminalCollisionEffectSink {
        override fun recordRunHit() { gameState.recordHit() }
        override fun suppressGhost(seconds: Float) { ghostPlayer.suppress(seconds) }
        override fun triggerStumble() { player.triggerStumble() }
        override fun showStumbleFlash(dominantColor: Int) {
            mercyFlashTimer = mercyFlashDuration
            mercyFlashPaint.color = Color.argb(200, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor))
        }
        override fun playNonLethalHit() { SfxManager.playHit() }
        override fun shakeHit() { CameraSystem.shakeHit() }
        override fun mediumPulse() { HapticManager.mediumPulse() }
        override fun showMercyFlash() {
            mercyFlashTimer = mercyFlashDuration
            mercyFlashPaint.color = Color.argb(200, 60, 240, 80)
        }
        override fun playMercyMiss() { SfxManager.playMercyMiss() }
        override fun doubleTap() { HapticManager.doubleTap() }
        override fun emitMercyStars(centerX: Float, centerY: Float) { ParticleManager.emit(FxPreset.MERCY_STARS, centerX, centerY) }
        override fun shakeMercyMiss() { CameraSystem.shakeMercyMiss() }
    }
}
