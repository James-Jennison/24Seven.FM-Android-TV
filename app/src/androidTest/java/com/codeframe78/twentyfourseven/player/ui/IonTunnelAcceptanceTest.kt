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

class IonTunnelAcceptanceTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private fun ui(block: () -> Unit) = instrument.runOnMainSync(block)
    @Test fun realTunnelCompilesMovesWithPcmAndRendersEveryColorFamily() {
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
        val live = mutableStateOf(true)
        val empty = kotlinx.coroutines.flow.MutableStateFlow(AudioVisualizationState())
        lateinit var root: View
        ui {
            controller = (activity.application as RadioApplication).appContainer.playbackController
            audio = ViewModelProvider(activity)[TvRadioViewModel::class.java].visualization
            wasPlaying = controller.state.value.status == PlaybackStatus.Playing
            controller.play()
            activity.setContent { root = LocalView.current; TvIdleVisualizer(IdleVisualizerStyle.IonTransit,
                if (live.value) audio else empty, reduceVisualIntensity = reduced.value,
                ionTunnelPalette = palette.value, modifier = Modifier.fillMaxSize()) }
        }
        fun find(v: View): GLSurfaceView? {
            if (v is GLSurfaceView) return v
            if (v is ViewGroup) repeat(v.childCount) { find(v.getChildAt(it))?.let { found -> return found } }
            return null
        }
        fun surface(): GLSurfaceView { var result: GLSurfaceView? = null; ui { result = find(root.rootView) }; return checkNotNull(result) }
        fun state(): Pair<Long,IonTunnelFrame> {
            val view = surface(); val done = CountDownLatch(1)
            var result: Pair<Long,IonTunnelFrame>? = null
            view.queueEvent {
                val renderer = view.javaClass.getDeclaredField("renderer").apply { isAccessible=true }.get(view)
                fun field(name:String) = renderer.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(renderer)
                assertEquals(IdleVisualizerStyle.IonTransit.ordinal,field("style"))
                assertEquals(704,field("feedbackWidth")); assertEquals(396,field("feedbackHeight"))
                result = (field("frameNumber") as Long) to (field("ionDrive") as IonTunnelDrive).frame
                done.countDown()
            }
            assertTrue("GL thread must remain responsive",done.await(5,TimeUnit.SECONDS)); return checkNotNull(result)
        }
        fun capture(name: String): IntArray {
            val view=surface(); val bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888)
            val done=CountDownLatch(1); var result=-1
            ui { android.view.PixelCopy.request(view,bitmap,{ result=it; done.countDown() },android.os.Handler(android.os.Looper.getMainLooper())) }
            assertTrue(done.await(5,TimeUnit.SECONDS)); assertEquals(android.view.PixelCopy.SUCCESS,result)
            java.io.File(context.getExternalFilesDir(null),"ion-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            val pixels=IntArray(1280*720);bitmap.getPixels(pixels,0,1280,0,0,1280,720);bitmap.recycle();return pixels
        }
        try {
            Thread.sleep(3000)
            assertTrue("Fresh radio PCM",SystemClock.elapsedRealtime()-audio.value.sampledAtElapsedRealtimeMs in 0..500)
            val first=state(); Thread.sleep(12000); val second=state()
            println("ION_LIVE frames=${second.first-first.first} travelStart=${first.second.travel} travelEnd=${second.second.travel} frame=${second.second} timing=${VisualizerPerformanceDiagnostics.latest}")
            val normal=capture("live")
            assertTrue("Hardware frames must advance",second.first-first.first > 450)
            assertNotEquals("Measured music drives forward travel",first.second.travel,second.second.travel)
            val timing=checkNotNull(VisualizerPerformanceDiagnostics.latest)
            assertEquals("Ion Transit",timing.styleName); assertTrue("GL p95 budget $timing",timing.p95FrameMillis <= 35f)
            assertTrue("Image must contain lit metal and darkness",normal.count { Color.red(it)+Color.green(it)+Color.blue(it)>180 }>10000)
            assertTrue(normal.count { Color.red(it)+Color.green(it)+Color.blue(it)<100 }>10000)
            ui { live.value=false };Thread.sleep(3500)
            val held=state().second.travel;Thread.sleep(350);assertEquals("Absent PCM freezes flight",held,state().second.travel,0f)
            val colors=mutableListOf<IntArray>()
            for (color in IonTunnelPalette.entries) {
                ui { palette.value=color };Thread.sleep(250)
                colors.add(capture(color.name))
            }
            for (i in 1 until colors.size) assertTrue("Palette must visibly change tunnel",colors[i].indices.count { colors[i][it]!=colors[0][it] }>100000)
            ui { live.value=true; reduced.value=true };Thread.sleep(2500)
            assertTrue(state().second.reduced);capture("reduced")
            ui { controller.pause() };Thread.sleep(4500)
            val paused=state().second;Thread.sleep(350)
            assertEquals("Real playback pause freezes flight",paused.travel,state().second.travel,0f)
            ui { controller.play() };Thread.sleep(3000)
            assertNotEquals("Resume restores forward travel",paused.travel,state().second.travel)
        } finally { ui { if (!wasPlaying) controller.pause(); activity.finish() } }
    }
}
