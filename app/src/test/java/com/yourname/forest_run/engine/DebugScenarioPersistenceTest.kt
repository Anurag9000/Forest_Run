package com.yourname.forest_run.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yourname.forest_run.entities.EncounterOutcome
import com.yourname.forest_run.entities.EntityFactory
import com.yourname.forest_run.entities.EntityType
import com.yourname.forest_run.entities.Player
import com.yourname.forest_run.systems.ParticleManager
import com.yourname.forest_run.ui.DialogueBubbleManager
import com.yourname.forest_run.ui.FlavorTextManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugScenarioPersistenceTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
        spriteManager = SpriteManager(context)
    }

    @After
    fun tearDown() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @Test
    fun `debug Cat spare leaves permanent history untouched`() {
        val player = Player(1_920, 1_080, spriteManager)
        val gameState = GameStateManager(context)
        repeat(5) { gameState.addMercyHeart() }
        val manager = EntityManager(context, 1_920f, 1_080f, spriteManager)
        val cat = EntityFactory.create(
            context = context,
            type = EntityType.CAT,
            startX = -1_000f,
            screenWidth = 1_920f,
            screenHeight = 1_080f,
            spriteManager = spriteManager
        ).apply {
            shouldRecordPersistence = false
            hitbox.set(
                player.hitbox.left - 220f,
                player.hitbox.top,
                player.hitbox.left - 120f,
                player.hitbox.bottom
            )
        }
        manager.activeEntities += cat

        manager.checkCollisions(player, gameState)

        assertEquals(EncounterOutcome.CLEAN_PASS, cat.encounterOutcome)
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, EntityType.CAT))
        assertEquals(0, PersistentMemoryManager.getPassCount(context, EntityType.CAT))
        assertEquals(0, PersistentMemoryManager.getSparedCount(context, EntityType.CAT))
        assertEquals(0, PersistentMemoryManager.getKindnessStreak(context, EntityType.CAT))
    }
}
