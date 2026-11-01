package com.codeframe78.twentyfourseven.player.ui

import android.graphics.*
import kotlin.math.*

/** Live details registered to the photographed surfaces, driven by the existing PCM tick.
 * The displays visualize the shared station signal, not two independently playing tracks.
 * No clock, decoder, recorder or per-frame bitmap allocation.
 */
internal class RaveDeckRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var exposure = 1f
    private val trace = Path()
    private val oval = RectF()
    private val pose = Matrix()
    private val poseValues = FloatArray(9)
    private val history = FloatArray(48)
    private val historyLines = FloatArray(48 * 4)
    private val dimMeters = Array(3) { FloatArray(128) }
    private val litMeters = Array(3) { FloatArray(128) }
    private val dimCounts = IntArray(3)
    private val litCounts = IntArray(3)
    private val meterColours = intArrayOf(Color.rgb(40, 215, 109), Color.rgb(217, 207, 30), Color.rgb(255, 73, 20))
    private var cursor = 0
    private var lastMotion = 0f
    private var angle = 0f
    private var frame = RaveLightingFrame()
    private val surfaces = arrayOf(
        polygon(floatArrayOf(367f, 668f, 659f, 669f, 603f, 970f, 12f, 966f)),
        polygon(floatArrayOf(682f, 686f, 1163f, 685f, 1290f, 975f, 624f, 975f)),
        polygon(floatArrayOf(1253f, 669f, 1539f, 668f, 1908f, 966f, 1314f, 970f)),
    )
    private val reflections = RaveStageGeometry.colours.map {
        LinearGradient(-1f, 0f, 1f, 0f, intArrayOf(Color.TRANSPARENT, it, Color.TRANSPARENT),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
    }
    private val gradientMatrix = Matrix()
    private val glass = LinearGradient(0f, 0f, 0f, 1f, Color.argb(30, 180, 220, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)

    fun present(next: RaveLightingFrame, visible: Boolean) {
        if (!visible || next.energy <= .001f) {
            history.fill(0f); cursor = 0; lastMotion = next.motion
            updateHistoryGeometry()
            frame = RaveLightingFrame(reduced = next.reduced)
            updateMeters()
            return
        }
        var delta = next.motion - lastMotion
        if (delta < -2048f) delta += 4096f
        // Ignore reset/jump inputs instead of producing a discontinuous spin.
        delta = delta.coerceIn(0f, .3f)
        angle = (angle + delta * 480f) % 360f
        if (delta > 0f) {
            history[cursor] = (next.bass * .5f + next.mids * .3f + next.highs * .2f).coerceIn(0f, 1f)
            cursor = (cursor + 1) % history.size
            updateHistoryGeometry()
        }
        lastMotion = next.motion; frame = next
        updateMeters()
    }

    private fun updateMeters() {
        dimCounts.fill(0); litCounts.fill(0)
        repeat(4) { channel ->
            val level = when (channel) { 0 -> frame.bass; 1 -> frame.mids; 2 -> frame.highs; else -> frame.energy }
            val base = when (channel) { 0 -> 873f; 1 -> 940f; 2 -> 1004f; else -> 1068f }
            val lean = when (channel) { 0 -> -4f; 1 -> -2f; 2 -> 1f; else -> 3f }
            repeat(12) { segment ->
                val h = segment / 11f; val x = base + lean * (1f - h); val y = 779f - h * 42f
                val group = if (segment >= 10) 2 else if (segment >= 8) 1 else 0
                val lit = frame.energy > .001f && level > h
                val counts = if (lit) litCounts else dimCounts
                val points = if (lit) litMeters[group] else dimMeters[group]
                val offset = counts[group]
                points[offset] = x - 1.6f; points[offset + 1] = y
                points[offset + 2] = x + 1.6f; points[offset + 3] = y
                counts[group] += 4
            }
        }
    }

    private fun updateHistoryGeometry() {
        repeat(history.size) { i ->
            val x = .025f + i * .98f / (history.size - 1)
            val amplitude = history[(cursor + i) % history.size]
            historyLines[i * 4] = x; historyLines[i * 4 + 1] = .535f - amplitude * .15f
            historyLines[i * 4 + 2] = x; historyLines[i * 4 + 3] = .535f + amplitude * .15f
        }
    }

    private fun colour(value: Int) {
        paint.color = value
        paint.alpha = (Color.alpha(value) * exposure).toInt()
    }
    private fun opacity(value: Int) { paint.alpha = (value * exposure).toInt().coerceIn(0, 255) }

    fun draw(canvas: Canvas, materialExposure: Float = 1f) {
        exposure = materialExposure.coerceIn(0f, 1f)
        val f = frame
        val active = f.energy > .001f
        // Perspective-aligned material markings rotate; photographed housings stay rigid.
        repeat(2) { deck ->
            val left = deck == 0
            poseValues[0] = 151f; poseValues[1] = if (left) 11.5f else -11.5f
            poseValues[2] = if (left) 396f else 1523f
            poseValues[3] = if (left) -30f else 30f; poseValues[4] = 59f; poseValues[5] = if (left) 819f else 818f
            poseValues[6] = 0f; poseValues[7] = 0f; poseValues[8] = 1f
            pose.setValues(poseValues)
            canvas.save(); canvas.concat(pose)
            paint.shader = null; paint.style = Paint.Style.FILL
            colour(if (left) Color.rgb(10, 27, 37) else Color.rgb(29, 14, 37))
            canvas.drawCircle(0f, 0f, .36f, paint)
            canvas.save(); canvas.rotate(angle + deck * 110f)
            // A large asymmetric label and two radial material marks make rotation legible.
            colour(if (left) Color.rgb(39, 99, 126) else Color.rgb(108, 44, 117))
            oval.set(-.34f, -.34f, .34f, .34f); canvas.drawArc(oval, -40f, 155f, true, paint)
            colour(Color.rgb(176, 185, 190))
            canvas.drawRect(.12f, -.045f, .29f, .045f, paint)
            opacity(160)
            canvas.drawRect(.52f, -.009f, .89f, .009f, paint)
            canvas.drawRect(-.79f, -.007f, -.60f, .007f, paint)
            canvas.restore()
            colour(Color.rgb(31, 34, 38)); canvas.drawCircle(0f, 0f, .04f, paint)
            canvas.restore()
            drawDisplay(canvas, deck, active)
        }
        // Six batched line draws replace 48 individual meter rectangles.
        paint.shader = null; paint.strokeWidth = 1.8f; paint.style = Paint.Style.STROKE
        repeat(3) { group ->
            colour(meterColours[group]); opacity(25)
            canvas.drawLines(dimMeters[group], 0, dimCounts[group], paint)
            opacity(235)
            canvas.drawLines(litMeters[group], 0, litCounts[group], paint)
        }
        paint.style = Paint.Style.FILL
        repeat(4) { button ->
            val x = when (button) { 0 -> 331f; 1 -> 365f; 2 -> 1296f; else -> 1329f }
            val y = if (button % 2 == 0) 758f else 756f
            colour(Color.rgb(255, 169, 33))
            opacity(if (active) (60f + 150f * if (button % 2 == 0) f.kick else f.highs).toInt().coerceIn(0, 210) else 0)
            oval.set(x - 8f, y - 2.7f, x + 8f, y + 2.7f); canvas.drawOval(oval, paint)
        }
        paint.shader = null; opacity(255)
    }

    private fun drawDisplay(canvas: Canvas, deck: Int, active: Boolean) {
        // Match the photographed LCD quadrilateral, leaving its bezel and controls untouched.
        val left = deck == 0
        poseValues[0] = if (left) 163.5f else 163f
        poseValues[1] = if (left) -34.5f else 24f
        poseValues[2] = if (left) 441.25f else 1311.5f
        poseValues[3] = 0f; poseValues[4] = 51f; poseValues[5] = 682.5f
        poseValues[6] = 0f; poseValues[7] = 0f; poseValues[8] = 1f
        pose.setValues(poseValues)
        canvas.save(); canvas.concat(pose)
        // Slight width expansion matches the bottom edge perspective.
        trace.reset(); trace.moveTo(.015f, .34f); trace.lineTo(1.015f, .34f)
        trace.lineTo(1.04f, .96f); trace.lineTo(.015f, .96f); trace.close()
        paint.shader = null; colour(Color.rgb(3, 9, 24)); paint.style = Paint.Style.FILL
        canvas.drawPath(trace, paint)
        colour(if (deck == 0) Color.rgb(52, 192, 255) else Color.rgb(171, 108, 255))
        opacity(if (active) 230 else 45); paint.style = Paint.Style.STROKE; paint.strokeWidth = .006f
        canvas.drawLines(historyLines, paint)
        colour(Color.rgb(200, 232, 255)); opacity(if (active) 220 else 40)
        canvas.drawLine(.88f, .36f, .88f, .72f, paint)
        // Bottom band is a live energy meter, which falls to dark when PCM goes stale.
        paint.style = Paint.Style.FILL; colour(Color.rgb(39, 144, 194)); opacity(220)
        if (active) canvas.drawRect(.025f, .85f, .025f + frame.energy.coerceIn(0f, 1f) * .97f, .92f, paint)
        paint.shader = glass; opacity(255); canvas.drawRect(.015f, .34f, 1.015f, .94f, paint)
        paint.shader = null; canvas.restore()
    }

    fun drawReflections(canvas: Canvas, f: RaveLightingFrame, materialExposure: Float = 1f) {
        if (f.energy <= .001f) return
        exposure = materialExposure.coerceIn(0f, 1f)
        paint.style = Paint.Style.FILL
        val power = (f.energy * .55f + f.kick * .2f + f.drop * .25f).coerceIn(0f, 1f)
        repeat(surfaces.size) { i ->
            canvas.save()
            val x = 260f + i * 650f + sin(f.motion * 2f + i * .7f) * 230f
            val shader = reflections[(f.palette.coerceIn(0, 3) + i) % 4]
            gradientMatrix.setScale(110f + f.mids * 90f, 1f)
            gradientMatrix.postRotate(-28f + i * 24f)
            gradientMatrix.postTranslate(x, 830f); shader.setLocalMatrix(gradientMatrix)
            paint.shader = shader; opacity((power * if (f.reduced) 35f else 85f).toInt())
            canvas.drawPath(surfaces[i], paint)
            paint.shader = null
            // Local reflections of the same spatial backlight burst, never a global flash.
            if (!f.reduced && f.white > .01f) {
                colour(Color.WHITE); opacity((f.white * 24f).toInt())
                canvas.drawPath(surfaces[i], paint)
            }
            canvas.restore()
        }
    }

    private fun polygon(points: FloatArray) = Path().apply {
        moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1]); close()
    }
}
