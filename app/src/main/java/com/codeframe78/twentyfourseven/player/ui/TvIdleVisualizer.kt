package com.codeframe78.twentyfourseven.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.codeframe78.twentyfourseven.player.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.LavaLampPalette
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Full-screen screensaver renderers, all driven by the decoded-audio spectrum in [audio]. */
@Composable
internal fun TvCanvasIdleVisualizer(
    style: IdleVisualizerStyle,
    audioState: StateFlow<AudioVisualizationState>,
    reduceVisualIntensity: Boolean = false,
    lavaLampPalette: LavaLampPalette = LavaLampPalette.Neon,
    modifier: Modifier = Modifier,
) {
    if (style.videoAsset() != null) {
        TvVideoIdleVisualizer(style, audioState, reduceVisualIntensity, modifier, lavaLampPalette)
        return
    }
    val audio by audioState.collectAsState()
    val laserStage = rememberLaserStage(style, 2)
    val responseGain = if (reduceVisualIntensity) .68f else 1f
    val energy = audio.energy * responseGain
    val bands = audio.bands.map { it * responseGain }
    val motion by rememberInfiniteTransition(label = "TV idle visualizer motion")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(11_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "TV idle visualizer phase",
        )
    Canvas(modifier = modifier.fillMaxSize().background(Color(0xFF02040A))) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minimum = minOf(size.width, size.height)
        fun band(index: Int) = bands[index % bands.size].coerceIn(0f, 1f)
        fun bandAt(position: Float): Float {
            val start = position.toInt()
            val progress = position - start
            return band(start) * (1f - progress) + band(start + 1) * progress
        }
        fun hue(index: Int, alpha: Float = 1f) = palette[index % palette.size].copy(alpha = alpha)
        fun polar(angle: Float, radius: Float) = Offset(
            center.x + cos(angle) * radius,
            center.y + sin(angle) * radius,
        )
        fun smoothPath(samples: List<Offset>) = Path().apply {
            moveTo(samples.first().x, samples.first().y)
            for (index in 1 until samples.lastIndex) {
                val current = samples[index]
                val next = samples[index + 1]
                quadraticTo(
                    current.x,
                    current.y,
                    (current.x + next.x) / 2f,
                    (current.y + next.y) / 2f,
                )
            }
            val last = samples.last()
            lineTo(last.x, last.y)
        }
        drawCircle(
            Brush.radialGradient(
                listOf(palette[(style.ordinal + 1) % palette.size].copy(alpha = .28f + energy * .28f), Color.Transparent),
                center = center,
                radius = minimum * .82f,
            ),
            radius = minimum * .82f,
            center = center,
        )
        repeat(48) { index ->
            val angle = index * 2.39996f + motion * .82f
            val radius = minimum * (.18f + (index % 17) / 23f)
            drawCircle(hue(index, .12f + band(index) * .24f), radius = 1.3f + band(index) * 2.6f, center = polar(angle, radius))
        }

        when (style) {
            IdleVisualizerStyle.NeonKaleidoscope -> {
                repeat(6) { ribbon ->
                    val path = Path()
                    for (index in 0..96) {
                        val phase = index / 96f * PI.toFloat() * 2f
                        val angle = phase * (2.1f + ribbon * .08f) + motion * PI.toFloat() * 2f
                        val radius = minimum * (.11f + .26f * sin(phase * 3f + ribbon) + band(index + ribbon) * .22f)
                        val point = polar(angle, radius.coerceAtLeast(minimum * .06f))
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, hue(ribbon, .76f), style = Stroke(2.2f + band(ribbon) * 5f))
                }
                repeat(56) { index ->
                    val angle = index * (2f * PI.toFloat() / 56f) - motion * PI.toFloat() * 2f
                    val length = minimum * (.12f + band(index) * .35f + energy * .19f)
                    drawLine(hue(index, .66f), polar(angle, minimum * .05f), polar(angle, length), 1.2f + band(index) * 5f)
                }
            }

            IdleVisualizerStyle.AuroraDrift -> repeat(5) { ribbon ->
                val samples = ArrayList<Offset>(145)
                for (index in 0..144) {
                    val x = size.width * index / 144f
                    val y = size.height * (.25f + ribbon * .12f) + sin(index * .18f + motion * 6.28f + ribbon) * minimum * (.04f + band(index + ribbon) * .16f)
                    samples += Offset(x, y)
                }
                drawPath(smoothPath(samples), hue(ribbon, .35f + energy * .48f), style = Stroke(minimum * (.025f + band(ribbon) * .045f)))
            }

            IdleVisualizerStyle.OscilloscopeTunnel -> {
                repeat(18) { index ->
                    val progress = ((index / 18f + motion) % 1f)
                    val radius = minimum * (.04f + progress * .70f) * (1f + energy * .32f)
                    drawCircle(hue(index, .76f), radius = radius, center = center, style = Stroke(1.4f + band(index) * 5f))
                }
                repeat(3) { line ->
                    val samples = ArrayList<Offset>(193)
                    for (index in 0..192) {
                        val x = size.width * index / 192f
                        val y = center.y + sin(index * .22f + motion * 10f + line * 2f) * minimum * (.03f + band(index + line) * .15f)
                        samples += Offset(x, y)
                    }
                    drawPath(smoothPath(samples), hue(line, .78f), style = Stroke(2.4f))
                }
            }

            IdleVisualizerStyle.SpectralMandala -> {
                repeat(72) { index ->
                    val angle = index * (2f * PI.toFloat() / 72f) - motion * PI.toFloat() * 2f
                    val inside = minimum * .11f
                    val outside = inside + minimum * (.18f + band(index) * .39f)
                    drawLine(hue(index, .70f), polar(angle, inside), polar(angle, outside), 1.4f + band(index) * 6f)
                }
                repeat(7) { curve ->
                    val path = Path()
                    for (index in 0..120) {
                        val phase = index / 120f * PI.toFloat() * 2f
                        val radius = minimum * (.30f + .12f * sin(phase * (2f + curve * .18f) + motion * 6.28f))
                        val point = polar(phase + curve * .88f + motion * .45f, radius)
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, hue(curve, .72f), style = Stroke(2.1f))
                }
            }

            IdleVisualizerStyle.ParticleNebula -> {
                repeat(180) { index ->
                    val arm = index % 5
                    val seed = (index * 37 % 97) / 97f
                    val angle = motion * 8.2f + arm * (PI.toFloat() * 2f / 5f) + seed * PI.toFloat() * 4.4f
                    val radius = minimum * (.06f + seed * (.42f + energy * .23f))
                    val point = polar(angle + sin(seed * 10f + motion * 5f) * .34f, radius)
                    val trail = polar(angle - .19f, radius * .90f)
                    drawLine(hue(index, .18f + band(index) * .46f), trail, point, 1f + band(index) * 3.6f)
                    drawCircle(hue(index, .38f + band(index) * .62f), radius = 1.2f + band(index) * 7f + energy * 4f, center = point)
                }
            }

            IdleVisualizerStyle.LiquidPrism -> repeat(15) { index ->
                val angle = motion * 6.28f * if (index % 2 == 0) 1f else -1f + index * PI.toFloat() / 7.5f
                val radius = minimum * (.11f + (index % 5) * .07f + band(index) * .24f)
                val path = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(polar(angle - .27f, radius).x, polar(angle - .27f, radius).y)
                    lineTo(polar(angle + .30f, radius * 1.14f).x, polar(angle + .30f, radius * 1.14f).y)
                    lineTo(polar(angle + .62f, radius * .72f).x, polar(angle + .62f, radius * .72f).y)
                    close()
                }
                drawPath(path, hue(index, .14f + band(index) * .40f))
                drawPath(path, hue(index, .70f), style = Stroke(1.2f))
            }

            IdleVisualizerStyle.LaserGrid -> {
                val horizon = size.height * .55f
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF13E7FF).copy(alpha = .32f + energy * .28f), Color.Transparent)),
                    radius = minimum * .38f,
                    center = Offset(center.x, horizon),
                )
                repeat(21) { index ->
                    val x = size.width * index / 20f
                    drawLine(hue(index, .58f), Offset(center.x, horizon), Offset(x, size.height), 1.2f + band(index) * 4f)
                }
                repeat(13) { index ->
                    val progress = ((index / 12f + motion) % 1f)
                    val y = horizon + (progress * progress * progress) * (size.height - horizon)
                    drawLine(hue(index, .55f + band(index) * .35f), Offset(0f, y), Offset(size.width, y), 1.4f + band(index) * 3f)
                }
            }

            IdleVisualizerStyle.StarfieldWarp -> repeat(180) { index ->
                val angle = index * 2.39996f + motion * 2f * PI.toFloat()
                val depth = ((index * 37 % 101) / 101f + motion * (0.34f + energy * .34f)) % 1f
                val distance = depth * depth * minimum * .88f
                val point = polar(angle, distance)
                val tail = polar(angle, distance * .67f)
                drawLine(hue(index, .25f + band(index) * .65f), tail, point, 1f + depth * 3.5f)
                drawCircle(hue(index, .58f), radius = 1f + depth * 3.4f, center = point)
            }

            IdleVisualizerStyle.ChromaticTerrain -> repeat(7) { row ->
                val path = Path()
                val baseline = size.height * (.23f + row * .095f)
                path.moveTo(0f, size.height)
                path.lineTo(0f, baseline)
                for (index in 0..48) {
                    val x = size.width * index / 48f
                    val amplitude = minimum * (.025f + band(index + row * 5) * .13f)
                    val y = baseline - sin(index * .52f + motion * 6.28f + row) * amplitude - cos(index * .13f - motion * 3.2f) * minimum * .035f
                    path.lineTo(x, y)
                }
                path.lineTo(size.width, size.height)
                path.close()
                drawPath(path, hue(row, .10f + energy * .16f))
                drawPath(path, hue(row, .68f), style = Stroke(1.6f + band(row) * 3.4f))
            }

            IdleVisualizerStyle.LightTrails -> repeat(6) { trail ->
                val rail = (trail / 5f - .5f) * minimum * .58f
                val points = ArrayList<Offset>(LightTrailSegments + 1)
                for (index in 0..LightTrailSegments) {
                    val x = size.width * index / LightTrailSegments
                    val contour = bandAt(index / 1.7f + trail)
                    val wave = sin(index * .34f + motion * 10.6f + trail * .83f) * minimum * (.05f + contour * .15f)
                    val sweep = cos(index * .09f - motion * 5.2f + trail * 1.4f) * minimum * .09f
                    points += Offset(x, center.y + rail + wave + sweep)
                }
                drawPoints(
                    points,
                    PointMode.Polygon,
                    hue(trail, .23f + band(trail) * .18f),
                    strokeWidth = 8f + band(trail) * 8f,
                    cap = StrokeCap.Round,
                )
                drawPoints(
                    points,
                    PointMode.Polygon,
                    hue(trail, .68f + band(trail) * .28f),
                    strokeWidth = 2.2f + band(trail) * 5f + energy * 4f,
                    cap = StrokeCap.Round,
                )
            }

            IdleVisualizerStyle.MinimalSpectrum -> {
                val floor = size.height * .90f
                val ceiling = size.height * .08f
                val segmentHeight = (floor - ceiling) / 18f
                repeat(bands.size) { index ->
                    val width = size.width * .90f / bands.size
                    val left = size.width * .05f + index * width + width * .10f
                    val lights = (band(index) * 18f).toInt().coerceAtLeast(1)
                    repeat(18) { segment ->
                        val y = floor - (segment + 1) * segmentHeight + segmentHeight * .10f
                        val colour = when {
                            segment >= lights -> Color(0xFF06200A)
                            segment < 11 -> Color(0xFF28FF30)
                            segment < 15 -> Color(0xFFE6FF1A)
                            else -> Color(0xFFFF321F)
                        }
                        drawRect(colour, Offset(left, y), Size(width * .80f, segmentHeight * .78f))
                    }
                }
            }

            IdleVisualizerStyle.VinylPulse -> {
                repeat(18) { index ->
                    val radius = minimum * (.07f + index * .031f) * (1f + energy * .30f)
                    drawCircle(hue(index, .34f + band(index) * .44f), radius = radius, center = center, style = Stroke(1.2f + band(index) * 3.4f))
                }
                repeat(56) { index ->
                    val angle = index * (2f * PI.toFloat() / 56f) + motion * 6.28f
                    drawLine(hue(index, .62f), polar(angle, minimum * .08f), polar(angle, minimum * (.15f + band(index) * .42f)), 1f + band(index) * 3f)
                }
                drawCircle(Color(0xFF05060B), radius = minimum * .105f, center = center)
                drawCircle(hue(0, .9f), radius = minimum * .038f * (1f + energy), center = center)
            }

            IdleVisualizerStyle.DjConsole -> {
                val jog = Offset(size.width * .27f, size.height * .57f)
                repeat(4) { ring ->
                    drawCircle(hue(ring + 1, .38f + band(ring) * .36f), minimum * (.10f + ring * .065f + energy * .025f), jog, style = Stroke(1.5f + band(ring) * 3f))
                }
                val waveform = Path()
                repeat(160) { index ->
                    val x = size.width * (.50f + index / 159f * .40f)
                    val y = size.height * .39f + sin(index * .24f + motion * 8f) * minimum * (.018f + band(index) * .075f)
                    if (index == 0) waveform.moveTo(x, y) else waveform.lineTo(x, y)
                }
                drawPath(waveform, hue(1, .84f), style = Stroke(2.2f))
                repeat(4) { meter ->
                    val left = size.width * (.49f + meter * .095f)
                    val height = minimum * (.12f + band(meter) * .26f)
                    drawRect(hue(meter * 2, .72f), Offset(left, size.height * .82f - height), Size(size.width * .045f, height))
                }
            }

            IdleVisualizerStyle.PsychedelicScene -> {
                drawCircle(Color(0xFF2B075D), radius = minimum * .62f, center = center)
                repeat(4) { ring ->
                    drawCircle(hue(ring * 2 + 1, .58f), radius = minimum * (.13f + ring * .10f), center = center, style = Stroke(1.8f))
                }
                repeat(10) { petal ->
                    val path = Path()
                    repeat(28) { index ->
                        val t = index / 27f
                        val angle = petal * .628f + (t - .5f) * .48f
                        val radius = minimum * (.10f + t * .34f + sin(t * 6.2f + petal) * .045f)
                        val point = polar(angle, radius)
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, hue(petal, .98f), style = Stroke(3.1f))
                }
                drawCircle(Color.White.copy(alpha = .72f), radius = minimum * .09f, center = center)
            }

            IdleVisualizerStyle.LavaLamp -> {
                val colours = lavaPalette(lavaLampPalette)
                drawRoundRect(colours[0].copy(alpha = .48f), Offset(size.width * .25f, size.height * .04f), Size(size.width * .50f, size.height * .92f), CornerRadius(minimum * .07f))
                repeat(12) { blob ->
                    val x = size.width * (.31f + (blob % 4) * .125f)
                    val y = size.height * (.16f + (blob / 4) * .29f)
                    val radius = minimum * (.110f + (blob % 3) * .030f)
                    drawCircle(colours[blob % colours.size].copy(alpha = .92f), radius, Offset(x, y))
                    drawCircle(Color.White.copy(alpha = .38f), radius * .38f, Offset(x - radius * .20f, y - radius * .22f))
                }
            }

            // Pre-O fallback keeps every later style available, with its distinct
            // scene signature represented without the GLES renderer's richer geometry.
            IdleVisualizerStyle.SignalRain,
            IdleVisualizerStyle.ContourIslands,
            IdleVisualizerStyle.RibbonReactor,
            IdleVisualizerStyle.PrismGarden,
            IdleVisualizerStyle.MoonlitTides,
            IdleVisualizerStyle.PulseQuilt,
            IdleVisualizerStyle.EchoReef,
            IdleVisualizerStyle.VelvetVortex,
            IdleVisualizerStyle.LanternDrift -> {
                when (style) {
                    IdleVisualizerStyle.SignalRain -> repeat(14) { index ->
                        val x = size.width * (.07f + index * .066f)
                        val height = minimum * (.18f + band(index) * .60f)
                        drawLine(hue(index, .72f), Offset(x, size.height * .84f), Offset(x, size.height * .84f - height), 1.5f + band(index) * 4f)
                    }
                    IdleVisualizerStyle.ContourIslands -> repeat(4) { ring ->
                        drawCircle(hue(ring, .55f), minimum * (.13f + ring * .12f + energy * .05f), center, style = Stroke(1.6f))
                    }
                    IdleVisualizerStyle.RibbonReactor -> repeat(6) { ribbon ->
                        val path = Path()
                        repeat(48) { index ->
                            val x = size.width * index / 47f
                            val y = size.height * (.16f + ribbon * .13f) + sin(index * .18f + motion * 4f + ribbon) * minimum * (.05f + band(ribbon) * .12f)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, hue(ribbon + 2, .65f), style = Stroke(2.6f + band(ribbon) * 4f))
                    }
                    IdleVisualizerStyle.PrismGarden -> repeat(12) { petal ->
                        val x = size.width * (.06f + petal * .08f)
                        val base = size.height * .78f
                        val top = base - minimum * (.14f + band(petal) * .38f)
                        drawLine(hue(petal, .68f), Offset(x, base), Offset(x, top), 3.2f + band(petal) * 5f)
                    }
                    IdleVisualizerStyle.MoonlitTides -> repeat(8) { tide ->
                        val path = Path()
                        repeat(64) { index ->
                            val x = size.width * index / 63f
                            val y = size.height * (.16f + tide * .10f) + sin(index * .18f + motion * 4f + tide) * minimum * (.025f + band(tide) * .09f)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, hue(tide + 5, .52f), style = Stroke(1.8f))
                    }
                    IdleVisualizerStyle.PulseQuilt -> laserStage?.let { drawLaserStagePreview(motion * 2f * PI.toFloat(), energy, it) }
                    IdleVisualizerStyle.EchoReef -> repeat(24) { branch ->
                        val x = size.width * (.04f + (branch % 12) * .084f)
                        val bottom = size.height * .94f
                        val top = bottom - minimum * (.14f + band(branch) * .42f)
                        drawLine(hue(branch + 4, .62f), Offset(x, bottom), Offset(x + if (branch % 2 == 0) 13f else -13f, top), 1.8f + band(branch) * 3f)
                    }
                    IdleVisualizerStyle.VelvetVortex -> repeat(7) { arm ->
                        val path = Path()
                        repeat(80) { index ->
                            val t = index / 79f
                            val point = polar(arm * .897f + t * 7.8f + motion, minimum * (.04f + t * (.50f + energy * .14f)))
                            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                        }
                        drawPath(path, hue(arm + 1, .42f), style = Stroke(1.8f))
                    }
                    IdleVisualizerStyle.LanternDrift -> repeat(8) { lantern ->
                        val point = Offset(size.width * (.12f + (lantern % 4) * .25f), size.height * (.26f + (lantern / 4) * .38f))
                        drawCircle(Color(0xFFFFB246).copy(alpha = .54f + band(lantern) * .36f), minimum * (.055f + band(lantern) * .06f), point)
                    }
                    else -> Unit
                }
            }
            else -> Unit // Video candidates are handled above without a Canvas renderer.
        }
    }
}

