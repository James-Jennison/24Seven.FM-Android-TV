package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.runtime.Immutable
import com.codeframe78.twentyfourseven.player.domain.AudioOutputKind
import com.codeframe78.twentyfourseven.player.domain.PlaybackState
import com.codeframe78.twentyfourseven.player.domain.PlaybackStatus

private const val MaximumDiagnosticValueLength = 80
private const val MaximumDiagnosticTransitions = 5
private const val VisualizerTimingSampleCount = 120
private const val VisualizerSlowFrameThresholdMillis = 40f

/**
 * Process-local, debug-only renderer timing evidence. It deliberately records no content,
 * identifiers, routes, or raw logs; release builds never publish a sample.
 */
@Immutable
internal data class VisualizerPerformanceSnapshot(
    val styleName: String,
    val sampleCount: Int,
    val averageFrameMillis: Float,
    val p95FrameMillis: Float,
    val maximumFrameMillis: Float,
    val averageCpuDrawMillis: Float,
    val maximumCpuDrawMillis: Float,
    val displayRefreshRateHz: Float,
    val slowFrameThresholdMillis: Float,
    val overBudgetFrameCount: Int,
)

internal object VisualizerPerformanceDiagnostics {
    @Volatile
    var latest: VisualizerPerformanceSnapshot? = null
        private set

    @Volatile
    private var completedByStyle: Map<String, VisualizerPerformanceSnapshot> = emptyMap()

    fun publish(snapshot: VisualizerPerformanceSnapshot) {
        latest = snapshot
        // Completed windows arrive only from the GL thread. Publish a fresh immutable map so
        // Compose/report readers never observe a mutable collection while it is being updated.
        completedByStyle = LinkedHashMap(completedByStyle).apply {
            remove(snapshot.styleName)
            put(snapshot.styleName, snapshot)
        }
    }

    /** Clears only an incomplete/obsolete active selection; completed catalog samples remain. */
    fun clear() {
        latest = null
    }

    fun completedSnapshots(): List<VisualizerPerformanceSnapshot> = completedByStyle.values.toList()

    fun clearHistory() {
        latest = null
        completedByStyle = emptyMap()
    }
}

/**
 * Fixed-size timing window so renderer diagnostics add no per-frame allocations.
 * Frame values are intervals between consecutive onDrawFrame starts; they may include scheduler
 * or buffer-swap backpressure and are not GPU timing, display-refresh, or FPS measurements.
 */
internal class VisualizerFrameTimingWindow(
    private val capacity: Int = VisualizerTimingSampleCount,
) {
    private val frameMillis = FloatArray(capacity)
    private val cpuDrawMillis = FloatArray(capacity)
    private var styleName = "Unknown"
    private var displayRefreshRateHz = 60f
    private var slowFrameThresholdMillis = VisualizerSlowFrameThresholdMillis
    private var nextIndex = 0
    private var sampleCount = 0

    fun reset(
        styleName: String,
        displayRefreshRateHz: Float = 60f,
        slowFrameThresholdMillis: Float = VisualizerSlowFrameThresholdMillis,
    ) {
        this.styleName = styleName
        this.displayRefreshRateHz = displayRefreshRateHz.coerceAtLeast(1f)
        this.slowFrameThresholdMillis = slowFrameThresholdMillis.coerceAtLeast(1f)
        nextIndex = 0
        sampleCount = 0
    }

    fun record(frameNanos: Long, cpuDrawNanos: Long): VisualizerPerformanceSnapshot? {
        if (capacity <= 0) return null
        frameMillis[nextIndex] = (frameNanos / 1_000_000.0).toFloat().coerceAtLeast(0f)
        cpuDrawMillis[nextIndex] = (cpuDrawNanos / 1_000_000.0).toFloat().coerceAtLeast(0f)
        nextIndex = (nextIndex + 1) % capacity
        sampleCount++
        if (sampleCount < capacity || sampleCount % capacity != 0) return null

        val orderedFrames = frameMillis.copyOf().also { it.sort() }
        val orderedDraws = cpuDrawMillis.copyOf().also { it.sort() }
        return VisualizerPerformanceSnapshot(
            styleName = styleName,
            sampleCount = capacity,
            averageFrameMillis = frameMillis.sum() / capacity,
            // Nearest-rank p95: ceil(0.95 * N) - 1 with a zero-based array index.
            p95FrameMillis = orderedFrames[(kotlin.math.ceil(capacity * .95).toInt() - 1).coerceIn(0, capacity - 1)],
            maximumFrameMillis = orderedFrames.last(),
            averageCpuDrawMillis = cpuDrawMillis.sum() / capacity,
            maximumCpuDrawMillis = orderedDraws.last(),
            displayRefreshRateHz = displayRefreshRateHz,
            slowFrameThresholdMillis = slowFrameThresholdMillis,
            overBudgetFrameCount = frameMillis.count { it > slowFrameThresholdMillis },
        )
    }
}

@Immutable
internal data class DiagnosticEnvironment(
    val appVersion: String = "Unknown",
    val versionCode: Long = 0L,
    val buildType: String = "Unknown",
    val sourceRevision: String = "unverified",
    val androidRelease: String = "Unknown",
    val apiLevel: Int = 0,
    val deviceManufacturer: String = "Unknown",
    val deviceModel: String = "Unknown",
)

