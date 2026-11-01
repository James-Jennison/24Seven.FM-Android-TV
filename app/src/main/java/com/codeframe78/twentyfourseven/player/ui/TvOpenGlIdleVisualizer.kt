package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import android.opengl.GLUtils
import android.app.ActivityManager
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.viewinterop.AndroidView
import com.codeframe78.twentyfourseven.player.domain.StationId
import com.codeframe78.twentyfourseven.player.ui.theme.StationPalette
import com.codeframe78.twentyfourseven.player.ui.theme.stationPalette
import com.codeframe78.twentyfourseven.player.R
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationBandCount
import com.codeframe78.twentyfourseven.player.domain.AudioWaveformSampleCount
import com.codeframe78.twentyfourseven.player.domain.CalibratedBandCount
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerRenderProfile
import com.codeframe78.twentyfourseven.player.domain.IonTunnelPalette
import com.codeframe78.twentyfourseven.player.domain.LavaLampPalette
import com.codeframe78.twentyfourseven.player.domain.metadata
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import com.codeframe78.twentyfourseven.player.domain.MusicReactiveState
import com.codeframe78.twentyfourseven.player.ui.visualization.NeonMusicMapper
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
    reduceVisualIntensity: Boolean = false,
    lavaLampPalette: LavaLampPalette = LavaLampPalette.Neon,
    ionTunnelPalette: IonTunnelPalette = IonTunnelPalette.AmberIce,
    onExit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    stationId: StationId? = null,
) {
    if (style != IdleVisualizerStyle.IonTransit && style.videoAsset() != null) {
        TvVideoIdleVisualizer(style, audioState, reduceVisualIntensity, modifier, lavaLampPalette)
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        TvOpenGlIdleVisualizer(style, audioState, stationName, reduceVisualIntensity, lavaLampPalette, ionTunnelPalette, onExit, modifier, stationId)
    } else {
        TvCanvasIdleVisualizer(style, audioState, reduceVisualIntensity, lavaLampPalette, modifier)
    }
}

