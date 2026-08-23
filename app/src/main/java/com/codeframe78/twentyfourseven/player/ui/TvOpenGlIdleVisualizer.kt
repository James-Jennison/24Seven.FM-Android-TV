package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import android.app.ActivityManager
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig as AndroidEglConfig
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import android.view.Choreographer
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationBandCount
import com.codeframe78.twentyfourseven.player.domain.AudioWaveformSampleCount
import com.codeframe78.twentyfourseven.player.domain.CalibratedBandCount
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Geometry-first GLES renderer: rich vertex scenes and a deliberately minimal colour shader. */
@Composable
internal fun TvIdleVisualizer(
    style: IdleVisualizerStyle,
    audioState: StateFlow<AudioVisualizationState>,
    stationName: String? = null,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) TvOpenGlIdleVisualizer(style, audioState, stationName, modifier)
    else TvCanvasIdleVisualizer(style, audioState, modifier)
}

@Composable
private fun TvOpenGlIdleVisualizer(
    style: IdleVisualizerStyle,
    audioState: StateFlow<AudioVisualizationState>,
    stationName: String?,
    modifier: Modifier,
) {
    var surface by remember { mutableStateOf<TvVisualizerSurfaceView?>(null) }
    var signalDeckReadings by remember { mutableStateOf(List(4) { "-30 dB" }) }
    var masterReadings by remember { mutableStateOf(listOf("-30 dB", "-30 dB")) }
    var signalDeckStereo by remember { mutableStateOf(false) }
    val signalDeckLabelPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(220, 235, 255)
            alpha = 190
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> TvVisualizerSurfaceView(context).also { surface = it; it.updateStyle(style) } },
            update = { it.updateStyle(style) },
        )
        if (style == IdleVisualizerStyle.VinylPulse) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centres = floatArrayOf(.19f, .395f, .605f, .81f)
                val labels = arrayOf("SUB", "LOW", "MID", "HIGH")
                val frequencyRanges = arrayOf("20–80 Hz", "80–250 Hz", "250 Hz–2 kHz", "2–14 kHz")
                val panelInset = size.width * .045f
                drawRoundRect(
                    color = Color(0x1A9AB9D8),
                    topLeft = Offset(panelInset, size.height * .065f),
                    size = Size(size.width - panelInset * 2f, size.height * .88f),
                    cornerRadius = CornerRadius(size.height * .022f),
                )
                drawRoundRect(
                    color = Color(0x557FA5C8),
                    topLeft = Offset(panelInset, size.height * .065f),
                    size = Size(size.width - panelInset * 2f, size.height * .88f),
                    cornerRadius = CornerRadius(size.height * .022f),
                    style = Stroke(width = size.height * .0022f),
                )
                drawLine(
                    color = Color(0x507FA5C8),
                    start = Offset(panelInset + size.width * .025f, size.height * .255f),
                    end = Offset(size.width - panelInset - size.width * .025f, size.height * .255f),
                    strokeWidth = size.height * .0015f,
                )
                drawLine(
                    color = Color(0x507FA5C8),
                    start = Offset(panelInset + size.width * .025f, size.height * .805f),
                    end = Offset(size.width - panelInset - size.width * .025f, size.height * .805f),
                    strokeWidth = size.height * .0015f,
                )
                listOf(.07f to .09f, .93f to .09f, .07f to .93f, .93f to .93f).forEach { (x, y) ->
                    drawCircle(Color(0x999FB7CA), radius = size.height * .005f, center = Offset(size.width * x, size.height * y))
                    drawCircle(Color(0x66080C12), radius = size.height * .0022f, center = Offset(size.width * x, size.height * y))
                }
                drawIntoCanvas { canvas ->
                    signalDeckLabelPaint.textAlign = AndroidPaint.Align.LEFT
                    signalDeckLabelPaint.textSize = size.height * .040f
                    signalDeckLabelPaint.alpha = 215
                    val deckStationName = signalDeckLabelPaint.truncateToWidth(
                        stationName?.takeIf { it.isNotBlank() } ?: "NO STATION SELECTED",
                        size.width * .45f,
                    )
                    canvas.nativeCanvas.drawText(deckStationName, size.width * .065f, size.height * .145f, signalDeckLabelPaint)
                    signalDeckLabelPaint.textSize = size.height * .020f
                    signalDeckLabelPaint.alpha = 135
                    canvas.nativeCanvas.drawText("SIGNAL DECK  •  PCM SPECTRUM / dBFS MASTER", size.width * .065f, size.height * .190f, signalDeckLabelPaint)
                    signalDeckLabelPaint.textAlign = AndroidPaint.Align.CENTER
                    val indicators = listOf("LIVE" to Color(0xFF73F6B3), "PCM" to Color(0xFF62BEFF), (if (signalDeckStereo) "STEREO" else "MONO") to Color(0xFFDA8CFF))
                    indicators.forEachIndexed { index, (label, color) ->
                        val x = size.width * (.62f + index * .115f)
                        canvas.nativeCanvas.drawCircle(x, size.height * .143f, size.height * .007f, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
                        signalDeckLabelPaint.textSize = size.height * .018f
                        signalDeckLabelPaint.alpha = 170
                        canvas.nativeCanvas.drawText(label, x + size.width * .035f, size.height * .150f, signalDeckLabelPaint)
                    }
                    centres.forEach { centre ->
                        val scaleLabels = arrayOf("-30", "-20", "-10", "0")
                        scaleLabels.forEachIndexed { index, scaleLabel ->
                            signalDeckLabelPaint.textSize = size.height * .019f
                            signalDeckLabelPaint.alpha = 125
                            val angle = Tau * (.39f - .28f * index / 3f)
                            val scaleRadius = size.height * .185f
                            val x = size.width * centre + cos(angle) * scaleRadius
                            val y = size.height * .57f - sin(angle) * scaleRadius
                            canvas.nativeCanvas.drawText(scaleLabel, x, y, signalDeckLabelPaint)
                        }
                    }
                    centres.forEachIndexed { index, centre ->
                        signalDeckLabelPaint.textSize = size.height * .018f
                        signalDeckLabelPaint.alpha = 120
                        canvas.nativeCanvas.drawText(frequencyRanges[index], size.width * centre, size.height * .690f, signalDeckLabelPaint)
                        signalDeckLabelPaint.textSize = size.height * .032f
                        signalDeckLabelPaint.alpha = 190
                        canvas.nativeCanvas.drawText(labels[index], size.width * centre, size.height * .735f, signalDeckLabelPaint)
                        signalDeckLabelPaint.textSize = size.height * .023f
                        signalDeckLabelPaint.alpha = 155
                        canvas.nativeCanvas.drawText(signalDeckReadings[index], size.width * centre, size.height * .782f, signalDeckLabelPaint)
                    }
                    signalDeckLabelPaint.textSize = size.height * .022f
                    signalDeckLabelPaint.alpha = 160
                    canvas.nativeCanvas.drawText("MASTER L", size.width * .41f, size.height * .875f, signalDeckLabelPaint)
                    canvas.nativeCanvas.drawText("MASTER R", size.width * .59f, size.height * .875f, signalDeckLabelPaint)
                    signalDeckLabelPaint.textSize = size.height * .018f
                    signalDeckLabelPaint.alpha = 130
                    canvas.nativeCanvas.drawText(masterReadings[0], size.width * .41f, size.height * .910f, signalDeckLabelPaint)
                    canvas.nativeCanvas.drawText(masterReadings[1], size.width * .59f, size.height * .910f, signalDeckLabelPaint)
                }
            }
        }
    }
    LaunchedEffect(surface, audioState, style) {
        val activeSurface = surface ?: return@LaunchedEffect
        var lastSignalDeckReadoutAt = 0L
        audioState.collect { audio ->
            activeSurface.updateAudio(audio)
            val now = SystemClock.elapsedRealtime()
            if (style == IdleVisualizerStyle.VinylPulse && now - lastSignalDeckReadoutAt >= 125L) {
                lastSignalDeckReadoutAt = now
                signalDeckReadings = audio.calibratedBandDbfs.map(::formatDbfs)
                masterReadings = listOf(audio.leftDbfs, audio.rightDbfs).map(::formatDbfs)
                signalDeckStereo = audio.channelCount > 1
            }
        }
    }
    DisposableEffect(Unit) { onDispose { surface?.releaseRenderer() } }
}

/**
 * A TextureView is deliberately used for the high-detail visualizer instead of a
 * direct SurfaceView overlay.  The Streamer can scan a direct overlay while its GL
 * buffer is changing, which was visible as a left-edge/horizontal seam.  TextureView
 * is composited as an ordinary app layer and therefore latches complete buffers.
 */
private class TvVisualizerTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private val renderer = TvGeometryVisualizerRenderer()
    private val supportsEs3 = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    private var renderThread: TvVisualizerRenderThread? = null
    private var desiredStyle = IdleVisualizerStyle.NeonKaleidoscope.ordinal
    private var desiredEnergy = 0f
    private var desiredBands = FloatArray(BandCount)
    private var desiredTransient = 0f

    init {
        isOpaque = true
        surfaceTextureListener = this
    }

    fun updateStyle(style: IdleVisualizerStyle) {
        desiredStyle = style.ordinal
        renderThread?.post { renderer.setStyle(desiredStyle) }
    }

    fun updateAudio(audio: AudioVisualizationState) {
        val compact = FloatArray(BandCount) { group ->
            val start = group * audio.bands.size / BandCount
            val end = ((group + 1) * audio.bands.size / BandCount).coerceAtLeast(start + 1)
            var total = 0f
            for (index in start until end) total += audio.bands[index]
            total / (end - start)
        }
        desiredEnergy = audio.energy
        desiredBands = compact
        desiredTransient = audio.transient
        renderThread?.post { renderer.setAudioTarget(desiredEnergy, compact, desiredTransient) }
    }

    fun releaseRenderer() {
        renderThread?.shutdown()
        renderThread = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        TvVisualizerRenderThread(surface, width, height, renderer, supportsEs3) { desiredStyle to Triple(desiredEnergy, desiredBands, desiredTransient) }
            .also { thread -> renderThread = thread; thread.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.post { renderer.onSurfaceChanged(null, width, height) }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releaseRenderer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

private class TvVisualizerRenderThread(
    private val surfaceTexture: SurfaceTexture,
    private val initialWidth: Int,
    private val initialHeight: Int,
    private val renderer: TvGeometryVisualizerRenderer,
    private val supportsEs3: Boolean,
    private val initialState: () -> Pair<Int, Triple<Float, FloatArray, Float>>,
) : Thread("24SevenVisualizer") {
    private val work = ConcurrentLinkedQueue<() -> Unit>()
    @Volatile private var running = true

    fun post(action: () -> Unit) { work.add(action) }
    fun shutdown() { running = false; interrupt() }

    override fun run() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "TV visualizer EGL initialization failed" }
        val config = chooseConfig(display, supportsEs3)
        val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, if (supportsEs3) 3 else 2, EGL14.EGL_NONE)
        val eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "TV visualizer EGL context unavailable" }
        val nativeSurface = Surface(surfaceTexture)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, nativeSurface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "TV visualizer EGL surface unavailable" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) { "TV visualizer EGL current context unavailable" }
        EGL14.eglSwapInterval(display, 1)
        renderer.setSupportsEs3(supportsEs3)
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, initialWidth, initialHeight)
        initialState().let { (style, audio) ->
            renderer.setStyle(style)
            renderer.setAudioTarget(audio.first, audio.second, audio.third)
        }

        var nextFrame = SystemClock.elapsedRealtimeNanos()
        while (running) {
            while (true) work.poll()?.invoke() ?: break
            renderer.onDrawFrame(null)
            EGL14.eglSwapBuffers(display, eglSurface)
            nextFrame += FrameIntervalNanos
            val remaining = nextFrame - SystemClock.elapsedRealtimeNanos()
            if (remaining > 0L) sleep(remaining / 1_000_000L, (remaining % 1_000_000L).toInt())
            else nextFrame = SystemClock.elapsedRealtimeNanos()
        }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglDestroyContext(display, eglContext)
        EGL14.eglTerminate(display)
        nativeSurface.release()
    }

    private fun chooseConfig(display: EGLDisplay, supportsEs3: Boolean): AndroidEglConfig {
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, if (supportsEs3) EglOpenGlEs3Bit else EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<AndroidEglConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "TV visualizer EGL configuration unavailable" }
        return checkNotNull(configs[0])
    }
}