@Immutable
internal data class DiagnosticActions(
    val onCopy: (String) -> Unit = {},
    val onShare: (String) -> Unit = {},
)

@Immutable
internal data class DiagnosticUi(
    val environment: DiagnosticEnvironment = DiagnosticEnvironment(),
    val actions: DiagnosticActions = DiagnosticActions(),
)

@Immutable
data class DiagnosticTransition(
    val playbackStatus: PlaybackStatus,
    val networkAvailable: Boolean,
    val audioOutputKind: AudioOutputKind,
)

internal fun buildDiagnosticReport(
    environment: DiagnosticEnvironment,
    stationName: String?,
    playback: PlaybackState,
    transitions: List<DiagnosticTransition>,
): String = buildString {
    appendLine("24Seven.FM diagnostics")
    appendLine("App version: ${safeDiagnosticValue(environment.appVersion)} (${environment.versionCode.coerceAtLeast(0L)})")
    appendLine("Build type: ${safeDiagnosticValue(environment.buildType)}")
    appendLine("Source revision: ${safeDiagnosticValue(environment.sourceRevision)}")
    appendLine("Android: ${safeDiagnosticValue(environment.androidRelease)} (API ${environment.apiLevel.coerceAtLeast(0)})")
    appendLine(
        "Device: ${safeDiagnosticValue(environment.deviceManufacturer)} ${safeDiagnosticValue(environment.deviceModel)}",
    )
    appendLine("Station: ${safeDiagnosticValue(stationName ?: "None selected")}")
    appendLine("Playback: ${playback.status.diagnosticLabel()}")
    appendLine("Error category: ${playback.errorCategoryLabel()}")
    appendLine("Network: ${if (playback.networkAvailable) "Available" else "Unavailable"}")
    appendLine("Audio output: ${playback.audioOutput.kind.diagnosticLabel()}")
    VisualizerPerformanceDiagnostics.latest?.let { timing ->
        appendLine(
            "Visualizer timing: ${safeDiagnosticValue(timing.styleName)} | ${timing.sampleCount} frames | " +
                "display %.1f Hz | callback interval avg/p95/max %.1f/%.1f/%.1f ms | CPU draw avg/max %.1f/%.1f ms | >%.1f ms ${timing.overBudgetFrameCount}".format(
                    java.util.Locale.US,
                    timing.displayRefreshRateHz,
                    timing.averageFrameMillis,
                    timing.p95FrameMillis,
                    timing.maximumFrameMillis,
                    timing.averageCpuDrawMillis,
                    timing.maximumCpuDrawMillis,
                    timing.slowFrameThresholdMillis,
                ),
        )
    }
    VisualizerPerformanceDiagnostics.completedSnapshots().takeIf { it.isNotEmpty() }?.let { completed ->
        appendLine("Visualizer timing history: ${completed.size} completed style samples")
        completed.forEach { timing ->
            appendLine(
                "- ${safeDiagnosticValue(timing.styleName)} | p95 %.1f ms | CPU max %.1f ms | >%.1f ms ${timing.overBudgetFrameCount}".format(
                    java.util.Locale.US,
                    timing.p95FrameMillis,
                    timing.maximumCpuDrawMillis,
                    timing.slowFrameThresholdMillis,
                ),
            )
        }
    }
    appendLine("Recent playback transitions (oldest to newest):")
    transitions.takeLast(MaximumDiagnosticTransitions).forEachIndexed { index, transition ->
        appendLine(
            "${index + 1}. ${transition.playbackStatus.diagnosticLabel()} | " +
                "${if (transition.networkAvailable) "Network available" else "Network unavailable"} | " +
                transition.audioOutputKind.diagnosticLabel(),
        )
    }
    if (transitions.isEmpty()) appendLine("None recorded")
    append("Privacy: This snapshot excludes account data, messages, content, URLs, identifiers, raw errors, and logs.")
}

private fun safeDiagnosticValue(value: String): String = value
    .asSequence()
    .filterNot(Char::isISOControl)
    .joinToString("")
    .trim()
    .ifEmpty { "Unknown" }
    .take(MaximumDiagnosticValueLength)

private fun PlaybackState.errorCategoryLabel(): String = when (status) {
    PlaybackStatus.WaitingForNetwork -> "Network unavailable"
    PlaybackStatus.Error -> "Playback failure"
    else -> "None"
}

private fun PlaybackStatus.diagnosticLabel(): String = when (this) {
    PlaybackStatus.Idle -> "Idle"
    PlaybackStatus.Connecting -> "Connecting"
    PlaybackStatus.Buffering -> "Buffering"
    PlaybackStatus.Playing -> "Playing"
    PlaybackStatus.Paused -> "Paused"
    PlaybackStatus.Retrying -> "Retrying alternate stream"
    PlaybackStatus.WaitingForNetwork -> "Waiting for network"
    PlaybackStatus.Error -> "Error"
}

private fun AudioOutputKind.diagnosticLabel(): String = when (this) {
    AudioOutputKind.Device -> "This device"
    AudioOutputKind.Bluetooth -> "Bluetooth"
    AudioOutputKind.Wired -> "Wired or USB"
    AudioOutputKind.Remote -> "System-managed remote output"
}
