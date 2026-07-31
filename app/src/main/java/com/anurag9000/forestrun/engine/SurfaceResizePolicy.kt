package com.anurag9000.forestrun.engine

/**
 * Decides whether a live game surface can keep its existing dimension-bound
 * systems or must recreate the host Activity.
 *
 * Player physics, entity spawn bounds, background layers, Garden/Menu layouts,
 * HUD geometry, and the game-over composition are all constructed from the
 * initial surface dimensions. Silently changing only GameView.screenWidth and
 * screenHeight would leave those systems disagreeing about world coordinates.
 */
object SurfaceResizePolicy {
    fun requiresActivityRecreation(
        previousWidth: Int,
        previousHeight: Int,
        newWidth: Int,
        newHeight: Int,
        dimensionBoundSystemsInitialized: Boolean
    ): Boolean {
        if (!dimensionBoundSystemsInitialized) return false
        if (previousWidth <= 0 || previousHeight <= 0) return false
        if (newWidth <= 0 || newHeight <= 0) return false
        return previousWidth != newWidth || previousHeight != newHeight
    }
}
