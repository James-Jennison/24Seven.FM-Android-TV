package com.codeframe78.twentyfourseven.player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

@Composable
internal fun TvProceduralIdleVisualizer(style: IdleVisualizerStyle, audio: StateFlow<AudioVisualizationState>?, reduced: Boolean, modifier: Modifier) {
    val owner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = remember(context, style) { ProceduralVisualizerView(context, style) }
    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, _ -> view.foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        view.foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { owner.lifecycle.removeObserver(observer); view.foreground = false }
    }
    key(style) { AndroidView(factory = { view }, modifier = modifier,
        update = { it.audio = audio; it.reduced = reduced; it.invalidate() },
        onRelease = { it.foreground = false; it.audio = null }) }
}

/** No decoder, bitmap, image texture, media session or off-screen blur pass. UI-thread owned. */
internal class ProceduralVisualizerView(context: Context, private val style: IdleVisualizerStyle) : View(context) {
    var audio: StateFlow<AudioVisualizationState>? = null
    var reduced = false
    private val envelope = ProceduralAudioDrive()
    private val scene = ReferenceInspiredScene()
    private var last = 0L
    private var time = 0.0
    var foreground = false
        set(value) { if(field != value) { field=value; last=0; envelope.reset(); if(value) invalidate() } }
    init { isFocusable=false; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); last=0; invalidate() }
    override fun onDetachedFromWindow() { last=0; envelope.reset(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        last=0
        if(visibility==VISIBLE) invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if(last==0L) 0f else ((now-last)/1e9).toFloat().coerceIn(0f,.1f)
        last=now
        if(foreground && audio!=null) {
            envelope.update(audio!!.value,dt,reduced)
            time += dt * (if(reduced) .2 else 1.0) * (.35 + envelope.low*.55)
        }
        scene.draw(canvas,width,height,style,if(audio==null) 2.0 else time,envelope,reduced)
        if(foreground && isAttachedToWindow && audio!=null && windowVisibility==VISIBLE) postInvalidateOnAnimation()
    }
}

