package com.anurag9000.forestrun

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anurag9000.forestrun.engine.RuntimeAssetValidator
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeAssetValidatorInstrumentedTest {
    @Test
    fun packagedReleaseAssetsPassTheNonDebuggableStartupGuard() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        RuntimeAssetValidator.validateRelease(ReleaseValidationContext(targetContext))
    }

    private class ReleaseValidationContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getApplicationInfo(): ApplicationInfo =
            ApplicationInfo(baseContext.applicationInfo).apply {
                flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            }
    }
}