private fun AndroidPaint.truncateToWidth(text: String, maxWidth: Float): String {
    if (measureText(text) <= maxWidth) return text
    val ellipsis = "…"
    val length = breakText(text, true, (maxWidth - measureText(ellipsis)).coerceAtLeast(0f), null)
    return text.take(length) + ellipsis
}

private fun formatDbfs(value: Float) = "${value.coerceIn(MasterMeterFloorDbfs, 0f).toInt()} dBFS"

private fun dbfsToMasterMeter(value: Float) =
    ((value.coerceIn(MasterMeterFloorDbfs, 0f) - MasterMeterFloorDbfs) / -MasterMeterFloorDbfs).coerceIn(0f, 1f)

private class TvVisualizerSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = TvGeometryVisualizerRenderer()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            requestRender()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        // The visualizer still has a GLES 2 geometry fallback, but the Streamer and
        // newer TVs can use the single-pass GLES 3 scene below.  That moves the
        // dense moving field onto the GPU instead of rebuilding hundreds of paths
        // on the UI/GL thread every frame.
        val supportsEs3 = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo.reqGlEsVersion >= 0x30000
        setEGLContextClientVersion(if (supportsEs3) 3 else 2)
        renderer.setSupportsEs3(supportsEs3)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        // Continuous mode can outrun the Streamer's compositor and expose two
        // animation frames in one TV refresh.  Rendering from Choreographer keeps
        // each new frame aligned with the display's vsync.
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    fun updateStyle(style: IdleVisualizerStyle) {
        queueEvent { renderer.setStyle(style.ordinal) }
    }

    fun updateAudio(audio: AudioVisualizationState) {
        val compact = FloatArray(BandCount) { group ->
            val start = group * audio.bands.size / BandCount
            val end = ((group + 1) * audio.bands.size / BandCount).coerceAtLeast(start + 1)
            var total = 0f
            for (index in start until end) total += audio.bands[index]
            total / (end - start)
        }
        val rawSpectrum = FloatArray(AudioVisualizationBandCount) { audio.bands.getOrElse(it) { 0f } }
        val rawWaveform = FloatArray(AudioWaveformSampleCount) { audio.waveform.getOrElse(it) { 0f } }
        queueEvent {
            renderer.setAudioTarget(
                energy = audio.energy,
                bands = compact,
                transient = audio.transient,
                rawSpectrum = rawSpectrum,
                leftMeterLevel = dbfsToMasterMeter(audio.leftDbfs),
                rightMeterLevel = dbfsToMasterMeter(audio.rightDbfs),
                calibratedMeterLevels = FloatArray(CalibratedBandCount) {
                    dbfsToMasterMeter(audio.calibratedBandDbfs.getOrElse(it) { MasterMeterFloorDbfs })
                },
                waveformSamples = rawWaveform,
            )
        }
    }

    fun releaseRenderer() = onPause()
}

private class TvGeometryVisualizerRenderer : GLSurfaceView.Renderer {
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(MaxVertices * 2 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val feedbackQuad: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
            position(0)
        }
    private val targets = FloatArray(BandCount)
    private val bands = FloatArray(BandCount)
    private val spectrumTargets = FloatArray(AudioVisualizationBandCount)
    private val spectrumBands = FloatArray(AudioVisualizationBandCount)
    private val spectrumPeaks = FloatArray(AudioVisualizationBandCount)
    private val calibratedMeterTargets = FloatArray(CalibratedBandCount)
    private val calibratedMeters = FloatArray(CalibratedBandCount)
    private val calibratedMeterPeaks = FloatArray(CalibratedBandCount)
    private val waveformTargets = FloatArray(AudioWaveformSampleCount)
    private val waveform = FloatArray(AudioWaveformSampleCount)
    private val particleX = FloatArray(ParticleCount)
    private val particleY = FloatArray(ParticleCount)
    private val particleSpeed = FloatArray(ParticleCount)
    private var targetEnergy = 0f
    private var energy = 0f
    private var targetMasterLeft = 0f
    private var targetMasterRight = 0f
    private var masterLeft = 0f
    private var masterRight = 0f
    private var masterLeftPeak = 0f
    private var masterRightPeak = 0f
    private var targetImpulse = 0f
    private var impulse = 0f
    private var targetTransient = 0f
    private var transient = 0f
    private var impact = 0f
    private var impactPhase = 0f
    private var style = 0
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var pointSizeHandle = 0
    private var feedbackProgram = 0
    private var feedbackPositionHandle = 0
    private var feedbackUvHandle = 0
    private var feedbackSamplerHandle = 0
    private var feedbackZoomHandle = 0
    private var feedbackRotationHandle = 0
    private var feedbackWarpHandle = 0
    private var feedbackPhaseHandle = 0
    private var feedbackDecayHandle = 0
    private var neonProgram = 0
    private var neonPositionHandle = 0
    private var neonResolutionHandle = 0
    private var neonPhaseHandle = 0
    private var neonEnergyHandle = 0
    private var neonImpulseHandle = 0
    private var neonTransientHandle = 0
    private var neonImpactHandle = 0
    private var neonBandsHandle = 0
    private var waterfallProgram = 0
    private var waterfallPositionHandle = 0
    private var waterfallSamplerHandle = 0
    private val waterfallTexture = IntArray(1)
    private val spectrumHistoryPixels = ByteBuffer.allocateDirect(AudioVisualizationBandCount * SpectrumHistoryRows)
    private var spectrumHistoryFrame = 0L
    private val feedbackTextures = IntArray(2)
    private val feedbackFramebuffers = IntArray(2)
    private var feedbackWidth = 1
    private var feedbackHeight = 1
    private var feedbackIndex = 0
    private var clearFeedback = true
    private var width = 1
    private var height = 1
    private var previousNanos = 0L
    private var phase = 0f
    private var deepWarpTravel = 0f
    private var frameNumber = 0L
    private var supportsEs3 = false

    init {
        repeat(ParticleCount) { index ->
            particleX[index] = (index * 47 % 251) / 125f - 1f
            particleY[index] = (index * 83 % 241) / 120f - 1f
            particleSpeed[index] = .12f + (index % 11) * .035f
        }
    }

    fun setStyle(style: Int) {
        val styleChanged = this.style != style
        if (styleChanged) clearFeedback = true
        this.style = style
        if (styleChanged && width > 1 && usesFeedback()) createFeedbackBuffers()
    }

    fun setSupportsEs3(supportsEs3: Boolean) {
        this.supportsEs3 = supportsEs3
    }

