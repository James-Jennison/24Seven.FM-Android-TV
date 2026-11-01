package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerRenderProfile
import com.codeframe78.twentyfourseven.player.domain.metadata
import org.junit.Assert.*
import org.junit.Test

class VideoAudioDriveTest {
    @Test fun videoAssetsAndCatalogProfilesStayInAgreement() {
        com.codeframe78.twentyfourseven.player.domain.AvailableIdleVisualizerStyles.forEach { style ->
            assertEquals(style.name, style.metadata.renderProfile == IdleVisualizerRenderProfile.Video,
                style.videoAsset()?.usesBundledVideo == true)
        }
        val assets = IdleVisualizerStyle.entries.mapNotNull { it.videoAsset() }
        assertEquals(19, assets.size)
        assertEquals(19, assets.map { it.fileName }.toSet().size)
        assertEquals(19, assets.map { it.poster }.toSet().size)
    }
    @Test fun nativeScenesKeepPostersWithoutBundledReferenceVideos() {
        assertFalse(IdleVisualizerStyle.IonTransit.videoAsset()!!.usesBundledVideo)
        assertFalse(IdleVisualizerStyle.LanternDrift.videoAsset()!!.usesBundledVideo)
        assertEquals(17, IdleVisualizerStyle.entries.mapNotNull { it.videoAsset() }.count { it.usesBundledVideo })
        com.codeframe78.twentyfourseven.player.domain.LavaLampPalette.entries.forEach {
            assertTrue(IdleVisualizerStyle.LavaLamp.videoAsset(it)!!.usesBundledVideo)
        }
    }

    @Test fun lavaPalettesHaveDistinctAssetsAndGentleResponse() {
        val assets = com.codeframe78.twentyfourseven.player.domain.LavaLampPalette.entries.map { IdleVisualizerStyle.LavaLamp.videoAsset(it)!! }
        assertEquals(4, assets.map { it.fileName }.toSet().size)
        assertEquals(4, assets.map { it.poster }.toSet().size)
        val drive = VideoAudioDrive()
        val reduced = VideoAudioDrive()
        val first = drive.update(IdleVisualizerStyle.LavaLamp, loud, .033f, false)
        assertTrue(first.scaleX < 1.002f)
        var full = first
        var calm = first
        repeat(180) {
            full = drive.update(IdleVisualizerStyle.LavaLamp, loud, .033f, false)
            calm = reduced.update(IdleVisualizerStyle.LavaLamp, loud, .033f, true)
        }
        assertTrue(full.moving && full.scaleX > 1.01f && full.scaleX <= 1.035f)
        assertTrue(full.speed > .5f && full.speed <= 1.1f)
        assertTrue(calm.scaleX < full.scaleX && calm.speed < full.speed && calm.alpha < full.alpha)
        assertFalse(drive.update(IdleVisualizerStyle.LavaLamp, AudioVisualizationState(), .033f, false).moving)
    }
    @Test fun djBoothPreservesLogoFramingAndRespondsToMusic() {
        assertEquals("dj_soff_snep", IdleVisualizerStyle.DjConsole.videoAsset()!!.fileName)
        val drive = VideoAudioDrive()
        val reduced = VideoAudioDrive()
        var full = VideoAudioFrame()
        var calm = VideoAudioFrame()
        repeat(180) {
            full = drive.update(IdleVisualizerStyle.DjConsole, loud, .033f, false)
            calm = reduced.update(IdleVisualizerStyle.DjConsole, loud, .033f, true)
            assertEquals(1f, full.scaleX, 0f)
            assertEquals(1f, full.scaleY, 0f)
            assertEquals(1f, calm.scaleX, 0f)
            assertEquals(1f, calm.scaleY, 0f)
        }
        assertTrue(full.moving)
        assertEquals(0f, full.shear, .0001f)
        assertTrue(full.speed > .5f && full.speed <= 1.1f)
        assertTrue(calm.alpha < full.alpha && calm.speed < full.speed)
        assertFalse(drive.update(IdleVisualizerStyle.DjConsole, AudioVisualizationState(), .033f, false).moving)
    }
    private val loud = AudioVisualizationState(
        energy = .8f, channelCount = 2, calibratedBandDbfs = listOf(-12f, -12f, -18f, -24f), transient = .9f,
    )

    @Test fun silenceStopsMotionAndSettlesAfterLoudAudio() {
        val drive = VideoAudioDrive()
        repeat(60) { drive.update(IdleVisualizerStyle.SpectralSilk, loud, 1f / 30f, false) }
        assertFalse(drive.update(IdleVisualizerStyle.SpectralSilk, AudioVisualizationState(), .033f, false).moving)
        var settled = VideoAudioFrame()
        repeat(120) { settled = drive.update(IdleVisualizerStyle.SpectralSilk, AudioVisualizationState(), 1f / 30f, false) }
        assertEquals(1f, settled.scaleX, .0001f)
        assertEquals(0f, settled.shear, .0001f)
        assertEquals(.55f, settled.alpha, .0001f)
    }

