package com.yourname.forest_run.entities

import android.content.Context
import com.yourname.forest_run.engine.EncounterVariant
import com.yourname.forest_run.engine.SpriteManager
import com.yourname.forest_run.entities.animals.*
import com.yourname.forest_run.entities.birds.*
import com.yourname.forest_run.entities.flora.*
import com.yourname.forest_run.entities.trees.*

/**
 * Instantiates the correct Entity subclass based on the requested EntityType.
 * Phase 27: all entities now receive a SpriteSheet from SpriteManager via .copy() so
 * each instance owns its own animation state.
 */
object EntityFactory {

    fun create(
        context: Context,
        type: EntityType,
        startX: Float,
        screenWidth: Float,
        screenHeight: Float,
        spriteManager: SpriteManager,
        variant: EncounterVariant = EncounterVariant.DEFAULT
    ): Entity {
        val groundY = screenHeight * 0.82f

        return when (type) {
            // ── Flora ────────────────────────────────────────────────────────
            EntityType.CACTUS         -> Cactus(context, startX, groundY, spriteManager.cactusSprite.copy())
            EntityType.LILY_OF_VALLEY -> LilyOfValley(context, startX, groundY, spriteManager.lilySprite.copy())
            EntityType.HYACINTH       -> Hyacinth(context, startX, groundY, spriteManager.hyacinthSprite.copy())
            EntityType.EUCALYPTUS     -> Eucalyptus(context, startX, groundY, spriteManager.eucalyptusSprite.copy())
            EntityType.VANILLA_ORCHID -> VanillaOrchid(context, startX, groundY, spriteManager.orchidSprite.copy())

            // ── Trees ────────────────────────────────────────────────────────
            EntityType.WEEPING_WILLOW -> WeepingWillow(context, startX, screenHeight, groundY, spriteManager.willowSprite.copy())
            EntityType.JACARANDA      -> Jacaranda(context, startX, screenHeight, groundY, spriteManager.jacarandaSprite.copy())
            EntityType.BAMBOO         -> Bamboo(context, startX, screenHeight, groundY, spriteManager.bambooSprite.copy())
            EntityType.CHERRY_BLOSSOM -> CherryBlossom(context, startX, screenHeight, groundY, spriteManager.cherryBlossomSprite.copy())

            // ── Birds ────────────────────────────────────────────────────────
            EntityType.DUCK      -> Duck(context, startX, groundY, spriteManager.duckFlying.copy())
            EntityType.TIT       -> TitGroup(context, startX, groundY, spriteManager.titFlying.copy())
            EntityType.CHICKADEE -> ChickadeeGroup(context, startX, groundY, spriteManager.chickadeeFlying.copy())
            EntityType.OWL       -> Owl(context, startX, groundY, spriteManager.owlSprite.copy(), spriteManager.owlFlying.copy())
            EntityType.EAGLE     -> Eagle(context, startX, screenWidth, groundY, spriteManager.eagleFlying.copy())

            // ── Animals ──────────────────────────────────────────────────────
            EntityType.CAT      -> Cat(context, startX, groundY, spriteManager.catSprite.copy())
            EntityType.WOLF     -> Wolf(context, startX, groundY, screenWidth, spriteManager.wolfSprite.copy())
            EntityType.FOX      -> Fox(context, startX, groundY, spriteManager.foxSprite.copy())
            EntityType.HEDGEHOG -> Hedgehog(context, startX, groundY, spriteManager.hedgehogSprite.copy())
            EntityType.DOG      -> Dog(
                context = context,
                startX = startX,
                groundY = groundY,
                screenWidth = screenWidth,
                sprite = spriteManager.dogSprite.copy(),
                isBuddy = when (variant) {
                    EncounterVariant.DOG_BUDDY -> true
                    EncounterVariant.DOG_HAZARD -> false
                    else -> kotlin.random.Random.nextFloat() < com.yourname.forest_run.engine.RelationshipArcSystem.dogBuddyChance(context)
                }
            )
        }
    }
}
