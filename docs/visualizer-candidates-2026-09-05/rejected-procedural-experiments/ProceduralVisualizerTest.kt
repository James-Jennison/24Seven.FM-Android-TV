package com.codeframe78.twentyfourseven.player.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import org.junit.Assert.*
import org.junit.Test

/** Deterministic Android Canvas fixtures, not evidence of live music or GPU throughput. */
class ProceduralVisualizerTest {
    @Test fun tenScenesAreDistinctMovingAndAudioResponsive() {
        val scene = ReferenceInspiredScene()
        val quiet = ProceduralAudioDrive()
        val loud = ProceduralAudioDrive().apply {
            repeat(20) { update(AudioVisualizationState(energy=.8f, channelCount=2,
                calibratedBandDbfs=listOf(-8f,-8f,-12f,-12f), transient=.8f), .05f, false) }
        }
        fun pixels(style: IdleVisualizerStyle, time: Double, drive: ProceduralAudioDrive): IntArray {
            val bitmap=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888)
            scene.draw(Canvas(bitmap),640,360,style,time,drive,false)
            val result=IntArray(640*360)
            bitmap.getPixels(result,0,640,0,0,640,360); bitmap.recycle()
            return result
        }
        val hashes=mutableSetOf<Int>()
        for(style in IdleVisualizerStyle.entries.filter { it.isReferenceInspired() }) {
            val baseline=pixels(style,2.0,quiet)
            assertTrue("Distinct scene: $style",hashes.add(baseline.contentHashCode()))
            assertFalse("Movement: $style",baseline.contentEquals(pixels(style,3.7,quiet)))
            assertFalse("Audio response: $style",baseline.contentEquals(pixels(style,2.0,loud)))
        }
        assertEquals(10,hashes.size)
    }
}
