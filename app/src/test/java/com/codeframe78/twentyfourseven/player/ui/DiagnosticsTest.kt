package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioOutputKind
import com.codeframe78.twentyfourseven.player.domain.AudioOutputState
import com.codeframe78.twentyfourseven.player.domain.PlaybackState
import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerRenderProfile
import com.codeframe78.twentyfourseven.player.domain.metadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun `visualizer timing window publishes a bounded rolling sample`() {
        val timing = VisualizerFrameTimingWindow(capacity = 4)
        timing.reset("Lava Lamp")

        assertEquals(null, timing.record(16_000_000L, 2_000_000L))
        assertEquals(null, timing.record(20_000_000L, 3_000_000L))
        assertEquals(null, timing.record(42_000_000L, 4_000_000L))
        val snapshot = timing.record(18_000_000L, 5_000_000L)

        assertNotNull(snapshot)
        checkNotNull(snapshot)
        assertEquals("Lava Lamp", snapshot.styleName)
        assertEquals(4, snapshot.sampleCount)
        assertEquals(1, snapshot.overBudgetFrameCount)
        assertTrue(snapshot.maximumFrameMillis >= 42f)
        assertTrue(snapshot.maximumCpuDrawMillis >= 5f)
    }

    @Test
    fun `visualizer timing window replaces the previous complete window`() {
        val timing = VisualizerFrameTimingWindow(capacity = 4)
        timing.reset("Signal Scope")

        repeat(4) { timing.record(10_000_000L, 1_000_000L) }
        repeat(3) { timing.record(20_000_000L, 2_000_000L) }
        val latest = timing.record(20_000_000L, 2_000_000L)

        assertNotNull(latest)
        checkNotNull(latest)
        assertEquals(20f, latest.averageFrameMillis)
        assertEquals(20f, latest.maximumFrameMillis)
        assertEquals(2f, latest.averageCpuDrawMillis)
    }

    @Test
    fun `visualizer timing window uses nearest rank p95`() {
        val timing = VisualizerFrameTimingWindow(capacity = 20)
        timing.reset("Deep Warp")
        var snapshot: VisualizerPerformanceSnapshot? = null
        repeat(20) { index -> snapshot = timing.record((index + 1) * 1_000_000L, 1_000_000L) }

        assertNotNull(snapshot)
        assertEquals(19f, checkNotNull(snapshot).p95FrameMillis)
    }

    @Test
    fun `visualizer timing window carries its refresh aware frame budget`() {
        val timing = VisualizerFrameTimingWindow(capacity = 4)
        timing.reset(
            styleName = "Lava Lamp",
            displayRefreshRateHz = 24f,
            slowFrameThresholdMillis = 104.16f,
        )
        var snapshot: VisualizerPerformanceSnapshot? = null
        repeat(4) { snapshot = timing.record(42_000_000L, 2_000_000L) }

        assertNotNull(snapshot)
        assertEquals(24f, checkNotNull(snapshot).displayRefreshRateHz, .001f)
        assertEquals(104.16f, snapshot.slowFrameThresholdMillis, .001f)
        assertEquals(0, snapshot.overBudgetFrameCount)
    }

    @Test
    fun `visualizer diagnostics clears obsolete renderer samples`() {
        VisualizerPerformanceDiagnostics.clearHistory()
        VisualizerPerformanceDiagnostics.publish(
            VisualizerPerformanceSnapshot(
                styleName = "Lava Lamp",
                sampleCount = 120,
                averageFrameMillis = 16f,
                p95FrameMillis = 17f,
                maximumFrameMillis = 18f,
                averageCpuDrawMillis = 1f,
                maximumCpuDrawMillis = 2f,
                displayRefreshRateHz = 60f,
                slowFrameThresholdMillis = 41.7f,
                overBudgetFrameCount = 0,
            ),
        )
        VisualizerPerformanceDiagnostics.clear()

        assertEquals(null, VisualizerPerformanceDiagnostics.latest)
        assertEquals(1, VisualizerPerformanceDiagnostics.completedSnapshots().size)
    }

    @Test
    fun `visualizer diagnostics retains one completed sample per style`() {
        VisualizerPerformanceDiagnostics.clearHistory()
        val lava = VisualizerPerformanceSnapshot(
            styleName = "Lava Lamp",
            sampleCount = 120,
            averageFrameMillis = 20f,
            p95FrameMillis = 25f,
            maximumFrameMillis = 30f,
            averageCpuDrawMillis = 1f,
            maximumCpuDrawMillis = 2f,
            displayRefreshRateHz = 60f,
            slowFrameThresholdMillis = 41.7f,
            overBudgetFrameCount = 0,
        )
        VisualizerPerformanceDiagnostics.publish(lava)
        VisualizerPerformanceDiagnostics.publish(lava.copy(p95FrameMillis = 24f))
        VisualizerPerformanceDiagnostics.publish(lava.copy(styleName = "Light Trails"))

        assertEquals(listOf("Lava Lamp", "Light Trails"), VisualizerPerformanceDiagnostics.completedSnapshots().map { it.styleName })
        assertEquals(24f, VisualizerPerformanceDiagnostics.completedSnapshots().first().p95FrameMillis)
    }

    @Test
    fun `visualizer metadata keeps stable profiles for feedback styles`() {
        assertEquals(IdleVisualizerRenderProfile.Video, IdleVisualizerStyle.LavaLamp.metadata.renderProfile)
        assertEquals(IdleVisualizerRenderProfile.Direct, IdleVisualizerStyle.LightTrails.metadata.renderProfile)
        assertEquals(IdleVisualizerRenderProfile.Video, IdleVisualizerStyle.OscilloscopeTunnel.metadata.renderProfile)
    }

    @Test
    fun `report uses an allowlist and excludes raw errors and route names`() {
        val report = buildDiagnosticReport(
            environment = DiagnosticEnvironment(
                appVersion = "0.1.0-alpha01-debug",
                versionCode = 2,
                buildType = "debug",
                sourceRevision = "a".repeat(40),
                androidRelease = "16",
                apiLevel = 36,
                deviceManufacturer = "motorola",
                deviceModel = "razr plus 2024",
            ),
            stationName = "StreamingSoundtracks.com",
            playback = PlaybackState(
                status = PlaybackStatus.Error,
                errorMessage = "https://private.example/?token=secret-value",
                networkAvailable = true,
                audioOutput = AudioOutputState("James's private JBL", AudioOutputKind.Bluetooth),
            ),
            transitions = listOf(
                DiagnosticTransition(PlaybackStatus.Buffering, true, AudioOutputKind.Device),
                DiagnosticTransition(PlaybackStatus.Error, true, AudioOutputKind.Bluetooth),
            ),
        )

        assertTrue(report.contains("Error category: Playback failure"))
        assertTrue(report.contains("Audio output: Bluetooth"))
        assertTrue(report.contains("Source revision: " + "a".repeat(40)))
        assertTrue(report.contains("StreamingSoundtracks.com"))
        assertFalse(report.contains("private.example"))
        assertFalse(report.contains("secret-value"))
        assertFalse(report.contains("James"))
    }

    @Test
    fun `report bounds and sanitizes externally supplied labels and transition history`() {
        val report = buildDiagnosticReport(
            environment = DiagnosticEnvironment(
                appVersion = "line one\nline two" + "x".repeat(100),
                deviceManufacturer = "Maker\r\nInjected",
            ),
            stationName = "Station\nInjected",
            playback = PlaybackState(),
            transitions = List(7) {
                DiagnosticTransition(PlaybackStatus.Idle, false, AudioOutputKind.Device)
            },
        )

        assertFalse(report.contains("\nline two"))
        assertFalse(report.contains("\nInjected"))
        assertFalse(report.contains("6. "))
        assertTrue(report.contains("5. Idle"))
    }
}