    fun setAudioTarget(
        energy: Float,
        bands: FloatArray,
        transient: Float,
        rawSpectrum: FloatArray? = null,
        leftMeterLevel: Float = 0f,
        rightMeterLevel: Float = 0f,
        calibratedMeterLevels: FloatArray? = null,
        waveformSamples: FloatArray? = null,
    ) {
        val incomingEnergy = energy.coerceIn(0f, 1f)
        var frequencyChange = 0f
        for (index in targets.indices) frequencyChange += abs(bands[index] - targets[index])
        targetImpulse = (abs(incomingEnergy - targetEnergy) * 1.6f + frequencyChange / BandCount * 4.8f).coerceIn(0f, 1f)
        targetEnergy = incomingEnergy
        val incomingTransient = transient.coerceIn(0f, 1f)
        if (incomingTransient > targetTransient + .035f) {
            impact = maxOf(impact, incomingTransient)
            impactPhase += 2.17f + incomingTransient * 3.6f
        }
        targetTransient = incomingTransient
        targetMasterLeft = leftMeterLevel.coerceIn(0f, 1f)
        targetMasterRight = rightMeterLevel.coerceIn(0f, 1f)
        calibratedMeterLevels?.copyInto(calibratedMeterTargets, endIndex = minOf(calibratedMeterLevels.size, calibratedMeterTargets.size))
        waveformSamples?.copyInto(waveformTargets, endIndex = minOf(waveformSamples.size, waveformTargets.size))
        bands.copyInto(targets)
        rawSpectrum?.copyInto(spectrumTargets, endIndex = minOf(rawSpectrum.size, spectrumTargets.size))
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        EGL14.eglSwapInterval(EGL14.eglGetCurrentDisplay(), 1)
        program = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, VertexShader), compileShader(GLES20.GL_FRAGMENT_SHADER, FragmentShader))
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
        feedbackProgram = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, FeedbackVertexShader), compileShader(GLES20.GL_FRAGMENT_SHADER, FeedbackFragmentShader))
        feedbackPositionHandle = GLES20.glGetAttribLocation(feedbackProgram, "aPosition")
        feedbackUvHandle = GLES20.glGetAttribLocation(feedbackProgram, "aUv")
        feedbackSamplerHandle = GLES20.glGetUniformLocation(feedbackProgram, "uFeedback")
        feedbackZoomHandle = GLES20.glGetUniformLocation(feedbackProgram, "uZoom")
        feedbackRotationHandle = GLES20.glGetUniformLocation(feedbackProgram, "uRotation")
        feedbackWarpHandle = GLES20.glGetUniformLocation(feedbackProgram, "uWarp")
        feedbackPhaseHandle = GLES20.glGetUniformLocation(feedbackProgram, "uPhase")
        feedbackDecayHandle = GLES20.glGetUniformLocation(feedbackProgram, "uDecay")
        if (supportsEs3) {
            neonProgram = linkProgram(
                compileShader(GLES20.GL_VERTEX_SHADER, NeonVertexShader),
                compileShader(GLES20.GL_FRAGMENT_SHADER, NeonFragmentShader),
            )
            neonPositionHandle = GLES20.glGetAttribLocation(neonProgram, "aPosition")
            neonResolutionHandle = GLES20.glGetUniformLocation(neonProgram, "uResolution")
            neonPhaseHandle = GLES20.glGetUniformLocation(neonProgram, "uPhase")
            neonEnergyHandle = GLES20.glGetUniformLocation(neonProgram, "uEnergy")
            neonImpulseHandle = GLES20.glGetUniformLocation(neonProgram, "uImpulse")
            neonTransientHandle = GLES20.glGetUniformLocation(neonProgram, "uTransient")
            neonImpactHandle = GLES20.glGetUniformLocation(neonProgram, "uImpact")
            neonBandsHandle = GLES20.glGetUniformLocation(neonProgram, "uBands")
            waterfallProgram = linkProgram(
                compileShader(GLES20.GL_VERTEX_SHADER, WaterfallVertexShader),
                compileShader(GLES20.GL_FRAGMENT_SHADER, WaterfallFragmentShader),
            )
            waterfallPositionHandle = GLES20.glGetAttribLocation(waterfallProgram, "aPosition")
            waterfallSamplerHandle = GLES20.glGetUniformLocation(waterfallProgram, "uHistory")
            GLES20.glGenTextures(1, waterfallTexture, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waterfallTexture[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES30.GL_R8,
                AudioVisualizationBandCount,
                SpectrumHistoryRows,
                0,
                GLES30.GL_RED,
                GLES20.GL_UNSIGNED_BYTE,
                spectrumHistoryPixels.position(0),
            )
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glClearColor(.002f, .006f, .014f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, this.width, this.height)
        createFeedbackBuffers()
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.elapsedRealtimeNanos()
        val delta = if (previousNanos == 0L) 1f / 60f else ((now - previousNanos) / NanosPerSecond).toFloat().coerceIn(0f, .05f)
        previousNanos = now
        val smoothing = 1f - exp((-delta * AudioSmoothing).toDouble()).toFloat()
        energy += (targetEnergy - energy) * smoothing
        masterLeft = advanceMasterMeter(masterLeft, targetMasterLeft, delta)
        masterRight = advanceMasterMeter(masterRight, targetMasterRight, delta)
        masterLeftPeak = maxOf(masterLeft, masterLeftPeak - delta * MasterPeakFallPerSecond)
        masterRightPeak = maxOf(masterRight, masterRightPeak - delta * MasterPeakFallPerSecond)
        for (index in calibratedMeters.indices) {
            calibratedMeters[index] += (calibratedMeterTargets[index] - calibratedMeters[index]) * smoothing
            calibratedMeterPeaks[index] = maxOf(calibratedMeters[index], calibratedMeterPeaks[index] - delta * SpectrumPeakFallPerSecond)
        }
        for (index in waveform.indices) waveform[index] += (waveformTargets[index] - waveform[index]) * smoothing
        for (index in bands.indices) bands[index] += (targets[index] - bands[index]) * smoothing
        for (index in spectrumBands.indices) {
            spectrumBands[index] += (spectrumTargets[index] - spectrumBands[index]) * smoothing
            spectrumPeaks[index] = maxOf(spectrumBands[index], spectrumPeaks[index] - delta * SpectrumPeakFallPerSecond)
        }
        impulse += (targetImpulse - impulse) * (1f - exp((-delta * ImpulseSmoothing).toDouble()).toFloat())
        transient += (targetTransient - transient) * (1f - exp((-delta * TransientSmoothing).toDouble()).toFloat())
        impact *= exp((-delta * ImpactDecay).toDouble()).toFloat()
        if (style == IdleVisualizerStyle.StarfieldWarp.ordinal) {
            // Deep Warp has no clock-driven camera movement: only measured sub and
            // low-frequency energy advances the viewer through the star field.
            val bassMotion = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
            // Do not wrap the shared travel clock: that reset made every star jump
            // at once. Individual stars recycle quietly at the vanishing point.
            deepWarpTravel += delta * bassMotion * DeepWarpTravelPerSecond
        }
        // Phase is a continuity accumulator, not an autonomous animation clock.
        // It advances only while decoded audio reports energy, spectral change, or
        // a recent attack; at silence all phase-led visuals settle in place.
        // Light Trails keeps the stronger measured-attack multiplier so its
        // feedback field remains expressive without becoming decorative motion.
        val impactMotion = when (style) {
            IdleVisualizerStyle.LightTrails.ordinal -> 13f
            IdleVisualizerStyle.NeonKaleidoscope.ordinal -> 4.5f
            else -> 2.4f
        }
        phase += delta * (energy * 6.2f + impulse * 7.8f + transient * 6f + impact * impactMotion)
        frameNumber++

        if (usesFeedback()) {
            renderFeedbackBase()
            renderSceneGeometry()
            presentFeedback()
        } else {
            renderDirectBase()
            renderSceneGeometry()
        }
    }

    private fun renderSceneGeometry() {
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glEnableVertexAttribArray(positionHandle)
        when (style) {
            0 -> kaleidoscope()
            1 -> fluidBloom()
            2 -> signalScope()
            3 -> mirrorScript()
            4 -> orbitBloom()
            5 -> glassCurrent()
            6 -> vectorSwitchboard()
            7 -> deepWarp()
            8 -> chromafield()
            9 -> lightTrails()
            10 -> eqSpectrum()
            else -> signalDeck()
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun usesFeedback() = style == IdleVisualizerStyle.LightTrails.ordinal

    private fun renderDirectBase() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun kaleidoscope() {
        if (neonProgram != 0) shaderKaleidoscope() else vortexScene()
    }

    /**
     * The high-detail Neon scene is calculated once per pixel, in one opaque draw.
     * That gives it smooth continuous filaments and avoids both feedback tiling and
     * the severe transparent-ribbon overdraw that made the previous rich scene lag.
     */
    private fun shaderKaleidoscope() {
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(neonProgram)
        GLES20.glUniform2f(neonResolutionHandle, width.toFloat(), height.toFloat())
        // The direct analytic scene has no accumulating feedback motion of its
        // own, so advance its visual clock faster than the shared renderer phase.
        // This restores the momentum of the earlier nebula without extra passes.
        GLES20.glUniform1f(neonPhaseHandle, phase * NeonMotionMultiplier)
        GLES20.glUniform1f(neonEnergyHandle, energy)
        GLES20.glUniform1f(neonImpulseHandle, impulse)
        GLES20.glUniform1f(neonTransientHandle, transient)
        GLES20.glUniform1f(neonImpactHandle, impact)
        GLES20.glUniform1fv(neonBandsHandle, BandCount, bands, 0)
        feedbackQuad.position(0)
        GLES20.glEnableVertexAttribArray(neonPositionHandle)
        GLES20.glVertexAttribPointer(neonPositionHandle, 2, GLES20.GL_FLOAT, false, 4 * Float.SIZE_BYTES, feedbackQuad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(neonPositionHandle)
    }

    private fun vortexScene() {
        lightTrailBackdrop()
        val count = LightTrailPalette.size
        repeat(count) { trail ->
            val color = palette(LightTrailPalette[trail], 1f)
            // Lines, rather than filled ribbon strips, keep the nebula responsive on
            // the Streamer's fill-rate-limited GPU.
            vortexCore(trail, 216, color.withAlpha(.40f + energy * .26f + impulse * .16f), 1.3f + band(trail) * 1.7f)
            vortexCore(trail, 216, White.withAlpha(.20f + band(trail + 3) * .22f), 1f)
        }
        vortexAccents(count)
    }

    private fun legacyKaleidoscope() {
        repeat(8) { ribbon ->
            val count = 96
            begin()
            repeat(count) { index ->
                val t = index / (count - 1f)
                val angle = t * Tau * 2f + ribbon * Tau / 8f + phase * (1.3f + ribbon * .03f)
                val radius = .16f + .36f * sin(t * Tau * 3f + phase * 2.1f + ribbon) + band(ribbon) * .20f
                vertex(cos(angle) * radius * aspect(), sin(angle) * radius)
            }
            lineStrip(count, palette(ribbon, .55f), 1.4f + band(ribbon) * 2.6f)
        }
        repeat(48) { index ->
            val angle = index * Tau / 48f - phase * 1.4f
            val radius = .15f + band(index) * .56f + energy * .16f
            begin(); vertex(0f, 0f); vertex(cos(angle) * radius * aspect(), sin(angle) * radius)
            lines(2, palette(index, .52f), 1f + band(index) * 2f)
        }
    }

    private fun trails(count: Int, speed: Float, amplitude: Float) {
        repeat(count) { trail ->
            val samples = if (count == 8) 192 else 144
            val lane = -.82f + trail * (1.64f / (count - 1).coerceAtLeast(1))
            val low = band(trail)
            val high = band(trail + 3)
            begin()
            repeat(samples) { index ->
                val x = -1f + index * (2f / (samples - 1))
                val motion = x * (5.2f + trail * .24f) + phase * (1.8f + trail * .11f) * speed
                val y = lane + sin(motion) * (.035f + low * .20f * amplitude) + sin(motion * 1.63f + trail * .8f) * (.018f + high * .09f * amplitude)
                vertex(x, y.coerceIn(-1f, 1f))
            }
            lineStrip(samples, palette(trail, .42f + energy * .34f), 1.2f + (low + high) * 1.5f)
        }
    }

    /**
     * A layered, long-exposure scene.  The broad strips give the waves body on a TV,
     * while the fine cores and fading echoes preserve the detail of a real analyser.
     * It deliberately uses only small, bounded vertex batches so it remains suitable
     * for the lowest-profiled TV GPU.
     */
    private fun lightTrails() {
        feedbackCurrent()
    }

    /** The flagship: sparse live currents, made rich by the low-resolution feedback field. */
    private fun feedbackCurrent() {
        // Refresh every other frame while the scene is calm; attack hits immediately
        // inject a fresh current so the feedback field has room to breathe.
        if (frameNumber % 2L != 0L && impact < .28f) return
        repeat(CurrentLanes.size) { current ->
            val color = currentThemeColor(current)
            feedbackCurrentRibbon(current, 224, color.withAlpha(.12f + energy * .13f + transient * .11f + impact * .14f), .0045f + band(current) * .010f + impact * .007f)
            feedbackCurrentCore(current, 224, White.withAlpha(.28f + band(current + 3) * .22f + impact * .17f), 1f)
        }
        feedbackCurrentNodes()
    }

    private fun currentThemeColor(current: Int): Rgba {
        // The three trail palettes are frequency assignments, not an autonomous
        // colour carousel: cool is the stable current, fuchsia follows the middle
        // detail, and amber arrives with low-end weight and attack energy.
        val low = (band(current) * .68f + band(current + 1) * .32f).coerceIn(0f, 1f)
        val mid = ((band(current + 2) + band(current + 3)) * .5f).coerceIn(0f, 1f)
        val high = ((band(current + 5) + band(current + 6)) * .5f).coerceIn(0f, 1f)
        return CurrentThemes[0][current]
            .blend(CurrentThemes[1][current], (mid * .72f + high * .28f).coerceIn(0f, 1f))
            .blend(CurrentThemes[2][current], (low * .70f + impact * .30f).coerceIn(0f, 1f))
    }

    private fun feedbackCurrentRibbon(current: Int, samples: Int, color: Rgba, thickness: Float) {
        begin()
        repeat(samples) { index ->
            val x = -1.08f + index * (2.16f / (samples - 1))
            val y = feedbackCurrentY(current, x)
            val previousX = x - .008f
            val nextX = x + .008f
            val dx = nextX - previousX
            val dy = feedbackCurrentY(current, nextX) - feedbackCurrentY(current, previousX)
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.0001f)
            val offsetX = -dy / length * thickness
            val offsetY = dx / length * thickness
            vertex(x + offsetX, y + offsetY)
            vertex(x - offsetX, y - offsetY)
        }
        triangleStrip(samples * 2, color)
    }

    private fun feedbackCurrentCore(current: Int, samples: Int, color: Rgba, width: Float) {
        begin()
        repeat(samples) { index ->
            val x = -1.08f + index * (2.16f / (samples - 1))
            vertex(x, feedbackCurrentY(current, x))
        }
        lineStrip(samples, color, width)
    }

    private fun feedbackCurrentY(current: Int, x: Float): Float {
        val low = band(current)
        val mid = band(current + 2)
        val high = band(current + 5)
        val motion = x * (2.45f + current * .53f) + phase * (1.18f + current * .13f) + current * 1.41f
        val carrier = sin(motion) * (.045f + low * .27f + energy * .07f)
        val fold = sin(motion * (1.54f + current * .08f) - phase * .55f) * (.018f + mid * .14f + transient * .055f)
        val fracture = sin(x * (10.5f + current) + phase * 3.4f) * impact * (.026f + high * .090f)
        val drift = sin(phase * (.19f + current * .018f) + current * 2.17f) * (.060f + mid * .11f)
        return (CurrentLanes[current] + carrier + fold + fracture + drift).coerceIn(-1.15f, 1.15f)
    }

    private fun feedbackCurrentNodes() {
        begin()
        var count = 0
        repeat(CurrentLanes.size) { current ->
            repeat(2) { node ->
                val progress = ((node / 2f) + phase * (.055f + current * .009f) + band(current + node) * .17f) % 1f
                val x = -1.02f + progress * 2.04f
                vertex(x, feedbackCurrentY(current, x))
                count++
            }
        }
        points(count, White.withAlpha(.22f + transient * .24f + impact * .22f), 1.4f + energy * 1.3f + transient * 2.1f + impact * 2.3f)
    }

    private fun lightTrailBackdrop() {
        begin()
        repeat(ParticleCount) { index ->
            val drift = phase * .027f * particleSpeed[index]
            val x = wrap(particleX[index] + drift + sin(phase * .17f + index) * .018f)
            val y = wrap(particleY[index] + drift * .46f)
            vertex(x, y)
        }
        points(ParticleCount, Cyan.withAlpha(.11f + energy * .08f), 1.8f + energy * 1.6f)
    }

    private fun vortexRibbon(trail: Int, samples: Int, color: Rgba, thickness: Float) {
        begin()
        repeat(samples) { index ->
            val t = index / (samples - 1f)
            val x = vortexX(trail, t)
            val y = vortexY(trail, t)
            val previousT = (t - .004f).coerceAtLeast(0f)
            val nextT = (t + .004f).coerceAtMost(1f)
            val dx = vortexX(trail, nextT) - vortexX(trail, previousT)
            val dy = vortexY(trail, nextT) - vortexY(trail, previousT)
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.0001f)
            val offsetX = -dy / length * thickness
            val offsetY = dx / length * thickness
            vertex(x + offsetX, y + offsetY)
            vertex(x - offsetX, y - offsetY)
        }
        triangleStrip(samples * 2, color)
    }

    private fun vortexCore(trail: Int, samples: Int, color: Rgba, width: Float) {
        begin()
        repeat(samples) { index ->
            val t = index / (samples - 1f)
            vertex(vortexX(trail, t), vortexY(trail, t))
        }
        lineStrip(samples, color, width)
    }

    private fun vortexX(trail: Int, t: Float): Float = cos(vortexAngle(trail, t)) * vortexRadius(trail, t) * aspect()

    private fun vortexY(trail: Int, t: Float): Float = sin(vortexAngle(trail, t)) * vortexRadius(trail, t)

    private fun vortexAngle(trail: Int, t: Float): Float {
        val identity = trail * Tau / LightTrailPalette.size
        val turns = 1.15f + trail * .115f + band(trail + 1) * .34f
        return identity + t * Tau * turns - phase * (.34f + trail * .025f) + sin(t * Tau * (1.4f + trail * .08f) + phase) * (.12f + band(trail + 4) * .30f)
    }

    private fun vortexRadius(trail: Int, t: Float): Float {
        val bass = band(trail)
        val mid = band(trail + 2)
        val high = band(trail + 5)
        val ripple = sin(t * Tau * (2.2f + trail * .15f) + phase * (1.9f + trail * .08f)) * (.018f + mid * .115f + impulse * .095f)
        val shimmer = sin(t * Tau * (6.4f + trail * .22f) - phase * 1.4f) * (.005f + high * .034f)
        return (.035f + t * (1.32f + bass * .34f + energy * .14f) + ripple + shimmer).coerceAtLeast(.015f)
    }

    private fun vortexAccents(trailCount: Int) {
        begin()
        var count = 0
        repeat(trailCount) { trail ->
            repeat(4) { accent ->
                val progress = ((accent / 4f) + phase * (.052f + trail * .007f) + band(trail + accent) * .24f) % 1f
                vertex(vortexX(trail, progress), vortexY(trail, progress))
                count++
            }
        }
        points(count, White.withAlpha(.42f + impulse * .34f), 2.2f + energy * 2.4f + impulse * 2.2f)
    }

    /** A screen-wide, translucent liquid-cell field: the pack's organic/blob preset. */
    private fun fluidBloom() {
        // Four columns by three rows make the whole television a liquid field.
        // Their positions are stable; measured low/mid/high content reshapes the
        // cells rather than making the scene drift away from the music.
        repeat(12) { cell ->
            val samples = 116
            val low = (band(cell) * .68f + band(cell + 1) * .32f).coerceIn(0f, 1f)
            val mid = ((band(cell + 2) + band(cell + 3)) * .5f).coerceIn(0f, 1f)
            val high = ((band(cell + 5) + band(cell + 6)) * .5f).coerceIn(0f, 1f)
            val column = cell % 4
            val row = cell / 4
            val centerX = -.84f + column * .56f + sin(phase * .24f + cell * 1.71f) * (.015f + mid * .035f)
            val centerY = -.58f + row * .58f + cos(phase * .20f + cell * 1.19f) * (.012f + low * .030f)
            val baseRadius = .31f + low * .105f + impact * .055f

            // A low-alpha body lets adjacent cells blend into large colour pools.
            begin()
            vertex(centerX, centerY)
            repeat(samples + 1) { point ->
                val angle = point * Tau / samples
                val wobble = sin(angle * 3f + phase * (.65f + cell * .035f) + cell) * (.045f + low * .085f) +
                    sin(angle * 5f - phase * .38f + cell * 1.37f) * (.020f + mid * .050f) +
                    sin(angle * 9f + phase * 1.25f + cell) * high * .014f
                val radius = baseRadius + wobble
                vertex(centerX + cos(angle) * radius * aspect(), centerY + sin(angle) * radius)
            }
            triangleFan(samples + 2, palette(cell, .018f + low * .042f + mid * .024f + transient * .018f))

            // The bright shoreline provides definition, while the soft interior
            // maintains the Geiss-like pooled-colour character at TV distance.
            begin()
            repeat(samples) { point ->
                val angle = point * Tau / samples
                val wobble = sin(angle * 3f + phase * (.65f + cell * .035f) + cell) * (.045f + low * .085f) +
                    sin(angle * 5f - phase * .38f + cell * 1.37f) * (.020f + mid * .050f) +
                    sin(angle * 9f + phase * 1.25f + cell) * high * .014f
                val radius = baseRadius + wobble
                vertex(centerX + cos(angle) * radius * aspect(), centerY + sin(angle) * radius)
            }
            lineLoop(samples, palette(cell + 2, .17f + low * .32f + mid * .16f + transient * .12f), 1.0f + high * 1.4f)

            begin()
            repeat(samples) { point ->
                val angle = point * Tau / samples
                val wobble = sin(angle * 3f + phase * (.65f + cell * .035f) + cell) * (.027f + low * .050f) +
                    sin(angle * 5f - phase * .38f + cell * 1.37f) * (.012f + mid * .030f)
                val radius = baseRadius * .58f + wobble
                vertex(centerX + cos(angle) * radius * aspect(), centerY + sin(angle) * radius)
            }
            lineLoop(samples, palette(cell + 5, .10f + mid * .22f + high * .16f), .75f + high * .95f)
        }
    }

    /** A decoded-PCM oscilloscope: every trace is a time-shifted view of the real waveform. */
    private fun signalScope() {
        val lanes = floatArrayOf(.48f, .16f, -.16f, -.48f)
        repeat(lanes.size) { laneIndex ->
            begin(); vertex(-.94f, lanes[laneIndex]); vertex(.94f, lanes[laneIndex])
            lines(2, White.withAlpha(.07f), 1f)
            val samples = 240
            begin()
            repeat(samples) { index ->
                val x = -.94f + index * (1.88f / (samples - 1))
                val waveformIndex = (
                    index * (AudioWaveformSampleCount - 1) / (samples - 1) +
                        laneIndex * AudioWaveformSampleCount / lanes.size
                    ) % AudioWaveformSampleCount
                // Keep the PCM amplitude on one fixed scope scale.  Loudness may
                // brighten the trace, but must never alter its measured height.
                val y = lanes[laneIndex] + waveform[waveformIndex] * ScopeWaveformAmplitude
                vertex(x, y)
            }
            lineStrip(samples, palette(laneIndex + 1, .44f + energy * .30f), 1.1f + band(laneIndex + 2) * 1.2f)
        }
    }

    /** Full-screen mirrored calligraphic vectors, with each frequency band writing a paired glyph. */
    private fun mirrorScript() {
        repeat(7) { stroke ->
            val samples = 176
            val low = (band(stroke) * .66f + band(stroke + 1) * .34f).coerceIn(0f, 1f)
            val mid = ((band(stroke + 2) + band(stroke + 3)) * .5f).coerceIn(0f, 1f)
            val high = ((band(stroke + 5) + band(stroke + 6)) * .5f).coerceIn(0f, 1f)
            val baseline = -.78f + stroke * .26f
            // The pen's sweep is intentionally source-led. A quiet stream leaves
            // a restrained mark; bass and attacks give it broad, immediate motion.
            val audioPhase = phase * (.22f + low * .78f + mid * .24f + transient * .18f)
            val strokePulse = low * .20f + mid * .13f + high * .07f + transient * .17f + impact * .18f
            repeat(2) { mirror ->
                begin()
                repeat(samples) { index ->
                    val t = index / (samples - 1f)
                    val hand = if (mirror == 0) 1f else -1f
                    // The path begins near the centre and writes outward across
                    // each half-screen, so it reads as a mirrored glyph rather
                    // than a conventional left-to-right oscilloscope.
                    val flourish = sin(t * Tau * (1.55f + stroke * .17f) + audioPhase * (.46f + stroke * .035f)) *
                        (.018f + mid * .13f + transient * .09f + impact * .075f)
                    val x = hand * (.025f + t * .94f + flourish)
                    val primary = sin(t * Tau * (.72f + stroke * .085f) + audioPhase * (.48f + stroke * .045f) + stroke) *
                        (.045f + low * .32f + transient * .11f + impact * .12f)
                    val loop = sin(t * Tau * (2.5f + stroke * .19f) - audioPhase * .72f + stroke * 1.27f) *
                        (.014f + mid * .18f + transient * .08f)
                    val quill = sin(t * Tau * (6.1f + stroke * .31f) + audioPhase * 1.25f) * (high * .065f + transient * .025f)
                    val y = baseline + primary + loop + quill + strokePulse * (t - .5f) * .24f + impactJolt(stroke + mirror * 4, .13f) * (t - .5f)
                    vertex(x, y)
                }
                lineStrip(samples, palette(stroke * 2 + mirror, .26f + low * .32f + mid * .18f + transient * .20f), 1.0f + high * 1.35f)

                // A quieter offset stroke leaves a readable ink echo around the
                // main glyph without collapsing the two mirrored halves together.
                begin()
                repeat(samples) { index ->
                    val t = index / (samples - 1f)
                    val hand = if (mirror == 0) 1f else -1f
                    val flourish = sin(t * Tau * (1.55f + stroke * .17f) + audioPhase * (.46f + stroke * .035f)) *
                        (.014f + mid * .085f + transient * .055f)
                    val x = hand * (.025f + t * .94f + flourish)
                    val primary = sin(t * Tau * (.72f + stroke * .085f) + audioPhase * (.48f + stroke * .045f) + stroke) *
                        (.030f + low * .20f + transient * .065f)
                    val loop = sin(t * Tau * (2.5f + stroke * .19f) - audioPhase * .72f + stroke * 1.27f) *
                        (.010f + mid * .105f)
                    val y = baseline + primary + loop + strokePulse * (t - .5f) * .14f + .042f * if (stroke % 2 == 0) 1f else -1f
                    vertex(x, y)
                }
                lineStrip(samples, palette(stroke * 2 + mirror + 3, .09f + mid * .16f + high * .10f), .75f)
            }
        }
    }

    /** A fixed nebula constellation: audio opens a bloom but never starts an autonomous orbit. */
    private fun orbitBloom() {
        val low = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) / 4f).coerceIn(0f, 1f)
        val high = ((band(6) + band(7)) * .5f).coerceIn(0f, 1f)

        // The semi-transparent, fixed contour layers give the bloom an ambient
        // body. Their only movement is a measured bass expansion and attack pulse.
        // The bloom is deliberately broad: it is a full-screen scene, not a
        // centre badge.  The aspect-adjusted x radius and compressed y radius
        // keep its outermost contour inside a 16:9 display.
        repeat(6) { layer ->
            val samples = 188
            val baseRadius = (.42f + layer * .17f + low * (.12f + layer * .022f) + impact * .12f)
                .coerceAtMost(1.34f)
            begin()
            repeat(samples) { index ->
                val angle = Tau * index / (samples - 1f)
                val texture = sin(angle * (3f + layer) + layer) * (.018f + mid * .035f) +
                    sin(angle * (7f + layer * 2f) + layer * 1.7f) * (.008f + high * .018f)
                val radius = baseRadius + texture
                vertex(cos(angle) * radius * aspect(), sin(angle) * radius * .70f)
            }
            lineStrip(samples, palette(layer * 2 + 1, .12f + mid * .20f + high * .14f + transient * .14f), 1f + high * 1.3f)
        }

        // Stable filaments turn the dot field into a constellation; routes brighten
        // with the mid band but never move independently of the source material.
        begin()
        repeat(112) { link ->
            val first = (link * 17 + 11) % ParticleCount
            val second = (first + 23 + link * 7) % ParticleCount
            val firstSeed = ((first * 29) % 191) / 191f
            val secondSeed = ((second * 29) % 191) / 191f
            val firstRadius = (.12f + sqrt(firstSeed) * (1.13f + low * .20f) + impact * .07f).coerceAtMost(1.34f)
            val secondRadius = (.12f + sqrt(secondSeed) * (1.13f + low * .20f) + impact * .07f).coerceAtMost(1.34f)
            vertex(cos(first * 2.399f) * firstRadius * aspect(), sin(first * 2.399f) * firstRadius * .70f)
            vertex(cos(second * 2.399f) * secondRadius * aspect(), sin(second * 2.399f) * secondRadius * .70f)
        }
        lines(224, White.withAlpha(.035f + mid * .13f + transient * .10f), .7f + high * .9f)

        repeat(4) { group ->
            begin()
            var count = 0
            for (index in group until ParticleCount step 4) {
                val angle = index * 2.399f
                val seed = ((index * 29) % 191) / 191f
                val radius = (.12f + sqrt(seed) * (1.13f + low * .20f) + impact * (.07f + (index % 5) * .014f))
                    .coerceAtMost(1.34f)
                val x = cos(angle) * radius * aspect()
                val y = sin(angle) * radius * .70f
                vertex(x, y); count++
            }
            points(
                count,
                palette(group * 2, .22f + mid * .46f + high * .30f + transient * .20f),
                2.4f + high * 5.2f + impact * 3.0f,
            )
        }
    }

    /** A screen-wide refractive pane surface: audio bends a stable sheet of luminous glass. */
    private fun glassCurrent() {
        val columns = 7
        val rows = 5
        fun nodeX(column: Int, row: Int): Float {
            val edge = if (column == 0 || column == columns || row == 0 || row == rows) 0f else 1f
            val low = band(column * 5 + row * 3)
            val mid = band(column * 5 + row * 3 + 3)
            val base = -1.06f + column * (2.12f / columns)
            return base + edge * (
                sin(phase * (.28f + low * .76f) + column * 1.53f + row * .71f) * (.018f + low * .072f + impact * .034f) +
                    sin(phase * .64f + row * 2.11f) * (mid * .038f + transient * .020f)
                )
        }
        fun nodeY(column: Int, row: Int): Float {
            val edge = if (column == 0 || column == columns || row == 0 || row == rows) 0f else 1f
            val low = band(column * 5 + row * 3 + 1)
            val high = band(column * 5 + row * 3 + 6)
            val base = -1.06f + row * (2.12f / rows)
            return base + edge * (
                cos(phase * (.31f + low * .70f) + column * 1.19f + row * 1.41f) * (.022f + low * .084f + impact * .040f) +
                    sin(phase * 1.1f + column * 1.83f) * (high * .030f + transient * .024f)
                )
        }

        repeat(rows) { row ->
            repeat(columns) { column ->
                val tlX = nodeX(column, row); val tlY = nodeY(column, row)
                val blX = nodeX(column, row + 1); val blY = nodeY(column, row + 1)
                val trX = nodeX(column + 1, row); val trY = nodeY(column + 1, row)
                val brX = nodeX(column + 1, row + 1); val brY = nodeY(column + 1, row + 1)
                val index = row * columns + column
                val brightness = (band(index) * .55f + band(index + 4) * .30f + band(index + 7) * .15f).coerceIn(0f, 1f)

                // Split every pane into coloured triangular facets. The stronger
                // translucent faces are what make this read as refracted glass,
                // rather than an office-window wireframe.
                begin()
                vertex(tlX, tlY); vertex(blX, blY); vertex(trX, trY)
                triangles(3, palette(index * 2 + 1, .055f + brightness * .135f + transient * .045f))

                begin()
                vertex(trX, trY); vertex(blX, blY); vertex(brX, brY)
                triangles(3, palette(index * 2 + 5, .045f + brightness * .115f + transient * .040f))

                begin()
                vertex(tlX, tlY); vertex(trX, trY); vertex(brX, brY); vertex(blX, blY)
                lineLoop(4, palette(index + 3, .12f + brightness * .28f + transient * .14f), .7f + band(index + 6) * 1.10f)

                // A diagonal caustic gives each pane a refracted direction that
                // becomes visible with the high-frequency content.
                begin(); vertex(tlX, tlY); vertex(brX, brY)
                lines(2, White.withAlpha(.045f + band(index + 6) * .22f + transient * .12f), .65f + band(index + 6) * .9f)
            }
        }
    }

    /** Fixed patch ports: measured attacks re-route the active band connections. */
    private fun vectorSwitchboard() {
        val routingGeneration = impactPhase.toInt()
        repeat(14) { module ->
            val sourceBand = module % BandCount
            val destinationBand = (sourceBand * 3 + routingGeneration) % BandCount
            val sourceY = -.72f + (module % 7) * .24f
            val destinationY = -.72f + destinationBand * (1.44f / (BandCount - 1))
            val strength = band(sourceBand)
            // This central bend is assigned to the relevant frequency region; it
            // should not drift unless that band changes.
            val bridgeY = (sourceY + destinationY) * .5f + (band(destinationBand) - .5f) * .18f
            begin()
            vertex(-.86f, sourceY)
            vertex(0f, bridgeY)
            vertex(.86f, destinationY)
            lineStrip(3, palette(sourceBand, .08f + strength * .60f + transient * .16f), .8f + strength * 2.2f)
            begin(); vertex(-.86f, sourceY); vertex(.86f, destinationY)
            points(2, White.withAlpha(.12f + strength * .52f), 1.6f + strength * 3.1f)
        }
    }

    /** Forward-only radial star streaks; the pack's sole deep-space movement scene. */
    private fun deepWarp() {
        val low = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) / 4f).coerceIn(0f, 1f)
        val high = ((band(6) + band(7)) * .5f).coerceIn(0f, 1f)
        begin()
        var count = 0
        repeat(ParticleCount) { index ->
            // A star's bearing is fixed.  Altering its angle with time makes the
            // field rotate, which misrepresents forward motion.
            val angle = index * 2.399f
            val depth = ((index * 31 % 211) / 211f + deepWarpTravel * particleSpeed[index]) % 1f
            val radius = .035f + depth * depth * 1.12f
            val tail = .006f + depth * (.014f + low * .11f + mid * .045f)
            val x = cos(angle) * radius * aspect()
            val y = sin(angle) * radius
            vertex(x, y); vertex(cos(angle) * (radius - tail) * aspect(), sin(angle) * (radius - tail))
            count += 2
        }
        lines(count, Cyan.withAlpha(.08f + mid * .20f + high * .24f + transient * .15f), 1f + impact * 1.3f)
    }

    /** A full-screen chromatic aurora field; vertical curtains make it distinct from Light Trails. */
    private fun chromafield() {
        repeat(16) { curtain ->
            val samples = 188
            val low = (band(curtain) * .68f + band(curtain + 1) * .32f).coerceIn(0f, 1f)
            val mid = ((band(curtain + 2) + band(curtain + 3)) * .5f).coerceIn(0f, 1f)
            val high = ((band(curtain + 5) + band(curtain + 6)) * .5f).coerceIn(0f, 1f)
            val base = -1.08f + curtain * (2.16f / 15f)
            val curtainPhase = phase * (.18f + low * .82f + mid * .22f + transient * .14f)
            fun curtainX(y: Float): Float {
                val broadFold = sin(y * (1.45f + curtain * .075f) + curtainPhase * (.52f + curtain * .018f) + curtain * 1.27f) *
                    (.035f + low * .180f + impact * .070f)
                val fineFold = sin(y * (5.8f + curtain * .17f) - curtainPhase * .92f + curtain * .71f) *
                    (.012f + mid * .075f + transient * .035f)
                val shimmer = sin(y * (15.5f + curtain * .31f) + curtainPhase * 1.35f) * high * .025f
                return (base + broadFold + fineFold + shimmer).coerceIn(-1.12f, 1.12f)
            }
            val coreWidth = .018f + low * .060f + mid * .025f + impact * .016f

            // A wide dim body makes each curtain feel like coloured light in air.
            begin()
            repeat(samples) { index ->
                val y = -1.06f + index * (2.12f / (samples - 1))
                val x = curtainX(y)
                vertex(x - coreWidth * 5.5f, y)
                vertex(x + coreWidth * 5.5f, y)
            }
            triangleStrip(samples * 2, palette(curtain + 1, .055f + low * .115f + mid * .060f + transient * .045f))

            begin()
            repeat(samples) { index ->
                val y = -1.06f + index * (2.12f / (samples - 1))
                val x = curtainX(y)
                vertex(x - coreWidth, y)
                vertex(x + coreWidth, y)
            }
            triangleStrip(samples * 2, palette(curtain + 3, .115f + low * .20f + mid * .085f + transient * .075f))

            begin()
            repeat(samples) { index ->
                val y = -1.06f + index * (2.12f / (samples - 1))
                vertex(curtainX(y), y)
            }
            lineStrip(samples, White.withAlpha(.050f + high * .22f + transient * .12f), .70f + high * 1.05f)
        }
    }

    /** Instrument-panel arcs and needles, intentionally not a rotating record. */
    private fun signalDeck() {
        // Keep the complete arc, tick scale, and dB markings inside the deck bezel.
        val centres = floatArrayOf(-.62f, -.21f, .21f, .62f)
        centres.forEachIndexed { meter, centerX ->
            meterArc(
                centerX = centerX,
                centerY = -.14f,
                level = calibratedMeters[meter],
                peak = calibratedMeterPeaks[meter],
                colour = meter * 2,
                scale = 1f,
            )
        }
        meterArc(centerX = -.18f, centerY = -.75f, level = masterLeft, peak = masterLeftPeak, colour = 0, scale = .34f)
        meterArc(centerX = .18f, centerY = -.75f, level = masterRight, peak = masterRightPeak, colour = 4, scale = .34f)
        begin(); vertex(-.94f, -.60f); vertex(.94f, -.60f)
        lines(2, White.withAlpha(.13f), 1f)
    }

    private fun meterArc(centerX: Float, centerY: Float, level: Float, peak: Float, colour: Int, scale: Float) {
        val samples = 72
        // Conventional meter direction: quiet at the left, 0 dB at the right.
        val start = Tau * .39f
        val span = -Tau * .28f
        val radius = .31f * scale
        begin()
        repeat(samples) { index ->
            val angle = start + span * index / (samples - 1f)
            vertex(centerX + cos(angle) * radius * aspect(), centerY + sin(angle) * radius)
        }
        lineStrip(samples, palette(colour, .32f + energy * .20f), 1.3f)
        begin()
        repeat(13) { tick ->
            val angle = start + span * tick / 12f
            val inner = radius * 1.04f
            val outer = radius * if (tick % 3 == 0) 1.18f else 1.11f
            vertex(centerX + cos(angle) * inner * aspect(), centerY + sin(angle) * inner)
            vertex(centerX + cos(angle) * outer * aspect(), centerY + sin(angle) * outer)
        }
        lines(26, White.withAlpha(.22f), 1f)
        val needleAngle = start + span * level.coerceIn(0f, 1f)
        begin(); vertex(centerX, centerY); vertex(centerX + cos(needleAngle) * radius * .90f * aspect(), centerY + sin(needleAngle) * radius * .90f)
        lines(2, White.withAlpha(.68f), 1.6f)
        val peakAngle = start + span * peak.coerceIn(0f, 1f)
        begin()
        vertex(centerX + cos(peakAngle) * radius * .25f * aspect(), centerY + sin(peakAngle) * radius * .25f)
        vertex(centerX + cos(peakAngle) * radius * 1.04f * aspect(), centerY + sin(peakAngle) * radius * 1.04f)
        lines(2, palette(colour, .90f), 1.1f)
        begin(); vertex(centerX, centerY)
        points(1, White.withAlpha(.80f), 3.2f)
    }

    private fun advanceMasterMeter(current: Float, target: Float, delta: Float): Float {
        val rate = if (target > current) MasterAttackPerSecond else MasterReleasePerSecond
        val interpolation = 1f - exp((-delta * rate).toDouble()).toFloat()
        return current + (target - current) * interpolation
    }

    private fun slashQuad(slash: Int, thickness: Float) {
        val x = slashX(slash)
        val y = slashY(slash)
        val angle = slashAngle(slash)
        val length = slashLength(slash)
        val dx = cos(angle) * length
        val dy = sin(angle) * length
        val nx = -sin(angle) * thickness
        val ny = cos(angle) * thickness
        val startX = x - dx
        val startY = y - dy
        val endX = x + dx
        val endY = y + dy
        vertex(startX + nx, startY + ny); vertex(endX + nx, endY + ny); vertex(endX - nx, endY - ny)
        vertex(startX + nx, startY + ny); vertex(endX - nx, endY - ny); vertex(startX - nx, startY - ny)
    }

    private fun slashCore(slash: Int) {
        val x = slashX(slash)
        val y = slashY(slash)
        val angle = slashAngle(slash)
        val length = slashLength(slash)
        val dx = cos(angle) * length
        val dy = sin(angle) * length
        vertex(x - dx, y - dy); vertex(x + dx, y + dy)
    }

    private fun slashX(slash: Int): Float = wrap(particleX[slash] + sin(phase * (.22f + slash * .017f) + slash) * (.16f + band(slash) * .22f))

    private fun slashY(slash: Int): Float = wrap(particleY[slash] + cos(phase * (.18f + slash * .021f) + slash * 1.41f) * (.14f + band(slash + 2) * .20f))

    private fun slashAngle(slash: Int): Float = slash * 2.399f + phase * (.32f + slash * .026f) + band(slash + 3) * Tau * .55f + impact * (slash % 2 * 2 - 1) * .8f

    private fun slashLength(slash: Int): Float = .07f + band(slash) * .35f + energy * .12f + transient * .22f + impact * .25f

    private fun slashSparks() {
        begin()
        repeat(LightTrailGroupCount * SlashesPerColour) { slash ->
            val x = slashX(slash)
            val y = slashY(slash)
            val angle = slashAngle(slash)
            val distance = slashLength(slash) * (.78f + impact * .42f)
            vertex(x + cos(angle) * distance, y + sin(angle) * distance)
        }
        points(LightTrailGroupCount * SlashesPerColour, White.withAlpha(.32f + transient * .26f + impact * .24f), 1.8f + energy * 1.6f + transient * 2.4f + impact * 3.4f)
    }

    private fun calligraphyRibbon(trail: Int, samples: Int, color: Rgba, thickness: Float) {
        begin()
        repeat(samples) { index ->
            val t = index / (samples - 1f)
            val x = calligraphyX(trail, t)
            val y = calligraphyY(trail, t)
            val previousT = (t - .004f).coerceAtLeast(0f)
            val nextT = (t + .004f).coerceAtMost(1f)
            val dx = calligraphyX(trail, nextT) - calligraphyX(trail, previousT)
            val dy = calligraphyY(trail, nextT) - calligraphyY(trail, previousT)
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.0001f)
            val offsetX = -dy / length * thickness
            val offsetY = dx / length * thickness
            vertex(x + offsetX, y + offsetY)
            vertex(x - offsetX, y - offsetY)
        }
        triangleStrip(samples * 2, color)
    }

    private fun calligraphyCore(trail: Int, samples: Int, color: Rgba, width: Float) {
        begin()
        repeat(samples) { index ->
            val t = index / (samples - 1f)
            vertex(calligraphyX(trail, t), calligraphyY(trail, t))
        }
        lineStrip(samples, color, width)
    }

    private fun calligraphyX(trail: Int, t: Float): Float {
        val beat = band(trail)
        val high = band(trail + 5)
        val controlOne = CalligraphyControlOneX[trail] + sin(phase * .54f + trail * 1.37f) * (.09f + beat * .25f + transient * .18f) + impactJolt(trail, .31f)
        val controlTwo = CalligraphyControlTwoX[trail] + sin(phase * .43f + trail * 2.11f) * (.08f + high * .20f + transient * .16f) - impactJolt(trail + 3, .27f)
        return cubic(CalligraphyStartX[trail], controlOne, controlTwo, CalligraphyEndX[trail], t)
    }

    private fun calligraphyY(trail: Int, t: Float): Float {
        val bass = band(trail + 1)
        val mid = band(trail + 3)
        val controlOne = CalligraphyControlOneY[trail] + sin(phase * .63f + trail * 1.73f) * (.13f + bass * .30f + impulse * .10f + transient * .24f) + impactJolt(trail + 7, .36f)
        val controlTwo = CalligraphyControlTwoY[trail] + sin(phase * .47f + trail * .91f) * (.12f + mid * .28f + impulse * .08f + transient * .21f) - impactJolt(trail + 11, .30f)
        return cubic(CalligraphyStartY[trail], controlOne, controlTwo, CalligraphyEndY[trail], t).coerceIn(-1.08f, 1.08f)
    }

    private fun cubic(start: Float, controlOne: Float, controlTwo: Float, end: Float, t: Float): Float {
        val inverse = 1f - t
        return inverse * inverse * inverse * start + 3f * inverse * inverse * t * controlOne + 3f * inverse * t * t * controlTwo + t * t * t * end
    }

    private fun impactJolt(seed: Int, scale: Float) = sin(impactPhase + seed * 2.399f) * impact * scale

    private fun calligraphyAccents(trailCount: Int) {
        begin()
        var count = 0
        repeat(trailCount) { trail ->
            repeat(5) { accent ->
                val progress = ((accent / 5f) + phase * (.055f + trail * .009f) + band(trail + accent) * .21f) % 1f
                vertex(calligraphyX(trail, progress), calligraphyY(trail, progress))
                count++
            }
        }
        points(count, White.withAlpha(.40f + impulse * .38f + transient * .20f + impact * .25f), 2.2f + energy * 2.4f + impulse * 2.2f + transient * 4f + impact * 5f)
    }

    private fun tunnel() {
        repeat(13) { ring -> circle(.10f + ((ring / 13f + phase * .11f) % 1f) * .82f, palette(ring, .30f + energy * .32f), 48, 1f + band(ring) * 1.5f) }
        repeat(20) { spoke ->
            val angle = spoke * Tau / 20f + phase * .45f
            val radius = .30f + band(spoke) * .55f
            begin(); vertex(cos(angle) * .08f * aspect(), sin(angle) * .08f); vertex(cos(angle) * radius * aspect(), sin(angle) * radius)
            lines(2, palette(spoke, .46f), 1f)
        }
    }

    private fun mandala() {
        repeat(4) { ring -> circle(.16f + ring * .14f + band(ring) * .05f, palette(ring + 2, .38f), 72, 1.2f) }
        repeat(72) { petal ->
            val angle = petal * Tau / 72f + phase * .48f
            val inner = .10f + band(petal) * .10f
            val outer = .26f + band(petal + 2) * .50f
            begin(); vertex(cos(angle) * inner * aspect(), sin(angle) * inner); vertex(cos(angle) * outer * aspect(), sin(angle) * outer)
            lines(2, palette(petal, .38f + energy * .30f), 1f + band(petal) * 1.4f)
        }
    }

    private fun particles(speed: Float) {
        repeat(4) { group ->
            begin(); var count = 0
            for (index in group until ParticleCount step 4) {
                val x = wrap(particleX[index] + phase * particleSpeed[index] * speed + sin(phase + index) * .02f)
                val y = wrap(particleY[index] + phase * particleSpeed[index] * speed * .55f)
                vertex(x, y); count++
            }
            points(count, palette(group * 2, .30f + energy * .45f), 2.2f + band(group) * 4f)
        }
    }

    private fun prisms() {
        repeat(18) { prism ->
            val angle = prism * Tau / 18f + phase * (.18f + prism * .006f)
            val radius = .13f + (prism % 6) * .10f + band(prism) * .10f
            begin()
            repeat(3) { corner ->
                val a = angle + corner * Tau / 3f
                vertex(cos(a) * radius * aspect(), sin(a) * radius)
            }
            lineLoop(3, palette(prism, .38f + energy * .28f), 1.2f)
        }
    }

    private fun laserGrid() {
        val horizon = -.18f + energy * .10f
        begin(); vertex(-1f, horizon); vertex(1f, horizon); lines(2, Cyan.withAlpha(.78f), 2.2f)
        repeat(22) { index ->
            val x = -1f + index * (2f / 21f)
            begin(); vertex(x * .10f, horizon); vertex(x, -1f); lines(2, palette(index, .34f), 1f)
        }
        repeat(12) { line ->
            val y = horizon - line * .075f - phase % .075f
            begin(); vertex(-1f, y); vertex(1f, y); lines(2, Cyan.withAlpha(.28f + band(line) * .28f), 1f)
        }
    }

    /** A full-screen classic Winamp-style LED spectrum analyzer. */
    private fun eqSpectrum() {
        val left = -.93f
        val right = .93f
        val floor = -.83f
        val ceiling = .82f
        val bandWidth = (right - left) / AudioVisualizationBandCount
        val segmentHeight = (ceiling - floor) / WinampLedSegments
        val lightCount = IntArray(AudioVisualizationBandCount) { index ->
            (spectrumBands[index].coerceIn(0f, 1f) * WinampLedSegments).toInt().coerceAtLeast(1)
        }

        // Ghost cells preserve the familiar dark analyzer matrix between active LEDs.
        begin()
        var ghostVertices = 0
        repeat(AudioVisualizationBandCount) { index ->
            val x0 = left + index * bandWidth + bandWidth * .12f
            val x1 = x0 + bandWidth * .76f
            repeat(WinampLedSegments) { segment ->
                val y0 = floor + segment * segmentHeight + segmentHeight * .10f
                val y1 = y0 + segmentHeight * .78f
                ledCell(x0, x1, y0, y1); ghostVertices += 6
            }
        }
        triangles(ghostVertices, Rgba(.025f, .085f, .026f, .44f))

        drawWinampLeds(lightCount, left, bandWidth, floor, segmentHeight, 0, Rgba(.16f, 1f, .18f, .92f))
        drawWinampLeds(lightCount, left, bandWidth, floor, segmentHeight, 1, Rgba(.90f, 1f, .10f, .96f))
        drawWinampLeds(lightCount, left, bandWidth, floor, segmentHeight, 2, Rgba(1f, .18f, .10f, 1f))

        repeat(AudioVisualizationBandCount) { index ->
            val x0 = left + index * bandWidth + bandWidth * .12f
            val x1 = x0 + bandWidth * .76f
            val peakSegment = (spectrumPeaks[index].coerceIn(0f, 1f) * (WinampLedSegments - 1)).toInt()
            val peakY = floor + peakSegment * segmentHeight + segmentHeight * .50f
            begin(); vertex(x0, peakY); vertex(x1, peakY)
            lines(2, White.withAlpha(.44f + transient * .42f), 1.25f)
        }
    }

    private fun drawWinampLeds(
        lightCount: IntArray,
        left: Float,
        bandWidth: Float,
        floor: Float,
        segmentHeight: Float,
        zone: Int,
        colour: Rgba,
    ) {
        begin()
        var count = 0
        repeat(AudioVisualizationBandCount) { index ->
            val x0 = left + index * bandWidth + bandWidth * .12f
            val x1 = x0 + bandWidth * .76f
            repeat(lightCount[index]) { segment ->
                val segmentZone = when {
                    segment < WinampLedSegments * .62f -> 0
                    segment < WinampLedSegments * .84f -> 1
                    else -> 2
                }
                if (segmentZone == zone) {
                    val y0 = floor + segment * segmentHeight + segmentHeight * .10f
                    val y1 = y0 + segmentHeight * .78f
                    ledCell(x0, x1, y0, y1)
                    count += 6
                }
            }
        }
        if (count > 0) triangles(count, colour)
    }

    private fun ledCell(x0: Float, x1: Float, y0: Float, y1: Float) {
        vertex(x0, y0); vertex(x1, y0); vertex(x1, y1)
        vertex(x0, y0); vertex(x1, y1); vertex(x0, y1)
    }

    private fun updateSpectrumHistory() {
        if (waterfallTexture[0] == 0 || spectrumHistoryFrame++ % SpectrumHistoryFrameStride != 0L) return
        for (row in SpectrumHistoryRows - 1 downTo 1) {
            repeat(AudioVisualizationBandCount) { band ->
                spectrumHistoryPixels.put(row * AudioVisualizationBandCount + band, spectrumHistoryPixels.get((row - 1) * AudioVisualizationBandCount + band))
            }
        }
        repeat(AudioVisualizationBandCount) { band ->
            spectrumHistoryPixels.put(band, (spectrumBands[band].coerceIn(0f, 1f) * 255f).toInt().toByte())
        }
        spectrumHistoryPixels.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waterfallTexture[0])
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            0,
            0,
            AudioVisualizationBandCount,
            SpectrumHistoryRows,
            GLES30.GL_RED,
            GLES20.GL_UNSIGNED_BYTE,
            spectrumHistoryPixels,
        )
    }

    private fun drawSpectrumWaterfall() {
        if (waterfallProgram == 0 || waterfallTexture[0] == 0) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glUseProgram(waterfallProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waterfallTexture[0])
        GLES20.glUniform1i(waterfallSamplerHandle, 0)
        feedbackQuad.position(0)
        GLES20.glEnableVertexAttribArray(waterfallPositionHandle)
        GLES20.glVertexAttribPointer(waterfallPositionHandle, 2, GLES20.GL_FLOAT, false, 4 * Float.SIZE_BYTES, feedbackQuad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(waterfallPositionHandle)
    }

    private fun vinyl() {
        repeat(16) { groove -> circle(.12f + groove * .043f + band(groove) * .018f, palette(groove, .26f + energy * .30f), 64, 1f) }
        val needle = phase * 1.4f
        begin(); vertex(0f, 0f); vertex(cos(needle) * .76f * aspect(), sin(needle) * .76f); lines(2, White.withAlpha(.72f), 2f)
    }

    /**
     * Milkdrop-like persistence without asking the TV GPU to shade every display pixel.
     * The feedback pass runs in a capped internal buffer, then a single texture sample
     * presents it at TV resolution.
     */
    private fun createFeedbackBuffers() {
        releaseFeedbackBuffers()
        feedbackWidth = (width * FeedbackResolutionFraction).toInt().coerceAtLeast(MinFeedbackWidth)
        feedbackHeight = (height * FeedbackResolutionFraction).toInt().coerceAtLeast(MinFeedbackHeight)
        GLES20.glGenTextures(2, feedbackTextures, 0)
        GLES20.glGenFramebuffers(2, feedbackFramebuffers, 0)
        repeat(2) { index ->
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, feedbackTextures[index])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, feedbackWidth, feedbackHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, feedbackFramebuffers[index])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, feedbackTextures[index], 0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "TV visualizer feedback buffer unavailable" }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        feedbackIndex = 0
        clearFeedback = true
    }

    private fun releaseFeedbackBuffers() {
        if (feedbackTextures.any { it != 0 }) GLES20.glDeleteTextures(2, feedbackTextures, 0)
        if (feedbackFramebuffers.any { it != 0 }) GLES20.glDeleteFramebuffers(2, feedbackFramebuffers, 0)
        feedbackTextures.fill(0)
        feedbackFramebuffers.fill(0)
    }

    private fun renderFeedbackBase() {
        val target = feedbackIndex
        val source = 1 - target
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, feedbackFramebuffers[target])
        GLES20.glViewport(0, 0, feedbackWidth, feedbackHeight)
        if (clearFeedback) {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, feedbackFramebuffers[source])
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, feedbackFramebuffers[target])
            clearFeedback = false
        }
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(feedbackProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, feedbackTextures[source])
        GLES20.glUniform1i(feedbackSamplerHandle, 0)
        val neonFeedback = style == IdleVisualizerStyle.NeonKaleidoscope.ordinal
        GLES20.glUniform1f(feedbackZoomHandle, if (neonFeedback) .998f else .994f - energy * .007f)
        GLES20.glUniform1f(feedbackRotationHandle, if (neonFeedback) (impulse - .18f) * .0015f else (impulse - .18f) * .006f)
        GLES20.glUniform1f(feedbackWarpHandle, if (neonFeedback) .0015f + transient * .005f else .003f + transient * .018f + impact * .022f)
        GLES20.glUniform1f(feedbackPhaseHandle, phase)
        val feedbackDecay = when (style) {
            IdleVisualizerStyle.LightTrails.ordinal -> (.84f + energy * .035f + impact * .035f).coerceAtMost(.915f)
            IdleVisualizerStyle.NeonKaleidoscope.ordinal -> (.885f + energy * .02f).coerceAtMost(.91f)
            else -> .928f
        }
        GLES20.glUniform1f(feedbackDecayHandle, feedbackDecay)
        drawFeedbackQuad()
    }

    private fun renderSceneBase() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, feedbackFramebuffers[0])
        GLES20.glViewport(0, 0, feedbackWidth, feedbackHeight)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun presentFeedback() {
        val completed = feedbackIndex
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(feedbackProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, feedbackTextures[completed])
        GLES20.glUniform1i(feedbackSamplerHandle, 0)
        GLES20.glUniform1f(feedbackZoomHandle, 1f)
        GLES20.glUniform1f(feedbackRotationHandle, 0f)
        GLES20.glUniform1f(feedbackWarpHandle, 0f)
        GLES20.glUniform1f(feedbackPhaseHandle, 0f)
        GLES20.glUniform1f(feedbackDecayHandle, 1f)
        drawFeedbackQuad()
        feedbackIndex = 1 - feedbackIndex
    }

    private fun presentSceneBuffer() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(feedbackProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, feedbackTextures[0])
        GLES20.glUniform1i(feedbackSamplerHandle, 0)
        GLES20.glUniform1f(feedbackZoomHandle, 1f)
        GLES20.glUniform1f(feedbackRotationHandle, 0f)
        GLES20.glUniform1f(feedbackWarpHandle, 0f)
        GLES20.glUniform1f(feedbackPhaseHandle, 0f)
        GLES20.glUniform1f(feedbackDecayHandle, 1f)
        drawFeedbackQuad()
    }

    private fun drawFeedbackQuad() {
        feedbackQuad.position(0)
        GLES20.glEnableVertexAttribArray(feedbackPositionHandle)
        GLES20.glVertexAttribPointer(feedbackPositionHandle, 2, GLES20.GL_FLOAT, false, 4 * Float.SIZE_BYTES, feedbackQuad)
        feedbackQuad.position(2)
        GLES20.glEnableVertexAttribArray(feedbackUvHandle)
        GLES20.glVertexAttribPointer(feedbackUvHandle, 2, GLES20.GL_FLOAT, false, 4 * Float.SIZE_BYTES, feedbackQuad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(feedbackPositionHandle)
        GLES20.glDisableVertexAttribArray(feedbackUvHandle)
    }

    private fun circle(radius: Float, color: Rgba, segments: Int, lineWidth: Float) {
        begin()
        repeat(segments) { index ->
            val angle = index * Tau / (segments - 1)
            vertex(cos(angle) * radius * aspect(), sin(angle) * radius)
        }
        lineStrip(segments, color, lineWidth)
    }

    private fun begin() = vertices.clear()
    private fun vertex(x: Float, y: Float) { vertices.put(x); vertices.put(y) }
    private fun lineStrip(count: Int, color: Rgba, width: Float) = draw(GLES20.GL_LINE_STRIP, count, color, width)
    private fun lineLoop(count: Int, color: Rgba, width: Float) = draw(GLES20.GL_LINE_LOOP, count, color, width)
    private fun lines(count: Int, color: Rgba, width: Float) = draw(GLES20.GL_LINES, count, color, width)
    private fun triangleFan(count: Int, color: Rgba) = draw(GLES20.GL_TRIANGLE_FAN, count, color, 1f)
    private fun triangleStrip(count: Int, color: Rgba) = draw(GLES20.GL_TRIANGLE_STRIP, count, color, 1f)
    private fun triangles(count: Int, color: Rgba) = draw(GLES20.GL_TRIANGLES, count, color, 1f)

    private fun points(count: Int, color: Rgba, size: Float) {
        vertices.position(0); GLES20.glUniform4f(colorHandle, color.r, color.g, color.b, color.a); GLES20.glUniform1f(pointSizeHandle, size)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices); GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
    }

    private fun draw(mode: Int, count: Int, color: Rgba, width: Float) {
        vertices.position(0); GLES20.glUniform4f(colorHandle, color.r, color.g, color.b, color.a); GLES20.glUniform1f(pointSizeHandle, 1f); GLES20.glLineWidth(width)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices); GLES20.glDrawArrays(mode, 0, count)
    }

    private fun band(index: Int) = bands[index % BandCount].coerceIn(0f, 1f)
    private fun aspect() = height.toFloat() / width.toFloat()
    private fun wrap(value: Float) = ((value + 1f) % 2f + 2f) % 2f - 1f
    private fun palette(index: Int, alpha: Float) = Palette[index % Palette.size].withAlpha(alpha)

    private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
        val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "TV visualizer shader compilation failed: ${GLES20.glGetShaderInfoLog(shader)}" }
    }

    private fun linkProgram(vertex: Int, fragment: Int): Int = GLES20.glCreateProgram().also { result ->
        GLES20.glAttachShader(result, vertex); GLES20.glAttachShader(result, fragment); GLES20.glLinkProgram(result)
        val status = IntArray(1); GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "TV visualizer shader link failed: ${GLES20.glGetProgramInfoLog(result)}" }
    }
}

