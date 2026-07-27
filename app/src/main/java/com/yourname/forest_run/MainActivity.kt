package com.yourname.forest_run

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.yourname.forest_run.engine.GameView
import com.yourname.forest_run.engine.HapticManager
import com.yourname.forest_run.engine.LeitmotifManager
import com.yourname.forest_run.engine.SfxManager

/**
 * Single Activity – does nothing except host the [GameView] full-screen.
 * System UI is hidden once, orientation is locked via the manifest.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView

    companion object {
        const val EXTRA_DEBUG_AUTOSTART = "debug_autostart"
        const val EXTRA_DEBUG_SCENARIO = "debug_scenario"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Keep screen on while the app is in the foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = GameView(this)
        setContentView(gameView)

        // WindowInsetsController can be null during very early activity creation on some OEM builds.
        gameView.post {
            hideSystemUI()
            gameView.applyDebugLaunchIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::gameView.isInitialized) {
            gameView.post { gameView.applyDebugLaunchIntent(intent) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-hide system UI if the user accidentally pulled it down
        if (hasFocus) hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        HapticManager.cancel()
        LeitmotifManager.destroy()
        SfxManager.destroy()
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ – WindowInsetsController (preferred)
            val controller = window.decorView.windowInsetsController ?: return
            controller.let {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // API 24-29 – legacy flags
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