/** A still, lightweight gallery preview; playback animation is reserved for the idle screen itself. */
@Composable
internal fun TvVisualizerPreview(
    style: IdleVisualizerStyle,
    modifier: Modifier = Modifier,
    lavaLampPalette: LavaLampPalette = LavaLampPalette.Neon,
) {
    if (style.videoAsset() != null) {
        TvVideoVisualizerPoster(style, modifier, lavaLampPalette)
        return
    }
    val laserStage = rememberLaserStage(style, 4)
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minimum = minOf(size.width, size.height)
        fun color(index: Int, alpha: Float = 1f) = palette[index % palette.size].copy(alpha = alpha)
        fun polar(angle: Float, radius: Float) = Offset(
            center.x + cos(angle) * radius,
            center.y + sin(angle) * radius,
        )
        drawRect(Brush.linearGradient(listOf(Color(0xFF07101D), color(style.ordinal, .48f))))
        when (style) {
            IdleVisualizerStyle.NeonKaleidoscope -> repeat(10) { arm ->
                val path = Path()
                repeat(48) { index ->
                    val t = index / 47f
                    val angle = arm * .628f + t * 8.7f
                    val radius = minimum * (.05f + t * .72f)
                    val point = polar(angle, radius)
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(path, color(arm, .8f), style = Stroke(2.3f))
            }

            IdleVisualizerStyle.AuroraDrift -> repeat(4) { index ->
                drawCircle(
                    Brush.radialGradient(listOf(color(index, .75f), Color.Transparent)),
                    radius = minimum * .56f,
                    center = Offset(size.width * (index + 1) / 5f, center.y),
                )
            }

            IdleVisualizerStyle.OscilloscopeTunnel -> repeat(4) { lane ->
                val path = Path()
                repeat(32) { index ->
                    val x = size.width * index / 31f
                    val y = size.height * (.20f + lane * .20f) + sin(index * .53f + lane) * minimum * .07f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color(lane, .88f), style = Stroke(2f))
            }

            IdleVisualizerStyle.SpectralMandala -> repeat(5) { stroke ->
                val path = Path()
                repeat(30) { index ->
                    val x = size.width * index / 29f
                    val y = center.y + sin(index * .30f + stroke * 1.3f) * minimum * .24f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color(stroke, .72f), style = Stroke(2.2f))
                drawPath(path, color(stroke + 3, .32f), style = Stroke(5f))
            }

            IdleVisualizerStyle.ParticleNebula -> repeat(42) { index ->
                val angle = index * 2.4f
                val point = polar(angle, minimum * ((index % 11 + 2) / 15f))
                drawCircle(color(index, .9f), radius = 1.5f + index % 4, center = point)
            }

            IdleVisualizerStyle.LiquidPrism -> {
                repeat(6) { row ->
                    val path = Path()
                    repeat(28) { index ->
                        val x = size.width * index / 27f
                        val y = size.height * (.14f + row * .14f) + sin(index * .46f + row) * minimum * .09f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color(row, .58f), style = Stroke(2f))
                }
            }

            IdleVisualizerStyle.LaserGrid -> {
                repeat(12) { index ->
                    val start = Offset(size.width * ((index * 37 % 101) / 100f), size.height * ((index * 61 % 83) / 100f))
                    val end = Offset(size.width * ((index * 19 % 97) / 96f), size.height * ((index * 47 % 89) / 88f))
                    drawLine(color(index, .84f), start, end, 2.2f)
                    drawCircle(Color.White.copy(alpha = .7f), radius = 2.2f, center = start)
                }
            }

            IdleVisualizerStyle.StarfieldWarp -> repeat(35) { index ->
                val angle = index * 2.399f
                val outer = polar(angle, minimum * (.18f + (index % 9) * .07f))
                val inner = polar(angle, minimum * (.05f + (index % 9) * .04f))
                drawLine(color(index, .78f), inner, outer, 1.4f)
            }

            IdleVisualizerStyle.ChromaticTerrain -> repeat(8) { layer ->
                val path = Path()
                repeat(28) { index ->
                    val x = size.width * index / 27f
                    val y = size.height * (.18f + layer * .095f) + sin(index * .38f + layer * .7f) * minimum * .075f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color(layer, .62f), style = Stroke(2.4f))
            }

            IdleVisualizerStyle.LightTrails -> repeat(4) { row ->
                val path = Path()
                for (index in 0..24) {
                    val x = size.width * index / 24f
                    val y = size.height * (.25f + row * .16f) + sin(index * .48f + row) * minimum * .10f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color(row, .82f), style = Stroke(3.5f))
            }

            IdleVisualizerStyle.MinimalSpectrum -> repeat(18) { bar ->
                val height = minimum * (.12f + (bar % 8) * .055f)
                val width = size.width / 24f
                repeat(10) { segment ->
                    val top = size.height * .88f - segment * height / 10f
                    val led = when {
                        segment > 7 -> Color(0xFFFF321F)
                        segment > 5 -> Color(0xFFE6FF1A)
                        else -> Color(0xFF28FF30)
                    }
                    drawRect(led, Offset(bar * size.width / 18f, top), Size(width, height / 14f))
                }
            }

            IdleVisualizerStyle.VinylPulse -> {
                repeat(4) { meter ->
                    val left = size.width * (.02f + meter * .245f)
                    val meterSize = minimum * .38f
                    drawArc(color(meter * 2, .86f), 25f, 130f, false, Offset(left, size.height * .31f), Size(meterSize, meterSize), style = Stroke(2f))
                    drawLine(Color.White.copy(alpha = .82f), Offset(left + meterSize * .50f, size.height * .50f), Offset(left + meterSize * .66f, size.height * .39f), 1.4f)
                }
            }

            IdleVisualizerStyle.DjConsole -> {
                val jog = Offset(size.width * .28f, size.height * .58f)
                repeat(3) { ring -> drawCircle(color(ring + 1, .80f), minimum * (.12f + ring * .09f), jog, style = Stroke(2f)) }
                val waveform = Path()
                repeat(32) { index ->
                    val x = size.width * (.52f + index / 31f * .40f)
                    val y = size.height * .40f + sin(index * .53f) * minimum * .075f
                    if (index == 0) waveform.moveTo(x, y) else waveform.lineTo(x, y)
                }
                drawPath(waveform, color(1, .88f), style = Stroke(2f))
                repeat(4) { meter ->
                    drawRect(color(meter * 2, .82f), Offset(size.width * (.52f + meter * .095f), size.height * .70f), Size(size.width * .055f, size.height * (.10f + meter * .035f)))
                }
            }

            IdleVisualizerStyle.PsychedelicScene -> {
                drawCircle(Color(0xFF2B075D), radius = minimum * .62f, center = center)
                repeat(4) { ring ->
                    drawCircle(color(ring * 2 + 1, .58f), radius = minimum * (.13f + ring * .10f), center = center, style = Stroke(1.8f))
                }
                repeat(10) { petal ->
                    val path = Path()
                    repeat(28) { index ->
                        val t = index / 27f
                        val angle = petal * .628f + (t - .5f) * .48f
                        val radius = minimum * (.10f + t * .34f + sin(t * 6.2f + petal) * .045f)
                        val point = polar(angle, radius)
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, color(petal, .98f), style = Stroke(3.1f))
                }
                drawCircle(Color.White.copy(alpha = .72f), radius = minimum * .09f, center = center)
            }

            IdleVisualizerStyle.LavaLamp -> {
                drawRoundRect(Color(0xFF3C075C), Offset(size.width * .25f, size.height * .04f), Size(size.width * .50f, size.height * .92f), CornerRadius(minimum * .07f))
                repeat(12) { blob ->
                    val x = size.width * (.31f + (blob % 4) * .125f)
                    val y = size.height * (.16f + (blob / 4) * .29f)
                    val radius = minimum * (.110f + (blob % 3) * .030f)
                    drawCircle(color(blob + 2, .92f), radius, Offset(x, y))
                    drawCircle(Color.White.copy(alpha = .38f), radius * .38f, Offset(x - radius * .20f, y - radius * .22f))
                }
            }

            IdleVisualizerStyle.SignalRain,
            IdleVisualizerStyle.ContourIslands,
            IdleVisualizerStyle.RibbonReactor,
            IdleVisualizerStyle.PrismGarden,
            IdleVisualizerStyle.MoonlitTides,
            IdleVisualizerStyle.PulseQuilt,
            IdleVisualizerStyle.EchoReef,
            IdleVisualizerStyle.VelvetVortex,
            IdleVisualizerStyle.LanternDrift -> {
                when (style) {
                    IdleVisualizerStyle.SignalRain -> repeat(12) { index ->
                        val x = size.width * (.08f + index * .076f)
                        drawLine(color(index, .82f), Offset(x, size.height * .18f), Offset(x, size.height * .84f), 2f)
                    }
                    IdleVisualizerStyle.ContourIslands -> repeat(4) { ring ->
                        drawCircle(color(ring, .66f), minimum * (.12f + ring * .11f), center, style = Stroke(1.7f))
                    }
                    IdleVisualizerStyle.RibbonReactor -> repeat(5) { ribbon ->
                        val path = Path()
                        repeat(36) { index ->
                            val x = size.width * index / 35f
                            val y = size.height * (.18f + ribbon * .15f) + sin(index * .26f + ribbon) * minimum * .065f
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color(ribbon + 2, .72f), style = Stroke(2.6f))
                    }
                    IdleVisualizerStyle.PrismGarden -> repeat(10) { petal ->
                        val x = size.width * (.09f + petal * .092f)
                        drawLine(color(petal, .78f), Offset(x, size.height * .79f), Offset(x, size.height * (.36f - (petal % 3) * .07f)), 3.2f)
                    }
                    IdleVisualizerStyle.MoonlitTides -> repeat(7) { tide ->
                        val path = Path()
                        repeat(40) { index ->
                            val x = size.width * index / 39f
                            val y = size.height * (.18f + tide * .10f) + sin(index * .28f + tide) * minimum * .04f
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color(tide + 4, .58f), style = Stroke(1.7f))
                    }
                    IdleVisualizerStyle.PulseQuilt -> laserStage?.let { drawLaserStagePreview(0f, .5f, it) }
                    IdleVisualizerStyle.EchoReef -> repeat(20) { branch ->
                        val x = size.width * (.06f + (branch % 10) * .10f)
                        drawLine(color(branch + 4, .70f), Offset(x, size.height * .90f), Offset(x + if (branch % 2 == 0) 8f else -8f, size.height * (.36f + (branch % 4) * .08f)), 2f)
                    }
                    IdleVisualizerStyle.VelvetVortex -> repeat(6) { arm ->
                        val path = Path()
                        repeat(56) { index ->
                            val t = index / 55f
                            val point = polar(arm * 1.047f + t * 7.2f, minimum * (.04f + t * .55f))
                            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                        }
                        drawPath(path, color(arm + 1, .48f), style = Stroke(1.8f))
                    }
                    IdleVisualizerStyle.LanternDrift -> repeat(8) { lantern ->
                        drawCircle(Color(0xFFFFB246).copy(alpha = .70f), minimum * .075f, Offset(size.width * (.14f + (lantern % 4) * .24f), size.height * (.28f + (lantern / 4) * .36f)))
                    }
                    else -> Unit
                }
            }
            else -> Unit // Video candidates are handled above without a Canvas renderer.
        }
    }
}

