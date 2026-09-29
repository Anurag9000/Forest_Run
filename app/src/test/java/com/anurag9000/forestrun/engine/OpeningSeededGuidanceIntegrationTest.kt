package com.anurag9000.forestrun.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpeningSeededGuidanceIntegrationTest {
    @Test
    fun `first normal seeded encounter and first teaching cue agree on duck response`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = EntityManager(
            context = context,
            screenWidth = 1_920f,
            screenHeight = 1_080f,
            spriteManager = SpriteManager(context)
        )
        manager.seedOpeningSequence()

        val first = manager.activeEntities.first()
        assertEquals(EntityType.DUCK, manager.entityTypeOf(first))

        val cue = requireNotNull(
            OpeningReadabilityGuide.cueFor(
                runTimeSeconds = 0f,
                inputState = OpeningInputState(),
                routeTier = PacifistRouteTier.NONE,
                mercyHearts = 0,
                kindnessChain = 0
            )
        )
        assertEquals("Duck The Low Flyer", cue.title)
        assertEquals("Swipe down when the first wings skim the lane.", cue.line)
        assertFalse(cue.chips.single { it.label == "Duck" }.isComplete)
    }
}
