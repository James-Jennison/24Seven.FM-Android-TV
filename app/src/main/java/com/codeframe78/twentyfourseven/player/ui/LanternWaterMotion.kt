package com.codeframe78.twentyfourseven.player.ui

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One wave field drives the photographed water, floating bodies and their illumination. */
internal object LanternWaterMotion {
    const val Columns = 64
    const val Rows = 40
    private const val Width = 1672f
    private const val Height = 941f
    // Include the wooden floats, not just the paper shades. Final value is the waterline.
    private val boats = arrayOf(
        floatArrayOf(170f, 380f, 405f, 580f),
        floatArrayOf(710f, 378f, 841f, 490f),
        floatArrayOf(895f, 466f, 1385f, 858f),
        floatArrayOf(1360f, 370f, 1510f, 510f),
    )

    private fun depth(y: Float) = ((y - 360f) / (Height - 360f)).coerceIn(0f, 1f)
    private fun amplitude(bass: Float, reduced: Boolean) =
        (3f + 5f * bass.coerceIn(0f, 1f)) * if (reduced) .25f else 1f

    fun waterX(y: Float, phase: Float, bass: Float, reduced: Boolean): Float =
        depth(y) * amplitude(bass, reduced) * .6f * (sin(y * .035f + phase * 2f) - sin(y * .035f))

    fun waterY(x: Float, y: Float, phase: Float, bass: Float, reduced: Boolean): Float =
        depth(y) * amplitude(bass, reduced) *
            (.65f * (sin(x * .015f + phase * 3f) - sin(x * .015f)) +
                .35f * (sin(x * .028f - y * .013f - phase * 2f) - sin(x * .028f - y * .013f)))

    private fun rock(i: Int, phase: Float, bass: Float, reduced: Boolean): Float {
        val b = boats[i]
        return ((waterY(b[2], b[3], phase, bass, reduced) - waterY(b[0], b[3], phase, bass, reduced)) /
            (b[2] - b[0])).coerceIn(-.018f, .018f)
    }

    fun lanternX(i: Int, x: Float, y: Float, phase: Float, bass: Float, reduced: Boolean): Float {
        val b = boats[i]
        return x + waterX(b[3], phase, bass, reduced) - rock(i, phase, bass, reduced) * (y - b[3])
    }

    fun lanternY(i: Int, x: Float, y: Float, phase: Float, bass: Float, reduced: Boolean): Float {
        val b = boats[i]
        val cx = (b[0] + b[2]) * .5f
        return y + waterY(cx, b[3], phase, bass, reduced) + rock(i, phase, bass, reduced) * (x - cx)
    }

    private data class GridPoint(
        val x: Float, val y: Float, val depth: Float, val pinned: Float,
        val weights: FloatArray,
        val sinX: Float, val cosX: Float, val sinB: Float, val cosB: Float,
        val sinY: Float, val cosY: Float,
    )

    // Geometry, mask weights and spatial wave terms never change. Cache them once, not per frame.
    private val grid = Array((Columns + 1) * (Rows + 1)) { index ->
        val x = Width * (index % (Columns + 1)) / Columns
        val y = Height * (index / (Columns + 1)) / Rows
        var total = 0f
        var uncovered = 1f
        val weights = FloatArray(boats.size) { i ->
            val b = boats[i]
            val outside = max(max(b[0] - x, x - b[2]), max(b[1] - y, y - b[3])).coerceAtLeast(0f)
            val t = (1f - outside / 32f).coerceIn(0f, 1f)
            val smooth = t * t * (3f - 2f * t)
            total += smooth
            uncovered *= 1f - smooth
            smooth
        }
        if (total > 0f) for (i in weights.indices) weights[i] *= (1f - uncovered) / total
        val edge = min(min(x, Width - x), min(y, Height - y)).div(24f).coerceIn(0f, 1f)
        val shore = ((y - 360f) / 20f).coerceIn(0f, 1f)
        GridPoint(x, y, depth(y), edge * shore, weights,
            sin(x * .015f), cos(x * .015f), sin(x * .028f - y * .013f), cos(x * .028f - y * .013f),
            sin(y * .035f), cos(y * .035f))
    }

    /** Caller-owned workspaces keep the live rendering path free of per-frame allocations. */
    fun update(vertices: FloatArray, phase: Float, bass: Float, reduced: Boolean, poses: FloatArray = FloatArray(12)) {
        require(vertices.size == (Columns + 1) * (Rows + 1) * 2 && poses.size == 12)
        val amplitude = amplitude(bass, reduced)
        val s2 = sin(phase * 2f)
        val c2 = cos(phase * 2f) - 1f
        val s3 = sin(phase * 3f)
        val c3 = cos(phase * 3f) - 1f
        for (i in boats.indices) {
            val b = boats[i]
            poses[i * 3] = waterX(b[3], phase, bass, reduced)
            poses[i * 3 + 1] = waterY((b[0] + b[2]) * .5f, b[3], phase, bass, reduced)
            poses[i * 3 + 2] = rock(i, phase, bass, reduced)
        }
        var index = 0
        for (v in grid) {
            var dx = v.depth * amplitude * .6f * (v.sinY * c2 + v.cosY * s2)
            var dy = v.depth * amplitude * (.65f * (v.sinX * c3 + v.cosX * s3) +
                .35f * (v.sinB * c2 - v.cosB * s2))
            val waterDx = dx
            val waterDy = dy
            for (i in boats.indices) {
                if (v.weights[i] == 0f) continue
                val b = boats[i]
                val rock = poses[i * 3 + 2]
                dx += (poses[i * 3] - rock * (v.y - b[3]) - waterDx) * v.weights[i]
                dy += (poses[i * 3 + 1] + rock * (v.x - (b[0] + b[2]) * .5f) - waterDy) * v.weights[i]
            }
            vertices[index++] = v.x + dx * v.pinned
            vertices[index++] = v.y + dy * v.pinned
        }
    }
}
