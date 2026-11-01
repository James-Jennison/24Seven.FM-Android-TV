package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.codeframe78.twentyfourseven.player.R
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import com.codeframe78.twentyfourseven.player.domain.LavaLampPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/** Native scenes retain their poster identity without shipping a reference MP4. */
internal data class VideoVisualizerAsset(val fileName: String, val poster: Int, val usesBundledVideo: Boolean = true)

internal fun IdleVisualizerStyle.videoAsset(palette: LavaLampPalette = LavaLampPalette.Neon): VideoVisualizerAsset? = when (this) {
    IdleVisualizerStyle.LanternDrift -> VideoVisualizerAsset("lantern_drift_realistic", R.drawable.visualizer_lantern_drift_realistic, usesBundledVideo = false)
    IdleVisualizerStyle.VinylPulse -> VideoVisualizerAsset("signal_deck_realistic", R.drawable.visualizer_signal_deck_realistic)
    IdleVisualizerStyle.PulseQuilt -> VideoVisualizerAsset("laser_show_realistic", R.drawable.visualizer_laser_show_realistic)
    IdleVisualizerStyle.DjConsole -> VideoVisualizerAsset("dj_soff_snep", R.drawable.visualizer_dj_soff_snep)
    IdleVisualizerStyle.LavaLamp -> when (palette) {
        LavaLampPalette.Neon -> VideoVisualizerAsset("lava_neon", R.drawable.visualizer_lava_neon)
        LavaLampPalette.Sunset -> VideoVisualizerAsset("lava_sunset", R.drawable.visualizer_lava_sunset)
        LavaLampPalette.Arctic -> VideoVisualizerAsset("lava_arctic", R.drawable.visualizer_lava_arctic)
        LavaLampPalette.Acid -> VideoVisualizerAsset("lava_acid", R.drawable.visualizer_lava_acid)
    }
    IdleVisualizerStyle.AuroraDrift -> VideoVisualizerAsset("abstract_fluid_bloom", R.drawable.visualizer_abstract_fluid_bloom)
    IdleVisualizerStyle.OscilloscopeTunnel -> VideoVisualizerAsset("abstract_signal_scope", R.drawable.visualizer_abstract_signal_scope)
    IdleVisualizerStyle.LiquidPrism -> VideoVisualizerAsset("abstract_facet_forge", R.drawable.visualizer_abstract_facet_forge)
    IdleVisualizerStyle.LaserGrid -> VideoVisualizerAsset("abstract_vector_switchboard", R.drawable.visualizer_abstract_vector_switchboard)
    IdleVisualizerStyle.StarfieldWarp -> VideoVisualizerAsset("abstract_pulse_observatory", R.drawable.visualizer_abstract_pulse_observatory)
    IdleVisualizerStyle.ChromaticTerrain -> VideoVisualizerAsset("abstract_chromafield", R.drawable.visualizer_abstract_chromafield)
    IdleVisualizerStyle.PsychedelicScene -> VideoVisualizerAsset("abstract_psychedelic_scene", R.drawable.visualizer_abstract_psychedelic_scene)
    IdleVisualizerStyle.SignalRain -> VideoVisualizerAsset("abstract_harmonic_gate", R.drawable.visualizer_abstract_harmonic_gate)
    IdleVisualizerStyle.ContourIslands -> VideoVisualizerAsset("abstract_contour_islands", R.drawable.visualizer_abstract_contour_islands)
    IdleVisualizerStyle.PrismGarden -> VideoVisualizerAsset("abstract_prism_garden", R.drawable.visualizer_abstract_prism_garden)
    IdleVisualizerStyle.EchoReef -> VideoVisualizerAsset("abstract_echo_reef", R.drawable.visualizer_abstract_echo_reef)
    IdleVisualizerStyle.VelvetVortex -> VideoVisualizerAsset("abstract_magnetic_fields", R.drawable.visualizer_abstract_magnetic_fields)
    IdleVisualizerStyle.EmeraldResonance -> VideoVisualizerAsset("emerald_resonance", R.drawable.visualizer_emerald_resonance)
    IdleVisualizerStyle.IonTransit -> VideoVisualizerAsset("ion_transit", R.drawable.visualizer_ion_transit, usesBundledVideo = false)
    else -> null
}

