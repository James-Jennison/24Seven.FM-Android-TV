package com.codeframe78.twentyfourseven.player.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.codeframe78.twentyfourseven.player.MainActivity
import com.codeframe78.twentyfourseven.player.RadioApplication
import com.codeframe78.twentyfourseven.player.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LightTrailsAcceptanceTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private fun ui(block: () -> Unit) = instrument.runOnMainSync(block)
    @Test fun cinematicTrailsRenderWithLivePcmAndSettleWhenPaused() {
        val context = instrument.targetContext
        context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        var screen: MainActivity? = null
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (screen == null && SystemClock.elapsedRealtime() < deadline) {
            ui { screen = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
            Thread.sleep(50)
        }
        val activity = checkNotNull(screen)
        lateinit var controller: PlaybackController
        lateinit var audio: kotlinx.coroutines.flow.StateFlow<AudioVisualizationState>
        var wasPlaying = false
        val palette = mutableStateOf(IonTunnelPalette.AmberIce)
        val reduced = mutableStateOf(false)
        val station = mutableStateOf<StationId?>(StationId("1980s"))
        val live = mutableStateOf(true)
        val empty = kotlinx.coroutines.flow.MutableStateFlow(AudioVisualizationState())
        lateinit var root: View
        ui {
            controller = (activity.application as RadioApplication).appContainer.playbackController
            audio = ViewModelProvider(activity)[TvRadioViewModel::class.java].visualization
            wasPlaying = controller.state.value.status == PlaybackStatus.Playing
            controller.play()
            activity.setContent { root = LocalView.current; TvIdleVisualizer(IdleVisualizerStyle.LightTrails,
                if (live.value) audio else empty, reduceVisualIntensity = reduced.value,
                ionTunnelPalette = palette.value, modifier = Modifier.fillMaxSize(), stationId = station.value) }
        }
        fun find(v: View): GLSurfaceView? {
            if (v is GLSurfaceView) return v
            if (v is ViewGroup) repeat(v.childCount) { find(v.getChildAt(it))?.let { found -> return found } }
            return null
        }
        fun surface(): GLSurfaceView { var result: GLSurfaceView? = null; ui { result = find(root.rootView) }; return checkNotNull(result) }
        fun state(): Pair<Long,Float> {
            val view = surface(); val done = CountDownLatch(1)
            var result: Pair<Long,Float>? = null
            view.queueEvent {
                val renderer = view.javaClass.getDeclaredField("renderer").apply { isAccessible=true }.get(view)
                fun field(name:String) = renderer.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(renderer)
                assertEquals(IdleVisualizerStyle.LightTrails.ordinal,field("style"))
                assertEquals(576,field("feedbackWidth")); assertEquals(324,field("feedbackHeight"))
                result = (field("frameNumber") as Long) to (field("lightTrailsDrive") as LightTrailsDrive).frame.travel
                done.countDown()
            }
            assertTrue("GL thread must remain responsive",done.await(5,TimeUnit.SECONDS)); return checkNotNull(result)
        }
        fun capture(name: String): IntArray {
            val view=surface(); val bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888)
            val done=CountDownLatch(1); var result=-1
            ui { android.view.PixelCopy.request(view,bitmap,{ result=it; done.countDown() },android.os.Handler(android.os.Looper.getMainLooper())) }
            assertTrue(done.await(5,TimeUnit.SECONDS)); assertEquals(android.view.PixelCopy.SUCCESS,result)
            java.io.File(context.getExternalFilesDir(null),"light-trails-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            val pixels=IntArray(1280*720);bitmap.getPixels(pixels,0,1280,0,0,1280,720);bitmap.recycle();return pixels
        }
        try {
            val pcmDeadline=SystemClock.elapsedRealtime()+15000
            while (SystemClock.elapsedRealtime()<pcmDeadline && (audio.value.channelCount==0 || SystemClock.elapsedRealtime()-audio.value.sampledAtElapsedRealtimeMs !in 0..500)) Thread.sleep(100)
            Thread.sleep(1000)
            assertTrue("Fresh radio PCM",SystemClock.elapsedRealtime()-audio.value.sampledAtElapsedRealtimeMs in 0..500)
            val first=state(); Thread.sleep(12000); val second=state()
            println("LIGHT_TRAILS_LIVE frames=${second.first-first.first} phaseStart=${first.second} phaseEnd=${second.second} frame=${second.second} timing=${VisualizerPerformanceDiagnostics.latest}")
            val normal=capture("live")
            assertTrue("Hardware frames must advance",second.first-first.first > 450)
            assertNotEquals("Measured music drives trails",first.second,second.second)
            val timing=checkNotNull(VisualizerPerformanceDiagnostics.latest)
            assertEquals("Light Trails",timing.styleName); assertTrue("GL p95 budget $timing",timing.p95FrameMillis <= 35f)
            assertTrue("Image must contain lit trails and darkness",normal.count { Color.red(it)+Color.green(it)+Color.blue(it)>180 }>10000)
            assertTrue(normal.count { Color.red(it)+Color.green(it)+Color.blue(it)<100 }>10000)
            ui { live.value=false };Thread.sleep(4500)
            val held=state().second;Thread.sleep(500)
            assertEquals("Absent PCM settles motion",held,state().second,.02f)
            val still=capture("settled")
            assertTrue("Live movement must change image",normal.indices.count { normal[it]!=still[it] }>10000)
            val stationImages=mutableListOf<IntArray>()
            for(id in listOf("1980s","sst","afm","dfm","efm","unknown")) {
                ui { station.value=StationId(id) }
                val expected=com.codeframe78.twentyfourseven.player.ui.theme.stationPalette(StationId(id))
                val glSurface=surface()
                val deadline=SystemClock.elapsedRealtime()+5000
                var matchingFrame: Long? = null
                var applied=false
                while (SystemClock.elapsedRealtime()<deadline && !applied) {
                    val ready=CountDownLatch(1)
                    var matches=false
                    var number=0L
                    var error: Throwable? = null
                    glSurface.queueEvent {
                        try {
                            val renderer=glSurface.javaClass.getDeclaredField("renderer").apply { isAccessible=true }.get(glSurface)
                            matches=renderer.javaClass.getDeclaredField("lightStationPalette").apply { isAccessible=true }.get(renderer)==expected
                            number=renderer.javaClass.getDeclaredField("frameNumber").apply { isAccessible=true }.getLong(renderer)
                        } catch(t: Throwable) { error=t } finally { ready.countDown() }
                    }
                    assertTrue("Palette render thread must respond",ready.await(1,TimeUnit.SECONDS))
                    error?.let { throw it }
                    if (matches) {
                        if (matchingFrame==null) matchingFrame=number
                        applied=number-checkNotNull(matchingFrame)>=2
                    } else matchingFrame=null
                    if (!applied) Thread.sleep(16)
                }
                assertTrue("Expected station palette must render before capture: $id",applied)
                stationImages.add(capture("station-$id"))
                assertEquals("Station colors must not restart motion",held,state().second,.02f)
            }
            for(i in 1 until stationImages.size) assertTrue("Each station must change visible lighting",stationImages[i].indices.count { stationImages[i][it]!=stationImages[0][it] }>10000)
            ui { live.value=true; reduced.value=true };Thread.sleep(2500)
            capture("reduced")
            ui { controller.pause() };Thread.sleep(4500)
            val paused=state().second;Thread.sleep(500)
            assertEquals("Real playback pause settles trails",paused,state().second,.02f)
            ui { controller.play() };Thread.sleep(3000)
            assertNotEquals("Resume restores travel",paused,state().second)
            repeat(3) {
                val done=CountDownLatch(1)
                var failure: Throwable? = null
                val glSurface=surface()
                glSurface.queueEvent {
                    try {
                        val renderer=glSurface.javaClass.getDeclaredField("renderer").apply { isAccessible=true }.get(glSurface)
                        val program=renderer.javaClass.getDeclaredField("lightTrailsProgram").apply { isAccessible=true }.get(renderer) as LightTrailsProgram
                        val vbo=IntArray(1)
                        android.opengl.GLES20.glGenBuffers(1,vbo,0)
                        android.opengl.GLES20.glBindBuffer(android.opengl.GLES20.GL_ARRAY_BUFFER,vbo[0])
                        android.opengl.GLES20.glBufferData(android.opengl.GLES20.GL_ARRAY_BUFFER,32,null,android.opengl.GLES20.GL_STATIC_DRAW)
                        program.draw(576,324,1f,.5f,.5f,.2f,false)
                        assertEquals("Client vertices handle a prior VBO",0,android.opengl.GLES20.glGetError())
                        android.opengl.GLES20.glDeleteBuffers(1,vbo,0)
                        program.release()
                        assertEquals("Release on GL thread",0,android.opengl.GLES20.glGetError())
                        program.draw(576,324,1f,.5f,.5f,.2f,false)
                        assertEquals("Re-entry recreates GPU resources",0,android.opengl.GLES20.glGetError())
                    } catch (t: Throwable) { failure=t } finally { done.countDown() }
                }
                assertTrue(done.await(5,TimeUnit.SECONDS))
                failure?.let { throw it }
            }
        } finally { ui { if (!wasPlaying) controller.pause(); activity.finish() } }
    }
}
