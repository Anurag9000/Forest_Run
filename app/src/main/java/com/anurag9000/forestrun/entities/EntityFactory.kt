package com.anurag9000.forestrun.entities

import android.content.Context
import com.anurag9000.forestrun.engine.EncounterVariant
import com.anurag9000.forestrun.engine.RelationshipArcSystem
import com.anurag9000.forestrun.engine.SpriteManager
import com.anurag9000.forestrun.entities.animals.Cat
import com.anurag9000.forestrun.entities.animals.Dog
import com.anurag9000.forestrun.entities.animals.Fox
import com.anurag9000.forestrun.entities.animals.Hedgehog
import com.anurag9000.forestrun.entities.animals.Wolf
import com.anurag9000.forestrun.entities.birds.ChickadeeGroup
import com.anurag9000.forestrun.entities.birds.Duck
import com.anurag9000.forestrun.entities.birds.Eagle
import com.anurag9000.forestrun.entities.birds.Owl
import com.anurag9000.forestrun.entities.birds.TitGroup
import com.anurag9000.forestrun.entities.flora.Cactus
import com.anurag9000.forestrun.entities.flora.Eucalyptus
import com.anurag9000.forestrun.entities.flora.Hyacinth
import com.anurag9000.forestrun.entities.flora.LilyOfValley
import com.anurag9000.forestrun.entities.flora.VanillaOrchid
import com.anurag9000.forestrun.entities.trees.Bamboo
import com.anurag9000.forestrun.entities.trees.CherryBlossom
import com.anurag9000.forestrun.entities.trees.Jacaranda
import com.anurag9000.forestrun.entities.trees.WeepingWillow
import kotlin.random.Random

/** Instantiates one concrete entity with finite, positive world geometry. */
object EntityFactory {
    private const val DEFAULT_SCREEN_WIDTH = 1_280f
    private const val DEFAULT_SCREEN_HEIGHT = 720f
    private const val GROUND_RATIO = 0.82f

    fun create(
        context: Context,
        type: EntityType,
        startX: Float,
        screenWidth: Float,
        screenHeight: Float,
        spriteManager: SpriteManager,
        variant: EncounterVariant = EncounterVariant.DEFAULT
    ): Entity {
        val safeScreenWidth = screenWidth.takeIf { it.isFinite() && it > 0f }
            ?: DEFAULT_SCREEN_WIDTH
        val safeScreenHeight = screenHeight.takeIf { it.isFinite() && it > 0f }
            ?: DEFAULT_SCREEN_HEIGHT
        val safeStartX = startX.takeIf { it.isFinite() }
            ?: safeScreenWidth
        val groundY = safeScreenHeight * GROUND_RATIO

        return when (type) {
            EntityType.CACTUS -> Cactus(context, safeStartX, groundY, spriteManager.cactusSprite.copy())
            EntityType.LILY_OF_VALLEY -> LilyOfValley(context, safeStartX, groundY, spriteManager.lilySprite.copy())
            EntityType.HYACINTH -> Hyacinth(context, safeStartX, groundY, spriteManager.hyacinthSprite.copy())
            EntityType.EUCALYPTUS -> Eucalyptus(context, safeStartX, groundY, spriteManager.eucalyptusSprite.copy())
            EntityType.VANILLA_ORCHID -> VanillaOrchid(context, safeStartX, groundY, spriteManager.orchidSprite.copy())

            EntityType.WEEPING_WILLOW -> WeepingWillow(
                context,
                safeStartX,
                safeScreenHeight,
                groundY,
                spriteManager.willowSprite.copy()
            )
            EntityType.JACARANDA -> Jacaranda(
                context,
                safeStartX,
                safeScreenHeight,
                groundY,
                spriteManager.jacarandaSprite.copy()
            )
            EntityType.BAMBOO -> Bamboo(
                context,
                safeStartX,
                safeScreenHeight,
                groundY,
                spriteManager.bambooSprite.copy()
            )
            EntityType.CHERRY_BLOSSOM -> CherryBlossom(
                context,
                safeStartX,
                safeScreenHeight,
                groundY,
                spriteManager.cherryBlossomSprite.copy()
            )

            EntityType.DUCK -> Duck(context, safeStartX, groundY, spriteManager.duckFlying.copy())
            EntityType.TIT -> TitGroup(context, safeStartX, groundY, spriteManager.titFlying.copy())
            EntityType.CHICKADEE -> ChickadeeGroup(context, safeStartX, groundY, spriteManager.chickadeeFlying.copy())
            EntityType.OWL -> Owl(
                context,
                safeStartX,
                groundY,
                spriteManager.owlSprite.copy(),
                spriteManager.owlFlying.copy()
            )
            EntityType.EAGLE -> Eagle(
                context,
                safeStartX,
                safeScreenWidth,
                groundY,
                spriteManager.eagleFlying.copy()
            )

            EntityType.CAT -> Cat(context, safeStartX, groundY, spriteManager.catSprite.copy())
            EntityType.WOLF -> Wolf(
                context,
                safeStartX,
                groundY,
                safeScreenWidth,
                spriteManager.wolfSprite.copy()
            )
            EntityType.FOX -> Fox(context, safeStartX, groundY, spriteManager.foxSprite.copy())
            EntityType.HEDGEHOG -> Hedgehog(context, safeStartX, groundY, spriteManager.hedgehogSprite.copy())
            EntityType.DOG -> Dog(
                context = context,
                startX = safeStartX,
                groundY = groundY,
                screenWidth = safeScreenWidth,
                sprite = spriteManager.dogSprite.copy(),
                isBuddy = when (variant) {
                    EncounterVariant.DOG_BUDDY -> true
                    EncounterVariant.DOG_HAZARD -> false
                    else -> Random.nextFloat() < RelationshipArcSystem.dogBuddyChance(context)
                }
            )
        }
    }
}