@Composable
internal fun TvVideoVisualizerPoster(style: IdleVisualizerStyle, modifier: Modifier, palette: LavaLampPalette = LavaLampPalette.Neon) {
    style.videoAsset(palette)?.let { asset ->
        Image(painterResource(asset.poster), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

/** A silent, local video surface. It never owns station audio, focus, or a MediaSession. */
@Composable
internal fun TvVideoIdleVisualizer(
    style: IdleVisualizerStyle,
    audioState: StateFlow<AudioVisualizationState>,
    reduceVisualIntensity: Boolean,
    modifier: Modifier,
    lavaLampPalette: LavaLampPalette = LavaLampPalette.Neon,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var surface by remember { mutableStateOf<VideoVisualizerView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context -> VideoVisualizerView(context).also { surface = it; it.select(style, lavaLampPalette) } },
        update = { it.select(style, lavaLampPalette) },
        onRelease = { it.release() },
    )
    DisposableEffect(surface, lifecycle) {
        val view = surface
        val observer = LifecycleEventObserver { _, _ ->
            view?.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        view?.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); view?.setForeground(false) }
    }
    LaunchedEffect(surface, audioState, reduceVisualIntensity, style) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = android.os.SystemClock.elapsedRealtimeNanos()
            while (true) {
                if (style == IdleVisualizerStyle.LanternDrift) androidx.compose.runtime.withFrameNanos { }
                val now = android.os.SystemClock.elapsedRealtimeNanos()
                surface?.updateAudio(audioState.value, (now - previous) / 1_000_000_000f, reduceVisualIntensity)
                previous = now
                if (style != IdleVisualizerStyle.LanternDrift) delay(33)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class VideoVisualizerView(context: Context) : FrameLayout(context) {
    private val texture = TextureView(context)
    private val poster = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.BLACK)
    }
    private val errorLabel = TextView(context).apply {
        text = "Video unavailable · choose another visualization"
        setTextColor(Color.WHITE)
        setBackgroundColor(0xB0000000.toInt())
        gravity = Gravity.CENTER
        textSize = 18f
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val transform = Matrix()
    private val drive = VideoAudioDrive()
    private val raveDrive = RaveLightingDrive()
    private val equipmentLayer = RaveEquipmentView(context)
    private val raveLayer = RaveLightingView(context)
    private val photographicDrive = PhotographicAudioDrive()
    private val lanternWaterLayer = LanternWaterView(context)
    private val photographicLayer = PhotographicReactiveView(context)
    private var photographicFrame = PhotographicFrame()
    private var raveFrame = RaveLightingFrame()
    private var style: IdleVisualizerStyle? = null
    private var palette = LavaLampPalette.Neon
    private var player: ExoPlayer? = null
    private var foreground = false
    private var disposed = false
    private var failed = false
    private var lastSpeed = .5f
    private var speedElapsed = 0f
    private var frame = VideoAudioFrame()

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        texture.isFocusable = false
        texture.isOpaque = false
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(poster, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(equipmentLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(raveLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(lanternWaterLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(photographicLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(errorLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun select(next: IdleVisualizerStyle, nextPalette: LavaLampPalette = LavaLampPalette.Neon) {
        val effectivePalette = if (next == IdleVisualizerStyle.LavaLamp) nextPalette else LavaLampPalette.Neon
        if ((style == next && palette == effectivePalette) || disposed) return
        stopDecoder()
        style = next
        palette = effectivePalette
        failed = false
        frame = if (next.isPhotographicNative()) VideoAudioFrame(alpha = 1f) else VideoAudioFrame()
        applyFrame()
        drive.reset()
        photographicDrive.reset()
        photographicFrame = PhotographicFrame()
        lanternWaterLayer.clear()
        photographicLayer.clear()
        raveDrive.reset()
        raveFrame = RaveLightingFrame()
        raveLayer.clear()
        equipmentLayer.visibility = View.GONE
        errorLabel.visibility = View.GONE
        next.videoAsset(palette)?.let { poster.setImageResource(it.poster) }
        poster.visibility = View.VISIBLE
        poster.imageAlpha = (frame.alpha * 255f).toInt()
        if (foreground) startDecoder()
    }

    fun setForeground(value: Boolean) {
        if (disposed || foreground == value) return
        foreground = value
        if (value) {
            // One fresh attempt per foreground entry; never spin on a failed codec.
            failed = false
            errorLabel.visibility = View.GONE
            startDecoder()
        } else {
            stopDecoder()
            drive.reset()
            frame = if (style?.isPhotographicNative() == true) VideoAudioFrame(alpha = 1f) else VideoAudioFrame()
            photographicDrive.reset()
            photographicFrame = PhotographicFrame()
            lanternWaterLayer.clear()
            photographicLayer.clear()
            raveDrive.reset()
            raveFrame = RaveLightingFrame()
            raveLayer.clear()
            equipmentLayer.visibility = View.GONE
        }
    }

    private fun startDecoder() {
        if (player != null || disposed || failed || !foreground) return
        if (style == IdleVisualizerStyle.LanternDrift) {
            applyFrame()
            return
        }
        val asset = style?.videoAsset(palette)?.takeIf { it.usesBundledVideo }
        if (asset == null) {
            texture.visibility = View.GONE
            return
        }
        texture.visibility = View.VISIBLE
        try {
            val decoder = ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(1_000, 3_000, 250, 500).build())
                .build()
            player = decoder
            // Defense in depth: transcoded assets have no audio tracks; audio selection is
            // also disabled. No audio-focus handling, wake mode, or MediaSession is created.
            decoder.volume = 0f
            decoder.trackSelectionParameters = decoder.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            decoder.repeatMode = Player.REPEAT_MODE_ONE
            decoder.setVideoTextureView(texture)
            decoder.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (player === decoder) {
                        poster.visibility = View.GONE
                        applyFrame()
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (player === decoder) failVideo()
                }
            })
            decoder.setMediaItem(MediaItem.fromUri("asset:///visualizers/${asset.fileName}.mp4"))
            decoder.setPlaybackSpeed(.5f)
            lastSpeed = .5f
            speedElapsed = 0f
            decoder.prepare()
        } catch (_: RuntimeException) {
            failVideo()
        }
    }

    private fun failVideo() {
        failed = true
        stopDecoder()
        poster.visibility = View.VISIBLE
        errorLabel.visibility = View.VISIBLE
    }

    fun updateAudio(audio: AudioVisualizationState, seconds: Float, reduced: Boolean) {
        val current = style ?: return
        if (disposed || !foreground) return
        val now = android.os.SystemClock.elapsedRealtime()
        val fresh = audio.freshForVideo(now)
        frame = drive.update(current, fresh, seconds, reduced, now)
        if (current == IdleVisualizerStyle.DjConsole) raveFrame = raveDrive.update(fresh, now, seconds, reduced)
        if (current.isPhotographicNative()) photographicFrame = photographicDrive.update(audio, now, seconds, reduced)
        applyFrame()
        val decoder = player ?: return
        decoder.playWhenReady = frame.moving
        speedElapsed += if (seconds.isFinite()) seconds.coerceIn(0f, .1f) else 0f
        // Rate changes are bounded to 5 Hz, preventing control-message floods.
        if (speedElapsed >= .2f && abs(lastSpeed - frame.speed) >= .05f) {
            decoder.setPlaybackSpeed(frame.speed)
            lastSpeed = frame.speed
            speedElapsed = 0f
        }
    }

    private fun applyFrame() {
        transform.reset()
        transform.setScale(frame.scaleX, frame.scaleY, width * .5f, height * .5f)
        transform.postSkew(frame.shear, 0f, width * .5f, height * .5f)
        texture.setTransform(transform)
        texture.alpha = frame.alpha
        poster.imageAlpha = (frame.alpha * 255f).toInt()
        presentRaveLayers(style == IdleVisualizerStyle.DjConsole && foreground && !failed && poster.visibility == View.GONE)
        if (style == IdleVisualizerStyle.LanternDrift) {
            val ready = lanternWaterLayer.present(foreground && !disposed, photographicFrame)
            texture.visibility = View.GONE
            poster.visibility = if (ready) View.GONE else View.VISIBLE
            poster.imageAlpha = 255
            presentPhotographicLayer(ready)
            return
        }
        lanternWaterLayer.clear()
        presentPhotographicLayer(foreground && !failed && poster.visibility == View.GONE)
    }

    private fun presentPhotographicLayer(visible: Boolean) {
        if (width > 0 && height > 0) {
            val scaled = style == IdleVisualizerStyle.PulseQuilt
            val layerWidth = if (scaled) minOf(480, maxOf(1, (width + 2) / 3)) else width
            val layerHeight = if (scaled) maxOf(1, (height.toFloat() * layerWidth / width).toInt()) else height
            photographicLayer.pivotX = 0f
            photographicLayer.pivotY = 0f
            photographicLayer.scaleX = width.toFloat() / layerWidth
            photographicLayer.scaleY = height.toFloat() / layerHeight
            val params = photographicLayer.layoutParams
            if (params.width != layerWidth || params.height != layerHeight) {
                photographicLayer.layoutParams = LayoutParams(layerWidth, layerHeight)
            }
        }
        photographicLayer.present(style, photographicFrame, visible)
    }

    private fun presentRaveLayers(visible: Boolean) {
        equipmentLayer.present(raveFrame, visible)
        // Match the booth material exposure. Emitted beams/haze retain their own
        // PCM intensity envelopes; VideoAudioDrive.alpha is not a scene fade.
        equipmentLayer.alpha = frame.alpha
        raveLayer.present(raveFrame, transform, visible, frame.alpha)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val lightWidth = maxOf(1, (w + 2) / 3)
        val lightHeight = maxOf(1, (h + 2) / 3)
        raveLayer.scaleX = maxOf(1, w).toFloat() / lightWidth
        raveLayer.scaleY = maxOf(1, h).toFloat() / lightHeight
        raveLayer.layoutParams = LayoutParams(lightWidth, lightHeight)
        applyFrame()
    }

    private fun stopDecoder() {
        // Keep the TextureView attached so a replacement decoder gets its surface.
        // The opaque poster background covers any retained buffer until first frame.
        poster.visibility = View.VISIBLE
        raveLayer.clear()
        lanternWaterLayer.clear()
        photographicLayer.clear()
        equipmentLayer.visibility = View.GONE
        val decoder = player
        player = null
        decoder?.clearVideoTextureView(texture)
        decoder?.release()
        poster.visibility = View.VISIBLE
    }

    fun release() {
        if (disposed) return
        disposed = true
        foreground = false
        stopDecoder()
        lanternWaterLayer.release()
        photographicLayer.release()
    }
}
