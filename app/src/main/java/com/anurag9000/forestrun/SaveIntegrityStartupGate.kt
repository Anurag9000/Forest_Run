package com.anurag9000.forestrun

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.anurag9000.forestrun.engine.SaveIntegrityReport
import com.anurag9000.forestrun.engine.SaveIntegrityStatus

/** Stops typed save consumers from running when the integrity repair did not persist. */
internal fun Activity.continueAfterSaveRepair(report: SaveIntegrityReport): Boolean {
    if (report.status != SaveIntegrityStatus.WRITE_FAILED) return true

    val message = TextView(this).apply {
        text = getString(R.string.save_integrity_repair_failed)
        setTextColor(Color.WHITE)
        textSize = 20f
        gravity = Gravity.CENTER
        setPadding(32, 32, 32, 32)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        contentDescription = text
        setBackgroundColor(Color.rgb(24, 39, 31))
    }
    setContentView(
        message,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )
    return false
}