    @Test fun bassAndMidProduceDifferentResponses() {
        fun render(db: List<Float>): VideoAudioFrame {
            val drive = VideoAudioDrive()
            var frame = VideoAudioFrame()
            repeat(60) { frame = drive.update(IdleVisualizerStyle.SpectralSilk, loud.copy(calibratedBandDbfs = db, transient = 0f), .033f, false) }
            return frame
        }
        val bass = render(listOf(-6f, -6f, -60f, -60f))
        val mid = render(listOf(-60f, -60f, -6f, -60f))
        assertTrue(bass.scaleY > mid.scaleY)
        assertEquals(0f, bass.shear, .0001f)
        assertTrue(mid.shear > .02f)
    }

    @Test fun reducedIntensityBoundsMotionSpeedAndBrightness() {
        val normal = VideoAudioDrive()
        val reduced = VideoAudioDrive()
        repeat(60) {
            val a = normal.update(IdleVisualizerStyle.ElectricSynapse, loud, .033f, false)
            val b = reduced.update(IdleVisualizerStyle.ElectricSynapse, loud, .033f, true)
            assertTrue(b.scaleX < a.scaleX)
            assertTrue(b.alpha < a.alpha)
            assertTrue(b.speed < a.speed)
        }
    }

    @Test fun malformedInputRemainsFiniteAndBounded() {
        val drive = VideoAudioDrive()
        val bad = loud.copy(energy = Float.NaN, calibratedBandDbfs = listOf(Float.NaN, Float.POSITIVE_INFINITY), transient = Float.NaN)
        val output = drive.update(IdleVisualizerStyle.SpectralSilk, bad, Float.NaN, false)
        assertFalse(output.moving)
        assertTrue(listOf(output.scaleX, output.scaleY, output.alpha, output.shear, output.speed).all { it.isFinite() })
        val maximum = loud.copy(calibratedBandDbfs = List(4) { 100f }, transient = 10f)
        repeat(60) {
            val frame = drive.update(IdleVisualizerStyle.ElectricSynapse, maximum, 20f, false)
            assertTrue(frame.alpha in 0f..1f)
            assertTrue(frame.scaleX in 1f..1.3f)
            assertTrue(frame.speed in .5f..2f)
        }
    }

    @Test fun responseDoesNotDependOnFrameRate() {
        fun run(fps: Int): VideoAudioFrame {
            val drive = VideoAudioDrive()
            var frame = VideoAudioFrame()
            repeat(fps) { frame = drive.update(IdleVisualizerStyle.OrbitalPulse, loud, 1f / fps, false) }
            return frame
        }
        assertEquals(run(30).scaleX, run(60).scaleX, .0001f)
        assertEquals(run(30).alpha, run(60).alpha, .0001f)
    }
    @Test fun staleMissingAndFuturePcmCannotDriveVideo() {
        val recent = loud.copy(sampledAtElapsedRealtimeMs = 1_000L)
        assertSame(recent, recent.freshForVideo(1_500L))
        assertEquals(AudioVisualizationState(), recent.freshForVideo(1_501L))
        assertEquals(AudioVisualizationState(), recent.freshForVideo(999L))
        assertEquals(AudioVisualizationState(), loud.freshForVideo(1_000L))
        val drive = VideoAudioDrive()
        assertTrue(drive.update(IdleVisualizerStyle.AuroraDrift, recent.freshForVideo(1_100L), .033f, false).moving)
        assertFalse(drive.update(IdleVisualizerStyle.AuroraDrift, recent.freshForVideo(1_501L), .033f, false).moving)
    }

    @Test fun allAbstractReplacementsRespondAndHonorReducedIntensity() {
        val styles = com.codeframe78.twentyfourseven.player.domain.AbstractVideoStyles
        assertEquals(12, styles.size)
        styles.forEach { style ->
            assertTrue(style.videoAsset()!!.fileName.startsWith("abstract_"))
            val full = VideoAudioDrive()
            val reduced = VideoAudioDrive()
            var a = VideoAudioFrame()
            var b = VideoAudioFrame()
            repeat(60) {
                a = full.update(style, loud, 1f / 30f, false)
                b = reduced.update(style, loud, 1f / 30f, true)
            }
            assertTrue(style.name, a.moving && a.speed > .5f && a.scaleX > 1f && a.alpha > .55f)
            assertTrue(style.name, b.speed < a.speed && b.scaleX < a.scaleX && b.alpha < a.alpha)
            assertTrue(style.name, a.alpha <= 1f && a.speed <= 2f && a.scaleX <= 1.3f)
        }
    }

}
