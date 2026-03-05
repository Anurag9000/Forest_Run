package com.yourname.forest_run.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Manages floating dialogue/flavour text overlays.
 *
 * Phase 16 will complete the rendering pipeline.
 * Phase 11 (animals) calls spawn() so the API is established now and ready.
 * GameView will call update() and draw() every frame once Phase 16 lands.
 */
object FlavorTextManager {

    data class FlavorText(
        val text: String,
        var x: Float,
        var y: Float,
        var alpha: Float = 1f,
        val colour: Int = Color.WHITE,
        val lifetime: Float = 1.5f,
        var elapsed: Float = 0f
    )

    private val active = mutableListOf<FlavorText>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        isFakeBoldText = true
    }

    /** Spawn a floating text at world position (x, y). */
    fun spawn(text: String, x: Float, y: Float, colour: Int = Color.WHITE) {
        active.add(FlavorText(text, x, y, colour = colour))
    }

    /** Advance all active texts. Call from GameView.update(). */
    fun update(deltaTime: Float) {
        val iter = active.iterator()
        while (iter.hasNext()) {
            val ft = iter.next()
            ft.elapsed += deltaTime
            ft.y -= 60f * deltaTime   // Float upward
            ft.alpha = 1f - (ft.elapsed / ft.lifetime).coerceIn(0f, 1f)
            if (ft.elapsed >= ft.lifetime) iter.remove()
        }
    }

    /** Draw all active texts. Call from GameView.draw() after all entities. */
    fun draw(canvas: Canvas) {
        for (ft in active) {
            paint.color = ft.colour
            paint.alpha = (ft.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawText(ft.text, ft.x, ft.y, paint)
        }
    }

    /** Clear all on run reset. */
    fun clear() = active.clear()
}
