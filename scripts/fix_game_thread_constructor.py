#!/usr/bin/env python3
"""Use one internal primary constructor and one public Android adapter constructor."""

from pathlib import Path

path = Path("app/src/main/java/com/anurag9000/forestrun/engine/GameThread.kt")
text = path.read_text(encoding="utf-8")
old = '''class GameThread private constructor(
    private val updateFrame: (Float) -> Unit,
    private val renderFrame: () -> Unit,
    private val targetFrameTimeNs: Long
) : Thread("GameThread") {
    constructor(surfaceHolder: SurfaceHolder, gameView: GameView) : this(
        updateFrame = { deltaTime -> gameView.update(deltaTime) },
        renderFrame = { renderSurfaceFrame(surfaceHolder, gameView) },
        targetFrameTimeNs = DEFAULT_TARGET_FRAME_TIME_NS
    )

    /** Test seam that still executes the production timing, sleep, and stop loop. */
    internal constructor(
        updateFrame: (Float) -> Unit,
        renderFrame: () -> Unit = {},
        targetFrameTimeNs: Long = DEFAULT_TARGET_FRAME_TIME_NS
    ) : this(
        updateFrame = updateFrame,
        renderFrame = renderFrame,
        targetFrameTimeNs = targetFrameTimeNs.coerceAtLeast(0L)
    )
'''
new = '''class GameThread internal constructor(
    private val updateFrame: (Float) -> Unit,
    private val renderFrame: () -> Unit = {},
    targetFrameTimeNs: Long = DEFAULT_TARGET_FRAME_TIME_NS
) : Thread("GameThread") {
    private val targetFrameTimeNs = targetFrameTimeNs.coerceAtLeast(0L)

    constructor(surfaceHolder: SurfaceHolder, gameView: GameView) : this(
        updateFrame = { deltaTime -> gameView.update(deltaTime) },
        renderFrame = { renderSurfaceFrame(surfaceHolder, gameView) },
        targetFrameTimeNs = DEFAULT_TARGET_FRAME_TIME_NS
    )
'''
if text.count(old) != 1:
    raise RuntimeError(f"GameThread constructor block: expected one match, found {text.count(old)}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
