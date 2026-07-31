package com.anurag9000.forestrun

import android.content.Intent
import com.anurag9000.forestrun.engine.LatestRequestGate
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityRobolectricTest {

    @Test
    fun onCreateDoesNotCrashAndSetsContentView() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity.findViewById(android.R.id.content))
    }

    @Test
    fun newIntentReplacesPendingDebugLaunchOwnershipAndDestroyCancelsIt() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val gate = debugLaunchGate(activity)
        val initialToken = currentToken(gate)
        assertNotNull(initialToken)

        val onNewIntent = MainActivity::class.java.getDeclaredMethod(
            "onNewIntent",
            Intent::class.java
        )
        onNewIntent.isAccessible = true
        onNewIntent.invoke(activity, Intent(activity, MainActivity::class.java))

        val replacementToken = currentToken(gate)
        assertNotNull(replacementToken)
        assertNotSame(initialToken, replacementToken)

        controller.pause().stop().destroy()

        assertNull(currentToken(gate))
    }

    private fun debugLaunchGate(activity: MainActivity): LatestRequestGate {
        val field = MainActivity::class.java.getDeclaredField("debugLaunchGate")
        field.isAccessible = true
        return field.get(activity) as LatestRequestGate
    }

    private fun currentToken(gate: LatestRequestGate): LatestRequestGate.Token? {
        val field = LatestRequestGate::class.java.getDeclaredField("current")
        field.isAccessible = true
        return field.get(gate) as LatestRequestGate.Token?
    }
}