private data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    fun withAlpha(alpha: Float) = copy(a = alpha.coerceIn(0f, 1f))
    fun blend(other: Rgba, amount: Float): Rgba {
        val t = amount.coerceIn(0f, 1f)
        return Rgba(r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t, a + (other.a - a) * t)
    }
}

private const val BandCount = 8
private const val MaxVertices = 4096
private const val ParticleCount = 192
private const val LightTrailGroupCount = 4
private const val SlashesPerColour = 2
// One-third is the feedback tier that remains smooth on the Streamer.
private const val FeedbackResolutionFraction = .33333334f
private const val MinFeedbackWidth = 320
private const val MinFeedbackHeight = 180
private const val AudioSmoothing = 16f
private const val MasterMeterFloorDbfs = -30f
private const val MasterAttackPerSecond = 12f
private const val MasterReleasePerSecond = 2.2f
private const val MasterPeakFallPerSecond = .12f
private const val ImpulseSmoothing = 26f
private const val TransientSmoothing = 34f
private const val ImpactDecay = 6.5f
private const val SpectrumPeakFallPerSecond = .34f
private const val SpectrumHistoryRows = 32
private const val SpectrumHistoryFrameStride = 3L
private const val WinampLedSegments = 18
private const val NanosPerSecond = 1_000_000_000.0
private const val FrameIntervalNanos = 16_666_667L
private const val EglOpenGlEs3Bit = 0x40
private const val NeonMotionMultiplier = 1.7f
private const val ScopeWaveformAmplitude = .18f
private const val DeepWarpTravelPerSecond = .95f
private const val Tau = (PI * 2.0).toFloat()
private val LightTrailPalette = intArrayOf(0, 6, 5, 3, 2, 1)
private val CurrentLanes = floatArrayOf(-.48f, .02f, .49f)
private val CurrentThemes = arrayOf(
    arrayOf(Rgba(.04f, .68f, 1f), Rgba(.05f, .95f, .78f), Rgba(.14f, .42f, 1f)), // electric water
    arrayOf(Rgba(1f, .08f, .62f), Rgba(.82f, .16f, 1f), Rgba(1f, .18f, .44f)), // fuchsia pulse
    arrayOf(Rgba(1f, .48f, .06f), Rgba(1f, .82f, .10f), Rgba(.92f, .23f, .04f)), // molten amber
)
private val CalligraphyStartX = floatArrayOf(-1.08f, -.92f, 1.08f, -1.08f, .30f)
private val CalligraphyStartY = floatArrayOf(-.78f, .92f, -.95f, .18f, 1.08f)
private val CalligraphyControlOneX = floatArrayOf(-.46f, -.64f, .54f, -.12f, .82f)
private val CalligraphyControlOneY = floatArrayOf(1.04f, -.62f, .72f, -1.04f, .34f)
private val CalligraphyControlTwoX = floatArrayOf(.36f, .64f, -.56f, .78f, -.66f)
private val CalligraphyControlTwoY = floatArrayOf(-1.02f, .34f, -.74f, .88f, -.38f)
private val CalligraphyEndX = floatArrayOf(1.08f, .96f, -1.08f, 1.08f, -.48f)
private val CalligraphyEndY = floatArrayOf(.70f, -.88f, .88f, -.30f, -1.08f)
private val CalligraphyPalette = intArrayOf(0, 5, 3, 2, 6)
private val Cyan = Rgba(.08f, .82f, 1f)
private val White = Rgba(.92f, .96f, 1f)
private val Palette = arrayOf(
    Rgba(.08f, .72f, 1f), Rgba(.28f, .32f, 1f), Rgba(.72f, .12f, 1f), Rgba(1f, .10f, .60f),
    Rgba(1f, .28f, .14f), Rgba(1f, .74f, .08f), Rgba(.42f, 1f, .12f), Rgba(.08f, .95f, .70f),
)

