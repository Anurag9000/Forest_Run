package com.anurag9000.forestrun

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.anurag9000.forestrun.engine.FeedbackSettings
import com.anurag9000.forestrun.engine.GameView
import com.anurag9000.forestrun.engine.HapticManager
import com.anurag9000.forestrun.engine.LeitmotifManager
import com.anurag9000.forestrun.engine.RuntimeAssetValidator
import com.anurag9000.forestrun.engine.SfxManager

/** Single full-screen Activity hosting the custom SurfaceView game. */
class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView
    private var hasPaused = false

    companion object {
        const val EXTRA_DEBUG_AUTOSTART = "debug_autostart"
        const val EXTRA_DEBUG_SCENARIO = "debug_scenario"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        FeedbackSettings.init(this)
        RuntimeAssetValidator.validateRelease(this)
        gameView = GameView(this)
        setContentView(gameView)
        configureSafeAreaInsets()
        gameView.post {
            hideSystemUI()
            gameView.applyDebugLaunchIntent(intent)
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
            gameView.post { gameView.applyDebugLaunchIntent(intent) }
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
        HapticManager.cancel()
        LeitmotifManager.destroy()
        SfxManager.destroy()
        super.onDestroy()
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