/** Ten distinct constructions. All loops are fixed; Paint/Path/HSV buffers are allocated once. */
internal class ReferenceInspiredScene {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val segments=FloatArray(400)
    private var segmentCount=0
    private var previousX=0f
    private var previousY=0f
    private val petal=Path().apply {
        moveTo(.1f,0f); cubicTo(.3f,-.45f,1f,-.9f,1f,0f)
        cubicTo(1f,.55f,.4f,.7f,.1f,0f); close()
    }
    private fun point(x: Float,y: Float,first: Boolean) {
        if(first) segmentCount=0 else {
            segments[segmentCount++]=previousX; segments[segmentCount++]=previousY
            segments[segmentCount++]=x; segments[segmentCount++]=y
        }
        previousX=x; previousY=y
    }
    private val hsv=FloatArray(3)
    private var t=0f
    private var l=0f; private var m=0f; private var h=0f; private var hit=0f
    private var gain=1f
    private fun ink(hue: Float, alpha: Float=1f, value: Float=1f) {
        hsv[0]=((hue%360)+360)%360; hsv[1]=.72f; hsv[2]=value.coerceIn(0f,1f)
        paint.color=Color.HSVToColor((alpha*gain*255).toInt().coerceIn(0,255),hsv)
    }
    private fun line(c: Canvas,x: Float,y: Float,xx: Float,yy: Float,hue: Float,a: Float,w: Float=1f) {
        ink(hue,a); paint.style=Paint.Style.STROKE; paint.strokeWidth=w; c.drawLine(x,y,xx,yy,paint)
    }
    private fun dot(c: Canvas,x: Float,y: Float,r: Float,hue: Float,a: Float) {
        ink(hue,a); paint.style=Paint.Style.FILL; c.drawCircle(x,y,r,paint)
    }
    private fun stroke(c: Canvas,hue: Float,a: Float,w: Float) {
        ink(hue,a); paint.style=Paint.Style.STROKE; paint.strokeWidth=w
        c.drawLines(segments,0,segmentCount,paint)
    }
    private fun fract(x: Float)=x-floor(x)
    fun draw(c: Canvas,w: Int,height: Int,style: IdleVisualizerStyle,time: Double,a: ProceduralAudioDrive,reduced: Boolean) {
        c.drawColor(Color.rgb(2,3,12)); if(w<=0 || height<=0) return
        t=(time%100000).toFloat(); l=a.low; m=a.mid; h=a.high; hit=a.hit; gain=if(reduced) .5f else 1f
        c.save(); c.scale(w/1000f,height/562f)
        when(style) {
            IdleVisualizerStyle.EmeraldResonance -> emerald(c)
            IdleVisualizerStyle.AuroraVeil -> aurora(c)
            IdleVisualizerStyle.SpectralSilk -> silk(c)
            IdleVisualizerStyle.OrbitalPulse -> orbit(c)
            IdleVisualizerStyle.ChromaticBlossom -> blossom(c)
            IdleVisualizerStyle.ElectricSynapse -> synapse(c)
            IdleVisualizerStyle.StardustIgnition -> stardust(c)
            IdleVisualizerStyle.VioletCircuit -> circuit(c)
            IdleVisualizerStyle.PrismaticPassage -> passage(c)
            IdleVisualizerStyle.IonTransit -> transit(c)
            else -> Unit
        }
        c.restore()
    }
    private fun emerald(c: Canvas) {
        // Two living plasma membranes: continuously changing interference lobes, not a kaleidoscope.
        repeat(2) { side -> repeat(18) { strand ->
            path.rewind()
            repeat(81) { j ->
                val y=j*7.1f; val u=y/562f
                val wave=sin(u*14+t*.7f+strand*.13f)*sin(u*5-t*.31f)
                val x=500+(if(side==0) -1 else 1)*(95+strand*6+wave*(85+l*90)+sin(u*21+t)*m*30)
                point(x,y,j==0)
            }
            stroke(c,95+strand*2f,.12f+l*.12f,7f); stroke(c,110f,.28f+hit*.5f,1.3f)
        } }
    }
    private fun aurora(c: Canvas) {
        // Broad overlapping folds, with the curtain height controlled directly by bass.
        repeat(20) { fold ->
            path.rewind()
            repeat(65) { j ->
                val x=j*16f; val y=210+fold*8+sin(x*.005f+t*.23f+fold*.11f)*(65+l*45)+sin(x*.011f-t*.18f)*30
                point(x,y,j==0)
            }
            stroke(c,155+fold*5f,.10f,35f); stroke(c,170+fold*4f,.13f+h*.14f,5f)
        }
        repeat(110) { i -> dot(c,fract(i*.618034f)*1000,fract(i*.38197f)*562,1f+h,200f,.18f+h*.3f) }
    }
    private fun silk(c: Canvas) {
        // A sampled fabric surface: folds travel across depth as mids deform the mesh.
        repeat(27) { row -> repeat(38) { col ->
            val u=col/37f; val v=row/26f
            val fold=sin(u*10+t*.5f+v*2.5f)
            val x=u*1080-40+sin(v*7+t*.3f)*24*m
            val y=65+v*390+fold*(45+l*70)*sin(v*3.14f)+sin(u*24-t)*m*18
            dot(c,x,y,.8f+v*1.1f,205+v*65,.2f+v*.5f+h*.2f)
        } }
    }
    private fun orbit(c: Canvas) {
        // Tilted concentric constellations with opposing precession, not radial flight.
        repeat(14) { ring ->
            val r=24+ring*23f+l*18
            repeat(44) { j ->
                val angle=j*6.28318f/44+t*(if(ring%2==0) .12f else -.1f)+ring*.13f
                val x=cos(angle)*r; val y=sin(angle)*r*(.62f+.1f*sin(t*.17f))
                dot(c,500+x,281+y,1.2f+ring*.09f+hit*1.5f,280+ring*4f,.2f+.6f*(1-ring/18f))
            }
        }
        dot(c,500f,281f,7+l*10,305f,.65f)
    }
    private fun blossom(c: Canvas) {
        // Curved translucent petals unfurl in offset whorls; each layer bends independently.
        repeat(3) { layer -> repeat(11) { p ->
            c.save(); c.translate(500f,281f); c.rotate(p*360f/11+layer*13+t*(if(layer%2==0) 5 else -4))
            val r=90+layer*65f+l*50; val bend=sin(t*.4f+layer)*25+m*25
            c.scale(r,100+bend)
            ink(p*32f+layer*50+t*2,.22f); paint.style=Paint.Style.FILL; c.drawPath(petal,paint)
            ink(p*32f+layer*50,.4f+hit*.3f); paint.style=Paint.Style.STROKE
            paint.strokeWidth=.008f; c.drawPath(petal,paint); c.restore()
        } }
        dot(c,500f,281f,8+hit*15,45f,.8f)
    }
    private fun synapse(c: Canvas) {
        // Branching tendrils: attacks travel outward as bright nodes, never a prerecorded bolt.
        repeat(24) { branch ->
            val angle=branch*2.399963f; var px=500f; var py=281f
            repeat(24) { j ->
                val r=j*22f; val a=angle+sin(j*.48f+t*.6f+branch)*(.06f+m*.15f)
                val x=500+cos(a)*r; val y=281+sin(a)*r*.65f
                line(c,px,py,x,y,195+branch%5*12f,.2f+hit*.5f,1.2f)
                if(j%6==0) line(c,x,y,x+cos(a+.5f)*40,y+sin(a+.5f)*30,230f,.3f,1f)
                if(abs(j/24f-fract(t*.7f+branch*.17f))<.05f) dot(c,x,y,2+hit*3,185f,.8f)
                px=x; py=y
            }
        }
    }
    private fun stardust(c: Canvas) {
        // Perspective particles erupt from a drifting source; warm sparks contrast cool trails.
        repeat(220) { i ->
            val a=i*2.399963f; val age=fract(i*.618f+t*.16f); val r=age*age*650*(1+l*.25f)
            val x=500+cos(a)*r; val y=281+sin(a)*r*.65f
            val hue=if(i%3==0) 205f else 28f+i%20
            line(c,x,y,x-cos(a)*(4+age*22),y-sin(a)*(4+age*22)*.65f,hue,age*.4f)
            dot(c,x,y,1+age*2+h*2,hue,(1-age)*(.4f+hit*.5f))
        }
    }
    private fun circuit(c: Canvas) {
        // Staggered architectural rails and packets: horizontal travel instead of a central tunnel.
        repeat(7) { row -> repeat(6) { col ->
            val x=fract(col/6f+t*.025f+row*.09f)*1300-150
            val y=50+row*78f; val reach=28+l*25
            line(c,x-reach,y+20,x,y-20,270+row*7f,.18f,9f)
            line(c,x,y-20,x+reach,y+20,270+row*7f,.18f,9f)
            line(c,x-reach,y+20,x,y-20,280f,.6f+hit*.3f,2f)
            line(c,x,y-20,x+reach,y+20,305f,.6f+hit*.3f,2f)
            dot(c,x+(fract(t*.7f+col*.2f)-.5f)*60,y+28,2+h*2,185f,.6f)
        } }
    }
    private fun passage(c: Canvas) {
        // Nested three-sided portals twist with depth and bass, unlike cylindrical Ion Transit.
        repeat(22) { i ->
            val z=fract(i/22f+t*.055f); val r=25+z*z*780
            val rotation=sin(t*.2f)*.14f+m*.12f*z
            path.rewind()
            repeat(4) { corner ->
                val a=-1.5708f+corner%3*2.094395f+rotation*z
                val x=500+cos(a)*r; val y=281+sin(a)*r
                point(x,y,corner==0)
            }
            stroke(c,if(i%2==0) 190f else 320f,z*.15f,10f)
            stroke(c,if(i%2==0) 190f else 320f,z*(.55f+hit*.35f),1.5f+l*2)
        }
    }
    private fun transit(c: Canvas) {
        // Cylindrical segmented wall in perspective; independent orange/blue traffic lanes.
        repeat(18) { lane -> repeat(12) { segment ->
            val z=fract(segment/12f+t*.1f+lane*.031f)
            val r=28+z*z*650; val a=lane*6.28318f/18+sin(t*.13f)*.18f
            val rr=r+12+z*50
            val x=500+cos(a)*r; val y=281+sin(a)*r*.72f
            val hue=if(lane%3==0) 28f else 195f
            line(c,x,y,500+cos(a)*rr,281+sin(a)*rr*.72f,hue,z*(.3f+h*.5f),2+z*3+l*2)
            if(segment%3==0) {
                val aa=a+.20f
                line(c,x,y,500+cos(aa)*r,281+sin(aa)*r*.72f,215f,z*.25f,1f)
            }
        } }
    }
}
