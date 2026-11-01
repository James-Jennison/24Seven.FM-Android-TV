package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.codeframe78.twentyfourseven.player.R
import kotlin.math.cos
import kotlin.math.sin

/** Shared optical origins keep photographic apertures and animated rays registered. */
internal object RaveStageGeometry {
    val laserX = floatArrayOf(115f, 1805f, 345f, 1575f)
    val laserY = floatArrayOf(545f, 545f, 132f, 132f)
    const val headCount = 28
    val colours = intArrayOf(0xFF167BFF.toInt(), 0xFF7625FF.toInt(), 0xFFFF20B8.toInt(), 0xFF20E5FF.toInt())
    fun headX(index: Int) = if (index < 14) 40f + index * (880f / 13f) else 1880f - (index - 14) * (880f / 13f)
    fun headY(index: Int) = 22f + (index % 14 % 3) * 32f
    fun isLeftHead(index: Int) = index < 14

}

/** Full-resolution photographic fixtures underneath the atmospheric layer.
 * Reuses the existing PCM presentation tick; no timer, decoder or audio resource.
 */
internal class RaveEquipmentView(context: Context) : View(context) {
    private val artwork by lazy { maskedArtwork(R.drawable.rave_equipment_atlas, R.drawable.rave_equipment_matte) }
    private val truss by lazy { maskedArtwork(R.drawable.rave_ceiling_truss, R.drawable.rave_ceiling_truss_matte, 2) }

    private fun maskedArtwork(photoId: Int, matteId: Int, sample: Int = 1): Bitmap {
        // Compose the generated opacity matte once, never per tick.
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val photo = BitmapFactory.decodeResource(resources, photoId, options)
        val matte = BitmapFactory.decodeResource(resources, matteId, options)
        val result = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
        val target = Canvas(result)
        val mattePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(floatArrayOf(
                0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 0f, 255f,
                0f, 0f, 0f, 0f, 255f, .238112f, .801024f, .080864f, 0f, -16f,
            ))
        }
        target.drawBitmap(matte, null, Rect(0, 0, photo.width, photo.height), mattePaint)
        mattePaint.colorFilter = null
        mattePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        target.drawBitmap(photo, 0f, 0f, mattePaint)
        photo.recycle(); matte.recycle()
        return result
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Pixel coordinates in the generated 1536x1024 atlas, including transparent margins.
    private val floorSource = Rect(20, 440, 515, 800)
    private val hangingSource = Rect(545, 65, 1020, 800)
    private val puckSheet by lazy { BitmapFactory.decodeResource(resources, R.drawable.rave_led_puck,
        BitmapFactory.Options().apply { inSampleSize = 2 }) }
    // Clip the photographed circular face ONCE into a small reusable texture, not
    // through 28 full-resolution clip paths on every music tick.
    private val puckFace by lazy {
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { bitmap ->
            val target = Canvas(bitmap)
            val clip = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            target.drawCircle(64f, 64f, 64f, clip)
            clip.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            target.drawBitmap(puckSheet, Rect(66, 156, 337, 426), Rect(0, 0, 128, 128), clip)
        }
    }
    // Crop the small fixed plate to its displayed aspect instead of squeezing the
    // whole wide motor housing into a narrow bracket.
    private val mountSource = Rect(522, 160, 583, 215)
    private val puckBounds = RectF(-16f, -16f, 16f, 16f)
    private val mountBounds = RectF(-5f, -19f, 5f, -10f)
    private val ledX = floatArrayOf(0f, 0f, -7.5f, 7.5f, -7.5f, 7.5f, 0f)
    private val ledY = floatArrayOf(0f, -8.9f, -4.5f, -4.5f, 4.5f, 4.5f, 8.9f)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = RaveLightingFrame()
    private val deck = RaveDeckRenderer()

    fun present(next: RaveLightingFrame, visible: Boolean) {
        frame = next
        deck.present(next, visible)
        visibility = if (visible) VISIBLE else GONE
        if (visible) invalidate()
    }
    private val destination = RectF()
    // Only the continuous truss strip is used; large hanging fixtures in that sheet
    // are outside this crop. Lower chords line up with each puck mounting plate.
    private val trussSource = Rect(16, 60, 988, 135)
    private val trussDestination = RectF()


    init {
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 1920f, height / 1080f)
        // Keep the fine screen/label/meter details at photograph resolution.
        deck.draw(canvas)
        // Four physically adjoining truss sections form one full-width ceiling rig.
        repeat(3) { row ->
            val top = row * 32f - 30f
            repeat(4) { section ->
                trussDestination.set(section * 480f, top, (section + 1) * 480f, top + 37f)
                canvas.drawBitmap(truss, trussSource, trussDestination, paint)
            }
        }
        repeat(RaveStageGeometry.laserX.size) { bank ->
            canvas.save()
            canvas.translate(RaveStageGeometry.laserX[bank], RaveStageGeometry.laserY[bank])
            canvas.scale(if (bank % 2 == 0) 1f else -1f, 1f)
            if (bank < 2) drawAtAperture(canvas, floorSource, 332f, 600f, .23f)
            else drawAtAperture(canvas, hangingSource, 780f, 600f, .14f)
            canvas.restore()
        }
        val active = frame.energy > .001f
        repeat(RaveStageGeometry.headCount) { index ->
            canvas.save()
            canvas.translate(RaveStageGeometry.headX(index), RaveStageGeometry.headY(index))
            canvas.drawBitmap(puckSheet, mountSource, mountBounds, paint)
            val phase = (if (active) frame.motion else 0f) * .8f + index * .3f
            // Compact head pose, anchored at the centre of its optical face. The
            // concealed mounting plate stays still while the photographic puck turns.
            canvas.rotate(sin(phase) * if (frame.reduced) 8f else 24f)
            canvas.scale(1f, .82f + .12f * cos(phase))
            canvas.drawBitmap(puckFace, null, puckBounds, paint)
            if (active && (!frame.reduced || index % 14 < 6)) {
                glowPaint.color = RaveStageGeometry.colours[(frame.palette.coerceIn(0, 3) + index / 4) % 4]
                glowPaint.alpha = ((frame.energy * .55f + frame.kick * .2f + frame.drop * .45f) * 210f).toInt().coerceIn(0, 230)
                repeat(ledX.size) { led -> canvas.drawCircle(ledX[led], ledY[led], 2.7f, glowPaint) }
            }
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawAtAperture(canvas: Canvas, source: Rect, lensX: Float, lensY: Float, scale: Float) {
        destination.set((source.left - lensX) * scale, (source.top - lensY) * scale,
            (source.right - lensX) * scale, (source.bottom - lensY) * scale)
        canvas.drawBitmap(artwork, source, destination, paint)
    }
}
