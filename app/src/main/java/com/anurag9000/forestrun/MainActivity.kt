package com.anurag9000.forestrun

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.anurag9000.forestrun.engine.EncounterScenario
import com.anurag9000.forestrun.engine.FeedbackSettings
import com.anurag9000.forestrun.engine.GameView
import com.anurag9000.forestrun.engine.HapticManager
import com.anurag9000.forestrun.engine.LeitmotifManager
import com.anurag9000.forestrun.engine.RunMode
import com.anurag9000.forestrun.engine.RuntimeAssetValidator
import com.anurag9000.forestrun.engine.SaveIntegrityManager
import com.anurag9000.forestrun.engine.SfxManager
import com.anurag9000.forestrun.engine.SurfaceResizePolicy

/** Single full-screen Activity hosting the custom SurfaceView game. */
class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private var hasPaused = false
    private var configurationWidthDp = 0
    private var configurationHeightDp = 0

    companion object {
        private const val TAG = "ForestRunLaunch"
        private const val DEBUG_LAUNCH_RETRY_MS = 100L
        private const val MAX_DEBUG_LAUNCH_ATTEMPTS = 150
        const val DEBUG_SCENARIO_READY_PREFIX = "FOREST_RUN_SCENARIO_READY"
        const val EXTRA_DEBUG_AUTOSTART = "debug_autostart"
        const val EXTRA_DEBUG_SCENARIO = "debug_scenario"
        const val EXTRA_RUN_MODE = "run_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configurationWidthDp = resources.configuration.screenWidthDp
        configurationHeightDp = resources.configuration.screenHeightDp

        SaveIntegrityManager.repair(this)
        FeedbackSettings.init(this)
        RuntimeAssetValidator.validateRelease(this)
        gameView = GameView(this)
        setContentView(gameView)
        configureSafeAreaInsets()
        gameView.post {
            hideSystemUI()
            applyDebugLaunchWhenReady(Intent(intent))
        }
    }

    /**
     * The manifest routes screen-size changes here instead of allowing Android to
     * recreate the Activity automatically. The engine's world and screen systems
     * are dimension-bound, so a genuine size change must still rebuild them as a
     * coherent new GameView rather than retaining stale hitboxes and layouts.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        val shouldRecreate = SurfaceResizePolicy.requiresActivityRecreation(
            previousWidth = configurationWidthDp,
            previousHeight = configurationHeightDp,
            newWidth = newConfig.screenWidthDp,
            newHeight = newConfig.screenHeightDp,
            dimensionBoundSystemsInitialized = ::gameView.isInitialized
        )
        configurationWidthDp = newConfig.screenWidthDp
        configurationHeightDp = newConfig.screenHeightDp
        super.onConfigurationChanged(newConfig)
        if (shouldRecreate && !isFinishing && !isDestroyed) {
            recreate()
        }
    }

    /**
     * launchMode=singleTask reuses this Activity. Without onNewIntent, the
     * screenshot/debug launcher could request a new scenario while the game
     * kept rendering the previous one.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::gameView.isInitialized) {
            gameView.post { applyDebugLaunchWhenReady(Intent(intent)) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onPause() {
        if (::gameView.isInitialized) gameView.pause()
        hasPaused = true
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // The initial Surface lifecycle starts the first GameThread. Only
        // recreate it after an actual pause; otherwise an initial onResume can
        // race surfaceCreated and start a duplicate render thread.
        if (hasPaused && ::gameView.isInitialized) {
            hasPaused = false
            gameView.resume()
        }
    }

    override fun onDestroy() {
        if (::gameView.isInitialized) gameView.pause()
        HapticManager.release()
        LeitmotifManager.destroy()
        SfxManager.destroy()
        super.onDestroy()
    }

    private fun applyDebugLaunchWhenReady(
        launchIntent: Intent,
        attemptsRemaining: Int = MAX_DEBUG_LAUNCH_ATTEMPTS
    ) {
        if (!::gameView.isInitialized) return
        val scenarioName = launchIntent.getStringExtra(EXTRA_DEBUG_SCENARIO)
        val autoStart = launchIntent.getBooleanExtra(EXTRA_DEBUG_AUTOSTART, false)
        if (scenarioName.isNullOrBlank() && !autoStart) return

        val scenario = scenarioName?.let { raw ->
            EncounterScenario.entries.firstOrNull { it.name == raw }
        }
        if (!scenarioName.isNullOrBlank() && scenario == null) {
            Log.e(TAG, "FOREST_RUN_SCENARIO_REJECTED scenario=$scenarioName reason=unknown")
            return
        }

        val surfaceReady = gameView.width > 0 &&
            gameView.height > 0 &&
            gameView.holder.surface?.isValid == true &&
            gameView.debugFrameCounter > 0L
        if (!surfaceReady) {
            if (attemptsRemaining <= 0) {
                Log.e(
                    TAG,
                    "FOREST_RUN_SCENARIO_REJECTED scenario=${scenarioName ?: "NORMAL"} reason=timeout"
                )
                return
            }
            gameView.postDelayed(
                { applyDebugLaunchWhenReady(launchIntent, attemptsRemaining - 1) },
                DEBUG_LAUNCH_RETRY_MS
            )
            return
        }

        gameView.applyDebugLaunchIntent(launchIntent)
        val effectiveMode = if (scenario != null) {
            RunMode.forScenario(launchIntent.getStringExtra(EXTRA_RUN_MODE))
        } else {
            RunMode.NORMAL
        }
        Log.i(
            TAG,
            "$DEBUG_SCENARIO_READY_PREFIX scenario=${scenario?.name ?: "NORMAL"} " +
                "mode=${effectiveMode.name} frame=${gameView.debugFrameCounter}"
        )
    }

    private fun configureSafeAreaInsets() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.decorView.windowInsetsController ?: return
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }
}
