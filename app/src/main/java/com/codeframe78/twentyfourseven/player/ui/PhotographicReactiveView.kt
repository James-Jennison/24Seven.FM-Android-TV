package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Full-resolution instrument ink; bounded offscreen light fill. Ticked by the existing video owner. */
internal class PhotographicReactiveView(context: Context) : View(context) {
    private var style: IdleVisualizerStyle? = null
    internal var frame = PhotographicFrame()
        private set
    internal var renderedFrames = 0
        private set
    internal var hardwareLaserFrames = 0
        private set
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    // Cap combined ray overlap as well as individual beams, preserving photographic detail.
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 220 }
    private val path = Path()
    private val beamMatrix = Matrix()
    private val warm = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFB34A.toInt(), 0x00FFB34A), null, Shader.TileMode.CLAMP)
    }
    private val colors = intArrayOf(0xFF50FF6A.toInt(), 0xFFFF38C7.toInt(), 0xFF43C9FF.toInt(), 0xFFA879FF.toInt())
    private val beams = colors.map { color -> Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
    } }
    private val glows = colors.map { color -> Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    } }
    private var lightBitmap: Bitmap? = null
    private var lightCanvas: Canvas? = null

    init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; visibility = GONE }

    fun present(next: IdleVisualizerStyle?, value: PhotographicFrame, visible: Boolean) {
        if (style != next) setLayerType(if (next == IdleVisualizerStyle.PulseQuilt) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE, null)
        style = next
        // Android applies this to the hardware-rendered view as a whole, bounding ray overlap.
        alpha = if (next == IdleVisualizerStyle.PulseQuilt) 220f / 255f else 1f
        frame = value
        visibility = if (visible && next?.isPhotographicNative() == true) VISIBLE else GONE
        if (visibility == VISIBLE) invalidate()
    }

    fun clear() { visibility = GONE; frame = PhotographicFrame() }

    fun release() {
        clear()
        lightCanvas = null
        lightBitmap?.recycle()
        lightBitmap = null
        setLayerType(LAYER_TYPE_NONE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        lightBitmap?.recycle()
        lightBitmap = null
        lightCanvas = null
        if (w > 0 && h > 0) {
            // The laser view is already bounded by its owner. Cap without shrinking it again.
            val bw = minOf(480, w)
            val bh = maxOf(1, (h.toFloat() * bw / w).toInt())
            lightBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            lightCanvas = Canvas(lightBitmap!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderedFrames++
        if (style == IdleVisualizerStyle.PulseQuilt && canvas.isHardwareAccelerated) {
            hardwareLaserFrames++
            canvas.save()
            canvas.scale(width / PhotographicGeometry.Width, height / PhotographicGeometry.Height)
            drawLasers(canvas)
            canvas.restore()
        } else if (style == IdleVisualizerStyle.VinylPulse) {
            canvas.save()
            canvas.scale(width / PhotographicGeometry.Width, height / PhotographicGeometry.Height)
            drawMeters(canvas)
            canvas.restore()
        } else {
            val bitmap = lightBitmap ?: return
            val lights = lightCanvas ?: return
            bitmap.eraseColor(Color.TRANSPARENT)
            lights.save()
            lights.scale(bitmap.width / PhotographicGeometry.Width, bitmap.height / PhotographicGeometry.Height)
            when (style) {
                IdleVisualizerStyle.LanternDrift -> drawLanterns(lights)
                IdleVisualizerStyle.PulseQuilt -> drawLasers(lights)
                else -> Unit
            }
            lights.restore()
            canvas.save()
            canvas.scale(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            canvas.drawBitmap(bitmap, 0f, 0f, blit)
            canvas.restore()
        }
    }

    private fun glow(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, strength: Float, paint: Paint) {
        if (strength <= 0f) return
        c.save(); c.translate(x, y); c.scale(rx, ry)
        paint.alpha = (strength.coerceIn(0f, 1f) * 255).toInt()
        c.drawCircle(0f, 0f, 1f, paint); c.restore()
    }

    private fun drawLanterns(c: Canvas) {
        val gain = if (frame.reduced) .24f else 1f
        PhotographicGeometry.lanterns.forEachIndexed { i, p ->
            val level = (frame.lanternBass * .12f + frame.lanternPulse * .88f) * gain
            val x = LanternWaterMotion.lanternX(i, p[0], p[1], frame.phase, frame.lanternWaterBass, frame.reduced)
            val y = LanternWaterMotion.lanternY(i, p[0], p[1], frame.phase, frame.lanternWaterBass, frame.reduced)
            // Illumination follows the same floating body as the photographed paper and raft.
            glow(c, x, y + p[3] * .15f, p[2] * 1.05f, p[3] * 1.05f, level, warm)
            repeat(12) { row ->
                val reflectionY = p[4] + row * p[3] * .13f
                val offset = LanternWaterMotion.waterX(reflectionY, frame.phase, frame.lanternWaterBass, frame.reduced) +
                    sin(frame.phase * 2f + row * 1.7f + i) * p[2] * (.12f + frame.lanternBass * .14f)
                glow(c, x + offset, reflectionY + LanternWaterMotion.waterY(p[0], reflectionY,
                    frame.phase, frame.lanternWaterBass, frame.reduced),
                    p[2] * (.8f - row * .05f), 2.5f + p[3] * .028f,
                    level * (1f - row / 13f) * .7f, warm)
            }
        }
    }

    private fun drawLasers(c: Canvas) {
        val reduced = frame.reduced
        // Pattern/color changes are counted from real PCM attacks, never a slideshow timer.
        val mode = if (reduced) 0 else (frame.cue / 8) % 4
        val time = frame.laserTravel
        PhotographicGeometry.laserFixtures.forEachIndexed { fixture, origin ->
            val truss = fixture >= PhotographicGeometry.lenses.size
            val i = fixture % 4
            val rays = if (truss) 3 else if (reduced) 5 else 13
            val activity = (frame.laserLight[i] * .75f + frame.laserEnergy * .25f).coerceIn(0f, 1f)
            if (activity <= 0f) return@forEachIndexed
            val level = (activity * (if (reduced) .055f else
                .24f + .76f * frame.accent * frame.accent)).coerceIn(0f, 1f)
            val side = if (i % 2 == 0) -1f else 1f
            val overhead = truss || i < 2
            val color = if (reduced) i else (fixture + frame.cue / 4) % colors.size
            val centreX = (836f + side * sin(time + fixture * .8f) * (160f + frame.laserBass * 480f) + frame.aim[i] * 350f + frame.laserPan * 180f).coerceIn(330f, 1340f)
            val centreY = if (overhead) 735f else 235f + sin(time * .7f + i) * 70f
            val spread = (if (reduced) 180f else 160f + frame.laserBass * 500f + frame.fan[i] * 150f + frame.accent * 170f)
            path.reset()
            var longestRay = 1f
            repeat(rays) { ray ->
                val u = ray.toFloat() / (rays - 1)
                val offset = (u * 2f - 1f) * if (truss) .16f else 1f
                val x: Float
                val y: Float
                when (mode) {
                    1 -> { // Crossed ribbons, with a slanted moving fan edge.
                        x = (836f - side * 400f + sin(time * 1.3f) * 160f + offset * spread * .7f).coerceIn(100f, 1572f)
                        y = centreY + offset * sin(time + i) * 95f
                    }
                    2 -> { // Four projectors trace a rotating elliptical tunnel in the haze.
                        val angle = u * 6.2831855f + time * side + i * .3f
                        x = 836f + cos(angle) * 530f
                        y = 440f + sin(angle) * 230f
                    }
                    3 -> { // Wide coordinated curtains traverse almost the whole stage.
                        x = (140f + u * 1392f + sin(time + i) * 60f).coerceIn(80f, 1592f)
                        y = centreY + sin(u * 3.1415927f + time) * 65f
                    }
                    else -> {
                        x = (centreX + offset * spread).coerceIn(80f, 1592f)
                        y = centreY + sin(u * 3.1415927f + time) * (if (reduced) 12f else 55f)
                    }
                }
                val dx = x - origin.first
                val dy = y - origin.second
                val exit = PhotographicGeometry.rayExitFactor(origin.first, origin.second, dx, dy)
                val endX = origin.first + dx * exit
                val endY = origin.second + dy * exit
                longestRay = maxOf(longestRay, hypot(endX - origin.first, endY - origin.second))
                path.moveTo(origin.first, origin.second)
                path.lineTo(endX, endY)
            }
            val paint = beams[color]
            // Radial attenuation measures actual distance from this lens in every direction.
            // A shared linear-axis fade would mis-light wide fans and tunnel rays.
            beamMatrix.setScale(longestRay * 1.5f, longestRay * 1.5f)
            beamMatrix.postTranslate(origin.first, origin.second)
            paint.shader.setLocalMatrix(beamMatrix)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            // Batch an entire projector fan: two path draws instead of two draws per ray.
            // The broad colored halo supplies atmosphere; crisp saturated cores supply impact.
            paint.strokeWidth = if (truss) { if (reduced) 14f else 28f } else if (reduced) 7f else 14f
            paint.alpha = (255f * level * .25f).toInt()
            c.drawPath(path, paint)
            paint.strokeWidth = if (truss) { if (reduced) 4f else 8f } else if (reduced) 2.5f else 4.5f
            paint.alpha = (255f * level).toInt()
            c.drawPath(path, paint)
            glow(c, origin.first, origin.second, 22f, 18f, level, glows[color])
            if (overhead) glow(c, centreX, 738f, if (reduced) 100f else 270f, 22f, level * .3f, glows[color])
        }
    }

    private fun drawMeters(c: Canvas) {
        fun text(value: String, x: Float, y: Float, size: Float, color: Int) {
            ink.style = Paint.Style.FILL; ink.color = color; ink.textSize = size; ink.textAlign = Paint.Align.CENTER
            c.drawText(value, x, y, ink)
        }
        val dark = 0xFF302111.toInt()
        listOf(214f to 778f, 898f to 1457f).forEachIndexed { i, bounds ->
            c.save(); c.clipRect(bounds.first + 10f, 138f, bounds.second - 10f, 390f)
            val cx = (bounds.first + bounds.second) / 2f
            val cy = 388f
            fun point(db: Float, radius: Float): Pair<Float, Float> {
                val a = PhotographicGeometry.meterAngle(db) * Math.PI.toFloat() / 180f
                return cx + cos(a) * radius to cy + sin(a) * radius
            }
            ink.color = dark; ink.style = Paint.Style.STROKE; ink.strokeWidth = 2f
            c.drawArc(cx - 200f, cy - 200f, cx + 200f, cy + 200f, 210f, 120f, false, ink)
            for (db in -60..0 step 5) {
                val a = point(db.toFloat(), 200f); val b = point(db.toFloat(), if (db % 10 == 0) 184f else 192f)
                ink.color = dark; ink.strokeWidth = 2f; ink.style = Paint.Style.STROKE
                c.drawLine(a.first, a.second, b.first, b.second, ink)
                if (db % 10 == 0) { val label = point(db.toFloat(), 224f); text(db.toString(), label.first, label.second + 6f, 19f, dark) }
            }
            text(if (i == 0) "LEFT · RMS dBFS" else "RIGHT · RMS dBFS", cx, 290f, 22f, dark)
            val measured = if (i == 0) frame.leftDbfs else frame.rightDbfs
            val peak = if (i == 0) frame.leftPeak else frame.rightPeak
            val p = point(peak, 194f); val q = point(peak, 178f)
            ink.color = 0xFFB95624.toInt(); ink.strokeWidth = 4f; c.drawLine(p.first, p.second, q.first, q.second, ink)
            val needle = point(measured, 180f)
            ink.color = dark; ink.strokeWidth = 3f; c.drawLine(cx, cy, needle.first, needle.second, ink)
            ink.style = Paint.Style.FILL; c.drawCircle(cx, cy, 8f, ink)
            text(if (frame.channels == 0) "NO PCM" else String.format(Locale.ROOT, "%.1f dBFS", measured), cx, 326f, 22f, dark)
            text("RMS MAX · DECAY", cx, 352f, 14f, dark)
            c.restore()
        }
        c.save(); c.clipRect(217f, 518f, 1450f, 696f)
        val amber = if (frame.reduced) 0xFFB89C72.toInt() else 0xFFFFC777.toInt()
        val muted = 0xFF8E795B.toInt()
        // A broadcast monitor: correlation gauge, stereo-balance carriage, and signal lamps.
        // No spectrum columns, frequency animation, or simulated radio tuning.
        text("PHASE CORRELATION", 365f, 544f, 20f, amber)
        ink.color = muted; ink.style = Paint.Style.STROKE; ink.strokeWidth = 2f
        c.drawArc(285f, 553f, 445f, 713f, 180f, 180f, false, ink)
        for (tick in 0..8) {
            val angle = (180f + tick * 22.5f) * Math.PI.toFloat() / 180f
            c.drawLine(365f + cos(angle) * 72f, 633f + sin(angle) * 72f,
                365f + cos(angle) * 80f, 633f + sin(angle) * 80f, ink)
        }
        if (frame.channels == 2) {
            val angle = SignalDeckPresentation.correlationAngle(frame) * Math.PI.toFloat() / 180f
            ink.color = amber; ink.strokeWidth = 3f
            c.drawLine(365f, 633f, 365f + cos(angle) * 65f, 633f + sin(angle) * 65f, ink)
            ink.style = Paint.Style.FILL; c.drawCircle(365f, 633f, 5f, ink)
        }
        text("−1", 275f, 652f, 18f, muted); text("+1", 455f, 652f, 18f, muted)
        text(if (frame.channels == 0) "NO PCM" else if (frame.channels == 1) "MONO" else
            String.format(Locale.ROOT, "%+.2f", frame.correlation), 365f, 683f, 21f, amber)

        text("STEREO BALANCE", 840f, 544f, 22f, amber)
        ink.color = muted; ink.style = Paint.Style.STROKE; ink.strokeWidth = 2f
        c.drawLine(560f, 606f, 1120f, 606f, ink)
        for (step in -4..4) {
            val x = 840f + step * 70f
            c.drawLine(x, 590f, x, if (step % 2 == 0) 622f else 615f, ink)
        }
        text("L", 548f, 576f, 20f, amber); text("CENTER", 840f, 576f, 16f, muted)
        text("R", 1132f, 576f, 20f, amber)
        val balance = SignalDeckPresentation.balanceDb(frame)
        if (frame.channels > 0) {
            val x = 840f + balance / 24f * 280f
            ink.color = amber; ink.style = Paint.Style.FILL
            path.reset(); path.moveTo(x, 630f); path.lineTo(x - 10f, 644f)
            path.lineTo(x + 10f, 644f); path.close(); c.drawPath(path, ink)
            ink.style = Paint.Style.STROKE; ink.strokeWidth = 3f
            c.drawLine(x, 586f, x, 628f, ink)
        }
        text(if (frame.channels == 0) "WAITING FOR AUDIO" else if (frame.channels == 1) "MONO · CENTERED" else if (balance >= 24f) "R ≥ 24 dB" else if (balance <= -24f) "L ≥ 24 dB" else
            String.format(Locale.ROOT, "L / R  %+.1f dB", balance), 840f, 681f, 20f, amber)

        fun lamp(x: Float, y: Float, value: Float, label: String) {
            ink.style = Paint.Style.FILL; ink.color = 0xFF342B1B.toInt()
            c.drawCircle(x, y, 10f, ink)
            if (value > 0f) {
                ink.color = amber; ink.alpha = (value * 255f).toInt().coerceIn(0, 255)
                c.drawCircle(x, y, 7f, ink)
            }
            text(label, x + 72f, y + 6f, 19f, amber)
        }
        text("PROGRAM", 1285f, 544f, 20f, amber)
        lamp(1225f, 577f, if (frame.channels > 0) 1f else 0f, "PCM")
        lamp(1225f, 622f, if (frame.channels == 2) 1f else 0f, "STEREO")
        lamp(1225f, 667f, SignalDeckPresentation.attack(frame), "ATTACK")
        c.restore()
    }
}
