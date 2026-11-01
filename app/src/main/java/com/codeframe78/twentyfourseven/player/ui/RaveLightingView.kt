package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Hardware Canvas light rig above the silent booth video. No timers or audio resources.
 * Geometry/paint objects are reused; the existing 30 Hz PCM presentation tick invalidates it.
 */
internal class RaveLightingView(context: Context) : View(context) {
    private val deck = RaveDeckRenderer()
    private var deckExposure = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val sceneTransform = Matrix()
    private val colours = RaveStageGeometry.colours
    private val washes = colours.map { RadialGradient(0f, 0f, 1f, it, Color.TRANSPARENT, Shader.TileMode.CLAMP) }
    private val beams = colours.map { LinearGradient(0f, 0f, 0f, 1f, it, Color.TRANSPARENT, Shader.TileMode.CLAMP) }
    // Lens coordinates are shared by the fans and their physical projector props.
    // Larger laser scanners stay separate from the small circular LED ceiling row.
    private val laserX = RaveStageGeometry.laserX
    private val laserY = RaveStageGeometry.laserY
    private val whiteWash = RadialGradient(0f, 0f, 1f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    private val erase = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { xfermode = erase }
    private val foregroundMask = LinearGradient(0f, 610f, 0f, 720f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
    internal var frame = RaveLightingFrame(); private set
    internal var renderedFrames = 0; private set

    init {
        // Rasterize the light-only hardware layer at one-third size; Android scales its
        // cached texture into the full-resolution booth without re-rasterizing paths.
        setLayerType(LAYER_TYPE_HARDWARE, null)
        pivotX = 0f; pivotY = 0f
        isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; visibility = GONE }

    fun present(next: RaveLightingFrame, transform: Matrix, visible: Boolean, exposure: Float = 1f) {
        frame = next; sceneTransform.set(transform)
        deckExposure = exposure.coerceIn(0f, 1f)
        visibility = if (visible) VISIBLE else GONE
        invalidate()
    }

    fun clear() { frame = RaveLightingFrame(); visibility = GONE; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderedFrames++
        val f = frame
        if (width <= 0 || height <= 0) return
        canvas.save()
        canvas.scale(1f / scaleX, 1f / scaleY)
        canvas.concat(sceneTransform)
        canvas.scale(width * scaleX / 1920f, height * scaleY / 1080f)
        val lightsActive = f.energy > .001f
        val laserPower = if (lightsActive) (f.highs * .45f + f.accent * .35f + f.drop * .85f + f.kick * .25f).coerceIn(0f, 1f) else 0f
        if (lightsActive) {
            val lightLayer = canvas.saveLayer(0f, 0f, 1920f, 720f, null)
            val power = (f.energy * .65f + f.kick * .25f + f.drop * .7f).coerceIn(0f, 1f)
            val hue = f.palette.coerceIn(0, 3)
            paint.style = Paint.Style.FILL
            repeat(5) { i ->
                val x = 140f + i * 410f
                canvas.save(); canvas.translate(x, 650f)
                canvas.scale(450f + f.drop * 230f, 720f)
                paint.shader = washes[(hue + i) % 4]
                paint.alpha = ((.08f + power * .32f + f.build * .14f) * 255).toInt().coerceIn(0, 180)
                canvas.drawCircle(0f, 0f, 1f, paint); canvas.restore()
            }
            // Opposing banks share coherent pan/tilt cues, with interleaved fixture chases.
            val count = RaveStageGeometry.headCount
            repeat(count) { i ->
                if (f.reduced && i % 14 >= 6) return@repeat
                val bank = if (RaveStageGeometry.isLeftHead(i)) -1f else 1f
                val lane = i % 14
                val originX = RaveStageGeometry.headX(i)
                val originY = RaveStageGeometry.headY(i)
                val sweep = sin(f.motion * 2f + lane * .19f + (f.cue % 3))
                val spread = when (f.cue % 3) { 0 -> lane * 49f; 1 -> (lane - count / 4f) * 100f; else -> sin(lane * .7f) * 470f }
                val targetX = 960f - bank * (sweep * (350f + f.build * 360f + f.drop * 460f) + spread)
                val targetY = 640f + 90f * cos(f.motion + lane * .12f)
                val dx = targetX - originX; val dy = targetY - originY
                val length = sqrt(dx * dx + dy * dy)
                val musicalPhase = if (f.confidence >= .3f) f.beatPhase else 0f
                val chase = .35f + .65f * max(0f, cos(lane * .7f - musicalPhase * 6.283185f))
                val activity = power * (.45f + chase * .55f)
                canvas.save(); canvas.translate(originX, originY)
                canvas.rotate((-atan2(dx, dy) * 180f / Math.PI).toFloat())
                canvas.scale(length, length)
                paint.shader = beams[(hue + i / 4) % 4]
                paint.alpha = (activity * 115f).toInt().coerceIn(0, 150)
                val cone = .008f + f.build * .006f + f.drop * .012f
                path.reset(); path.moveTo(-.001f, 0f); path.lineTo(-cone, 1f); path.lineTo(cone, 1f); path.lineTo(.001f, 0f); path.close()
                canvas.drawPath(path, paint)
                paint.shader = null; paint.color = colours[(hue + i / 4) % 4]
                paint.alpha = (activity * 92f).toInt().coerceIn(0, 160)
                paint.strokeWidth = .0012f
                canvas.drawLine(0f, 0f, 0f, 1f, paint)
                canvas.restore()

            }
            // Fine high-frequency accents open into wide, kick-driven laser fans on drops.
            if (laserPower > .12f) repeat(laserX.size) { bank ->
                val ox = laserX[bank]
                val oy = laserY[bank]
                val rays = if (f.reduced) 5 else 15
                repeat(rays) { ray ->
                    val fan = (ray - (rays - 1) * .5f) / rays
                    val aim = sin(f.motion * (1f + f.highs) + bank) * 320f
                    val tx = 960f + aim + fan * (450f + f.drop * 1500f)
                    val ty = 10f + abs(fan) * 260f
                    paint.color = colours[(hue + bank + 2) % 4]
                    paint.alpha = (laserPower * 38f).toInt(); paint.strokeWidth = 5f
                    canvas.drawLine(ox, oy, tx, ty, paint)
                    paint.alpha = (laserPower * 220f).toInt(); paint.strokeWidth = 1.2f
                    canvas.drawLine(ox, oy, tx, ty, paint)
                }
            }
            if (laserPower > .12f) repeat(laserX.size) { bank ->
                paint.shader = null
                paint.color = colours[(hue + bank + 2) % 4]
                paint.alpha = (laserPower * 150f).toInt()
                canvas.drawCircle(laserX[bank], laserY[bank], 4f, paint)
                paint.color = Color.WHITE; paint.alpha = (laserPower * 190f).toInt()
                canvas.drawCircle(laserX[bank], laserY[bank], 1f, paint)
            }
            // Haze layers drift with the music-controlled motion coordinate.
            repeat(7) { i ->
                canvas.save(); canvas.translate(120f + i * 300f + sin(f.motion * .3f + i) * 150f, 280f + i % 3 * 105f)
                canvas.scale(360f, 80f)
                paint.shader = washes[(hue + i) % 4]; paint.alpha = (power * 20f).toInt()
                canvas.drawCircle(0f, 0f, 1f, paint); canvas.restore()
            }
            // Spatial white backlight bursts pass across the projected wall artwork.
            if (!f.reduced && f.white > .01f) repeat(4) { i ->
                canvas.save(); canvas.translate(if (i < 2) 220f + i * 250f else 1450f + (i - 2) * 250f, 630f)
                canvas.scale(280f, 850f)
                paint.shader = whiteWash; paint.alpha = (f.white * 235f).toInt().coerceIn(0, 235)
                canvas.drawCircle(0f, 0f, 1f, paint); canvas.restore()
            }
            paint.shader = null
            // The logo belongs to the rear wall video: beams and haze pass over it.
            // Only the foreground deck occludes the atmospheric lighting.
            maskPaint.shader = foregroundMask
            // Extend beyond layer edge so antialiasing cannot leave a bright seam.
            canvas.drawRect(-2f, 610f, 1922f, 1080f, maskPaint)
            maskPaint.shader = null
            canvas.restoreToCount(lightLayer)
        }
        // Reuse the existing bounded texture. Individual paint alpha carries the
        // material exposure, avoiding an extra offscreen deck-band composition.
        deck.drawReflections(canvas, frame, deckExposure)
        canvas.restore()
    }
}
