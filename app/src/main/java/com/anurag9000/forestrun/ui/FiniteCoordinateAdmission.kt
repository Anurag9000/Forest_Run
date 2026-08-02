package com.anurag9000.forestrun.ui

/** Fail-closed admission for mapped pointer coordinates before UI hit-testing. */
internal object FiniteCoordinateAdmission {
    fun accepts(x: Float, y: Float): Boolean = x.isFinite() && y.isFinite()
}
