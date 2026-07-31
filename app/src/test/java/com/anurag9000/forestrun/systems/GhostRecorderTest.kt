package com.anurag9000.forestrun.systems

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GhostRecorderTest {
    private lateinit var player: Player

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        player = Player(
            screenWidth = 1_280,
            screenHeight = 720,
            spriteManager = SpriteManager(context)
        )
    }

    @Test
    fun `malformed pose is skipped and next valid sample recovers`() {
        val recorder = GhostRecorder()
        player.x = Float.NaN

        recorder.record(GhostRecorder.SAMPLE_INTERVAL_S, player)

        assertTrue(recorder.frames.isEmpty())
        assertTrue(recorder.runDuration > 0f)

        player.x = 240f
        recorder.record(GhostRecorder.SAMPLE_INTERVAL_S, player)

        assertEquals(1, recorder.frames.size)
        assertTrue(GhostRunValidator.isValid(recorder.frames))
        assertTrue(recorder.frames.single().t >= GhostRecorder.SAMPLE_INTERVAL_S * 2f)
    }

    @Test
    fun `huge finite delta clamps to maximum duration and stays valid`() {
        val recorder = GhostRecorder()

        recorder.record(Float.MAX_VALUE, player)

        assertEquals(GhostRecorder.MAX_DURATION_S.toFloat(), recorder.runDuration, 0f)
        assertEquals(1, recorder.frames.size)
        assertTrue(GhostRunValidator.isValid(recorder.frames))
    }

    @Test
    fun `sampling remains capped near thirty hertz`() {
        val recorder = GhostRecorder()

        recorder.record(GhostRecorder.SAMPLE_INTERVAL_S, player)
        repeat(10) {
            recorder.record(GhostRecorder.SAMPLE_INTERVAL_S / 20f, player)
        }

        assertEquals(1, recorder.frames.size)

        recorder.record(GhostRecorder.SAMPLE_INTERVAL_S, player)

        assertEquals(2, recorder.frames.size)
        assertTrue(GhostRunValidator.isValid(recorder.frames))
    }

    @Test
    fun `detached completed buffer is not mutated by next run`() {
        val recorder = GhostRecorder()
        recorder.record(GhostRecorder.SAMPLE_INTERVAL_S, player)
        val completed = recorder.detachSnapshot()

        player.x += 100f
        recorder.record(GhostRecorder.SAMPLE_INTERVAL_S, player)

        assertEquals(1, completed.size)
        assertEquals(1, recorder.frames.size)
        assertTrue(completed !== recorder.frames)
        assertTrue(completed.single().x != recorder.frames.single().x)
        assertEquals(0f, completed.single().t - GhostRecorder.SAMPLE_INTERVAL_S, 0.0001f)
    }
}
