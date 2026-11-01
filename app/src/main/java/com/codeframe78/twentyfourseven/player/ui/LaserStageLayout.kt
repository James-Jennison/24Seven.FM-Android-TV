package com.codeframe78.twentyfourseven.player.ui

/** Measured lens centers in the slim generated backplate, shared by both render paths. */
internal object LaserStageLayout {
    private val lensPixels = intArrayOf(106, 195, 279, 359, 440, 519, 598, 678, 757,
        835, 915, 994, 1073, 1153, 1233, 1311, 1390, 1470, 1550)
    val lensCount get() = lensPixels.size
    val bankCount get() = (lensCount + 1) / 2
    const val lensY = 871f / 941f
    fun lensX(index: Int) = lensPixels[index] / 1672f
    fun bankX(bank: Int) = (lensX(bank * 2) + lensX(minOf(bank * 2 + 1, lensCount - 1))) * .5f
}
