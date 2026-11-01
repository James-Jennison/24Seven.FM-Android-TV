package com.codeframe78.twentyfourseven.player.ui

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Analytic 3D cylinder intersection and shaded metal, rendered once into a bounded scene buffer. */
internal class IonTunnelProgram {
    private var program = 0
    private var position = 0
    private var resolution = 0
    private var signal = 0
    private var reduced = 0
    private var warm = 0
    private var cool = 0
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }
    fun contextCreated() { program = 0 }
    fun draw(width: Int, height: Int, frame: IonTunnelFrame, palette: Int = 0) {
        if (program == 0) {
            fun shader(type: Int, source: String): Int {
                val id = GLES20.glCreateShader(type)
                GLES20.glShaderSource(id, source); GLES20.glCompileShader(id)
                val status = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
                if (status[0] == 0) { val error = GLES20.glGetShaderInfoLog(id); GLES20.glDeleteShader(id); error("Ion shader: $error") }
                return id
            }
            val vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 aPosition; void main(){gl_Position=vec4(aPosition,0.,1.);}")
            var fragment = 0
            val linked = GLES20.glCreateProgram()
            try {
                fragment = shader(GLES20.GL_FRAGMENT_SHADER, Fragment)
                GLES20.glAttachShader(linked, vertex); GLES20.glAttachShader(linked, fragment); GLES20.glLinkProgram(linked)
                val status = IntArray(1); GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
                check(status[0] != 0) { "Ion program: ${GLES20.glGetProgramInfoLog(linked)}" }
                program = linked
            } finally {
                GLES20.glDeleteShader(vertex); if (fragment != 0) GLES20.glDeleteShader(fragment)
                if (program == 0) GLES20.glDeleteProgram(linked)
            }
            position = GLES20.glGetAttribLocation(program, "aPosition")
            resolution = GLES20.glGetUniformLocation(program, "uResolution")
            signal = GLES20.glGetUniformLocation(program, "uSignal")
            reduced = GLES20.glGetUniformLocation(program, "uReduced")
            warm = GLES20.glGetUniformLocation(program, "uWarm")
            cool = GLES20.glGetUniformLocation(program, "uCool")
        }
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolution, width.toFloat(), height.toFloat())
        GLES20.glUniform4f(signal, frame.travel, frame.energy, frame.bass, frame.attack)
        GLES20.glUniform1f(reduced, if (frame.reduced) 1f else 0f)
        val colors = Palettes[palette.coerceIn(0, Palettes.lastIndex)]
        GLES20.glUniform3f(warm, colors[0], colors[1], colors[2])
        GLES20.glUniform3f(cool, colors[3], colors[4], colors[5])
        quad.position(0); GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    companion object {
        private val Palettes = arrayOf(
            floatArrayOf(1f,.19f,.012f,.22f,.64f,1f),
            floatArrayOf(.60f,.04f,1f,.05f,1f,.90f),
            floatArrayOf(.10f,1f,.32f,1f,.55f,.035f),
            floatArrayOf(1f,.025f,.055f,.65f,.85f,1f),
        )
        const val Fragment = """
precision highp float;
uniform vec2 uResolution;
uniform vec4 uSignal;
uniform float uReduced;
uniform vec3 uWarm;
uniform vec3 uCool;
const float TAU=6.28318530718;
float hash(float n){ return fract(sin(n*127.1)*43758.5453); }
float line(float d,float width,float aa){return 1.-smoothstep(width,width+aa,abs(d));}
void main(){
    vec2 screen=(2.*gl_FragCoord.xy-uResolution)/uResolution.y;
    // Banked, off-axis flight. Travel is PCM-driven and freezes when playback stops.
    float roll=.52+uSignal.x*TAU/96.;
    vec2 q=screen+vec2(.55,.44);
    q=mat2(cos(roll),-sin(roll),sin(roll),cos(roll))*q;
    vec3 ray=normalize(vec3(q,1.30));
    vec2 origin=vec2(1.05,.65);
    float aaRay=max(dot(ray.xy,ray.xy),.00001);
    float b=dot(origin,ray.xy);
    float t=(-b+sqrt(b*b+aaRay*(9.-dot(origin,origin))))/aaRay;
    vec3 hit=vec3(origin,0.)+ray*t;
    float z=hit.z+uSignal.x;
    float angle=atan(hit.y,hit.x)/TAU+.5;
    float sector=angle*24.;
    float sid=floor(sector);
    float u=fract(sector);
    float row=floor(z/3.);
    float v=fract(z/3.);
    float aa=clamp(t/uResolution.y*.7,.0015,.15);
    float edge=min(min(u,1.-u),min(v,1.-v));
    float seam=1.-smoothstep(.012,.032+aa,edge);
    float bevel=line(edge-.045,.012,aa);
    // Raised, inset panel faces with ribs and a brushed metal finish.
    float plate=smoothstep(.025,.065+aa,edge);
    float detail=hash(sid+mod(row,32.)*24.);
    vec3 metal=mix(vec3(.018,.028,.043),vec3(.028,.033,.040),detail)*(.6+.4*plate);
    metal*=1.-.8*seam;
    float grain=sin(z*TAU*24.+sid*2.)*sin(u*370.);
    metal*=1.+grain*.08*(1.-smoothstep(7.,23.,t));
    // Reflect the light rig in the cylindrical metal and its chamfered panel edges.
    // Directional reflections move over the walls as the camera banks and advances.
    vec3 normal=normalize(vec3(-hit.xy,0.));
    vec3 tangent=normalize(vec3(-normal.y,normal.x,0.));
    normal=normalize(normal+tangent*((1.-smoothstep(.025,.09,u))-smoothstep(.91,.975,u))*.24
        +vec3(0.,0.,((1.-smoothstep(.025,.10,v))-smoothstep(.90,.975,v))*.20));
    vec3 reflected=reflect(ray,normal);
    float chrome=pow(max(0.,dot(reflected,normalize(vec3(.6,.9,.65)))),24.);
    float silver=pow(max(0.,dot(reflected,normalize(vec3(-.8,.25,.75)))),42.);
    float sheen=pow(abs(dot(reflected,normalize(vec3(.2,.95,.72)))),9.);
    float machining=.97+.03*sin(u*180.+z*TAU*4.);
    metal+=(vec3(.75,.86,1.)*chrome*1.9+vec3(1.,.91,.78)*silver*1.4
        +vec3(.16,.20,.26)*sheen)*machining*(.45+.55*plate)*(1.-.9*seam);

    float ribs=line(fract(z/6.)-.04,.012,aa*.5);
    metal+=vec3(.025,.035,.055)*bevel+vec3(.035,.045,.065)*ribs;
    // Alternating orange and ice-blue longitudinal light rails, with staggered dark gaps.
    float cold=step(.5,mod(sid,4.)/3.);
    vec3 hue=mix(uWarm,uCool,cold);
    float lane=abs(u-.5);
    float gate=smoothstep(.025,.07+aa,v)*(1.-smoothstep(.83,.91,v));
    float fixture=(1.-step(.5,mod(sid,3.)))*step(.16,hash(sid+mod(row,32.)));
    float rail=line(lane,.026,aa)*gate*fixture;
    float spill=exp(-lane*14.)*gate*fixture;
    float bolts=line(abs(u-.50)-.36,.014,aa)*line(v-.13,.014,aa);
    float pulse=.65+.45*uSignal.z+.2*uSignal.w*(1.-uReduced);
    vec3 light=hue*(rail*14.+spill*1.4+bolts*.5)*pulse;
    // Light reflected across the dark metal faces, including narrow specular streaks.
    float reflection=pow(max(0.,cos((u-.5)*3.)),12.)*(.35+.65*gate);
    metal+=hue*reflection*(.015+.035*uSignal.z);
    float reflectedRail=exp(-abs(u-.26)*18.)*smoothstep(.3,.8,detail);
    metal+=mix(uCool,uWarm,step(.5,hash(sid+3.)))*reflectedRail*(.08+.35*pow(sin(v*9.+sid)*.5+.5,6.));
    metal+=mix(uCool*.085,uWarm*.09,cold)*pow(1.-edge,3.);
    vec3 col=metal+light;
    float fog=1.-exp(-t*.017);
    col=mix(col,vec3(.027,.053,.092),fog*.55);
    // Atmospheric shaft and distant aperture, bounded so attacks never flash the whole frame.
    float r=length(q);
    col+=uCool*.026/(.035+r*r)*(.65+.25*uSignal.y);
    float core=exp(-r*r*220.);
    col+=vec3(.72,.88,1.)*core*3.;
    col*=1.-.12*smoothstep(.7,2.1,length(screen));
    col*=mix(1.,.66,uReduced);
    col=vec3(1.)-exp(-col*1.1);
    col=pow(max(col,vec3(0.)),vec3(.83));
    gl_FragColor=vec4(col,1.);
}
"""
    }
}
