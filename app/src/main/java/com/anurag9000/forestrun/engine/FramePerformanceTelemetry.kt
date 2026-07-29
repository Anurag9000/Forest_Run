package com.anurag9000.forestrun.engine

/**
 * Process-wide access point for render-thread telemetry.
 *
 * Keeping the recorder outside [GameView] lets measurements survive Activity and
 * Surface recreation while still avoiding static Android Context ownership.
 */
object FramePerformanceTelemetry {
    internal val monitor = FramePerformanceMonitor()

    /** Capture an out-of-band profiling snapshot; never call this every frame. */
    fun snapshot(): FramePerformanceSnapshot = monitor.snapshot()
}