@Composable
private fun TvOpenGlIdleVisualizer(
    style: IdleVisualizerStyle,
    audioState: StateFlow<AudioVisualizationState>,
    stationName: String?,
    reduceVisualIntensity: Boolean,
    lavaLampPalette: LavaLampPalette,
    ionTunnelPalette: IonTunnelPalette,
    onExit: (() -> Unit)?,
    modifier: Modifier,
    stationId: StationId?,
) {
    var surface by remember { mutableStateOf<TvVisualizerSurfaceView?>(null) }
    var signalDeckReadings by remember { mutableStateOf(List(4) { "-30 dB" }) }
    var masterReadings by remember { mutableStateOf(listOf("-30 dB", "-30 dB")) }
    var signalDeckStereo by remember { mutableStateOf(false) }
    var signalDeckCorrelation by remember { mutableStateOf(0f) }
    var djWaveform by remember { mutableStateOf(List(AudioWaveformSampleCount) { 0f }) }
    var djBandLevels by remember { mutableStateOf(List(CalibratedBandCount) { 0f }) }
    var djDeckEnergy by remember { mutableStateOf(0f) }
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
            factory = { context ->
                TvVisualizerSurfaceView(context).also {
                    surface = it
                    it.updateStyle(style, lavaLampPalette, ionTunnelPalette, stationId)
                    it.setOnBackRequested(onExit)
                }
            },
            update = {
                it.updateStyle(style, lavaLampPalette, ionTunnelPalette, stationId)
                it.setOnBackRequested(onExit)
            },
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
                    // A real L/R correlation measurement is useful hardware-deck
                    // information: +1 is mono-compatible, 0 is decorrelated, and
                    // negative values identify phase-inverted stereo content.
                    val phaseStart = size.width * .705f
                    val phaseEnd = size.width * .920f
                    val phaseY = size.height * .214f
                    val phaseCentre = (phaseStart + phaseEnd) * .5f
                    val phaseNeedle = phaseStart + (signalDeckCorrelation.coerceIn(-1f, 1f) + 1f) * .5f * (phaseEnd - phaseStart)
                    val phaseColour = if (signalDeckCorrelation < 0f) Color(0xFFFF6B87) else Color(0xFF79E9C2)
                    drawLine(Color(0x506F8DAA), Offset(phaseStart, phaseY), Offset(phaseEnd, phaseY), strokeWidth = size.height * .002f)
                    drawLine(Color(0x809DB8D0), Offset(phaseCentre, phaseY - size.height * .010f), Offset(phaseCentre, phaseY + size.height * .010f), strokeWidth = size.height * .0015f)
                    drawCircle(phaseColour.copy(alpha = .30f), radius = size.height * .010f, center = Offset(phaseNeedle, phaseY))
                    drawCircle(phaseColour, radius = size.height * .0048f, center = Offset(phaseNeedle, phaseY))
                    signalDeckLabelPaint.textSize = size.height * .014f
                    signalDeckLabelPaint.alpha = 145
                    canvas.nativeCanvas.drawText("PHASE ${formatCorrelation(signalDeckCorrelation)}", phaseCentre, size.height * .235f, signalDeckLabelPaint)
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
        if (style == IdleVisualizerStyle.DjConsole) {
            // Decode this large local mark only while its tribute scene is active.
            val djSoffSnepDecal = ImageBitmap.imageResource(R.drawable.dj_soff_snep_overlay)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val panelLeft = size.width * .035f
                val panelTop = size.height * .045f
                val panelWidth = size.width * .93f
                val panelHeight = size.height * .930f
                val jogCentre = Offset(size.width * .190f, size.height * .545f)
                val rightJogCentre = Offset(size.width * .810f, size.height * .545f)
                val jogRadius = size.height * .225f
                val mixerLeft = size.width * .350f
                val mixerRight = size.width * .650f
                val waveformLeft = size.width * .340f
                val waveformTop = size.height * .710f
                val waveformWidth = size.width * .320f
                val waveformHeight = size.height * .080f

                // The tribute fills the room, while the deck remains a separate
                // physical surface in front of it. An opaque base also prevents
                // the retired GL layout from bleeding through this Canvas scene.
                drawRect(Color(0xFF03070E))
                // A physical control surface first: a matte faceplate, recessed
                // displays and hardware details. The animated GL layer above it
                // supplies the live PCM waveform, jog response, EQ and VU levels.
                drawRoundRect(
                    color = Color(0x960A101B),
                    topLeft = Offset(panelLeft, panelTop),
                    size = Size(panelWidth, panelHeight),
                    cornerRadius = CornerRadius(size.height * .025f),
                )
                drawRoundRect(
                    color = Color(0x70111D2B),
                    topLeft = Offset(panelLeft + size.height * .006f, panelTop + size.height * .006f),
                    size = Size(panelWidth - size.height * .012f, panelHeight - size.height * .012f),
                    cornerRadius = CornerRadius(size.height * .020f),
                )
                drawRoundRect(
                    color = Color(0xFF5C7D9D),
                    topLeft = Offset(panelLeft, panelTop),
                    size = Size(panelWidth, panelHeight),
                    cornerRadius = CornerRadius(size.height * .025f),
                    style = Stroke(width = size.height * .0018f),
                )

                listOf(.052f to .078f, .948f to .078f, .052f to .922f, .948f to .922f).forEach { (x, y) ->
                    val screw = Offset(size.width * x, size.height * y)
                    drawCircle(Color(0xFF7892AA), size.height * .006f, screw)
                    drawCircle(Color(0xFF070B10), size.height * .0028f, screw)
                }

                // Raised platters sit directly on the tribute background; there
                // are intentionally no rectangular deck bays behind them.
                drawCircle(Color(0x9A000000), jogRadius * 1.08f, jogCentre + Offset(0f, size.height * .022f))
                drawCircle(Color(0xFF101D2B), jogRadius * 1.045f, jogCentre)
                drawCircle(Color(0xFF86A6C2), jogRadius * 1.010f, jogCentre)
                drawCircle(Color(0xFF1A2E42), jogRadius * .976f, jogCentre)
                drawCircle(Color(0x7202060A), jogRadius, jogCentre)
                drawCircle(Color(0xFF6A7F91), jogRadius, jogCentre, style = Stroke(width = size.height * .005f))
                drawCircle(Color(0x5A142232), jogRadius * .885f, jogCentre)
                drawCircle(Color(0xFF293D51), jogRadius * .800f, jogCentre, style = Stroke(width = size.height * .0014f))
                drawCircle(Color(0x4A0B121C), jogRadius * .585f, jogCentre)
                drawCircle(Color(0xFF54738C), jogRadius * .585f, jogCentre, style = Stroke(width = size.height * .002f))
                drawArc(
                    color = Color(0xFFC7E9FF),
                    startAngle = 198f,
                    sweepAngle = 142f,
                    useCenter = false,
                    topLeft = Offset(jogCentre.x - jogRadius * .992f, jogCentre.y - jogRadius * .992f),
                    size = Size(jogRadius * 1.984f, jogRadius * 1.984f),
                    style = Stroke(width = size.height * .005f),
                )
                drawArc(
                    color = Color(0xFF07111B),
                    startAngle = 18f,
                    sweepAngle = 142f,
                    useCenter = false,
                    topLeft = Offset(jogCentre.x - jogRadius * .992f, jogCentre.y - jogRadius * .992f),
                    size = Size(jogRadius * 1.984f, jogRadius * 1.984f),
                    style = Stroke(width = size.height * .0065f),
                )
                repeat(40) { tick ->
                    val angle = tick * Tau / 40f
                    val tickInner = jogRadius * .910f
                    val tickOuter = jogRadius * .955f
                    val start = Offset(jogCentre.x + cos(angle) * tickInner, jogCentre.y + sin(angle) * tickInner)
                    val end = Offset(jogCentre.x + cos(angle) * tickOuter, jogCentre.y + sin(angle) * tickOuter)
                    drawLine(Color(0xFF668096), start, end, strokeWidth = size.height * .0014f)
                }
                drawLine(
                    Color(0xFFFF4CA4),
                    Offset(jogCentre.x, jogCentre.y - jogRadius * .78f),
                    Offset(jogCentre.x, jogCentre.y - jogRadius * .61f),
                    strokeWidth = size.height * .004f,
                )
                // The platter's illumination is PCM-led: each ring and spoke
                // follows one calibrated frequency range, with no free-running
                // rotation or decorative clock animation.
                djBandLevels.forEachIndexed { band, level ->
                    val radius = jogRadius * (.23f + band * .125f + level * .080f)
                    val colour = listOf(Color(0xFF48C7FF), Color(0xFFB764FF), Color(0xFFFF4CA4), Color(0xFF7FF0B1))[band]
                    drawCircle(
                        colour.copy(alpha = (.18f + level * .68f).coerceAtMost(.82f)),
                        radius,
                        jogCentre,
                        style = Stroke(width = size.height * (.0013f + level * .0022f)),
                    )
                }
                repeat(32) { spoke ->
                    val level = djBandLevels[spoke % CalibratedBandCount]
                    val angle = spoke * Tau / 32f
                    val inner = jogRadius * .285f
                    val outer = jogRadius * (.430f + level * .385f)
                    val colour = listOf(Color(0xFF48C7FF), Color(0xFFB764FF), Color(0xFFFF4CA4), Color(0xFF7FF0B1))[spoke % CalibratedBandCount]
                    drawLine(
                        colour.copy(alpha = .08f + level * .48f),
                        Offset(jogCentre.x + cos(angle) * inner, jogCentre.y + sin(angle) * inner),
                        Offset(jogCentre.x + cos(angle) * outer, jogCentre.y + sin(angle) * outer),
                        strokeWidth = size.height * (.001f + level * .0014f),
                    )
                }

                // A matching second platter completes the physical two-deck
                // layout. It deliberately has no second logo: the tribute mark
                // remains the unique label on the DJ's own deck.
                drawCircle(Color(0x9A000000), jogRadius * 1.08f, rightJogCentre + Offset(0f, size.height * .022f))
                drawCircle(Color(0xFF101D2B), jogRadius * 1.045f, rightJogCentre)
                drawCircle(Color(0xFF86A6C2), jogRadius * 1.010f, rightJogCentre)
                drawCircle(Color(0xFF1A2E42), jogRadius * .976f, rightJogCentre)
                drawCircle(Color(0x7202060A), jogRadius, rightJogCentre)
                drawCircle(Color(0xFF6A7F91), jogRadius, rightJogCentre, style = Stroke(width = size.height * .005f))
                drawCircle(Color(0x5A142232), jogRadius * .885f, rightJogCentre)
                drawCircle(Color(0xFF293D51), jogRadius * .800f, rightJogCentre, style = Stroke(width = size.height * .0014f))
                drawCircle(Color(0x4A0B121C), jogRadius * .585f, rightJogCentre)
                drawCircle(Color(0xFF54738C), jogRadius * .585f, rightJogCentre, style = Stroke(width = size.height * .002f))
                drawArc(
                    color = Color(0xFFC7E9FF),
                    startAngle = 198f,
                    sweepAngle = 142f,
                    useCenter = false,
                    topLeft = Offset(rightJogCentre.x - jogRadius * .992f, rightJogCentre.y - jogRadius * .992f),
                    size = Size(jogRadius * 1.984f, jogRadius * 1.984f),
                    style = Stroke(width = size.height * .005f),
                )
                drawArc(
                    color = Color(0xFF07111B),
                    startAngle = 18f,
                    sweepAngle = 142f,
                    useCenter = false,
                    topLeft = Offset(rightJogCentre.x - jogRadius * .992f, rightJogCentre.y - jogRadius * .992f),
                    size = Size(jogRadius * 1.984f, jogRadius * 1.984f),
                    style = Stroke(width = size.height * .0065f),
                )
                repeat(40) { tick ->
                    val angle = tick * Tau / 40f
                    val start = Offset(
                        rightJogCentre.x + cos(angle) * jogRadius * .910f,
                        rightJogCentre.y + sin(angle) * jogRadius * .910f,
                    )
                    val end = Offset(
                        rightJogCentre.x + cos(angle) * jogRadius * .955f,
                        rightJogCentre.y + sin(angle) * jogRadius * .955f,
                    )
                    drawLine(Color(0xFF668096), start, end, strokeWidth = size.height * .0014f)
                }
                drawLine(
                    Color(0xFF48C7FF),
                    Offset(rightJogCentre.x, rightJogCentre.y - jogRadius * .78f),
                    Offset(rightJogCentre.x, rightJogCentre.y - jogRadius * .61f),
                    strokeWidth = size.height * .004f,
                )
                djBandLevels.forEachIndexed { band, level ->
                    val radius = jogRadius * (.23f + band * .125f + level * .080f)
                    val colour = listOf(Color(0xFF48C7FF), Color(0xFFB764FF), Color(0xFFFF4CA4), Color(0xFF7FF0B1))[band]
                    drawCircle(
                        colour.copy(alpha = (.14f + level * .56f).coerceAtMost(.70f)),
                        radius,
                        rightJogCentre,
                        style = Stroke(width = size.height * (.0011f + level * .0018f)),
                    )
                }
                repeat(24) { spoke ->
                    val level = djBandLevels[(spoke + 2) % CalibratedBandCount]
                    val angle = spoke * Tau / 24f
                    val inner = jogRadius * .285f
                    val outer = jogRadius * (.430f + level * .340f)
                    drawLine(
                        Color(0xFF48C7FF).copy(alpha = .06f + level * .38f),
                        Offset(rightJogCentre.x + cos(angle) * inner, rightJogCentre.y + sin(angle) * inner),
                        Offset(rightJogCentre.x + cos(angle) * outer, rightJogCentre.y + sin(angle) * outer),
                        strokeWidth = size.height * (.0009f + level * .0012f),
                    )
                }

                // The tribute is the foreground focal point: drawn above the
                // platter hardware, but inside the clear centre field so it
                // never competes with the audio controls below it.
                val foregroundLogoSize = (size.height * .60f).toInt()
                val foregroundLogoLeft = ((size.width - foregroundLogoSize) * .5f).toInt()
                val foregroundLogoTop = (size.height * .075f).toInt()
                drawImage(
                    image = djSoffSnepDecal,
                    dstOffset = androidx.compose.ui.unit.IntOffset(foregroundLogoLeft, foregroundLogoTop),
                    dstSize = androidx.compose.ui.unit.IntSize(foregroundLogoSize, foregroundLogoSize),
                    alpha = .82f,
                    filterQuality = FilterQuality.Medium,
                    blendMode = BlendMode.Screen,
                )

                drawRoundRect(
                    color = Color(0xA0040A11),
                    topLeft = Offset(waveformLeft, waveformTop),
                    size = Size(waveformWidth, waveformHeight),
                    cornerRadius = CornerRadius(size.height * .010f),
                )
                drawRoundRect(
                    color = Color(0xFF587895),
                    topLeft = Offset(waveformLeft, waveformTop),
                    size = Size(waveformWidth, waveformHeight),
                    cornerRadius = CornerRadius(size.height * .010f),
                    style = Stroke(width = size.height * .0015f),
                )
                repeat(4) { row ->
                    val y = waveformTop + waveformHeight * (row + 1f) / 5f
                    drawLine(Color(0x243C5772), Offset(waveformLeft, y), Offset(waveformLeft + waveformWidth, y), strokeWidth = size.height * .001f)
                }
                repeat(8) { column ->
                    val x = waveformLeft + waveformWidth * (column + 1f) / 9f
                    drawLine(Color(0x1C3C5772), Offset(x, waveformTop), Offset(x, waveformTop + waveformHeight), strokeWidth = size.height * .001f)
                }
                val waveformCentreY = waveformTop + waveformHeight * .5f
                val waveformAmplitude = waveformHeight * (.28f + djDeckEnergy.coerceIn(0f, 1f) * .22f)
                val waveformPoints = 112
                repeat(waveformPoints - 1) { index ->
                    val sourceA = index * (djWaveform.lastIndex.coerceAtLeast(1)) / (waveformPoints - 1)
                    val sourceB = (index + 1) * (djWaveform.lastIndex.coerceAtLeast(1)) / (waveformPoints - 1)
                    val start = Offset(
                        waveformLeft + index * waveformWidth / (waveformPoints - 1),
                        waveformCentreY - djWaveform[sourceA].coerceIn(-1f, 1f) * waveformAmplitude,
                    )
                    val end = Offset(
                        waveformLeft + (index + 1) * waveformWidth / (waveformPoints - 1),
                        waveformCentreY - djWaveform[sourceB].coerceIn(-1f, 1f) * waveformAmplitude,
                    )
                    drawLine(Color(0xFFB86CFF), start, end, strokeWidth = size.height * .0030f)
                    drawLine(Color(0xFF6DE3FF), start, end, strokeWidth = size.height * .0010f)
                }

                val phaseStart = size.width * .360f
                val phaseEnd = size.width * .640f
                val phaseY = size.height * .815f
                val phaseNeedle = phaseStart + (signalDeckCorrelation.coerceIn(-1f, 1f) + 1f) * .5f * (phaseEnd - phaseStart)
                val phaseColour = if (signalDeckCorrelation < 0f) Color(0xFFFF6B87) else Color(0xFF79E9C2)
                drawLine(Color(0xFF3B5872), Offset(phaseStart, phaseY), Offset(phaseEnd, phaseY), strokeWidth = size.height * .003f)
                drawLine(Color(0xFF6F8FA8), Offset((phaseStart + phaseEnd) * .5f, phaseY - size.height * .010f), Offset((phaseStart + phaseEnd) * .5f, phaseY + size.height * .010f), strokeWidth = size.height * .0014f)
                drawCircle(phaseColour, size.height * .006f, Offset(phaseNeedle, phaseY))

                drawRoundRect(
                    color = Color(0x9808121E),
                    topLeft = Offset(mixerLeft, size.height * .835f),
                    size = Size(mixerRight - mixerLeft, size.height * .080f),
                    cornerRadius = CornerRadius(size.height * .010f),
                )
                drawRoundRect(
                    color = Color(0xFF496C88),
                    topLeft = Offset(mixerLeft, size.height * .835f),
                    size = Size(mixerRight - mixerLeft, size.height * .080f),
                    cornerRadius = CornerRadius(size.height * .010f),
                    style = Stroke(width = size.height * .0014f),
                )
                repeat(4) { channel ->
                    val channelX = size.width * (.382f + channel * .079f)
                    // The compact rail has one high-visibility meter per band,
                    // rather than controls over the tribute image.
                    val meterBottom = size.height * .902f
                    val meterTop = size.height * .850f
                    val segmentGap = size.height * .0028f
                    val segmentHeight = (meterBottom - meterTop - segmentGap * 7f) / 8f
                    val segmentWidth = size.width * .050f
                    val litSegments = (djBandLevels[channel].coerceIn(0f, 1f) * 8f).toInt().coerceIn(0, 8)
                    repeat(8) { segment ->
                        val segmentY = meterBottom - (segment + 1) * (segmentHeight + segmentGap) + segmentGap
                        val active = segment < litSegments
                        val colour = when {
                            segment >= 6 -> Color(0xFFFF4CA4)
                            segment >= 4 -> Color(0xFFFFCC66)
                            else -> Color(0xFF52E6C2)
                        }
                        drawRoundRect(
                            color = if (active) colour else Color(0xFF172534),
                            topLeft = Offset(channelX - segmentWidth * .5f, segmentY),
                            size = Size(segmentWidth, segmentHeight),
                            cornerRadius = CornerRadius(segmentHeight * .18f),
                        )
                    }
                }
                drawLine(Color(0xFF425C73), Offset(size.width * .420f, size.height * .930f), Offset(size.width * .580f, size.height * .930f), strokeWidth = size.height * .006f)
                drawRoundRect(
                    color = Color(0xFFD5E9F8),
                    topLeft = Offset(size.width * .485f, size.height * .920f),
                    size = Size(size.width * .024f, size.height * .026f),
                    cornerRadius = CornerRadius(size.height * .004f),
                )
                val deckControls = listOf(
                    "CUE" to .092f,
                    "LIVE" to .190f,
                    "SYNC" to .288f,
                    "FX" to .712f,
                    "LOOP" to .810f,
                    "PITCH" to .908f,
                )
                deckControls.forEach { (_, position) ->
                    val center = Offset(size.width * position, size.height * .875f)
                    drawCircle(Color(0xFF0A121D), size.height * .030f, center)
                    drawCircle(Color(0xFF607A92), size.height * .030f, center, style = Stroke(width = size.height * .0015f))
                }

                drawIntoCanvas { canvas ->
                    signalDeckLabelPaint.textAlign = AndroidPaint.Align.LEFT
                    signalDeckLabelPaint.textSize = size.height * .034f
                    signalDeckLabelPaint.alpha = 220
                    canvas.nativeCanvas.drawText(
                        signalDeckLabelPaint.truncateToWidth(stationName?.takeIf { it.isNotBlank() } ?: "NO STATION SELECTED", size.width * .56f),
                        size.width * .070f,
                        size.height * .135f,
                        signalDeckLabelPaint,
                    )
                    signalDeckLabelPaint.textAlign = AndroidPaint.Align.CENTER
                    signalDeckLabelPaint.textSize = size.height * .015f
                    signalDeckLabelPaint.alpha = 145
                    canvas.nativeCanvas.drawText("LIVE PCM", size.width * .500f, size.height * .735f, signalDeckLabelPaint)
                    canvas.nativeCanvas.drawText("LOW", size.width * .382f, size.height * .845f, signalDeckLabelPaint)
                    canvas.nativeCanvas.drawText("LOW-MID", size.width * .461f, size.height * .845f, signalDeckLabelPaint)
                    canvas.nativeCanvas.drawText("MID", size.width * .540f, size.height * .845f, signalDeckLabelPaint)
                    canvas.nativeCanvas.drawText("HIGH", size.width * .619f, size.height * .845f, signalDeckLabelPaint)
                    signalDeckLabelPaint.textSize = size.height * .014f
                    signalDeckLabelPaint.alpha = 150
                    canvas.nativeCanvas.drawText("PHASE ${formatCorrelation(signalDeckCorrelation)}", size.width * .500f, size.height * .830f, signalDeckLabelPaint)
                    deckControls.forEach { (label, position) ->
                        canvas.nativeCanvas.drawText(label, size.width * position, size.height * .882f, signalDeckLabelPaint)
                    }
                    signalDeckLabelPaint.textSize = size.height * .013f
                    signalDeckLabelPaint.alpha = 130
                    canvas.nativeCanvas.drawText("CROSSFADER", size.width * .500f, size.height * .970f, signalDeckLabelPaint)
                }
            }
        }
    }
    LaunchedEffect(surface, audioState, style, reduceVisualIntensity) {
        val activeSurface = surface ?: return@LaunchedEffect
        var lastSignalDeckReadoutAt = 0L
        var lastDjDeckCanvasAt = 0L
        audioState.collect { audio ->
            activeSurface.updateAudio(audio, reduceVisualIntensity)
            val now = SystemClock.elapsedRealtime()
            if ((style == IdleVisualizerStyle.VinylPulse || style == IdleVisualizerStyle.DjConsole) && now - lastSignalDeckReadoutAt >= 125L) {
                lastSignalDeckReadoutAt = now
                signalDeckReadings = audio.calibratedBandDbfs.map(::formatDbfs)
                masterReadings = listOf(audio.leftDbfs, audio.rightDbfs).map(::formatDbfs)
                signalDeckStereo = audio.channelCount > 1
                signalDeckCorrelation = audio.stereoCorrelation
            }
            if (style == IdleVisualizerStyle.DjConsole && now - lastDjDeckCanvasAt >= 50L) {
                lastDjDeckCanvasAt = now
                djWaveform = audio.waveform.toList()
                djBandLevels = audio.calibratedBandDbfs.map(::dbfsToMasterMeter)
                djDeckEnergy = audio.energy
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
    private val renderer = TvGeometryVisualizerRenderer(context.applicationContext.resources)
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

private fun formatCorrelation(value: Float) = "%+.2f".format(java.util.Locale.US, value.coerceIn(-1f, 1f))

private fun dbfsToMasterMeter(value: Float) =
    ((value.coerceIn(MasterMeterFloorDbfs, 0f) - MasterMeterFloorDbfs) / -MasterMeterFloorDbfs).coerceIn(0f, 1f)

private class TvVisualizerSurfaceView(context: Context) : GLSurfaceView(context) {
    private val pendingAudio = AtomicReference<(() -> Unit)?>(null)
    private val renderer = TvGeometryVisualizerRenderer(context.applicationContext.resources) {
        pendingAudio.getAndSet(null)?.invoke()
    }
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val compatibilityRendering = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let {
        // Old TV devices can advertise GLES 3 while still having too little GPU/heap
        // headroom for a 60 fps feedback scene. Keep the rich path on current hardware,
        // but use a stable presentation tier when Android classifies the device as
        // low-RAM or supplies the 192 MB heap class used by the Fire TV test device.
        // The 384 MB Google TV Streamer has enough headroom for the richer scene.
        it.isLowRamDevice || it.memoryClass <= CompatibilityMemoryClassMb
    }
    private var renderEveryOtherVsync = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == display?.displayId) updateRendererRefreshRate()
        }
    }
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            if (!compatibilityRendering || renderEveryOtherVsync) requestRender()
            renderEveryOtherVsync = !renderEveryOtherVsync
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private var onBackRequested: (() -> Unit)? = null

    init {
        // The visualizer still has a GLES 2 geometry fallback, but the Streamer and
        // newer TVs can use the single-pass GLES 3 scene below.  That moves the
        // dense moving field onto the GPU instead of rebuilding hundreds of paths
        // on the UI/GL thread every frame.
        val supportsEs3 = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo.reqGlEsVersion >= 0x30000
        setEGLContextClientVersion(if (supportsEs3) 3 else 2)
        renderer.setSupportsEs3(supportsEs3)
        renderer.setCompatibilityRendering(compatibilityRendering)
        renderer.setPerformanceDiagnosticsEnabled(
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        // A TV remote routes Back to the focused native GL view before Compose
        // can observe it. The view delegates that one key to Compose below.
        isFocusable = true
        isFocusableInTouchMode = false
        // Continuous mode can outrun the Streamer's compositor and expose two
        // animation frames in one TV refresh.  Rendering from Choreographer keeps
        // each new frame aligned with the display's vsync.
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        displayManager.registerDisplayListener(displayListener, null)
        updateRendererRefreshRate()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        displayManager.unregisterDisplayListener(displayListener)
        super.onDetachedFromWindow()
    }

    private fun updateRendererRefreshRate() {
        val refreshRate = display?.refreshRate?.takeIf { it > 0f } ?: DefaultDisplayRefreshRateHz
        queueEvent { renderer.setDisplayRefreshRate(refreshRate) }
    }

    fun updateStyle(style: IdleVisualizerStyle, lavaLampPalette: LavaLampPalette, ionTunnelPalette: IonTunnelPalette, stationId: StationId?) {
        queueEvent {
            renderer.setStyle(style.ordinal)
            renderer.setLavaLampPalette(lavaLampPalette.ordinal)
            renderer.setIonPalette(ionTunnelPalette.ordinal)
            renderer.setLightStationPalette(stationPalette(stationId))
        }
    }

    fun setOnBackRequested(onBackRequested: (() -> Unit)?) {
        this.onBackRequested = onBackRequested
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            onBackRequested?.invoke()
            return onBackRequested != null
        }
        return super.onKeyUp(keyCode, event)
    }

    fun updateAudio(audio: AudioVisualizationState, reduceVisualIntensity: Boolean) {
        val responseGain = if (reduceVisualIntensity) .68f else 1f
        val transientGain = if (reduceVisualIntensity) .46f else 1f
        val compact = FloatArray(BandCount) { group ->
            val start = group * audio.bands.size / BandCount
            val end = ((group + 1) * audio.bands.size / BandCount).coerceAtLeast(start + 1)
            var total = 0f
            for (index in start until end) total += audio.bands.getOrElse(index) { 0f }
            total / (end - start) * responseGain
        }
        val rawSpectrum = FloatArray(AudioVisualizationBandCount) { audio.bands.getOrElse(it) { 0f } * responseGain }
        val rawWaveform = FloatArray(AudioWaveformSampleCount) { audio.waveform.getOrElse(it) { 0f } * responseGain }
        pendingAudio.set {
            renderer.setIonAudio(audio)
            renderer.setAudioTarget(
                music = audio.music,
                reduced = reduceVisualIntensity,
                energy = audio.energy * responseGain,
                bands = compact,
                transient = audio.transient * transientGain,
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

private class TvGeometryVisualizerRenderer(
    private val resources: Resources,
    private val consumeAudio: () -> Unit = {},
) : GLSurfaceView.Renderer {
    private var ionPalette = 0
    fun setIonPalette(palette: Int) { ionPalette = palette.coerceIn(0, 3) }
    private var ionAudio = AudioVisualizationState()
    private val ionDrive = IonTunnelDrive()
    private val ionProgram = IonTunnelProgram()
    private val lightTrailsProgram = LightTrailsProgram(resources)
    private val lightTrailsDrive = LightTrailsDrive()
    private var lightStationPalette = stationPalette(null)
    fun setLightStationPalette(palette: StationPalette) { lightStationPalette = palette }
    fun setIonAudio(audio: AudioVisualizationState) { ionAudio = audio }
    private var musicTarget: MusicReactiveState? = null
    private var musicReduced = false
    private var laserStageDecode: Future<Bitmap?>? = null
    private var laserStageLoadFailed = false
    private val laserStageTexture = IntArray(1)
    private var laserStageProgram = 0
    private var concertProgram = 0
    private var concertPosition = 0
    private var concertStart = 0
    private var concertAxis = 0
    private var concertWidths = 0
    private var concertLength = 0
    private var concertAspect = 0
    private var concertColor = 0
    private var laserStagePosition = 0
    private var laserStageSampler = 0
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
    private val laserChoreography = LaserChoreography()
    private val laserCues = LaserCueChoreography()
    // Lava receives a deliberately slow, global heat signal. It is not driven by
    // individual FFT attacks, so wax remains heavy instead of turning into fireballs.
    private var lavaThermalEnergy = 0f
    private var lavaPulseEnergy = 0f
    private var lavaConvectionClock = 0f
    private var style = 0
    private var lavaLampPalette = LavaLampPalette.Neon.ordinal
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
    private var lavaMetaballProgram = 0
    private var lavaMetaballPositionHandle = 0
    private var lavaMetaballResolutionHandle = 0
    private var lavaMetaballColoursHandle = 0
    private var lavaMetaballEnergyHandle = 0
    private var lavaMetaballPulseHandle = 0
    private var lavaMetaballPhaseHandle = 0
    private var waterfallProgram = 0
    private var waterfallPositionHandle = 0
    private var waterfallSamplerHandle = 0
    private val waterfallTexture = IntArray(1)
    private val lavaPaletteUniform = FloatArray(5 * 3)
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
    private var frameNumber = 0L
    private var supportsEs3 = false
    private var compatibilityRendering = false
    private var performanceDiagnosticsEnabled = false
    private var displayRefreshRateHz = DefaultDisplayRefreshRateHz
    private val frameTiming = VisualizerFrameTimingWindow()

    init {
        repeat(ParticleCount) { index ->
            particleX[index] = (index * 47 % 251) / 125f - 1f
            particleY[index] = (index * 83 % 241) / 120f - 1f
            particleSpeed[index] = .12f + (index % 11) * .035f
        }
    }

    fun setStyle(style: Int) {
        val previouslyUsedRenderBuffer = usesRenderBuffer()
        val styleChanged = this.style != style
        if (styleChanged && this.style == IdleVisualizerStyle.LightTrails.ordinal) lightTrailsProgram.release()
        if (styleChanged) clearFeedback = true
        this.style = style
        if (styleChanged) { laserCues.reset(); ionDrive.reset(); lightTrailsDrive.reset() }
        if (styleChanged && performanceDiagnosticsEnabled) {
            resetTimingWindow()
        }
        if (styleChanged && width > 1 && usesRenderBuffer()) {
            createFeedbackBuffers()
        } else if (styleChanged && previouslyUsedRenderBuffer) {
            // Direct styles must not carry scene buffers allocated by the prior style.
            releaseFeedbackBuffers()
        }
    }

    fun setLavaLampPalette(palette: Int) {
        lavaLampPalette = palette.coerceIn(0, LavaLampColourPalettes.lastIndex)
    }

    fun setSupportsEs3(supportsEs3: Boolean) {
        this.supportsEs3 = supportsEs3
    }

    fun setCompatibilityRendering(compatibilityRendering: Boolean) {
        if (this.compatibilityRendering == compatibilityRendering) return
        this.compatibilityRendering = compatibilityRendering
        if (width > 1 && usesRenderBuffer()) createFeedbackBuffers()
    }

    fun setPerformanceDiagnosticsEnabled(enabled: Boolean) {
        performanceDiagnosticsEnabled = enabled
        if (enabled) resetTimingWindow()
    }

    fun setDisplayRefreshRate(refreshRateHz: Float) {
        val normalizedRate = refreshRateHz.coerceAtLeast(1f)
        if (abs(displayRefreshRateHz - normalizedRate) < .1f) return
        displayRefreshRateHz = normalizedRate
        if (performanceDiagnosticsEnabled) resetTimingWindow()
    }

    private fun resetTimingWindow() {
        // A fresh renderer/style/display-mode window must never leave Device Status
        // showing a completed sample from a renderer that is no longer visible.
        // 2.5 display periods retains the prior roughly-40 ms 60 Hz tolerance while
        // keeping a nominal 24 Hz callback from being classified as slow by itself.
        VisualizerPerformanceDiagnostics.clear()
        frameTiming.reset(
            styleName = IdleVisualizerStyle.entries.getOrNull(style)?.displayName ?: "Unknown",
            displayRefreshRateHz = displayRefreshRateHz,
            slowFrameThresholdMillis = (1000f / displayRefreshRateHz) * 2.5f,
        )
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
        music: MusicReactiveState? = null,
        reduced: Boolean = false,
    ) {
        musicTarget = music
        musicReduced = reduced
        val mapped = if (style == IdleVisualizerStyle.NeonKaleidoscope.ordinal) {
            music?.let { NeonMusicMapper.map(it, SystemClock.elapsedRealtime(), reduced) }
        } else null
        fun incomingBand(index: Int): Float = mapped?.let {
            when { index < 2 -> it.bass; index < 5 -> it.mid; else -> it.treble }
        } ?: unitInterval(bands.getOrElse(index) { 0f })
        val incomingEnergy = mapped?.energy ?: unitInterval(energy)
        var frequencyChange = 0f
        for (index in targets.indices) frequencyChange += abs(incomingBand(index) - targets[index])
        targetImpulse = (abs(incomingEnergy - targetEnergy) * 1.6f + frequencyChange / BandCount * 4.8f).coerceIn(0f, 1f)
        targetEnergy = incomingEnergy
        val incomingTransient = mapped?.impact ?: unitInterval(transient)
        if (style == IdleVisualizerStyle.PulseQuilt.ordinal) {
            laserCues.accept(SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0,
                incomingTransient, incomingEnergy)
        }
        if (incomingTransient > targetTransient + .035f) {
            impact = maxOf(impact, incomingTransient)
            impactPhase += 2.17f + incomingTransient * 3.6f
        }
        targetTransient = incomingTransient
        targetMasterLeft = unitInterval(leftMeterLevel)
        targetMasterRight = unitInterval(rightMeterLevel)
        calibratedMeterLevels?.let { source ->
            calibratedMeterTargets.indices.forEach { index ->
                calibratedMeterTargets[index] = unitInterval(source.getOrElse(index) { 0f })
            }
        }
        waveformSamples?.let { source ->
            waveformTargets.indices.forEach { index -> waveformTargets[index] = signedUnit(source.getOrElse(index) { 0f }) }
        }
        targets.indices.forEach { index -> targets[index] = incomingBand(index) }
        rawSpectrum?.let { source ->
            spectrumTargets.indices.forEach { index -> spectrumTargets[index] = unitInterval(source.getOrElse(index) { 0f }) }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ionProgram.contextCreated()
        lightTrailsProgram.contextCreated()
        laserCues.reset()
        laserStageDecode?.cancel(true)
        laserStageDecode = null
        laserStageLoadFailed = false
        // Names from a lost context cannot be reused in the new one.
        laserStageTexture[0] = 0
        laserStageProgram = 0
        concertProgram = 0
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
        lavaMetaballProgram = linkProgram(
            compileShader(GLES20.GL_VERTEX_SHADER, LavaMetaballVertexShader),
            compileShader(GLES20.GL_FRAGMENT_SHADER, LavaMetaballFragmentShader),
        )
        lavaMetaballPositionHandle = GLES20.glGetAttribLocation(lavaMetaballProgram, "aPosition")
        lavaMetaballResolutionHandle = GLES20.glGetUniformLocation(lavaMetaballProgram, "uResolution")
        lavaMetaballColoursHandle = GLES20.glGetUniformLocation(lavaMetaballProgram, "uColours[0]")
        lavaMetaballEnergyHandle = GLES20.glGetUniformLocation(lavaMetaballProgram, "uEnergy")
        lavaMetaballPulseHandle = GLES20.glGetUniformLocation(lavaMetaballProgram, "uPulse")
        lavaMetaballPhaseHandle = GLES20.glGetUniformLocation(lavaMetaballProgram, "uPhase")
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
        if (usesRenderBuffer()) createFeedbackBuffers() else releaseFeedbackBuffers()
    }

    override fun onDrawFrame(gl: GL10?) {
        consumeAudio()
        if (style == IdleVisualizerStyle.NeonKaleidoscope.ordinal) {
            musicTarget?.let { music ->
                val mapped = NeonMusicMapper.map(music, SystemClock.elapsedRealtime(), musicReduced)
                targetEnergy = mapped.energy
                targetTransient = mapped.impact
                if (mapped.energy == 0f && mapped.impact == 0f) targetImpulse = 0f
                for (index in targets.indices) targets[index] = when {
                    index < 2 -> mapped.bass
                    index < 5 -> mapped.mid
                    else -> mapped.treble
                }
            }
        }
        if (style != 20) {
            laserStageDecode?.cancel(true)
            laserStageDecode = null
            laserStageLoadFailed = false
        }
        if (style != 20 && laserStageTexture[0] != 0) {
            GLES20.glDeleteTextures(1, laserStageTexture, 0)
            laserStageTexture[0] = 0
        }
        val now = SystemClock.elapsedRealtimeNanos()
        val rawFrameNanos = if (previousNanos == 0L) FrameIntervalNanos else now - previousNanos
        val delta = (rawFrameNanos / NanosPerSecond).toFloat().coerceIn(0f, .05f)
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
        if (style == IdleVisualizerStyle.PulseQuilt.ordinal) {
            laserCues.advance((rawFrameNanos / NanosPerSecond).toFloat())
            laserChoreography.update(delta, band(0) * .7f + band(1) * .3f,
                band(3) * .25f + band(4) * .45f + band(5) * .30f, energy)
        } else {
            laserChoreography.reset()
            laserCues.reset()
        }
        if (style == IdleVisualizerStyle.LavaLamp.ordinal) {
            val bassHeat = (band(0) * .72f + band(1) * .28f).coerceIn(0f, 1f)
            val heatSmoothing = 1f - exp((-delta / LavaThermalRelaxationSeconds).toDouble()).toFloat()
            lavaThermalEnergy += (bassHeat - lavaThermalEnergy) * heatSmoothing
            // A shared heat envelope gives the glass an obvious musical response
            // without moving separate wax blobs like a particle visualizer.
            val pulseTarget = (band(0) * .52f + band(1) * .31f + band(2) * .17f).coerceIn(0f, 1f)
            val pulseSeconds = if (pulseTarget > lavaPulseEnergy) .18f else .85f
            val pulseSmoothing = 1f - exp((-delta / pulseSeconds).toDouble()).toFloat()
            lavaPulseEnergy += (pulseTarget - lavaPulseEnergy) * pulseSmoothing
            // The shader maps this clock to a long convection cycle, not a beat toy.
            lavaConvectionClock += delta * (.86f + lavaThermalEnergy * .34f)
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
        if (style == IdleVisualizerStyle.IonTransit.ordinal) ionDrive.update(ionAudio, SystemClock.elapsedRealtime(), delta, musicReduced)
        if (style == IdleVisualizerStyle.LightTrails.ordinal) lightTrailsDrive.update(ionAudio, SystemClock.elapsedRealtime(), delta, musicReduced)
        frameNumber++

        if (usesSceneBuffer()) {
            // Opaque analytical scenes render offscreen, then use one filtered
            // texture sample per display pixel. No temporal feedback is added.
            renderSceneBase()
            renderSceneGeometry()
            presentSceneBuffer()
        } else if (usesFeedback()) {
            renderFeedbackBase()
            renderSceneGeometry()
            presentFeedback()
        } else {
            renderDirectBase()
            renderSceneGeometry()
        }
        if (performanceDiagnosticsEnabled) {
            frameTiming.record(rawFrameNanos, SystemClock.elapsedRealtimeNanos() - now)
                ?.let(VisualizerPerformanceDiagnostics::publish)
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
            11 -> signalDeck()
            12 -> djConsole()
            13 -> psychedelicScene()
            14 -> if (lavaMetaballProgram != 0) shaderLavaLamp() else lavaLamp()
            15 -> signalRain()
            16 -> contourIslands()
            17 -> ribbonReactor()
            18 -> prismGarden()
            19 -> moonlitTides()
            20 -> laserLightShow()
            21 -> echoReef()
            22 -> velvetVortex()
            23 -> lanternDrift()
            IdleVisualizerStyle.IonTransit.ordinal -> ionProgram.draw(feedbackWidth, feedbackHeight, ionDrive.frame, ionPalette)
            else -> kaleidoscope()
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun usesFeedback() = IdleVisualizerStyle.entries
        .getOrNull(style)
        ?.metadata
        ?.renderProfile == IdleVisualizerRenderProfile.Feedback

    private fun usesSceneBuffer() = style == IdleVisualizerStyle.LavaLamp.ordinal ||
        style == IdleVisualizerStyle.NeonKaleidoscope.ordinal || style == IdleVisualizerStyle.IonTransit.ordinal ||
        style == IdleVisualizerStyle.LightTrails.ordinal

    private fun usesRenderBuffer() = usesFeedback() || usesSceneBuffer()

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
        val renderWidth = if (usesSceneBuffer()) feedbackWidth else width
        val renderHeight = if (usesSceneBuffer()) feedbackHeight else height
        GLES20.glUniform2f(neonResolutionHandle, renderWidth.toFloat(), renderHeight.toFloat())
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

    /** Curved long exposures on wet asphalt, independent of the legacy feedback ribbons. */
    private fun lightTrails() {
        val frame = lightTrailsDrive.frame
        lightTrailsProgram.draw(feedbackWidth, feedbackHeight, frame.travel, frame.energy, frame.bass, frame.attack, frame.reduced, frame.sky, frame.sweep, lightStationPalette)
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

    /** A stained-glass forge: measured spectrum opens large faceted apertures. */
    private fun glassCurrent() {
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.008f, .014f, .048f, 1f))
        repeat(4) { aperture ->
            val centreX = when (aperture) {
                0 -> -.54f
                1 -> .12f
                2 -> .56f
                else -> -.20f
            }
            val centreY = when (aperture) {
                0 -> .20f
                1 -> .34f
                2 -> -.28f
                else -> -.42f
            }
            val local = (band(aperture * 2 + 1) * .62f + band(aperture * 2 + 4) * .38f).coerceIn(0f, 1f)
            repeat(4) { ring ->
                val segments = 10
                val radius = .15f + ring * .095f + local * (.040f + ring * .017f) + impact * .025f
                begin()
                repeat(segments) { point ->
                    val angle = point * Tau / segments + aperture * .37f + ring * .13f
                    val facet = if (point % 2 == 0) 1f else .76f
                    vertex(centreX + cos(angle) * radius * facet * aspect(), centreY + sin(angle) * radius * facet)
                }
                lineLoop(segments, palette(aperture + ring * 3, .18f + local * .30f + transient * .12f), 1.1f + ring * .25f + local * 1.35f)
            }
            repeat(5) { ray ->
                val angle = ray * Tau / 5f + aperture * .82f
                begin(); vertex(centreX, centreY); vertex(centreX + cos(angle) * (.40f + local * .16f) * aspect(), centreY + sin(angle) * (.40f + local * .16f))
                lines(2, palette(aperture + ray + 2, .08f + local * .22f), .7f + local * 1.15f)
            }
        }
    }

    /** A dense, fixed patch board: frequency routes establish an obvious living circuit. */
    private fun vectorSwitchboard() {
        val routingGeneration = impactPhase.toInt()
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.004f, .014f, .031f, 1f))
        repeat(7) { row ->
            val y = -.72f + row * .24f
            thickSegment(-.92f, y, .92f, y, .0024f, Rgba(.12f, .20f, .30f, .12f))
        }
        repeat(8) { column ->
            val x = -.86f + column * .246f
            thickSegment(x, -.84f, x, .84f, .0024f, Rgba(.12f, .20f, .30f, .11f))
        }
        repeat(18) { module ->
            val sourceBand = module % BandCount
            val destinationBand = (sourceBand * 3 + routingGeneration) % BandCount
            val sourceY = -.72f + (module % 7) * .24f
            val destinationY = -.72f + destinationBand * (1.44f / (BandCount - 1))
            val strength = band(sourceBand)
            // This central bend is assigned to the relevant frequency region; it
            // should not drift unless that band changes.
            val bridgeY = (sourceY + destinationY) * .5f + (band(destinationBand) - .5f) * .18f
            val colour = palette(sourceBand, .08f + strength * .60f + transient * .16f)
            val thickness = .0036f + strength * .0080f
            thickSegment(-.86f, sourceY, -.30f, sourceY, thickness, colour)
            thickSegment(-.30f, sourceY, 0f, bridgeY, thickness, colour)
            thickSegment(0f, bridgeY, .30f, destinationY, thickness, colour)
            thickSegment(.30f, destinationY, .86f, destinationY, thickness, colour)
        }
    }

    /** A celestial instrument: three static observatory rings flex only with audio energy. */
    private fun deepWarp() {
        val low = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) / 4f).coerceIn(0f, 1f)
        val high = ((band(6) + band(7)) * .5f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.004f, .006f, .026f, 1f))
        repeat(3) { orbit ->
            val samples = 96
            val radiusX = (.28f + orbit * .22f + low * .045f) * aspect()
            val radiusY = .20f + orbit * .16f + low * .035f
            begin()
            repeat(samples) { point ->
                val angle = point * Tau / (samples - 1f)
                val wobble = sin(angle * 3f + orbit * 1.8f) * (mid * .028f + transient * .012f)
                vertex(cos(angle) * (radiusX + wobble), sin(angle) * (radiusY + wobble))
            }
            lineLoop(samples, palette(orbit + 5, .20f + low * .28f + high * .12f), 1.1f + high * 1.4f)
        }
        repeat(12) { marker ->
            val angle = marker * Tau / 12f
            val local = band(marker)
            val radius = .30f + (marker % 3) * .22f + local * .085f
            val x = cos(angle) * radius * aspect()
            val y = sin(angle) * radius
            val markerRadius = .012f + local * .026f
            begin()
            vertex(x, y)
            repeat(19) { point ->
                val markerAngle = point * Tau / 18f
                vertex(x + cos(markerAngle) * markerRadius * aspect(), y + sin(markerAngle) * markerRadius)
            }
            triangleFan(20, palette(marker + 2, .35f + local * .42f))
            begin(); vertex(x, y); vertex(cos(angle) * (radius + .11f + local * .08f) * aspect(), sin(angle) * (radius + .11f + local * .08f))
            lines(2, White.withAlpha(.10f + high * .26f), .75f + high)
        }
    }

    /** Broad chromatic ribbons with a soft parallax lift rather than flat colour bands. */
    private fun chromafield() {
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.008f, .022f, .052f, 1f))

        val currents = arrayOf(
            Rgba(.08f, .48f, .90f, .30f),
            Rgba(.10f, .76f, .70f, .28f),
            Rgba(.42f, .28f, .88f, .30f),
            Rgba(.86f, .20f, .58f, .26f),
            Rgba(.98f, .48f, .15f, .22f),
            Rgba(.18f, .38f, .96f, .28f),
            Rgba(.12f, .72f, .82f, .26f),
        )
        val samples = 72
        val slowClock = phase * .052f
        currents.forEachIndexed { index, colour ->
            val low = (band(index) * .72f + band(index + 1) * .28f).coerceIn(0f, 1f)
            val mid = ((band(index + 3) + band(index + 4)) * .5f).coerceIn(0f, 1f)
            val baseY = -1.16f + index * .355f
            val thickness = .37f + low * .055f
            fun currentY(x: Float): Float {
                val primary = sin(x * 1.58f + slowClock + index * .82f) * (.085f + low * .065f)
                val secondary = sin(x * 4.15f - slowClock * .56f + index * 1.31f) * (.024f + mid * .032f)
                return baseY + primary + secondary
            }

            fun ribbon(offsetY: Float, ribbonThickness: Float, tint: Rgba) {
                begin()
                repeat(samples) { point ->
                    val x = -1.06f + point * (2.12f / (samples - 1))
                    val y = currentY(x) + offsetY
                    vertex(x, y - ribbonThickness * .5f)
                    vertex(x, y + ribbonThickness * .5f)
                }
                triangleStrip(samples * 2, tint)
            }

            // A restrained shadow, fill, and lit crest give every current a raised,
            // floating-ribbon read without an additional pass or high-frequency noise.
            ribbon(
                offsetY = -.046f - low * .012f,
                ribbonThickness = thickness * 1.04f,
                tint = colour.blend(Rgba(.006f, .010f, .035f, 1f), .70f)
                    .withAlpha(.24f + low * .12f),
            )
            ribbon(
                offsetY = 0f,
                ribbonThickness = thickness,
                tint = colour.withAlpha(colour.a * (.84f + low * .16f)),
            )
            ribbon(
                offsetY = thickness * .205f,
                ribbonThickness = thickness * .17f,
                tint = colour.blend(White, .56f).withAlpha(.13f + mid * .12f),
            )

            begin()
            repeat(samples) { point ->
                val x = -1.06f + point * (2.12f / (samples - 1))
                vertex(x, currentY(x) + thickness * .28f)
            }
            lineStrip(samples, White.withAlpha(.10f + mid * .09f), 1.15f)
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

    /** A single honest live deck: every animated control is sourced from decoded PCM. */
    private fun djConsole() {
        val low = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) * .25f).coerceIn(0f, 1f)
        val high = ((band(6) + band(7)) * .5f).coerceIn(0f, 1f)
        val jogCenterX = -.49f
        val jogCenterY = -.07f

        // Live low-end gives the jog wheel its mass. The ring never rotates on
        // its own; it only reshapes and brightens with the current programme.
        repeat(4) { ring ->
            val radius = .105f + ring * .075f + low * (.014f + ring * .010f) + impact * .025f
            begin()
            repeat(84) { index ->
                val angle = index * Tau / 83f
                vertex(jogCenterX + cos(angle) * radius * aspect(), jogCenterY + sin(angle) * radius)
            }
            lineStrip(84, palette(ring + 1, .13f + low * .27f + mid * .10f), 1f + high * .80f)
        }
        repeat(24) { spoke ->
            val angle = spoke * Tau / 24f + phase * (.035f + low * .12f)
            val inner = .075f
            val outer = .22f + low * .115f + mid * .045f + impact * .040f
            begin()
            vertex(jogCenterX + cos(angle) * inner * aspect(), jogCenterY + sin(angle) * inner)
            vertex(jogCenterX + cos(angle) * outer * aspect(), jogCenterY + sin(angle) * outer)
            lines(2, palette(spoke, .08f + low * .20f + transient * .10f), .7f + high * .75f)
        }

        // The waveform display is the actual recent decoded PCM, on one fixed
        // amplitude scale so it represents signal shape rather than loudness gain.
        val waveformSamples = 220
        begin()
        repeat(waveformSamples) { index ->
            val x = .02f + index * (.80f / (waveformSamples - 1))
            val source = index * (AudioWaveformSampleCount - 1) / (waveformSamples - 1)
            val y = .29f + waveform[source] * .145f
            vertex(x, y)
        }
        lineStrip(waveformSamples, palette(1, .48f + mid * .30f + high * .14f), 1.3f + high * .8f)
        begin(); vertex(.02f, .29f); vertex(.82f, .29f)
        lines(2, White.withAlpha(.10f), .7f)

        // Four calibrated EQ faders at the right of the jog wheel. Their height
        // is the same fixed dBFS-to-display mapping as Signal Deck's band meters.
        repeat(CalibratedBandCount) { channel ->
            val x0 = -.02f + channel * .145f
            val x1 = x0 + .070f
            val bottom = -.67f
            val top = -.32f
            val active = calibratedMeters[channel]
            begin()
            repeat(12) { segment ->
                val y0 = bottom + segment * ((top - bottom) / 12f) + .006f
                val y1 = y0 + (top - bottom) / 12f - .012f
                ledCell(x0, x1, y0, y1)
            }
            triangles(72, Rgba(.025f, .055f, .085f, .42f))
            val lit = (active * 12f).toInt().coerceIn(0, 12)
            if (lit > 0) {
                begin()
                repeat(lit) { segment ->
                    val y0 = bottom + segment * ((top - bottom) / 12f) + .006f
                    val y1 = y0 + (top - bottom) / 12f - .012f
                    ledCell(x0, x1, y0, y1)
                }
                triangles(lit * 6, palette(channel * 2, .55f + active * .34f))
            }
        }

        // Independent left/right master meters retain the real decoded channel
        // values instead of implying two separate playback decks.
        val masterXs = floatArrayOf(.58f, .73f)
        val masterLevels = floatArrayOf(masterLeft, masterRight)
        masterXs.forEachIndexed { index, x ->
            // Leave a dedicated caption/readout strip below the moving hardware
            // meter instead of allowing its stem to run through the text.
            val y0 = -.56f
            val y1 = -.28f
            begin(); vertex(x, y0); vertex(x, y1)
            lines(2, White.withAlpha(.14f), 5f)
            begin(); vertex(x, y0); vertex(x, y0 + (y1 - y0) * masterLevels[index])
            lines(2, if (index == 0) Cyan.withAlpha(.78f) else palette(3, .78f), 4f)
        }
    }

    /** A source-led psychedelic field: bass opens petals, mids bend them, and attacks flash their seams. */
    private fun psychedelicScene() {
        val low = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) * .25f).coerceIn(0f, 1f)
        val high = ((band(6) + band(7)) * .5f).coerceIn(0f, 1f)
        begin()
        vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.040f, .002f, .105f, 1f))
        // Keep a dense radial composition but cap per-frame translucent geometry.
        // The previous three-ring, 11-petal, 96-sample construction was visibly
        // rich but exceeded the Streamer's CPU budget under active playback.
        val petals = 10
        // The wide, high-saturation rings make the colour field legible from a
        // sofa. They still breathe from decoded energy instead of free-running.
        repeat(4) { ring ->
            val radius = .18f + ring * .18f + low * .075f + impact * .055f
            circle(
                radius,
                palette(ring * 2 + 1, .20f + mid * .14f + high * .09f + transient * .10f),
                112,
                1.1f + high * 1.2f,
            )
        }
        repeat(2) { ring ->
            val samples = 56
            val baseRadius = .20f + ring * .22f + low * (.10f + ring * .025f) + impact * .08f
            repeat(petals) { petal ->
                val centreAngle = petal * Tau / petals + ring * .17f
                begin()
                vertex(0f, 0f)
                repeat(samples + 1) { point ->
                    val t = point / samples.toFloat()
                    val localAngle = centreAngle + (t - .5f) * (.42f + mid * .18f)
                    val ripple = sin(t * Tau * (2.2f + ring * .6f) + phase * (.54f + low * .55f) + petal) *
                        (.035f + mid * .10f + transient * .05f)
                    val fringe = sin(t * Tau * (6.5f + ring) - phase * .34f + petal * 1.7f) * high * .027f
                    val radius = (baseRadius + ripple + fringe) * (1f + sin(t * PI.toFloat()) * (.34f + low * .22f))
                    vertex(cos(localAngle) * radius * aspect(), sin(localAngle) * radius)
                }
                triangleFan(samples + 2, palette(petal + ring * 3, .30f + low * .26f + mid * .16f + impact * .12f))

                begin()
                repeat(samples) { point ->
                    val t = point / (samples - 1f)
                    val localAngle = centreAngle + (t - .5f) * (.42f + mid * .18f)
                    val ripple = sin(t * Tau * (2.2f + ring * .6f) + phase * (.54f + low * .55f) + petal) *
                        (.035f + mid * .10f + transient * .05f)
                    val fringe = sin(t * Tau * (6.5f + ring) - phase * .34f + petal * 1.7f) * high * .027f
                    val radius = (baseRadius + ripple + fringe) * (1f + sin(t * PI.toFloat()) * (.34f + low * .22f))
                    vertex(cos(localAngle) * radius * aspect(), sin(localAngle) * radius)
                }
                lineStrip(samples, palette(petal + ring * 3 + 2, .64f + mid * .22f + high * .12f + transient * .12f), 1.5f + high * 1.8f)
            }
        }
        // Interleaved rainbow spirals keep the scene immersive at TV distance;
        // their twist is still driven by the source-led phase accumulator.
        repeat(5) { ribbon ->
            val samples = 112
            begin()
            repeat(samples) { point ->
                val t = point / (samples - 1f)
                val angle = t * Tau * (2.3f + ribbon * .17f) + phase * (.42f + low * .62f) + ribbon * Tau / 7f
                val wave = sin(t * Tau * (4.1f + ribbon * .25f) - phase * .34f) * (.030f + mid * .090f + transient * .045f)
                val radius = .10f + t * (.74f + low * .22f) + wave
                vertex(cos(angle) * radius * aspect(), sin(angle) * radius)
            }
            lineStrip(samples, palette(ribbon + 4, .48f + mid * .26f + high * .16f + transient * .10f), 1.25f + high * 1.55f)
        }
        begin()
        var count = 0
        repeat(42) { spark ->
            val angle = spark * Tau / 42f + phase * (.12f + high * .34f)
            val radius = .12f + (spark % 7) * .105f + low * .18f + impact * .12f
            vertex(cos(angle) * radius * aspect(), sin(angle) * radius)
            count++
        }
        points(count, White.withAlpha(.44f + high * .28f + transient * .16f), 2.2f + high * 2.6f + impact * 3.0f)
    }

    /**
     * One bounded fragment-field is evaluated only inside the active vessel.
     * Its offset wax masses overlap into short, curved bridges; it deliberately
     * has no separately drawn neck or per-frame geometry allocation.
     */
    private fun shaderLavaLamp() {
        val colours = LavaLampColourPalettes[lavaLampPalette]
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(lavaMetaballProgram)
        val renderWidth = if (feedbackWidth > 1 && feedbackHeight > 1) feedbackWidth else width
        val renderHeight = if (feedbackWidth > 1 && feedbackHeight > 1) feedbackHeight else height
        GLES20.glUniform2f(lavaMetaballResolutionHandle, renderWidth.toFloat(), renderHeight.toFloat())
        colours.forEachIndexed { index, colour ->
            val offset = index * 3
            lavaPaletteUniform[offset] = colour.r
            lavaPaletteUniform[offset + 1] = colour.g
            lavaPaletteUniform[offset + 2] = colour.b
        }
        GLES20.glUniform3fv(lavaMetaballColoursHandle, 5, lavaPaletteUniform, 0)
        GLES20.glUniform1f(lavaMetaballEnergyHandle, lavaThermalEnergy)
        GLES20.glUniform1f(lavaMetaballPulseHandle, lavaPulseEnergy)
        GLES20.glUniform1f(lavaMetaballPhaseHandle, lavaConvectionClock)
        feedbackQuad.position(0)
        GLES20.glEnableVertexAttribArray(lavaMetaballPositionHandle)
        GLES20.glVertexAttribPointer(
            lavaMetaballPositionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            4 * Float.SIZE_BYTES,
            feedbackQuad,
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(lavaMetaballPositionHandle)
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** A lava lamp without decorative clockwork: every lift, merge, and glow is tied to decoded audio. */
    private fun lavaLamp() {
        val low = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) * .25f).coerceIn(0f, 1f)
        val high = ((band(6) + band(7)) * .5f).coerceIn(0f, 1f)
        begin()
        vertex(-.72f, -1f); vertex(.72f, -1f); vertex(-.72f, 1f); vertex(.72f, 1f)
        triangleStrip(4, Rgba(.105f, .004f, .200f, 1f))
        begin()
        vertex(-.72f, -1f); vertex(.72f, -1f); vertex(-.72f, 1f); vertex(.72f, 1f)
        triangleStrip(4, Rgba(.100f, .004f, .180f, .84f))
        begin(); vertex(-.73f, -.98f); vertex(-.73f, .98f); vertex(.73f, .98f); vertex(.73f, -.98f)
        lineLoop(4, Rgba(.96f, .28f, .78f, .38f + high * .28f), 1.6f)

        val liquidColours = LavaLampColourPalettes[lavaLampPalette]
        // A lamp is one wax colour family, not a scatter of unrelated balloons.
        // Six substantial bodies are intentionally overlapped so additive blending
        // produces long, merging lava columns as the decoded bass rises and falls.
        repeat(6) { blob ->
            val samples = 112
            val lane = floatArrayOf(-.13f, .08f, -.05f, .13f, -.10f, .04f)[blob]
            val heightSeed = floatArrayOf(-.69f, -.42f, -.13f, .18f, .48f, .75f)[blob]
            val localLow = (band(blob) * .66f + band(blob + 1) * .34f).coerceIn(0f, 1f)
            val localMid = (band(blob + 3) * .62f + band(blob + 4) * .38f).coerceIn(0f, 1f)
            val lift = sin(phase * (.21f + localLow * .64f) + blob * 1.91f) * (.10f + localLow * .25f + impact * .10f)
            val centreX = lane + sin(phase * (.16f + localMid * .38f) + blob * .83f) * (.025f + localMid * .070f)
            val centreY = (heightSeed + lift).coerceIn(-.82f, .82f)
            val radiusX = .18f + localLow * .12f + impact * .060f
            val radiusY = .24f + localLow * .19f + localMid * .075f
            begin()
            vertex(centreX, centreY)
            repeat(samples + 1) { point ->
                val angle = point * Tau / samples
                val wobble = sin(angle * 3f + phase * (.52f + localLow * .62f) + blob) * (.020f + localMid * .060f) +
                    sin(angle * 5f - phase * .30f + blob * 1.7f) * high * .023f
                vertex(
                    centreX + cos(angle) * (radiusX + wobble) * aspect(),
                    centreY + sin(angle) * (radiusY + wobble),
                )
            }
            val liquidColour = liquidColours[blob % liquidColours.size]
            triangleFan(samples + 2, liquidColour.withAlpha(.68f + localLow * .20f + localMid * .08f))
            begin()
            repeat(samples) { point ->
                val angle = point * Tau / (samples - 1f)
                val wobble = sin(angle * 3f + phase * (.52f + localLow * .62f) + blob) * (.020f + localMid * .060f) +
                    sin(angle * 5f - phase * .30f + blob * 1.7f) * high * .023f
                vertex(
                    centreX + cos(angle) * (radiusX + wobble) * aspect(),
                    centreY + sin(angle) * (radiusY + wobble),
                )
            }
            lineLoop(samples, liquidColour.blend(White, .22f).withAlpha(.72f + localLow * .16f + high * .08f), 1.55f + high * 1.25f)

            // A small hot core catches the light and makes the overlapping bodies
            // read as glowing liquid, not flat discs.
            begin()
            val highlightX = centreX - radiusX * .20f * aspect()
            val highlightY = centreY + radiusY * .20f
            vertex(highlightX, highlightY)
            repeat(40) { point ->
                val angle = point * Tau / 39f
                vertex(
                    highlightX + cos(angle) * radiusX * .30f * aspect(),
                    highlightY + sin(angle) * radiusY * .28f,
                )
            }
            triangleFan(42, liquidColour.blend(White, .58f).withAlpha(.32f + localLow * .16f + high * .08f))
        }
    }

    /** A large harmonic gate: arches visibly open and brighten with their assigned bands. */
    private fun signalRain() {
        val low = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.005f, .018f, .045f, 1f))
        repeat(9) { arch ->
            val local = (band(arch) * .64f + band(arch + 3) * .36f).coerceIn(0f, 1f)
            val x = -.84f + arch * .21f
            val baseY = -.78f
            val height = .34f + local * .78f + low * .16f
            val width = .055f + local * .036f
            val samples = 36
            begin()
            repeat(samples) { point ->
                val t = point / (samples - 1f)
                val y = baseY + height * sin(t * PI.toFloat())
                vertex(x + (t - .5f) * width * aspect() * 2f, y)
            }
            lineStrip(samples, palette(arch + 2, .25f + local * .42f + transient * .14f), 1.4f + local * 2.6f)
            begin(); vertex(x, baseY); vertex(x, baseY + height * .82f)
            lines(2, White.withAlpha(.08f + local * .22f), .7f + local * 1.1f)
        }
        begin(); vertex(-.94f, -.78f); vertex(.94f, -.78f)
        lines(2, Cyan.withAlpha(.24f + low * .32f), 1.3f)
    }

    /** Nested shorelines keep this scene distinctly calm and non-vortex-like. */
    private fun contourIslands() {
        val low = (band(0) * .72f + band(1) * .28f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) * .25f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.008f, .043f, .060f, 1f))
        val islandX = floatArrayOf(-.47f, .34f, .05f)
        val islandY = floatArrayOf(-.18f, -.28f, .42f)
        repeat(3) { island ->
            val local = (band(island * 3 + 1) * .65f + band(island * 3 + 3) * .35f).coerceIn(0f, 1f)
            repeat(5) { contour ->
                val samples = 84
                val base = .105f + contour * .080f + local * .045f + low * .025f
                begin()
                repeat(samples) { point ->
                    val angle = point * Tau / (samples - 1f)
                    val shoreline = base + sin(angle * 3f + island * 1.7f + phase * .14f) * (.014f + mid * .026f) +
                        cos(angle * 5f - phase * .10f) * .012f
                    vertex(
                        islandX[island] + cos(angle) * shoreline * aspect(),
                        islandY[island] + sin(angle) * shoreline * (.72f + local * .16f),
                    )
                }
                lineLoop(samples, palette(island * 2 + contour, .22f + contour * .055f + local * .20f), 1f + local * 1.45f)
            }
        }
    }

    /** Wide crossed bands form a textile-like field rather than a generic waveform. */
    private fun ribbonReactor() {
        val low = (band(0) * .68f + band(1) * .32f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) * .25f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.018f, .006f, .044f, 1f))
        val samples = 72
        repeat(7) { ribbon ->
            begin()
            repeat(samples) { point ->
                val t = point / (samples - 1f)
                val x = -1.10f + t * 2.20f
                val y = -.82f + ribbon * .27f + sin(t * Tau * .72f + ribbon * .91f + phase * (.18f + mid * .28f)) *
                    (.060f + low * .11f + band(ribbon + 2) * .08f)
                val slope = cos(t * Tau * .72f + ribbon * .91f + phase * (.18f + mid * .28f)) * Tau * .72f *
                    (.060f + low * .11f + band(ribbon + 2) * .08f)
                val normalX = -slope * .010f
                val normalY = .010f
                vertex(x + normalX, y + normalY); vertex(x - normalX, y - normalY)
            }
            triangleStrip(samples * 2, palette(ribbon + 2, .22f + mid * .28f + low * .18f))
        }
        repeat(6) { ribbon ->
            begin()
            repeat(samples) { point ->
                val t = point / (samples - 1f)
                val x = -.82f + ribbon * .32f + sin(t * Tau * .72f + ribbon * .77f - phase * (.16f + mid * .24f)) *
                    (.060f + low * .10f + band(ribbon + 3) * .07f)
                val y = -1.08f + t * 2.16f
                val slope = cos(t * Tau * .72f + ribbon * .77f - phase * (.16f + mid * .24f)) * Tau * .72f *
                    (.060f + low * .10f + band(ribbon + 3) * .07f)
                val normalX = .010f
                val normalY = -slope * .010f
                vertex(x + normalX, y + normalY); vertex(x - normalX, y - normalY)
            }
            triangleStrip(samples * 2, palette(ribbon + 5, .18f + mid * .28f + low * .16f))
        }
    }

    private fun prismGarden() {
        val low = (band(0) * .72f + band(1) * .28f).coerceIn(0f, 1f)
        val high = ((band(6) + band(7)) * .5f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.008f, .020f, .044f, 1f))
        begin(); vertex(-1f, -.48f); vertex(1f, -.48f)
        lines(2, palette(4, .22f + low * .24f), 1.1f)
        repeat(13) { petal ->
            val local = band(petal + 2)
            val x = -.92f + petal * (1.84f / 12f)
            val base = -.50f + sin(petal * 1.7f) * .045f
            val height = .16f + local * .43f + low * .18f
            val half = .052f + local * .035f
            begin()
            vertex(x, base)
            vertex(x - half * aspect(), base + height * .42f)
            vertex(x, base + height)
            vertex(x + half * aspect(), base + height * .42f)
            triangleFan(4, palette(petal + 1, .28f + local * .34f + low * .14f))
            begin(); vertex(x, base); vertex(x, base + height)
            lines(2, White.withAlpha(.22f + high * .36f), .8f + high * 1.2f)
        }
    }

    /** A calm dusk lattice: offset cells expand from their own spectral bands. */
    private fun moonlitTides() {
        val low = (band(0) * .72f + band(1) * .28f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) * .25f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.005f, .017f, .055f, 1f))
        repeat(15) { cell ->
            val local = (band(cell) * .62f + band(cell + 3) * .38f).coerceIn(0f, 1f)
            val column = cell % 5
            val row = cell / 5
            val centreX = -.72f + column * .36f + if (row % 2 == 0) 0f else .08f
            val centreY = -.48f + row * .43f
            val radius = .080f + local * .105f + low * .035f
            val samples = 6
            begin()
            vertex(centreX, centreY)
            repeat(samples + 1) { point ->
                val angle = point * Tau / samples + PI.toFloat() / 6f
                val lobe = 1f + sin(angle * 3f + cell * .73f + phase * .16f) * (mid * .13f + transient * .05f)
                vertex(
                    centreX + cos(angle) * radius * lobe * aspect(),
                    centreY + sin(angle) * radius * lobe,
                )
            }
            triangleFan(samples + 2, palette(cell + 5, .13f + local * .28f + low * .10f))
        }
    }

    // Aperture centers measured from the bundled 1672 x 941 backplate.
    private fun laserLensX(fixture: Int): Float = when (fixture) {
        0 -> -.712f
        1 -> -.432f
        2 -> -.146f
        3 -> .143f
        4 -> .432f
        5 -> .713f
        6 -> -.707f
        else -> .708f
    }

    private fun drawLaserStageBackplate(): Boolean {
        if (laserStageLoadFailed) return false
        if (laserStageTexture[0] == 0) {
            val decodeSample = if (compatibilityRendering) 2 else 1
            val pending = laserStageDecode ?: LaserStageDecoder.submit<Bitmap?> {
                BitmapFactory.decodeResource(resources, R.drawable.laser_stage_led_slim,
                    BitmapFactory.Options().apply { inScaled = false; inSampleSize = decodeSample })
            }.also { laserStageDecode = it }
            // Never block the GL thread waiting for disk/decompression.
            if (!pending.isDone) return false
            val bitmap = try { pending.get() } catch (_: java.util.concurrent.ExecutionException) { null }
                ?: run { laserStageLoadFailed = true; return false }
            GLES20.glGenTextures(1, laserStageTexture, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, laserStageTexture[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            try { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0) } finally { bitmap.recycle() }
            laserStageDecode = null
        }
        if (laserStageProgram == 0) {
            laserStageProgram = linkProgram(
                compileShader(GLES20.GL_VERTEX_SHADER, LavaMetaballVertexShader),
                compileShader(GLES20.GL_FRAGMENT_SHADER, LaserStageFragmentShader),
            )
            laserStagePosition = GLES20.glGetAttribLocation(laserStageProgram, "aPosition")
            laserStageSampler = GLES20.glGetUniformLocation(laserStageProgram, "uStage")
        }
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(laserStageProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, laserStageTexture[0])
        GLES20.glUniform1i(laserStageSampler, 0)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(laserStagePosition)
        GLES20.glVertexAttribPointer(laserStagePosition, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisableVertexAttribArray(laserStagePosition)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnable(GLES20.GL_BLEND)
        return true
    }

    /** Concert cues: soft volumetric-looking cones with live audio-driven narrow cores. */
    private fun laserLightShow() {
        val bass = band(0) * .7f + band(1) * .3f
        // Positions are held cue targets; only incoming PCM attacks advance them.
        fun ellipse(x: Float, y: Float, rx: Float, ry: Float, colour: Rgba) {
            begin(); vertex(x, y)
            repeat(25) { i ->
                val a = i * Tau / 24f
                vertex(x + cos(a) * rx, y + sin(a) * ry)
            }
            triangleFan(26, colour)
        }
        if (!drawLaserStageBackplate()) return
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)

        // Low-intensity foreground wash; final bank has one lens in the generated plate.
        // All endpoints lie outside the viewport so the photographed floor cannot cut them off.
        repeat(LaserStageLayout.bankCount) { bank ->
            val x = LaserStageLayout.bankX(bank) * 2f - 1f
            val y = 1f - LaserStageLayout.lensY * 2f
            val drive = laserAudioDrive(band(bank), bass, laserChoreography.bassHit)
            val chase = laserCues.floorActivity(bank)
            val colour = if (bank % 3 == 0) Rgba(1f, .72f, .34f, 1f) else Rgba(.30f, .66f, 1f, 1f)
            val endX = x * 1.8f + laserCues.aim(bank) * .5f
            repeat(3) { layer ->
                val width = (.65f + drive * .65f) * (1f - layer * .25f)
                begin()
                vertex(x - .065f, y); vertex(x + .065f, y)
                vertex(endX - width, 1.8f); vertex(endX + width, 1.8f)
                triangleStrip(4, colour.withAlpha((.003f + drive * .023f) * (.3f + chase * .7f)))
            }
            repeat(2) { lamp ->
                val index = bank * 2 + lamp
                if (index < LaserStageLayout.lensCount) {
                    ellipse(LaserStageLayout.lensX(index) * 2f - 1f, y,
                        .016f * aspect(), .005f,
                        colour.withAlpha(.12f + drive * .65f * (.3f + chase * .7f)))
                }
            }
        }

        repeat(6) { fixture ->
            val x = laserLensX(fixture)
            val y = .573f
            val bank = if (fixture < 3) 0 else 3
            val local = (band(bank) + band(bank + 1) + band(bank + 2)) / 3f
            val drive = laserAudioDrive(local, bass, 0f)
            val colour = if (fixture % 3 == 0) Rgba(1f, .78f, .48f, 1f) else Rgba(.38f, .72f, 1f, 1f)
            val hit = laserCues.pulse
            val activity = laserCues.activity(fixture) * (.25f + hit * .75f)
            val centre = laserCues.aim(fixture)
            val endY = -1.8f
            val dx = (centre - x) / aspect()
            val dy = endY - y
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.001f)
            val nx = -dy / length * aspect()
            val ny = dx / length
            val width = .20f + laserCues.spread(fixture) * .35f + hit * .85f
            begin()
            vertex(x + nx * .009f, y + ny * .009f); vertex(x - nx * .009f, y - ny * .009f)
            vertex(centre + nx * width, endY + ny * width); vertex(centre - nx * width, endY - ny * width)
            softConcertBeam(x, y, dx / length, dy / length, length, width,
                colour.withAlpha(activity * (.084f + drive * .385f + hit * .245f)))
            val accent = (.12f + drive * .10f + hit * .78f) * laserCues.activity(fixture)
            if (accent > .001f) {
                begin()
                repeat(3) { ray ->
                    val tip = centre + (ray - 1) * laserCues.spread(fixture) * (.25f + hit * 1.5f)
                    vertex(x + nx * .002f, y + ny * .002f); vertex(x - nx * .002f, y - ny * .002f)
                    vertex(tip + nx * .006f, endY + ny * .006f)
                    vertex(tip + nx * .006f, endY + ny * .006f); vertex(x - nx * .002f, y - ny * .002f)
                    vertex(tip - nx * .006f, endY - ny * .006f)
                }
                // Near-white cores remain readable against the photographed stage at low audio levels.
                triangles(18, Rgba(.75f + colour.r * .25f, .75f + colour.g * .25f,
                    .75f + colour.b * .25f, accent * (.70f + drive * .30f)))
            }
        }

        // Live aperture glow stays anchored to the photographed glass, not a second cabinet.
        repeat(6) { fixture ->
            val x = laserLensX(fixture)
            val y = .573f
            val colour = if (fixture % 3 == 0) Rgba(1f, .78f, .48f, 1f) else Rgba(.38f, .72f, 1f, 1f)
            val activity = maxOf(laserCues.activity(fixture) * .25f,
                concertLaserStrength(band(fixture), laserCues.pulse, laserCues.pulse))
            val drive = laserAudioDrive(band(fixture), bass, laserChoreography.snareHit)
            val radius = .008f + drive * .012f
            ellipse(x, y, radius * aspect(), radius, colour.withAlpha(activity * (.12f + drive * .55f)))
            ellipse(x, y, .004f * aspect(), .004f, White.withAlpha(activity * (.15f + drive * .75f)))
        }
    }

    /** One bounded quad per beam; analytic edge falloff avoids visible nested-cone bands. */
    private fun softConcertBeam(x: Float, y: Float, ax: Float, ay: Float, length: Float, width: Float, color: Rgba) {
        if (concertProgram == 0) {
            concertProgram = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, LavaMetaballVertexShader),
                compileShader(GLES20.GL_FRAGMENT_SHADER, ConcertBeamFragmentShader))
            concertPosition = GLES20.glGetAttribLocation(concertProgram, "aPosition")
            concertStart = GLES20.glGetUniformLocation(concertProgram, "uStart")
            concertAxis = GLES20.glGetUniformLocation(concertProgram, "uAxis")
            concertWidths = GLES20.glGetUniformLocation(concertProgram, "uWidths")
            concertLength = GLES20.glGetUniformLocation(concertProgram, "uLength")
            concertAspect = GLES20.glGetUniformLocation(concertProgram, "uAspect")
            concertColor = GLES20.glGetUniformLocation(concertProgram, "uColor")
        }
        GLES20.glUseProgram(concertProgram)
        GLES20.glUniform2f(concertStart, x / aspect(), y)
        GLES20.glUniform2f(concertAxis, ax, ay)
        GLES20.glUniform2f(concertWidths, .009f, width)
        GLES20.glUniform1f(concertLength, length)
        GLES20.glUniform1f(concertAspect, aspect())
        GLES20.glUniform4f(concertColor, color.r, color.g, color.b, color.a)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(concertPosition)
        GLES20.glVertexAttribPointer(concertPosition, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(concertPosition)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
    }

    /** A dense paired reef: shared bases and short branches make a full underwater canopy. */
    private fun echoReef() {
        val low = (band(0) * .68f + band(1) * .32f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) * .25f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.002f, .034f, .039f, 1f))
        repeat(18) { branch ->
            val local = band(branch + 2)
            val rootX = -.90f + (branch % 9) * .225f
            val rootY = -.86f + (branch / 9) * .13f
            val direction = if (branch % 2 == 0) 1f else -1f
            val length = .21f + local * .38f + low * .17f
            begin()
            repeat(6) { joint ->
                val t = joint / 5f
                val x = rootX + direction * sin(t * Tau * .70f + branch) * (.025f + mid * .055f) * t
                val y = rootY + length * t
                vertex(x, y)
            }
            lineStrip(6, palette(branch + 4, .26f + local * .40f), 1.25f + local * 2.4f)
            repeat(2) { spur ->
                val at = .43f + spur * .23f
                val x = rootX + direction * sin(at * Tau * .70f + branch) * (.025f + mid * .055f) * at
                val y = rootY + length * at
                begin(); vertex(x, y); vertex(x + direction * (.075f + local * .11f) * aspect(), y + length * (.11f + spur * .035f))
                lines(2, palette(branch + spur + 5, .20f + local * .34f), .8f + local * 1.45f)
            }
        }
    }

    /** Field-line petals bend around a fixed magnetic core, never becoming another vortex. */
    private fun velvetVortex() {
        val low = (band(0) * .74f + band(1) * .26f).coerceIn(0f, 1f)
        val high = ((band(6) + band(7)) * .5f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.024f, .004f, .050f, 1f))
        repeat(10) { arm ->
            val samples = 76
            var previousX = 0f
            var previousY = 0f
            var vertexCount = 0
            begin()
            repeat(samples) { point ->
                val t = point / (samples - 1f)
                val side = if (arm % 2 == 0) -1f else 1f
                val lane = (arm / 2 - 2) * .19f
                val x = side * (.10f + t * .78f) * aspect()
                val y = lane + sin(t * PI.toFloat() + arm * .71f + phase * (.12f + low * .20f)) * (.070f + high * .075f)
                if (point > 0) {
                    appendThickSegment(
                        previousX,
                        previousY,
                        x,
                        y,
                        .0032f + high * .0021f,
                    )
                    vertexCount += 6
                }
                previousX = x
                previousY = y
            }
            triangles(vertexCount, palette(arm + 1, .48f + low * .22f + high * .17f))
        }
        circle(.075f + low * .055f, White.withAlpha(.52f + low * .30f), 48, 1.8f)
    }

    private fun lanternDrift() {
        val low = (band(0) * .70f + band(1) * .30f).coerceIn(0f, 1f)
        val mid = ((band(2) + band(3) + band(4) + band(5)) * .25f).coerceIn(0f, 1f)
        begin(); vertex(-1f, -1f); vertex(1f, -1f); vertex(-1f, 1f); vertex(1f, 1f)
        triangleStrip(4, Rgba(.085f, .016f, .006f, 1f))
        repeat(9) { lantern ->
            val local = band(lantern + 1)
            val x = -.80f + (lantern % 5) * .40f + sin(phase * (.10f + mid * .14f) + lantern) * (.035f + mid * .040f)
            val y = -.48f + (lantern / 5) * .72f + cos(phase * (.08f + low * .12f) + lantern * 1.9f) * (.050f + low * .065f)
            val radius = .120f + local * .080f + low * .035f
            // Soft outer body: a lantern should remain legible at silence, with
            // the music increasing its size and brightness rather than creating it.
            begin()
            vertex(x, y)
            repeat(44) { point ->
                val angle = point * Tau / 43f
                val shoulder = 1f - abs(sin(angle)) * .13f
                vertex(x + cos(angle) * radius * shoulder * aspect(), y + sin(angle) * radius * 1.17f)
            }
            triangleFan(45, Rgba(1f, .28f + local * .18f, .045f, .18f + local * .19f + low * .08f))
            begin()
            vertex(x, y)
            repeat(36) { point ->
                val angle = point * Tau / 35f
                vertex(x + cos(angle) * radius * .53f * aspect(), y + sin(angle) * radius * .66f)
            }
            triangleFan(37, Rgba(1f, .72f + local * .15f, .24f, .46f + local * .30f + low * .09f))
            begin(); vertex(x, y - radius * .90f); vertex(x, y + radius * .85f)
            lines(2, Rgba(1f, .52f, .14f, .34f + local * .32f), 5.5f + local * 6.2f)
            circle(radius, Rgba(1f, .72f, .28f, .54f + local * .32f), 40, 1.4f + local * 1.5f)
            begin(); vertex(x, y + radius); vertex(x, y + radius + .070f)
            lines(2, White.withAlpha(.30f + mid * .22f), .9f)
        }
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
        // Neon is a single opaque analytical pass. Bound its pixel cost independently
        // of the TV output resolution, then use the existing filtered presentation pass.
        val feedbackScale = if (style == IdleVisualizerStyle.NeonKaleidoscope.ordinal) {
            minOf(1f, 640f / width, 360f / height)
        } else if (style == IdleVisualizerStyle.LightTrails.ordinal) {
            minOf(1f, (if (compatibilityRendering) 480f else 576f) / width, (if (compatibilityRendering) 270f else 324f) / height)
        } else if (style == IdleVisualizerStyle.IonTransit.ordinal) {
            minOf(1f, (if (compatibilityRendering) 640f else 704f) / width, (if (compatibilityRendering) 360f else 396f) / height)
        } else if (compatibilityRendering) CompatibilityFeedbackResolutionFraction else FeedbackResolutionFraction
        feedbackWidth = (width * feedbackScale).toInt().coerceAtLeast(MinFeedbackWidth)
        feedbackHeight = (height * feedbackScale).toInt().coerceAtLeast(MinFeedbackHeight)
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
    private fun thickSegment(x0: Float, y0: Float, x1: Float, y1: Float, halfWidth: Float, color: Rgba) {
        begin()
        appendThickSegment(x0, y0, x1, y1, halfWidth)
        triangles(6, color)
    }

    /** Appends one camera-space ribbon segment; callers can batch a whole path into one draw. */
    private fun appendThickSegment(x0: Float, y0: Float, x1: Float, y1: Float, halfWidth: Float) {
        val dx = (x1 - x0) * aspect()
        val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(.0001f)
        val offsetX = -dy / length * halfWidth / aspect()
        val offsetY = dx / length * halfWidth
        vertex(x0 + offsetX, y0 + offsetY); vertex(x0 - offsetX, y0 - offsetY); vertex(x1 + offsetX, y1 + offsetY)
        vertex(x1 + offsetX, y1 + offsetY); vertex(x0 - offsetX, y0 - offsetY); vertex(x1 - offsetX, y1 - offsetY)
    }
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

    private fun unitInterval(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    private fun signedUnit(value: Float) = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f

    private fun band(index: Int) = unitInterval(bands[index % BandCount])
    private fun aspect() = height.toFloat() / width.toFloat()
    private fun wrap(value: Float) = ((value + 1f) % 2f + 2f) % 2f - 1f
    private fun palette(index: Int, alpha: Float) = Palette[index % Palette.size].withAlpha(alpha)

    private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        // Kotlin raw strings begin with a newline when the opening delimiter occupies its own
        // line. Most drivers accept that before a GLSL #version directive, but Fire OS 7's
        // compiler requires the directive to be the very first source line.
        GLES20.glShaderSource(shader, source.trimStart()); GLES20.glCompileShader(shader)
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
private const val CompatibilityFeedbackResolutionFraction = .25f
private const val MinFeedbackWidth = 320
private const val MinFeedbackHeight = 180
private const val CompatibilityMemoryClassMb = 256
private const val DefaultDisplayRefreshRateHz = 60f
private const val AudioSmoothing = 16f
private const val MasterMeterFloorDbfs = -30f
private const val MasterAttackPerSecond = 12f
private const val MasterReleasePerSecond = 2.2f
private const val MasterPeakFallPerSecond = .12f
private const val ImpulseSmoothing = 26f
private const val TransientSmoothing = 34f
private const val ImpactDecay = 6.5f
private const val LavaThermalRelaxationSeconds = 2.1f
private const val SpectrumPeakFallPerSecond = .34f
private const val SpectrumHistoryRows = 32
private const val SpectrumHistoryFrameStride = 3L
private const val WinampLedSegments = 18
private const val NanosPerSecond = 1_000_000_000.0
private const val FrameIntervalNanos = 16_666_667L
private const val EglOpenGlEs3Bit = 0x40
private const val NeonMotionMultiplier = 1.7f
private const val ScopeWaveformAmplitude = .18f
private const val Tau = (PI * 2.0).toFloat()
private val LightTrailPalette = intArrayOf(0, 6, 5, 3, 2, 1)
private val CurrentLanes = floatArrayOf(-.48f, .02f, .49f)
private val CurrentThemes = arrayOf(
    arrayOf(Rgba(.04f, .68f, 1f), Rgba(.05f, .95f, .78f), Rgba(.14f, .42f, 1f)), // electric water
    arrayOf(Rgba(1f, .08f, .62f), Rgba(.82f, .16f, 1f), Rgba(1f, .18f, .44f)), // fuchsia pulse
    arrayOf(Rgba(1f, .48f, .06f), Rgba(1f, .82f, .10f), Rgba(.92f, .23f, .04f)), // molten amber
)
private val LavaLampColourPalettes = arrayOf(
    arrayOf(Rgba(1f, .02f, .42f), Rgba(1f, .22f, .03f), Rgba(.82f, .04f, 1f), Rgba(1f, .76f, .02f), Rgba(.08f, .72f, 1f)), // neon
    arrayOf(Rgba(1f, .03f, .12f), Rgba(1f, .16f, .02f), Rgba(1f, .47f, .02f), Rgba(1f, .88f, .04f), Rgba(.94f, .04f, .38f)), // sunset
    arrayOf(Rgba(.02f, .40f, 1f), Rgba(.02f, .92f, 1f), Rgba(.18f, 1f, .86f), Rgba(.48f, .10f, 1f), Rgba(.86f, .94f, 1f)), // arctic
    arrayOf(Rgba(.44f, 1f, .02f), Rgba(.82f, 1f, .01f), Rgba(.96f, .72f, .01f), Rgba(.02f, 1f, .70f), Rgba(.56f, .04f, 1f)), // acid
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

// One process-wide decoder bounds concurrent image work across surface recreation.
private val LaserStageDecoder = Executors.newSingleThreadExecutor { task ->
    Thread(task, "laser-stage-decoder").apply { isDaemon = true }
}

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

private const val LaserStageFragmentShader = """
precision mediump float;
varying vec2 vPosition;
uniform sampler2D uStage;
void main() {
    vec2 uv = vec2(vPosition.x * 0.5 + 0.5, 0.5 - vPosition.y * 0.5);
    gl_FragColor = texture2D(uStage, uv);
}
"""

private const val ConcertBeamFragmentShader = """
precision mediump float;
varying vec2 vPosition;
uniform vec2 uStart;
uniform vec2 uAxis;
uniform vec2 uWidths;
uniform float uLength;
uniform float uAspect;
uniform vec4 uColor;
void main() {
    vec2 p = vec2(vPosition.x / uAspect, vPosition.y) - uStart;
    float t = clamp(dot(p, uAxis) / uLength, 0.0, 1.0);
    float radius = mix(uWidths.x, uWidths.y, t);
    float across = abs(p.x * uAxis.y - p.y * uAxis.x) / max(radius, 0.001);
    float falloff = 1.0 - smoothstep(0.0, 1.0, across);
    gl_FragColor = vec4(uColor.rgb, uColor.a * falloff * (1.0 - 0.30 * t));
}
"""

private const val LavaMetaballVertexShader = """
attribute vec2 aPosition;
varying vec2 vPosition;
void main() {
    vPosition = aPosition;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

/**
 * Four tapered chambers each evaluate a small local implicit field. The wax is
 * composed from deliberately offset, rotated masses; a bridge exists only when
 * their fields overlap, never as a drawn stem.
 */
private const val LavaMetaballFragmentShader = """
precision mediump float;
varying vec2 vPosition;
uniform vec2 uResolution;
uniform vec3 uColours[5];
uniform float uEnergy;
uniform float uPulse;
uniform float uPhase;

float taperedRibbonField(
    vec2 point,
    vec2 from,
    vec2 to,
    float fromRadius,
    float neckRadius,
    float toRadius
) {
    // A short, tapered implicit ribbon is used only while wax leaves its pool.
    // It prevents a detached "seed" without ever drawing a full-height stem.
    vec2 axis = to - from;
    float axisLengthSquared = max(dot(axis, axis), 0.0004);
    float t = clamp(dot(point - from, axis) / axisLengthSquared, 0.0, 1.0);
    vec2 spine = from + axis * t;
    vec2 delta = point - spine;
    // A stable polynomial skew makes the liquid shoulder uneven without the
    // fragment trigonometry/noise that previously caused the Streamer lag.
    delta.x += delta.y * abs(delta.y) * 0.18;
    float inverseT = 1.0 - t;
    float radius = fromRadius * inverseT * inverseT
        + neckRadius * (4.0 * t * inverseT)
        + toRadius * t * t;
    vec2 normalized = delta / max(radius * (1.0 + uPulse * 0.035), 0.012);
    return 1.0 / max(dot(normalized, normalized), 0.060);
}

float waxNodeField(vec2 point, vec2 centre, vec2 radius, float skew) {
    vec2 normalized = (point - centre) / (radius * (1.0 + uPulse * 0.040));
    // Stable per-node deformation: a different-sized wax node is never a
    // pristine UI disc, even before it blends with a neighbour.
    normalized.x += skew * normalized.y * abs(normalized.y) * 0.24;
    normalized.y += (0.12 - skew * 0.06) * normalized.x * abs(normalized.x);
    return 1.0 / max(dot(normalized, normalized), 0.060);
}

void main() {
    float aspect = uResolution.y / max(uResolution.x, 1.0);
    vec2 point = vec2(vPosition.x / aspect, vPosition.y);
    vec3 background = vec3(0.010, 0.003, 0.025) + vec3(0.025, 0.004, 0.055) * (1.0 - abs(point.y));
    // Four 120%-tall tapered chambers are deliberately cropped by the display.
    // Their dark gaps make the scene read as a wall of lamps, not a single field.
    float column = floor((point.x + 1.64) / 0.82);
    if (column < 0.0 || column > 3.0) { gl_FragColor = vec4(background, 1.0); return; }
    float centreX = -1.23 + column * 0.82;
    float normalizedY = clamp((point.y + 1.0) * 0.5, 0.0, 1.0);
    float localX = point.x - centreX;
    // A recognizable lamp silhouette: broad rounded glass belly, a narrower
    // neck, a metallic cap, and a tapered metal base. The old linear width
    // function is intentionally gone so these no longer read as light columns.
    float glassBottom = 0.12;
    float glassTop = 0.84;
    float bodyT = clamp((normalizedY - glassBottom) / (glassTop - glassBottom), 0.0, 1.0);
    float glassHalfWidth = 0.185 + 0.082 * sin(bodyT * 3.14159);
    float baseHalfWidth = mix(0.270, 0.195, smoothstep(0.02, glassBottom, normalizedY));
    float capHalfWidth = mix(0.195, 0.145, smoothstep(glassTop, 0.98, normalizedY));
    float halfWidth = glassHalfWidth;
    if (normalizedY < glassBottom) halfWidth = baseHalfWidth;
    if (normalizedY > glassTop) halfWidth = capHalfWidth;
    if (normalizedY < 0.02 || normalizedY > 0.98 || abs(localX) > halfWidth + 0.014) {
        gl_FragColor = vec4(background, 1.0); return;
    }
    float glassMask = step(glassBottom, normalizedY) * (1.0 - step(glassTop, normalizedY));
    float baseMask = 1.0 - step(glassBottom, normalizedY);
    float capMask = step(glassTop, normalizedY);

    // This is a single conserved circulation, not independently spawned
    // particles. One lopsided pool sheds a heavy two-lobed mass, which returns
    // on the other side of the chamber. The only bridge is short-lived during
    // release, so the silhouette cannot become a tall parallel-sided column.
    float clock = uPhase * 0.0104 + column * 0.251;
    float leadTravel = 0.5 - 0.5 * cos(clock * 6.2831853);
    float lean = fract(column * 0.38196601 + 0.29) * 2.0 - 1.0;
    float bend = fract(column * 0.61803399 + 0.17) * 2.0 - 1.0;
    vec2 baseLarge = vec2(centreX - 0.085, -0.690);
    vec2 baseSmall = vec2(centreX + 0.075, -0.665);
    vec2 lead = vec2(
        centreX + lean * (0.050 + leadTravel * 0.110) + bend * sin(clock * 3.1415926) * 0.040,
        -0.650 + leadTravel * 1.205
    );
    // The companion is not a free particle: it remains attached to the lead
    // profile, producing an asymmetric wax lobe rather than a clean oval.
    vec2 companion = lead + vec2(
        -lean * (0.065 + 0.025 * leadTravel),
        -0.070 + 0.030 * sin(clock * 6.2831853 + bend)
    );
    // Two smaller, unequal lobes travel with that same wax body. They are
    // offset diagonally so the field forms shoulders and tapering seams, not
    // a vertical chain of identical round beads.
    vec2 shoulder = lead + vec2(
        lean * (0.070 - 0.020 * leadTravel),
        0.060 + 0.020 * cos(clock * 6.2831853 + lean)
    );
    vec2 tail = companion + vec2(
        -bend * 0.052,
        -0.082 - 0.018 * sin(clock * 6.2831853 + lean)
    );
    float release = smoothstep(0.15, 0.52, leadTravel);
    float roofContact = smoothstep(0.84, 0.985, leadTravel);
    vec2 roofLobe = vec2(centreX + lean * 0.090, 0.555);
    vec2 visibleLead = mix(lead, roofLobe, roofContact);
    vec2 visibleCompanion = mix(companion, roofLobe + vec2(-lean * 0.060, -0.045), roofContact);
    vec2 visibleShoulder = mix(shoulder, roofLobe + vec2(lean * 0.058, 0.010), roofContact);
    vec2 visibleTail = mix(tail, roofLobe + vec2(-bend * 0.050, -0.092), roofContact);
    float field = waxNodeField(point, baseLarge, vec2(0.155, 0.085), bend);
    field += waxNodeField(point, baseSmall, vec2(0.090, 0.110), -lean) * 0.82;
    field += waxNodeField(point, visibleLead, mix(vec2(0.102, 0.142), vec2(0.148, 0.080), roofContact), lean);
    field += waxNodeField(point, visibleCompanion, vec2(0.072, 0.110), -bend) * 0.84;
    field += waxNodeField(point, visibleShoulder, vec2(0.052, 0.080), bend) * 0.62;
    field += waxNodeField(point, visibleTail, vec2(0.044, 0.070), -lean) * 0.46;
    float releaseBridge = taperedRibbonField(
        point,
        mix(baseLarge, baseSmall, 0.36),
        lead,
        0.118,
        0.038,
        0.092
    );
    field += releaseBridge * (1.0 - release) * 0.82;
    float wax = smoothstep(1.60, 2.24, field) * glassMask;
    float edge = (smoothstep(1.06, 1.74, field) - smoothstep(1.60, 2.24, field)) * glassMask;
    // Each chamber gets a stable two-tone blend inside the user-selected
    // palette. The tone changes gradually with height, never with beat data.
    vec3 vesselBase;
    vec3 vesselTop;
    if (column < 0.5) {
        vesselBase = uColours[0]; vesselTop = uColours[1];
    } else if (column < 1.5) {
        vesselBase = uColours[1]; vesselTop = uColours[2];
    } else if (column < 2.5) {
        vesselBase = uColours[2]; vesselTop = uColours[3];
    } else {
        vesselBase = uColours[3]; vesselTop = uColours[4];
    }
    vec3 waxColour = mix(vesselBase, vesselTop, smoothstep(-0.78, 0.78, point.y));
    float subsurface = smoothstep(3.10, 6.00, field) * glassMask;
    float rim = smoothstep(glassHalfWidth + 0.018, glassHalfWidth - 0.006, abs(localX)) * glassMask;
    float pulseGlow = uPulse * uPulse;
    vec3 glass = vec3(0.026, 0.008, 0.062) + vec3(0.050, 0.010, 0.090) * (1.0 - abs(point.y));
    glass += waxColour * glassMask * (0.018 + pulseGlow * 0.085);
    vec3 colour = glass + vec3(0.25, 0.14, 0.36) * rim * 0.36;
    colour += waxColour * edge * (0.042 + uEnergy * 0.026 + pulseGlow * 0.135);
    colour = mix(colour, waxColour * (0.68 + uEnergy * 0.10 + pulseGlow * 0.22), wax);
    colour += mix(waxColour, vesselTop, 0.18) * subsurface * (0.052 + uEnergy * 0.035 + pulseGlow * 0.070);
    vec3 metal = vec3(0.075, 0.070, 0.105) + vec3(0.22, 0.18, 0.28) * pow(1.0 - abs(localX) / max(halfWidth, 0.01), 2.0);
    colour = mix(colour, metal, max(baseMask, capMask));
    gl_FragColor = vec4(colour, 1.0);
}
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
/**
 * Bounded-cost Neon path for living-room GPUs.  It keeps the original's neon
 * spiral ribbons and audio-driven torsion, but replaces the prior per-pixel
 * star fields and six independent arm loops with three analytic fields.
 */
private const val NeonLiteFragmentShader = """
#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragmentColor;
uniform vec2 uResolution;
uniform float uPhase;
uniform float uEnergy;
uniform float uImpulse;
uniform float uTransient;
uniform float uImpact;
uniform float uBands[8];

void main() {
    vec2 point = vUv - 0.5;
    point.x *= uResolution.x / uResolution.y;
    float low = uBands[0] * 0.62 + uBands[1] * 0.38;
    float mid = (uBands[2] + uBands[3] + uBands[4] + uBands[5]) * 0.25;
    float high = (uBands[6] + uBands[7]) * 0.5;
    float radius = length(point);
    float angle = atan(point.y, point.x);

    // A single bounded warp keeps the arms visibly tied to decoded audio.
    float torsion = sin(angle * 3.0 + uPhase * (0.62 + mid * 0.48))
        * (0.10 + mid * 0.18 + uImpact * 0.10);
    float curl = radius * (15.0 + low * 5.0)
        - angle * 3.0
        + uPhase * (1.15 + low * 1.35)
        + torsion;
    float primary = abs(sin(curl));
    float echo = abs(sin(radius * (24.0 + high * 5.0) - angle * 5.0 + uPhase * 1.8));
    float shock = abs(sin(radius * (34.0 + low * 8.0) - uPhase * 3.4));
    float falloff = smoothstep(1.06, 0.08, radius);

    float ribbon = smoothstep(0.105 + mid * 0.035, 0.018, primary);
    float accent = smoothstep(0.065 + high * 0.020, 0.012, echo);
    float pulse = smoothstep(0.080, 0.014, shock) * (uImpulse * 0.12 + uImpact * 0.30);
    float colourPhase = sin(angle * 2.0 + uPhase * 0.42) * 0.5 + 0.5;
    vec3 neon = mix(vec3(0.06, 0.28, 1.0), vec3(1.0, 0.06, 0.72), colourPhase);
    vec3 colour = vec3(0.001, 0.003, 0.012);
    colour += neon * ribbon * falloff * (0.38 + low * 0.30 + uEnergy * 0.12);
    colour += vec3(0.26, 0.74, 1.0) * accent * falloff * (0.16 + high * 0.28);
    colour += vec3(0.82, 0.94, 1.0) * (ribbon * ribbon + pulse) * falloff * (0.20 + uTransient * 0.20);
    float haze = smoothstep(0.94, 0.18, radius) * (0.010 + low * 0.030);
    colour += neon * haze;
    fragmentColor = vec4(min(colour, vec3(1.0)), 1.0);
}
"""

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
