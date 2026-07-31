package com.anurag9000.forestrun.systems

import com.anurag9000.forestrun.entities.PlayerState

/**
 * Stable binary identifiers for ghost animation states.
 *
 * These codes are intentionally independent from enum order. Code 7 remains
 * reserved for the deprecated legacy Bloom locomotion state so old recordings
 * can be decoded without shifting Stumble or Rest.
 */
object GhostStateCodec {
    private const val RUNNING = 0
    private const val JUMP_START = 1
    private const val JUMPING = 2
    private const val APEX = 3
    private const val FALLING = 4
    private const val LANDING = 5
    private const val DUCKING = 6
    private const val LEGACY_BLOOM = 7
    private const val STUMBLE = 8
    private const val REST = 9

    fun encodeOrdinal(stateOrdinal: Int): Int? =
        PlayerState.entries.getOrNull(stateOrdinal)?.let(::encode)

    fun encode(state: PlayerState): Int = when (state) {
        PlayerState.RUNNING -> RUNNING
        PlayerState.JUMP_START -> JUMP_START
        PlayerState.JUMPING -> JUMPING
        PlayerState.APEX -> APEX
        PlayerState.FALLING -> FALLING
        PlayerState.LANDING -> LANDING
        PlayerState.DUCKING -> DUCKING
        PlayerState.BLOOM -> LEGACY_BLOOM
        PlayerState.STUMBLE -> STUMBLE
        PlayerState.REST -> REST
    }

    fun decodeToOrdinal(code: Int): Int? = when (code) {
        RUNNING -> PlayerState.RUNNING.ordinal
        JUMP_START -> PlayerState.JUMP_START.ordinal
        JUMPING -> PlayerState.JUMPING.ordinal
        APEX -> PlayerState.APEX.ordinal
        FALLING -> PlayerState.FALLING.ordinal
        LANDING -> PlayerState.LANDING.ordinal
        DUCKING -> PlayerState.DUCKING.ordinal
        LEGACY_BLOOM -> PlayerState.BLOOM.ordinal
        STUMBLE -> PlayerState.STUMBLE.ordinal
        REST -> PlayerState.REST.ordinal
        else -> null
    }
}
