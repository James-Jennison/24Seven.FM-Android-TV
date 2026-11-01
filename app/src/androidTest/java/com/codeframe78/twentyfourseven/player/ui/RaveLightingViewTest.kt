package com.codeframe78.twentyfourseven.player.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real hardware compositing/decode with a deterministic musical fixture, not a BPM claim
 * about the currently playing station. LiveVideoPlaybackTest covers real station PCM.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RaveLightingViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun residualCueEnvelopesCannotLightInactiveProjectors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var quiet: Bitmap
        lateinit var residual: Bitmap
        lateinit var active: Bitmap
        lateinit var poseA: Bitmap
        lateinit var poseB: Bitmap
        compose.runOnUiThread {
            val layer = RaveLightingView(context).apply {
                layout(0, 0, 640, 360)
                scaleX = 3f; scaleY = 3f
            }
            val equipment = RaveEquipmentView(context).apply { layout(0, 0, 640, 360); visibility = View.VISIBLE }
            fun render(frame: RaveLightingFrame): Bitmap {
                equipment.present(frame, true)
                layer.present(frame, android.graphics.Matrix(), true)
                return Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).also {
                    val canvas = android.graphics.Canvas(it)
                    equipment.draw(canvas)
                    layer.draw(canvas)
                }
            }
            quiet = render(RaveLightingFrame())
            residual = render(RaveLightingFrame(energy = 0f, drop = 1f, kick = 1f, highs = 1f))
            active = render(RaveLightingFrame(energy = .8f, drop = 1f, kick = 1f, highs = 1f))
            fun equipmentPose(motion: Float): Bitmap {
                equipment.present(RaveLightingFrame(energy = .8f, motion = motion), true)
                return Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).also {
                    equipment.draw(android.graphics.Canvas(it))
                }
            }
            poseA = equipmentPose(0f); poseB = equipmentPose(1f)
        }
        try {
            assertTrue("inactive rig must keep identical unlit props despite residual cues", quiet.sameAs(residual))
            assertCeilingFixturesSupported(quiet)
            assertFalse("active music must still produce visible light", quiet.sameAs(active))
            assertFalse("photographic puck pose must visibly turn with musical motion", poseA.sameAs(poseB))
            assertEquals("larger projector stays fixed as pucks turn", poseA.getPixel(38, 186), poseB.getPixel(38, 186))
            // The physical image persists in silence, but foreground haze changes its
            // appearance during active music; the former always-on-top opacity is gone.
            val bodyX = 38; val bodyY = 186
            assertTrue(android.graphics.Color.alpha(quiet.getPixel(bodyX, bodyY)) > 240)
            assertNotEquals("haze must composite in front of equipment", quiet.getPixel(bodyX, bodyY), active.getPixel(bodyX, bodyY))
            assertEquals("atlas background must be transparent", 0, quiet.getPixel(200, 200))
        } finally { quiet.recycle(); residual.recycle(); active.recycle(); poseA.recycle(); poseB.recycle() }
    }

    @Test fun deckDetailsFollowPcmAndStayOnTheirSurfaces() {
        val bitmaps = mutableListOf<Bitmap>()
        compose.runOnUiThread {
            val deck = RaveDeckRenderer()
            fun render(f: RaveLightingFrame): Bitmap {
                deck.present(f, true)
                return Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).also {
                    deck.draw(android.graphics.Canvas(it)); deck.drawReflections(android.graphics.Canvas(it), f); bitmaps.add(it)
                }
            }
            val silent = render(RaveLightingFrame())
            val bass = render(RaveLightingFrame(energy = .8f, bass = .9f, mids = .1f, highs = .1f, motion = .1f))
            val treble = render(RaveLightingFrame(energy = .8f, bass = .1f, mids = .1f, highs = .9f, motion = .1f))
            val moved = render(RaveLightingFrame(energy = .8f, bass = .1f, mids = .1f, highs = .9f, motion = .3f))
            fun regionDiff(a: Bitmap, b: Bitmap, x: Int, y: Int, w: Int, h: Int): Int {
                var changed = 0
                for (yy in y until y+h) for (xx in x until x+w) if (a.getPixel(xx, yy) != b.getPixel(xx, yy)) changed++
                return changed
            }
            assertTrue("bass strip follows bass", regionDiff(bass, treble, 867, 735, 10, 46) > 30)
            assertTrue("high strip follows highs", regionDiff(bass, treble, 1000, 735, 10, 46) > 30)
            assertTrue("large platter markings visibly move", regionDiff(treble, moved, 330, 780, 130, 75) > 300)
            assertTrue("material reflections move across deck", regionDiff(treble, moved, 700, 820, 400, 140) > 1000)
            assertTrue("LCD history responds to PCM", regionDiff(silent, moved, 420, 701, 170, 30) > 100)
            for (bitmap in bitmaps) {
                assertEquals("deck never paints over wall logo", 0, bitmap.getPixel(960, 400))
                assertEquals("deck never paints outside hardware", 0, bitmap.getPixel(50, 700))
            }
            val stopped = render(RaveLightingFrame())
            val residual = render(RaveLightingFrame(drop = 1f, white = 1f, motion = 900f))
            assertTrue("stale PCM leaves no moving reflections or live meters", stopped.sameAs(residual))
            val reduced = render(RaveLightingFrame(energy = .2f, reduced = true, motion = .1f))
            val reducedWhite = render(RaveLightingFrame(energy = .2f, reduced = true, motion = .1f, white = 1f))
            assertTrue("reduced mode suppresses white deck flashes", reduced.sameAs(reducedWhite))
            bitmaps.forEach { it.recycle() }
        }
    }

    private fun assertCeilingFixturesSupported(bitmap: Bitmap) {
        // Follow actual rendered opaque hardware from the top ceiling boundary to
        // every puck centre. An isolated sprite or gap in its mount must fail.
        val w = bitmap.width
        val h = 40
        val seen = BooleanArray(w * h)
        val queue = java.util.ArrayDeque<Int>()
        fun visit(x: Int, y: Int) {
            if (x !in 0 until w || y !in 0 until h) return
            val at = y * w + x
            if (!seen[at] && android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 16) {
                seen[at] = true; queue.add(at)
            }
        }
        repeat(w) { visit(it, 0) }
        while (!queue.isEmpty()) {
            val at = queue.removeFirst(); val x = at % w; val y = at / w
            for (dy in -1..1) for (dx in -1..1) visit(x + dx, y + dy)
        }
        repeat(RaveStageGeometry.headCount) { i ->
            val x = (RaveStageGeometry.headX(i) / 3f).toInt()
            val y = (RaveStageGeometry.headY(i) / 3f).toInt()
            assertTrue("ceiling puck $i must connect through real hardware to the ceiling", seen[y * w + x])
        }
    }

    @Test fun quietBuildDropLightsCompositeAndStopAcrossLifecycle() {
        lateinit var view: VideoVisualizerView
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = {
                VideoVisualizerView(it).also { v -> view = v }
            }, onRelease = { it.release() })
        }
        val instrument = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrument.targetContext.getExternalFilesDir(null), "rave-acceptance").apply { mkdirs() }
        fun fixture(level: Float, hit: Boolean) = AudioVisualizationState(
            sampledAtElapsedRealtimeMs = SystemClock.elapsedRealtime(), channelCount = 2,
            energy = maxOf(.01f, level), calibratedBandDbfs = List(4) { level * 54f - 60f },
            bands = List(32) { level }, transient = if (hit) .98f else .01f,
        )
        fun capture(name: String): Bitmap {
            val bitmap = checkNotNull(instrument.uiAutomation.takeScreenshot())
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        compose.runOnUiThread { view.select(IdleVisualizerStyle.DjConsole); view.setForeground(true); view.updateAudio(fixture(.08f, false), .033f, false) }
        compose.waitUntil(10000) {
            var ready = false
            compose.runOnUiThread { ready = view.getChildAt(1).visibility == View.GONE }
            ready
        }
        val equipment = view.getChildAt(2) as RaveEquipmentView
        val layer = view.getChildAt(3) as RaveLightingView
        compose.runOnUiThread {
            assertEquals(view.width, equipment.width)
            assertEquals(view.height, equipment.height)
            assertEquals(View.VISIBLE, equipment.visibility)
            assertEquals(View.LAYER_TYPE_HARDWARE, layer.layerType)
            assertTrue("light raster must stay bounded", layer.width <= (view.width + 2) / 3)
            assertEquals(view.width.toFloat(), layer.width * layer.scaleX, .01f)
            assertEquals(view.height.toFloat(), layer.height * layer.scaleY, .01f)
        }
        var quiet: Bitmap? = null
        var peak: Bitmap? = null
        val cameraTransform = android.graphics.Matrix()
        var buildSeen = false
        var dropSeen = false
        val start = SystemClock.elapsedRealtime()
        try {
            repeat(960) { tick ->
                val level = when {
                    tick < 120 -> .08f
                    tick < 360 -> .15f + (tick - 120) / 240f * .64f
                    tick < 780 -> if (tick % 15 < 2) .99f else .64f
                    else -> .08f
                }
                compose.runOnUiThread {
                    view.updateAudio(fixture(level, tick >= 360 && tick % 15 < 2), .033f, false)
                    (view.getChildAt(0) as android.view.TextureView).getTransform(cameraTransform)
                    assertTrue("DJ camera must stay fixed throughout quiet/build/drop", cameraTransform.isIdentity)
                    buildSeen = buildSeen || layer.frame.section == RaveSection.BUILD
                    dropSeen = dropSeen || layer.frame.section == RaveSection.DROP
                }
                Thread.sleep(33)
                if (tick == 110) quiet = capture("quiet")
                if (tick == 340) capture("build").recycle()
                if (tick == 361) peak = capture("drop")
            }
            assertTrue("rising amplitude must produce a build", buildSeen)
            assertTrue("kick release must produce a drop", dropSeen)
            val q = checkNotNull(quiet); val p = checkNotNull(peak)
            var difference = 0L; var samples = 0
            for (y in 30 until minOf(q.height, p.height) * 55 / 100 step 8) {
                for (x in 20 until minOf(q.width, p.width) * 30 / 100 step 8) {
                    val a = q.getPixel(x, y); val b = p.getPixel(x, y)
                    difference += kotlin.math.abs((a shr 16 and 255) - (b shr 16 and 255)) +
                        kotlin.math.abs((a shr 8 and 255) - (b shr 8 and 255)) + kotlin.math.abs((a and 255) - (b and 255))
                    samples += 3
                }
            }
            assertTrue("drop must visibly change the composited light field", difference.toDouble() / samples > 5.0)
            // Catch the actual full-width mask seams found during visual acceptance.
            // Side-wall row averages avoid the naturally sharp deck controls in the center.
            val rows = FloatArray(p.height)
            val lo = (p.height * .55f).toInt(); val hi = (p.height * .74f).toInt()
            for (y in lo - 2..hi + 2) {
                var total = 0L; var count = 0
                for (x in 0 until p.width step 8) if (x < p.width * .18f || x > p.width * .82f) {
                    val rgb = p.getPixel(x, y)
                    total += (rgb shr 16 and 255) + (rgb shr 8 and 255) + (rgb and 255)
                    count += 3
                }
                rows[y] = total.toFloat() / count
            }
            var seam = 0f
            for (y in lo..hi) seam = maxOf(seam, kotlin.math.abs(rows[y] - (rows[y-2] + rows[y+2]) * .5f))
            assertTrue("visible horizontal lighting-mask seam: $seam", seam < 20f)
            compose.runOnUiThread {
                assertTrue("hardware light overlay must draw throughout the fixture", layer.renderedFrames > 500)
                view.updateAudio(AudioVisualizationState(), .033f, false)
                assertEquals(0f, layer.frame.energy, 0f)
                view.setForeground(false)
                assertEquals(View.GONE, layer.visibility); assertEquals(View.GONE, equipment.visibility)
                view.setForeground(true)
                view.updateAudio(fixture(.95f, true), .033f, true)
                assertEquals(0f, layer.frame.white, 0f)
                view.select(IdleVisualizerStyle.LavaLamp)
                assertEquals(View.GONE, layer.visibility); assertEquals(View.GONE, equipment.visibility)
                println("RAVE_ACCEPTANCE draws=${layer.renderedFrames} elapsedMs=${SystemClock.elapsedRealtime()-start} build=$buildSeen drop=$dropSeen pixelDelta=${difference.toDouble()/samples} seam=$seam silence/reduced/recreation/style-switch=PASS")
            }
        } finally { quiet?.recycle(); peak?.recycle(); compose.runOnUiThread { view.release(); view.release() } }
    }
}
