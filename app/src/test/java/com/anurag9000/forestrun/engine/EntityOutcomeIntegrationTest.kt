package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityFactory
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.systems.ParticleManager
import com.anurag9000.forestrun.ui.DialogueBubbleManager
import com.anurag9000.forestrun.ui.FlavorTextManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntityOutcomeIntegrationTest {
    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager
    private lateinit var player: Player

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPersistence()
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
        spriteManager = SpriteManager(context)
        player = Player(1_920, 1_080, spriteManager)
    }

    @After
    fun tearDown() {
        ParticleManager.clear()
        DialogueBubbleManager.clear()
        FlavorTextManager.clear()
    }

    @Test
    fun `hit outranks simultaneous stumble and mercy and only winner receives effects`() {
        val manager = manager()
        val gameState = GameStateManager(context)
        val mercy = ProbeEntity(context, CollisionResult.MERCY_MISS)
        val stumble = ProbeEntity(context, CollisionResult.STUMBLE)
        val hit = ProbeEntity(context, CollisionResult.HIT)
        manager.activeEntities += listOf(mercy, stumble, hit)

        val frame = manager.checkCollisions(player, gameState)

        requireNotNull(frame)
        assertEquals(CollisionResult.HIT, frame.result)
        assertSame(hit, frame.entity)
        assertEquals(1, hit.selectedCount)
        assertEquals(0, stumble.selectedCount)
        assertEquals(0, mercy.selectedCount)
        assertEquals(EncounterOutcome.HIT, hit.encounterOutcome)
        assertEquals(EncounterOutcome.PENDING, stumble.encounterOutcome)
        assertEquals(EncounterOutcome.PENDING, mercy.encounterOutcome)
    }

    @Test
    fun `mercy resolves once even when collision checks repeat`() {
        val manager = manager()
        val gameState = GameStateManager(context)
        val mercy = ProbeEntity(context, CollisionResult.MERCY_MISS)
        manager.activeEntities += mercy

        val first = manager.checkCollisions(player, gameState)
        val heartsAfterFirst = gameState.mercyHearts
        val second = manager.checkCollisions(player, gameState)

        requireNotNull(first)
        assertEquals(CollisionResult.MERCY_MISS, first.result)
        assertNull(second)
        assertEquals(1, mercy.selectedCount)
        assertEquals(EncounterOutcome.MERCY, mercy.encounterOutcome)
        assertEquals(heartsAfterFirst, gameState.mercyHearts)
        assertTrue(heartsAfterFirst > 0)
    }

    @Test
    fun `collision arbitration runs before pass resolution`() {
        val manager = manager()
        val gameState = GameStateManager(context)
        val lethalBehindPlayer = ProbeEntity(
            context = context,
            collisionResult = CollisionResult.HIT,
            right = player.hitbox.left - 20f
        )
        manager.activeEntities += lethalBehindPlayer

        val frame = manager.checkCollisions(player, gameState)

        requireNotNull(frame)
        assertEquals(CollisionResult.HIT, frame.result)
        assertEquals(EncounterOutcome.HIT, lethalBehindPlayer.encounterOutcome)
        assertEquals(0, lethalBehindPlayer.uniqueActionCount)
    }

    @Test
    fun `complete encounter bounds delay terminal pass after collision body clears`() {
        val manager = manager()
        val gameState = GameStateManager(context)
        val composite = ProbeEntity(
            context = context,
            collisionResult = CollisionResult.NONE,
            right = player.hitbox.left - 20f,
            encounterRight = player.hitbox.right + 80f
        )
        manager.activeEntities += composite

        assertNull(manager.checkCollisions(player, gameState))
        assertEquals(EncounterOutcome.PENDING, composite.encounterOutcome)
        assertEquals(0, composite.uniqueActionCount)

        composite.encounterBounds.offsetTo(
            player.hitbox.left - composite.encounterBounds.width() - 20f,
            composite.encounterBounds.top
        )
        assertNull(manager.checkCollisions(player, gameState))
        assertEquals(EncounterOutcome.CLEAN_PASS, composite.encounterOutcome)
        assertEquals(1, composite.uniqueActionCount)
    }

    @Test
    fun `valid hitbox remains the fallback when aggregate encounter bounds are unavailable`() {
        val manager = manager()
        val gameState = GameStateManager(context)
        val entity = ProbeEntity(
            context = context,
            collisionResult = CollisionResult.NONE,
            right = player.hitbox.left - 20f
        ).apply {
            setEncounterBounds(RectF())
        }
        manager.activeEntities += entity

        assertNull(manager.checkCollisions(player, gameState))

        assertEquals(EncounterOutcome.CLEAN_PASS, entity.encounterOutcome)
        assertTrue(entity.hasBeenPassed)
        assertEquals(1, entity.uniqueActionCount)
        assertEquals(1, gameState.cleanPassesThisRun)
    }

    @Test
    fun `malformed encounter geometry cannot manufacture clean pass rewards`() {
        val manager = manager()
        val gameState = GameStateManager(context)
        val malformedEntities = malformedGeometryEntities()
        manager.activeEntities += malformedEntities

        assertNull(manager.checkCollisions(player, gameState))

        malformedEntities.forEach { entity ->
            assertEquals(EncounterOutcome.PENDING, entity.encounterOutcome)
            assertFalse(entity.hasBeenPassed)
            assertTrue(entity.isActive)
            assertEquals(0, entity.uniqueActionCount)
        }
        assertEquals(0, gameState.cleanPassesThisRun)
        assertEquals(0, manager.seedOrbManager.activeOrbCount)
    }

    @Test
    fun `malformed encounter geometry cannot manufacture Bloom conversions`() {
        val manager = manager()
        val gameState = GameStateManager(context).apply { debugActivateBloom() }
        val malformedEntities = malformedGeometryEntities()
        manager.activeEntities += malformedEntities

        assertNull(manager.checkCollisions(player, gameState))

        malformedEntities.forEach { entity ->
            assertEquals(EncounterOutcome.PENDING, entity.encounterOutcome)
            assertFalse(entity.hasBeenPassed)
            assertTrue(entity.isActive)
        }
        assertEquals(0, gameState.bloomConversionsThisRun)
        assertEquals(0, gameState.seedsThisRun)
    }

    @Test
    fun `Bloom conversion is exclusive of clean pass and unique action rewards`() {
        val manager = manager()
        val gameState = GameStateManager(context).apply { debugActivateBloom() }
        val entity = ProbeEntity(
            context = context,
            collisionResult = CollisionResult.NONE,
            right = player.hitbox.left - 20f
        )
        manager.activeEntities += entity

        val frame = manager.checkCollisions(player, gameState)

        assertNull(frame)
        assertEquals(EncounterOutcome.BLOOM_CONVERTED, entity.encounterOutcome)
        assertEquals(0, entity.uniqueActionCount)
        assertEquals(1, gameState.bloomConversionsThisRun)
        assertEquals(0, gameState.cleanPassesThisRun)
        assertTrue(!entity.isActive)
    }

    @Test
    fun `every concrete entity records exactly one clean pass and encounter`() {
        EntityType.entries.forEach { type ->
            clearTypePersistence(type)
            val manager = manager()
            val gameState = GameStateManager(context)
            val entity = createEntity(type, startX = -1_000f)
            entity.hitbox.set(
                player.hitbox.left - 220f,
                player.hitbox.top,
                player.hitbox.left - 120f,
                player.hitbox.bottom
            )
            manager.activeEntities += entity

            val frame = manager.checkCollisions(player, gameState)
            manager.checkCollisions(player, gameState)

            assertNull("$type should resolve as a pass, not a collision", frame)
            assertEquals("$type outcome", EncounterOutcome.CLEAN_PASS, entity.encounterOutcome)
            assertEquals("$type pass count", 1, PersistentMemoryManager.getPassCount(context, type))
            assertEquals("$type encounter count", 1, PersistentMemoryManager.getEncounterCount(context, type))
            assertEquals("$type session clean pass", 1, gameState.cleanPassesThisRun)
            ParticleManager.clear()
            DialogueBubbleManager.clear()
            FlavorTextManager.clear()
        }
    }

    @Test
    fun `debug entities never write encounter or clean pass history`() {
        EntityType.entries.forEach { type ->
            clearTypePersistence(type)
            val manager = manager()
            val gameState = GameStateManager(context)
            val entity = createEntity(type, startX = -1_000f).apply {
                shouldRecordPersistence = false
                hitbox.set(
                    player.hitbox.left - 220f,
                    player.hitbox.top,
                    player.hitbox.left - 120f,
                    player.hitbox.bottom
                )
            }
            manager.activeEntities += entity

            manager.checkCollisions(player, gameState)
            manager.checkCollisions(player, gameState)

            assertEquals("$type debug outcome", EncounterOutcome.CLEAN_PASS, entity.encounterOutcome)
            assertEquals("$type debug pass history", 0, PersistentMemoryManager.getPassCount(context, type))
            assertEquals("$type debug encounter history", 0, PersistentMemoryManager.getEncounterCount(context, type))
            ParticleManager.clear()
            DialogueBubbleManager.clear()
            FlavorTextManager.clear()
        }
    }

    private fun malformedGeometryEntities(): List<ProbeEntity> = listOf(
        ProbeEntity(context, CollisionResult.NONE).apply {
            setGeometry(RectF(), RectF())
        },
        ProbeEntity(context, CollisionResult.NONE).apply {
            val inverted = RectF(100f, 100f, 40f, 80f)
            setGeometry(inverted, inverted)
        },
        ProbeEntity(context, CollisionResult.NONE).apply {
            val nonFinite = RectF(Float.NaN, 100f, 40f, 180f)
            setGeometry(nonFinite, nonFinite)
        }
    )

    private fun manager(): EntityManager = EntityManager(
        context = context,
        screenWidth = 1_920f,
        screenHeight = 1_080f,
        spriteManager = spriteManager
    )

    private fun createEntity(type: EntityType, startX: Float): Entity = EntityFactory.create(
        context = context,
        type = type,
        startX = startX,
        screenWidth = 1_920f,
        screenHeight = 1_080f,
        spriteManager = spriteManager,
        variant = if (type == EntityType.DOG) EncounterVariant.DOG_HAZARD else EncounterVariant.DEFAULT
    )

    private fun clearPersistence() {
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun clearTypePersistence(type: EntityType) {
        clearPersistence()
        assertEquals(0, PersistentMemoryManager.getPassCount(context, type))
        assertEquals(0, PersistentMemoryManager.getEncounterCount(context, type))
    }

    private class ProbeEntity(
        context: Context,
        private val collisionResult: CollisionResult,
        right: Float = 600f,
        encounterRight: Float = right
    ) : Entity(context) {
        var selectedCount = 0
        var uniqueActionCount = 0
        private val completeBounds = RectF()

        override val encounterBounds: RectF
            get() = completeBounds

        init {
            hitbox.set(right - 100f, 600f, right, 700f)
            completeBounds.set(hitbox.left, hitbox.top, encounterRight, hitbox.bottom)
        }

        fun setGeometry(hitboxBounds: RectF, encounterBounds: RectF) {
            hitbox.set(hitboxBounds)
            completeBounds.set(encounterBounds)
        }

        fun setEncounterBounds(bounds: RectF) {
            completeBounds.set(bounds)
        }

        override fun update(deltaTime: Float, scrollSpeed: Float) = Unit
        override fun draw(canvas: Canvas) = Unit
        override fun onCollision(player: Player, gameState: GameStateManager): CollisionResult =
            collisionResult

        override fun onOutcomeSelected(
            result: CollisionResult,
            player: Player,
            gameState: GameStateManager
        ) {
            selectedCount++
        }

        override fun performUniqueAction(player: Player, gameState: GameStateManager) {
            uniqueActionCount++
        }
    }
}
