package com.anurag9000.forestrun.entities.animals

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.engine.EntityManager
import com.anurag9000.forestrun.engine.GameConstants
import com.anurag9000.forestrun.engine.GameStateManager
import com.anurag9000.forestrun.engine.PersistentMemoryManager
import com.anurag9000.forestrun.engine.RunMode
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.CollisionResult
import com.anurag9000.forestrun.entities.EncounterOutcome
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DogTest {

    private lateinit var context: Context
    private lateinit var spriteManager: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("forest_run_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        spriteManager = SpriteManager(context)
    }

    @Test
    fun `bonded buddy dog gains escort celebration flag dialogue depth and stronger reward`() {
        val baselineDog = Dog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = true
        )
        val baselineState = GameStateManager(context)

        repeat(5) { PersistentMemoryManager.recordEncounter(context, EntityType.DOG) }
        repeat(3) { PersistentMemoryManager.recordSpare(context, EntityType.DOG) }

        val bondedDog = Dog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = true
        )
        val bondedState = GameStateManager(context)
        val player = Player(1920, 1080, spriteManager)

        baselineDog.performUniqueAction(player, baselineState)
        bondedDog.performUniqueAction(player, bondedState)

        assertTrue(booleanField(bondedDog, "buddyCelebration"))
        assertEquals(4, listFieldSize(bondedDog, "buddyDialogue"))
        assertTrue(bondedState.seedsThisRun > baselineState.seedsThisRun)
        assertTrue(bondedState.score > baselineState.score)
    }


    @Test
    fun `compact max-speed buddy dash resolves pass before offscreen cull`() {
        val screenWidth = 640f
        val screenHeight = 360f
        val player = Player(screenWidth.toInt(), screenHeight.toInt(), spriteManager)
        val state = GameStateManager(context) { false }
        repeat(3) { state.update(5_000f) }
        assertEquals(GameConstants.MAX_SCROLL_SPEED, state.scrollSpeed, 0f)

        val dog = Dog(
            context = context,
            startX = 520f,
            groundY = player.groundY,
            screenWidth = screenWidth,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = true
        )
        dog.shouldRecordPersistence = false
        setFloatField(dog, "buddyTimer", 0f)
        val manager = EntityManager(
            context,
            screenWidth,
            screenHeight,
            spriteManager
        )
        manager.activeEntities += dog

        val scoreBefore = state.score
        val seedsBefore = state.seedsThisRun

        // First frame changes BUDDY -> BUDDY_DASH without translating the dog.
        manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
        assertEquals(null, manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.PENDING, dog.encounterOutcome)

        // 5 * 2,000 * 0.05 = 500 px: from compact buddy x=270 to -230.
        // The dog is now beyond its old cull threshold but must survive until
        // EntityManager resolves the completed harmless escort.
        manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
        assertTrue(dog.x < 0f)
        assertTrue(dog.isActive)
        assertTrue(dog in manager.activeEntities)
        assertEquals(null, manager.checkCollisions(player, state))
        assertEquals(EncounterOutcome.CLEAN_PASS, dog.encounterOutcome)
        assertTrue(state.score > scoreBefore)
        assertTrue(state.seedsThisRun > seedsBefore)

        // Once resolution exists, the next update may retire the departed dog.
        manager.update(0.05f, state, player, runMode = RunMode.DEBUG_SCENARIO)
        assertFalse(dog.isActive)
        assertFalse(dog in manager.activeEntities)
    }

    @Test
    fun `projectile mercy ring cannot hide direct dog body hit`() {
        val dog = Dog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = false
        )
        val player = Player(1920, 1080, spriteManager)
        val gameState = GameStateManager(context)

        // Trigger the first bark and move its projectile just beyond the
        // player's right edge: inside mercy padding, outside direct overlap.
        dog.update(deltaTime = 1f, scrollSpeed = 0f)
        val field = Dog::class.java.getDeclaredField("projectiles")
        field.isAccessible = true
        val projectiles = field.get(dog) as List<*>
        assertTrue(projectiles.isNotEmpty())
        val projectile = projectiles.first()!!
        val rectangleField = projectile.javaClass.getDeclaredField("rect")
        rectangleField.isAccessible = true
        val projectileRect = rectangleField.get(projectile) as RectF

        player.hitbox.set(dog.hitbox)
        projectileRect.set(
            player.hitbox.right + 2f,
            player.hitbox.top,
            player.hitbox.right + 24f,
            player.hitbox.bottom
        )
        assertEquals(CollisionResult.HIT, dog.onCollision(player, gameState))
    }

    @Test
    fun `projectile padded query remains stable and distinguishes untouched from direct contact`() {
        val dog = Dog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = false
        )
        val player = Player(1920, 1080, spriteManager)
        val state = GameStateManager(context)
        dog.update(deltaTime = 1f, scrollSpeed = 0f)
        val field = Dog::class.java.getDeclaredField("projectiles")
        field.isAccessible = true
        val projectile = (field.get(dog) as List<*>).first()!!
        val rectField = projectile.javaClass.getDeclaredField("rect")
        rectField.isAccessible = true
        val projectileRect = rectField.get(projectile) as RectF

        // Isolate the bark from Dog's body so the result comes from the
        // projectile alone, not an overlapping second encounter component.
        player.hitbox.set(1_000f, 700f, 1_020f, 720f)
        projectileRect.set(1_022f, 700f, 1_040f, 720f)
        val unchanged = RectF(projectileRect)
        repeat(4) {
            assertEquals(CollisionResult.MERCY_MISS, dog.onCollision(player, state))
            assertEquals(unchanged, projectileRect)
        }

        projectileRect.set(1_010f, 700f, 1_030f, 720f)
        assertEquals(CollisionResult.HIT, dog.onCollision(player, state))
        projectileRect.set(1_100f, 700f, 1_120f, 720f)
        assertEquals(CollisionResult.NONE, dog.onCollision(player, state))
    }


    @Test
    fun `bark shockwave detects same time contact with a falling Player`() {
        val dog = Dog(
            context = context,
            startX = 1_400f,
            groundY = 885.6f,
            screenWidth = 1_920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = false
        )
        val player = Player(1_920, 1_080, spriteManager)
        val state = GameStateManager(context)
        // Create the projectile using the real hazard state machine.
        dog.update(deltaTime = 1f, scrollSpeed = 0f)
        val listField = Dog::class.java.getDeclaredField("projectiles")
        listField.isAccessible = true
        val projectile = (listField.get(dog) as List<*>).first()!!
        val rectField = projectile.javaClass.getDeclaredField("rect")
        rectField.isAccessible = true
        val projectileRect = rectField.get(projectile) as RectF
        projectileRect.set(500f, 200f, 584f, 236f)

        capturePlayerMotion(
            player = player,
            before = RectF(460f, 200f, 480f, 220f),
            after = RectF(460f, 300f, 480f, 320f)
        )
        dog.update(deltaTime = 0.05f, scrollSpeed = 1_480f)
        // Both endpoint player/projectile pairs miss; the real falling
        // player intersects the moving shockwave only between samples.
        assertEquals(RectF(400f, 200f, 484f, 236f), projectileRect)
        assertEquals(CollisionResult.HIT, dog.onCollision(player, state))
    }

    @Test
    fun `bark shockwave does not use a stationary final player for an earlier crossing`() {
        val dog = Dog(
            context = context,
            startX = 1_400f,
            groundY = 885.6f,
            screenWidth = 1_920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = false
        )
        val player = Player(1_920, 1_080, spriteManager)
        val state = GameStateManager(context)
        dog.update(deltaTime = 1f, scrollSpeed = 0f)
        val listField = Dog::class.java.getDeclaredField("projectiles")
        listField.isAccessible = true
        val projectile = (listField.get(dog) as List<*>).first()!!
        val rectField = projectile.javaClass.getDeclaredField("rect")
        rectField.isAccessible = true
        val projectileRect = rectField.get(projectile) as RectF
        projectileRect.set(430f, 200f, 514f, 236f)

        capturePlayerMotion(
            player = player,
            before = RectF(460f, 300f, 480f, 320f),
            after = RectF(460f, 200f, 480f, 220f)
        )
        dog.update(deltaTime = 0.05f, scrollSpeed = 2_000f)
        // The shockwave has cleared X before the Player reaches its Y lane.
        assertEquals(CollisionResult.NONE, dog.onCollision(player, state))
    }

    @Test
    fun `hazard Dog body also detects real simultaneous midframe contact`() {
        val dog = Dog(
            context = context,
            startX = 520f,
            groundY = 885.6f,
            screenWidth = 1_920f,
            sprite = spriteManager.dogSprite.copy(),
            isBuddy = false
        )
        val player = Player(1_920, 1_080, spriteManager)
        val state = GameStateManager(context)
        val before = RectF(dog.hitbox)
        dog.previousHitbox.set(before)
        dog.hasMotionSample = true
        val left = before.left - 44f
        val right = before.left - 24f
        capturePlayerMotion(
            player = player,
            before = RectF(left, before.top + 5f, right, before.top + 25f),
            after = RectF(left, before.bottom + 8f, right, before.bottom + 28f)
        )
        dog.update(deltaTime = 0.05f, scrollSpeed = 2_000f)

        // Before: horizontal miss. After: vertical miss. There is a genuine
        // shared-time overlap as the body moves left and Player falls.
        assertEquals(CollisionResult.HIT, dog.onCollision(player, state))
    }

    private fun capturePlayerMotion(player: Player, before: RectF, after: RectF) {
        player.hitbox.set(before)
        // The motion-sample flag has a private setter by design. Let Player's
        // real admitted physics update own it, then place the final geometry.
        player.update(0.001f)
        assertTrue(player.hasMotionSample)
        assertEquals(before, player.previousHitbox)
        player.hitbox.set(after)
    }


    private fun setFloatField(dog: Dog, name: String, value: Float) {
        val field = Dog::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setFloat(dog, value)
    }

    private fun booleanField(dog: Dog, name: String): Boolean {
        val field = Dog::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(dog)
    }

    private fun listFieldSize(dog: Dog, name: String): Int {
        val field = Dog::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(dog) as List<Any>).size
    }
}
