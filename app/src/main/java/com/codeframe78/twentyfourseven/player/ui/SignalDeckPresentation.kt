package com.codeframe78.twentyfourseven.player.ui

/** Measured stereo instruments, deliberately independent of spectrum bands or animation phase. */
internal object SignalDeckPresentation {
    fun balanceDb(frame: PhotographicFrame): Float {
        if (frame.channels != 2) return 0f
        fun db(value: Float) = if (value.isFinite()) value.coerceIn(-60f, 0f) else -60f
        return (db(frame.rightDbfs) - db(frame.leftDbfs)).coerceIn(-24f, 24f)
    }

    fun correlationAngle(frame: PhotographicFrame): Float {
        val correlation = if (frame.correlation.isFinite()) frame.correlation.coerceIn(-1f, 1f) else 0f
        return 270f + correlation * 90f
    }

    fun attack(frame: PhotographicFrame): Float =
        if (frame.channels == 0 || !frame.onset.isFinite()) 0f else frame.onset.coerceIn(0f, 1f)
}