private const val VertexShader = """
attribute vec2 aPosition;
uniform float uPointSize;
void main() { gl_Position = vec4(aPosition, 0.0, 1.0); gl_PointSize = uPointSize; }
"""

private const val FragmentShader = """
precision mediump float;
uniform vec4 uColor;
void main() { gl_FragColor = uColor; }
"""

private const val NeonVertexShader = """
#version 300 es
in vec2 aPosition;
out vec2 vUv;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vUv = aPosition * 0.5 + 0.5;
}
"""

/**
 * A single opaque pass deliberately favours crisp, fine filaments over soft,
 * stacked transparent ribbons.  Eight spectrum-controlled wave families form a
 * broad nebula; flux and attacks deform every family independently so energetic
 * music changes the structure rather than only making it brighter.
 */
private const val NeonFragmentShader = """
#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragmentColor;
uniform vec2 uResolution;
uniform float uPhase;
uniform float uEnergy;
uniform float uImpulse;
uniform float uTransient;
uniform float uImpact;
uniform float uBands[8];

vec3 strandColor(float index) {
    float t = index / 7.0;
    vec3 cool = mix(vec3(0.025, 0.36, 1.0), vec3(0.88, 0.045, 0.82), t * 1.45);
    vec3 warm = mix(vec3(1.0, 0.09, 0.54), vec3(0.18, 1.0, 0.38), (t - 0.52) * 2.08);
    return mix(cool, warm, step(0.52, t));
}

float hash21(vec2 value) {
    value = fract(value * vec2(123.34, 456.21));
    value += dot(value, value + 45.32);
    return fract(value.x * value.y);
}

float starLayer(vec2 point, float scale, float drift) {
    vec2 grid = point * scale + vec2(drift, drift * 0.71);
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float seed = hash21(cell);
    vec2 starPosition = vec2(hash21(cell + 17.3), hash21(cell + 53.1)) - 0.5;
    float distanceToStar = length(local - starPosition * 0.64);
    float visible = smoothstep(0.84, 0.985, seed);
    float twinkle = 0.62 + 0.38 * sin(drift * 1.7 + seed * 30.0);
    return smoothstep(0.050, 0.008, distanceToStar) * visible * twinkle;
}

void main() {
    vec2 point = vUv - 0.5;
    point.x *= uResolution.x / uResolution.y;
    // Keep the nebula's audio mapping explicit: low frequencies breathe its
    // mass, mids deform the filaments, and highs reveal stars and caustics.
    float lowBand = (uBands[0] * 0.62 + uBands[1] * 0.38);
    float midBand = (uBands[2] + uBands[3] + uBands[4] + uBands[5]) * 0.25;
    float highBand = (uBands[6] + uBands[7]) * 0.5;
    vec2 starPoint = point;
    // This is the smooth, analytic counterpart to Milkdrop's feedback warp:
    // it continuously advects the scene without keeping a costly history buffer.
    float breathing = sin(uPhase * 0.23) * (0.008 + lowBand * 0.044 + uImpact * 0.025);
    point += vec2(
        sin(point.y * 3.4 + uPhase * 0.49) * breathing,
        cos(point.x * 4.1 - uPhase * 0.37) * breathing
    );
    point -= vec2(sin(uPhase * 0.13) * (0.009 + midBand * 0.030), cos(uPhase * 0.11) * (0.007 + midBand * 0.022));
    float unwarpedRadius = length(point);
    vec2 radialDirection = point / max(unwarpedRadius, 0.001);
    vec2 tangentDirection = vec2(-radialDirection.y, radialDirection.x);
    // Attack transients create clearly visible, bounded shockwaves and torsion.
    // Their small spatial wavelength makes the visual react as structure, not a
    // global pulse; decay is already controlled by the Kotlin-side impact state.
    float shockwave = sin(unwarpedRadius * (22.0 + lowBand * 12.0) - uPhase * 4.2)
        * (uImpulse * 0.026 + uImpact * 0.096 + lowBand * 0.016);
    float torsion = sin(atan(point.y, point.x) * 7.0 + uPhase * 2.5)
        * (uTransient * 0.020 + uImpact * 0.055 + midBand * 0.010);
    point += radialDirection * shockwave + tangentDirection * torsion;
    float radial = length(point);
    float angle = atan(point.y, point.x);
    float atmosphere = 0.012 + 0.030 * smoothstep(0.92, 0.10, radial);
    vec3 colour = vec3(0.0015, 0.004, 0.012) + vec3(0.008, 0.014, 0.040) * atmosphere * 10.0;

    float cloudOne = smoothstep(0.92, 0.08, length(starPoint - vec2(-0.34, 0.19)));
    float cloudTwo = smoothstep(0.78, 0.05, length(starPoint - vec2(0.38, -0.24)));
    colour += vec3(0.012, 0.035, 0.095) * cloudOne * (0.14 + lowBand * 0.30 + uEnergy * 0.12);
    colour += vec3(0.070, 0.010, 0.100) * cloudTwo * (0.12 + midBand * 0.20 + uTransient * 0.17);
    float nearStars = starLayer(starPoint, 23.0, -uPhase * (0.055 + uEnergy * 0.035));
    float farStars = starLayer(starPoint, 41.0, uPhase * 0.031 + uTransient * 0.3);
    colour += vec3(0.26, 0.63, 1.0) * nearStars * (0.24 + highBand * 0.68 + uEnergy * 0.24);
    colour += vec3(0.72, 0.45, 1.0) * farStars * (0.16 + highBand * 0.34 + uTransient * 0.45);

    // Six centre-origin arm families are the defining Neon Kaleidoscope shape.
    // Audio alters their curvature, density, and sharp attack fracture instead
    // of merely changing brightness on an oscilloscope-like row of waves.
    for (int arm = 0; arm < 6; arm++) {
        float index = float(arm);
        float spectrum = (uBands[arm] + uBands[(arm + 3) % 8]) * 0.5;
        float armAngle = angle + index * 1.04719755;
        // All angular frequencies are integers.  atan() wraps at the negative-X
        // ray, so a noninteger multiplier here would produce a visible left-edge
        // discontinuity even though the rest of the scene is continuous.
        float fold = sin(armAngle * 2.0 + uPhase * (0.36 + index * 0.032))
            * (0.12 + spectrum * 0.25 + midBand * 0.08);
        float curl = radial * (12.0 + index * 0.74 + spectrum * 3.6)
            - armAngle * 3.0
            + fold + uPhase * (0.76 + spectrum * 1.34 + midBand * 0.40);
        float fracture = sin(radial * (31.0 + index * 1.6) - armAngle * 6.0 + uPhase * 3.1)
            * uImpact * (0.18 + spectrum * 0.24);
        float primary = abs(sin(curl + fracture));
        float echo = abs(sin(
            radial * (16.2 + index * 0.91 + spectrum * 2.7)
                - armAngle * 4.0
                + uPhase * (1.43 + spectrum * 0.61)
                + index * 1.91
        ));
        float filament = smoothstep(0.145 + spectrum * 0.055, 0.016, primary)
            + smoothstep(0.078 + spectrum * 0.032, 0.010, echo) * 0.48;
        float core = smoothstep(0.034 + spectrum * 0.012, 0.0028, primary);
        float glow = smoothstep(0.52 + spectrum * 0.10, 0.018, primary);
        vec3 strand = strandColor(index);
        float falloff = smoothstep(1.04, 0.02, radial);
        colour += strand * glow * falloff * (0.10 + spectrum * 0.13 + uEnergy * 0.05);
        colour += strand * filament * falloff * (0.27 + spectrum * 0.25 + uTransient * 0.12);
        colour += vec3(0.82, 0.94, 1.0) * core * falloff * (0.24 + spectrum * 0.28 + uImpact * 0.13);
    }

    // A fine iridescent caustic is the final high-band detail layer. It uses
    // integer angular repetition so it cannot introduce a left-edge seam.
    float caustic = abs(sin(radial * (18.0 + highBand * 10.0) - angle * 4.0 + uPhase * 1.9));
    float causticLine = smoothstep(0.12 + highBand * 0.035, 0.012, caustic);
    colour += vec3(0.20, 0.68, 1.0) * causticLine * smoothstep(1.02, 0.18, radial) * (0.008 + highBand * 0.055 + uTransient * 0.035);

    // A restrained central haze binds the independent strands into a nebula
    // without adding a second pass or accumulating stale frames.
    float halo = smoothstep(0.88, 0.18, radial) * (0.014 + uEnergy * 0.025 + uTransient * 0.018);
    colour += vec3(0.08, 0.12, 0.30) * halo;
    fragmentColor = vec4(min(colour, vec3(1.0)), 1.0);
}
"""

