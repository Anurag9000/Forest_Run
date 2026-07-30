package com.anurag9000.forestrun.engine

/**
 * Process-wide access point for render-thread telemetry.
 *
 * Keeping the recorder outside [GameView] lets measurements survive Activity and
 * Surface recreation while still avoiding static Android Context ownership.
 */
object FramePerformanceTelemetry {
    @Volatile
    internal var monitor = FramePerformanceMonitor()
        private set

    /**
     * Start an isolated profiling session before creating the Activity/Surface.
     * Existing GameThreads retain their original monitor, so callers must invoke
     * this only while no run thread is active.
     */
    @Synchronized
    fun beginSession(
        windowSize: Int = FramePerformanceMonitor.DEFAULT_WINDOW_SIZE,
        frameBudgetNs: Long = FramePerformanceMonitor.DEFAULT_FRAME_BUDGET_NS
    ) {
        monitor = FramePerformanceMonitor(windowSize, frameBudgetNs)
        RuntimeWorkloadTelemetry.reset()
    }

    /** Capture an out-of-band profiling snapshot; never call this every frame. */
    fun snapshot(): FramePerformanceSnapshot = monitor.snapshot()
}
