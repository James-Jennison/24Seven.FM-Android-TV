package com.codeframe78.twentyfourseven.player.ui

/** Reserve 30% headroom for hits even when the sustained bands are at full scale. */
internal fun laserAudioDrive(local: Float, bass: Float, attack: Float): Float =
    (local * .45f + bass * .25f + attack * .30f).coerceIn(0f, 1f)
