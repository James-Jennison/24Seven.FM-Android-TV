package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.codeframe78.twentyfourseven.player.R

/** Native texture mesh over the existing static photographic plate. No independent clock/player. */
internal class LanternWaterView(context: Context) : View(context) {
    private var photo: Bitmap? = null
    private var decodeAttempted = false
    private var frame = PhotographicFrame()
    private val vertices = FloatArray((LanternWaterMotion.Columns + 1) * (LanternWaterMotion.Rows + 1) * 2)
    private val poses = FloatArray(12)
    private val colors = IntArray(vertices.size / 2)
    private val shadeMask = FloatArray(colors.size) { index ->
        val x = PhotographicGeometry.Width * (index % (LanternWaterMotion.Columns + 1)) / LanternWaterMotion.Columns
        val y = PhotographicGeometry.Height * (index / (LanternWaterMotion.Columns + 1)) / LanternWaterMotion.Rows
        PhotographicGeometry.lanterns.maxOf { p ->
            val dx = (x - p[0]) / p[2]
            val dy = (y - p[1]) / p[3]
            val t = ((1f - dx * dx - dy * dy) * 2f).coerceIn(0f, 1f)
            t * t * (3f - 2f * t)
        }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    internal var renderedFrames = 0
        private set
    internal var hardwareFrames = 0
        private set

    init { visibility = GONE; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun present(visible: Boolean, value: PhotographicFrame): Boolean {
        frame = value
        visibility = if (visible) VISIBLE else GONE
        if (visible) {
            if (!decodeAttempted) {
                decodeAttempted = true
                photo = BitmapFactory.decodeResource(resources, R.drawable.visualizer_lantern_drift_realistic)
            }
            invalidate()
        }
        return visible && photo != null
    }

    fun clear() { visibility = GONE; frame = PhotographicFrame() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = photo ?: return
        LanternWaterMotion.update(vertices, frame.phase, frame.lanternWaterBass, frame.reduced, poses)
        // Modulate the photographed paper itself, so kicks remain visible over its baked-in light.
        val contrast = if (frame.reduced) .12f else .58f
        for (i in colors.indices) {
            val value = (255f * (1f - contrast * (1f - frame.lanternPulse) * shadeMask[i])).toInt().coerceIn(0, 255)
            colors[i] = android.graphics.Color.rgb(value, value, value)
        }
        canvas.save()
        canvas.scale(width / PhotographicGeometry.Width, height / PhotographicGeometry.Height)
        canvas.drawBitmapMesh(bitmap, LanternWaterMotion.Columns, LanternWaterMotion.Rows, vertices, 0, colors, 0, paint)
        canvas.restore()
        renderedFrames++
        if (canvas.isHardwareAccelerated) hardwareFrames++
    }

    fun release() { clear(); photo?.recycle(); photo = null; decodeAttempted = false }
}
