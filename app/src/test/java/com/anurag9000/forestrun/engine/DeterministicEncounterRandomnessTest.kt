package com.anurag9000.forestrun.engine

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.anurag9000.forestrun.entities.Entity
import com.anurag9000.forestrun.entities.EntityFactory
import com.anurag9000.forestrun.entities.EntityType
import com.anurag9000.forestrun.entities.Player
import com.anurag9000.forestrun.entities.animals.Dog
import com.anurag9000.forestrun.entities.birds.ChickadeeGroup
import com.anurag9000.forestrun.entities.trees.Bamboo
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeterministicEncounterRandomnessTest {
    private lateinit var context: Context
    private lateinit var sprites: SpriteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SaveManager.usePrimaryPreferences()
        context.getSharedPreferences(SaveManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        sprites = SpriteManager(context)
    }

    @After
    fun tearDown() {
        SaveManager.usePrimaryPreferences()
    }

    private fun create(type: EntityType, seed: Int, variant: EncounterVariant = EncounterVariant.DEFAULT): Entity =
        EntityFactory.create(
            context = context,
            type = type,
            startX = 2_400f,
            screenWidth = 1_920f,
            screenHeight = 1_080f,
            spriteManager = sprites,
            variant = variant,
            random = Random(seed)
        )

    @Test
    fun `seeded Bamboo gaps match across separate entity instances`() {
        val first = create(EntityType.BAMBOO, 104)
        val second = create(EntityType.BAMBOO, 104)
        assertEquals(gapTop(first), gapTop(second), 0f)
        first.update(0.05f, 650f)
        second.update(0.05f, 650f)
        assertEquals(gapTop(first), gapTop(second), 0f)
    }

    @Test
    fun `seeded Chickadees preserve initial and subsequent random target paths`() {
        val first = create(EntityType.CHICKADEE, 205) as ChickadeeGroup
        val second = create(EntityType.CHICKADEE, 205) as ChickadeeGroup
        assertEquals(altitudes(first), altitudes(second))
        assertEquals(targets(first), targets(second))
        repeat(35) {
            first.update(0.05f, 650f)
            second.update(0.05f, 650f)
            assertEquals(altitudes(first), altitudes(second))
            assertEquals(targets(first), targets(second))
        }
    }

    @Test
    fun `seeded Dog buddy duration is stable without changing forced variant`() {
        val first = create(EntityType.DOG, 306, EncounterVariant.DOG_BUDDY) as Dog
        val second = create(EntityType.DOG, 306, EncounterVariant.DOG_BUDDY) as Dog
        assertEquals(floatField(first, "buddyTimer"), floatField(second, "buddyTimer"), 0f)
        assertEquals("BUDDY", enumField(first, "mode"))
        assertEquals("BUDDY", enumField(second, "mode"))
    }

    @Test
    fun `director and manager seed authored Bamboo consistently across frame partitions`() {
        fun run(partitions: List<Float>): List<Float> {
            val director = EncounterDirector().apply {
                selectScenario(EncounterScenario.BAMBOO_GAP)
                startSelectedScenario()
            }
            val manager = EntityManager(context, 1_920f, 1_080f, sprites)
            val state = GameStateManager(context) { false }
            val player = Player(1_920, 1_080, sprites)
            partitions.forEach {
                manager.update(it, state, player, director, RunMode.DEBUG_SCENARIO)
            }
            val bamboo = manager.activeEntities.filterIsInstance<Bamboo>()
            assertEquals(2, bamboo.size)
            assertTrue(bamboo.all { it.isActive })
            return bamboo.map(::gapTop)
        }

        val single = run(listOf(3f))
        val partitioned = run(listOf(0.5f, 2.5f))
        assertEquals(single, partitioned)
    }

    private fun gapTop(entity: Entity): Float {
        val field = Bamboo::class.java.getDeclaredField("gapRects")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val gaps = field.get(entity) as Array<RectF>
        return gaps.first().top
    }

    private fun altitudes(group: ChickadeeGroup): List<Float> {
        val field = ChickadeeGroup::class.java.getDeclaredField("altitudes")
        field.isAccessible = true
        return (field.get(group) as FloatArray).toList()
    }

    private fun targets(group: ChickadeeGroup): List<Float> {
        val field = ChickadeeGroup::class.java.getDeclaredField("targetAltitudes")
        field.isAccessible = true
        return (field.get(group) as FloatArray).toList()
    }

    private fun floatField(entity: Dog, name: String): Float {
        val field = Dog::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getFloat(entity)
    }

    private fun enumField(entity: Dog, name: String): String {
        val field = Dog::class.java.getDeclaredField(name)
        field.isAccessible = true
        return requireNotNull(field.get(entity)).toString()
    }
}