private const val WaterfallVertexShader = """
#version 300 es
in vec2 aPosition;
out vec2 vUv;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vUv = aPosition * 0.5 + 0.5;
}
"""

private const val WaterfallFragmentShader = """
#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragmentColor;
uniform sampler2D uHistory;
void main() {
    // Keep the waterfall inside the EQ graph's bounds.
    vec2 lower = vec2(0.035, 0.10);
    vec2 upper = vec2(0.965, 0.92);
    if (any(lessThan(vUv, lower)) || any(greaterThan(vUv, upper))) discard;
    vec2 historyUv = (vUv - lower) / (upper - lower);
    float value = texture(uHistory, vec2(historyUv.x, 1.0 - historyUv.y)).r;
    vec3 quiet = vec3(0.006, 0.014, 0.060);
    vec3 electric = mix(vec3(0.02, 0.32, 1.0), vec3(0.90, 0.03, 0.72), smoothstep(0.28, 0.72, value));
    vec3 hot = vec3(1.0, 0.52, 0.08);
    vec3 colour = mix(quiet, electric, smoothstep(0.03, 0.64, value));
    colour = mix(colour, hot, smoothstep(0.76, 1.0, value));
    float ageFade = 0.20 + (1.0 - historyUv.y) * 0.42;
    fragmentColor = vec4(colour, ageFade * smoothstep(0.02, 0.95, value));
}
"""

private const val FeedbackVertexShader = """
attribute vec2 aPosition;
attribute vec2 aUv;
varying vec2 vUv;
void main() { gl_Position = vec4(aPosition, 0.0, 1.0); vUv = aUv; }
"""

private const val FeedbackFragmentShader = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vUv;
uniform sampler2D uFeedback;
uniform float uZoom;
uniform float uRotation;
uniform float uWarp;
uniform float uPhase;
uniform float uDecay;
void main() {
    vec2 point = vUv - 0.5;
    float cosine = cos(uRotation);
    float sine = sin(uRotation);
    point = mat2(cosine, -sine, sine, cosine) * point;
    float radius = dot(point, point);
    vec2 bend = vec2(sin(point.y * 15.0 + uPhase * 1.7), cos(point.x * 13.0 - uPhase * 1.3)) * uWarp * (0.35 + radius);
    vec2 sourceUv = 0.5 + point * (uZoom - radius * uWarp * 0.4) + bend;
    vec3 history = texture2D(uFeedback, clamp(sourceUv, 0.001, 0.999)).rgb;
    gl_FragColor = vec4(history * uDecay, 1.0);
}
"""