/** Cheap gallery/fallback rendering of the same full-width stage and projector topology. */
@Composable
private fun rememberLaserStage(style: IdleVisualizerStyle, sample: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    val image by produceState<ImageBitmap?>(null, style, sample, resources) {
        value = null
        if (style == IdleVisualizerStyle.PulseQuilt) value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(resources, R.drawable.laser_stage_led_slim,
                BitmapFactory.Options().apply { inScaled = false; inSampleSize = sample })?.asImageBitmap()
        }
    }
    return image
}

private fun DrawScope.drawLaserStagePreview(time: Float, energy: Float, stage: ImageBitmap) = clipRect {
    val drive = energy.coerceIn(0f, 1f)
    fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
    drawImage(stage, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    repeat(LaserStageLayout.bankCount) { bank ->
        val x = LaserStageLayout.bankX(bank)
        val chase = .5f + .5f * sin(time * 4f - bank * .7f)
        val c = if (bank % 3 == 0) Color(1f, .72f, .34f) else Color(.30f, .66f, 1f)
        val endX = .5f + (x - .5f) * 1.8f + sin(time * 4f) * .65f
        repeat(3) { layer ->
            val width = (.325f + drive * .325f) * (1f - layer * .25f)
            val wash = Path().apply {
                moveTo(size.width * (x - .0325f), size.height * LaserStageLayout.lensY)
                lineTo(size.width * (x + .0325f), size.height * LaserStageLayout.lensY)
                lineTo(size.width * (endX + width), size.height * -.4f)
                lineTo(size.width * (endX - width), size.height * -.4f)
                close()
            }
            drawPath(wash, c.copy(alpha = (.003f + drive * .023f) * (.3f + chase * .7f)))
        }
        repeat(2) { lamp ->
            val index = bank * 2 + lamp
            if (index < LaserStageLayout.lensCount) {
                val centre = point(LaserStageLayout.lensX(index), LaserStageLayout.lensY)
                drawOval(c.copy(alpha = .12f + drive * .65f * (.3f + chase * .7f)),
                    topLeft = centre - Offset(size.height * .008f, size.height * .0025f),
                    size = Size(size.height * .016f, size.height * .005f))
            }
        }
    }
    repeat(6) { fixture ->
        val x = when (fixture) { 0 -> .144f; 1 -> .284f; 2 -> .427f; 3 -> .5715f; 4 -> .716f; else -> .8565f }
        val c = if (fixture % 3 == 0) Color(1f, .78f, .48f) else Color(.38f, .72f, 1f)
        val activity = concertActivity(time, fixture)
        val start = point(x, .2135f)
        val end = point(.5f + concertAim(time, fixture) * .5f, 1.4f)
        val difference = end - start
        val length = difference.getDistance().coerceAtLeast(1f)
        val normal = Offset(-difference.y / length, difference.x / length)
        repeat(7) { layer ->
            val width = (.65f + drive * .35f) * (1f - layer * .12f) * size.height * .5f
            val near = normal * (size.height * .0045f)
            val far = normal * width
            val beam = Path().apply {
                moveTo((start + near).x, (start + near).y)
                lineTo((start - near).x, (start - near).y)
                lineTo((end - far).x, (end - far).y)
                lineTo((end + far).x, (end + far).y)
                close()
            }
            drawPath(beam, c.copy(alpha = activity * (.012f + drive * .055f)))
        }
        val accent = concertLaserStrength(drive, 0f, 0f)
        repeat(3) { ray ->
            drawLine(c.copy(alpha = accent * (.45f + drive * .55f)), start,
                end + Offset((ray - 1) * (.06f + drive * .275f) * size.width, 0f), size.height * .002f)
        }
        drawCircle(c.copy(alpha = activity * .3f), size.height * .012f, start)
        drawCircle(Color.White.copy(alpha = activity * (.15f + drive * .75f)), size.height * .002f, start)
    }
}

private val palette = listOf(
    Color(0xFF00E5FF), Color(0xFF7B61FF), Color(0xFFFF3DCE), Color(0xFFFFB000),
    Color(0xFF75FF8A), Color(0xFF40A8FF),
)

private fun lavaPalette(palette: LavaLampPalette): List<Color> = when (palette) {
    LavaLampPalette.Neon -> listOf(Color(0xFFFF1475), Color(0xFFFF3BC8), Color(0xFF9D28FF))
    LavaLampPalette.Sunset -> listOf(Color(0xFFFF2538), Color(0xFFFF6A16), Color(0xFFFFD026))
    LavaLampPalette.Arctic -> listOf(Color(0xFF1AA7FF), Color(0xFF22F4FF), Color(0xFF7455FF))
    LavaLampPalette.Acid -> listOf(Color(0xFF7CFF16), Color(0xFFC7FF12), Color(0xFF26FFD0))
}

private const val LightTrailSegments = 32
