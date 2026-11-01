package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.ui.theme.StationPalette
import com.codeframe78.twentyfourseven.player.ui.theme.stationPalette
import android.opengl.GLES20
import android.opengl.GLUtils
import android.content.res.Resources
import android.graphics.BitmapFactory
import com.codeframe78.twentyfourseven.player.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Perspective light exposures and wet-road reflections, drawn into a bounded scene buffer. */
internal class LightTrailsProgram(private val resources: Resources) {
    private val texture = IntArray(1)
    private var backdrop = 0
    private var program = 0
    private var position = 0
    private var resolution = 0
    private var signal = 0
    private var reduced = 0
    private var accent = 0
    private var primaryColor = 0
    private var secondaryColor = 0
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }
    fun contextCreated() { program = 0; texture[0] = 0 }
    fun release() {
        if (texture[0] != 0) GLES20.glDeleteTextures(1, texture, 0)
        if (program != 0) GLES20.glDeleteProgram(program)
        contextCreated()
    }
    fun draw(width: Int, height: Int, phase: Float, energy: Float, bass: Float, attack: Float, reduceMotion: Boolean, sky: Float = 0f, sweep: Float = 0f, palette: StationPalette = stationPalette(null)) {
        if (program == 0) {
            fun shader(type: Int, source: String): Int {
                val id = GLES20.glCreateShader(type)
                GLES20.glShaderSource(id, source); GLES20.glCompileShader(id)
                val status = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
                if (status[0] == 0) { val error = GLES20.glGetShaderInfoLog(id); GLES20.glDeleteShader(id); error("Light trails shader: $error") }
                return id
            }
            val vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 aPosition; void main(){gl_Position=vec4(aPosition,0.,1.);}")
            var fragment = 0
            val linked = GLES20.glCreateProgram()
            try {
                fragment = shader(GLES20.GL_FRAGMENT_SHADER, Fragment)
                GLES20.glAttachShader(linked, vertex); GLES20.glAttachShader(linked, fragment); GLES20.glLinkProgram(linked)
                val status = IntArray(1); GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
                check(status[0] != 0) { "Light trails program: ${GLES20.glGetProgramInfoLog(linked)}" }
                program = linked
            } finally {
                GLES20.glDeleteShader(vertex); if (fragment != 0) GLES20.glDeleteShader(fragment)
                if (program == 0) GLES20.glDeleteProgram(linked)
            }
            position = GLES20.glGetAttribLocation(program, "aPosition")
            resolution = GLES20.glGetUniformLocation(program, "uResolution")
            signal = GLES20.glGetUniformLocation(program, "uSignal")
            reduced = GLES20.glGetUniformLocation(program, "uReduced")
            accent = GLES20.glGetUniformLocation(program, "uAccent")
            primaryColor = GLES20.glGetUniformLocation(program, "uPrimary")
            secondaryColor = GLES20.glGetUniformLocation(program, "uSecondary")
            backdrop = GLES20.glGetUniformLocation(program, "uBackdrop")
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (texture[0] == 0) {
            val bitmap = checkNotNull(BitmapFactory.decodeResource(resources, R.drawable.visualizer_light_trails_city,
                BitmapFactory.Options().apply { inScaled = false }))
            try {
                GLES20.glGenTextures(1, texture, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            } finally { bitmap.recycle() }
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glUseProgram(program)
        GLES20.glUniform1i(backdrop, 0)
        GLES20.glUniform2f(resolution, width.toFloat(), height.toFloat())
        GLES20.glUniform4f(signal, phase, energy, bass, attack)
        GLES20.glUniform2f(accent, sky, sweep)
        GLES20.glUniform3f(primaryColor, palette.accent.red, palette.accent.green, palette.accent.blue)
        GLES20.glUniform3f(secondaryColor, palette.secondary.red, palette.secondary.green, palette.secondary.blue)
        GLES20.glUniform1f(reduced, if (reduceMotion) 1f else 0f)
        quad.position(0); GLES20.glEnableVertexAttribArray(position)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    companion object {
        const val Fragment = """
precision highp float;
uniform vec2 uResolution;
uniform vec4 uSignal;
uniform float uReduced;
uniform vec2 uAccent;
uniform vec3 uPrimary;
uniform vec3 uSecondary;
uniform sampler2D uBackdrop;
float hash(float n) { return fract(sin(n * 127.1) * 43758.5453); }
float glow(float d, float w) { float a=d/w; return 1.0/(1.0+a*a); }
void main() {
    vec2 uv=gl_FragCoord.xy/uResolution;
    float aspect=uResolution.x/uResolution.y;
    float horizon=.66;
    vec3 photo=texture2D(uBackdrop,vec2(uv.x,1.0-uv.y)).rgb;
    // Re-tint photographed colored light while retaining neutral architectural detail.
    float chroma=max(photo.r,max(photo.g,photo.b))-min(photo.r,min(photo.g,photo.b));
    float luminance=dot(photo,vec3(.2126,.7152,.0722));
    vec3 stationTint=mix(uPrimary,uSecondary,clamp(.5+(photo.b-photo.r)*2.0,0.0,1.0));
    vec3 recolored=stationTint*luminance/max(.16,dot(stationTint,vec3(.2126,.7152,.0722)));
    photo=mix(photo,recolored,smoothstep(.07,.30,chroma)*.85);
    float pulse=mix(uSignal.z*.34+uSignal.w*.24,uSignal.z*.07,uReduced);
    float highlight=smoothstep(.16,.7,max(photo.r,max(photo.g,photo.b)));
    float district=.5+.5*sin(uv.x*31.0+uAccent.y*2.0);
    photo*=.76+highlight*(.20+pulse*(.5+district));
    vec3 col=vec3(0.0);
    // Six rooftop fixtures project broad volumetric fans into the night sky.
    // Brightness follows separate mid/high energy; bass articulates their openings.
    if(uv.y>horizon) {
        for(int j=0;j<6;j++) {
            float k=float(j);
            vec2 origin=vec2(.14+k*.144,.70+.045*sin(k*2.4));
            vec2 ray=vec2((uv.x-origin.x)*aspect,uv.y-origin.y);
            float sweep=sin(uAccent.y+(mod(k,2.0)<.5 ? k : -k));
            vec2 axis=normalize(vec2(sweep*.8,1.0));
            float along=dot(ray,axis);
            float across=abs(ray.x*axis.y-ray.y*axis.x);
            float opening=mix(.035+uSignal.z*.085,.035+uSignal.z*.018,uReduced);
            float cone=glow(across,.009+max(along,0.0)*opening);
            cone*=smoothstep(0.0,.025,along)*exp(-max(along,0.0)*2.8);
            vec3 tint=mix(uSecondary,uPrimary,mod(k,2.0));
            float level=mix(.12+uAccent.x*.4+uSignal.z*.75,.12+uAccent.x*.15,uReduced);
            col+=tint*cone*level;
        }
    }
    if(uv.y<horizon) {
        float depth=horizon-uv.y;
        float z=1.6/max(depth,.012);
        float x=(uv.x-.5)*z*aspect;
        float bend=1.0*sin(z*.055);
        float road=x-bend;
        float fog=exp(-z*.016);
        float travel=uSignal.x;
        float strength=mix(.30+.30*uSignal.y+2.8*uSignal.z+1.1*uSignal.w,.65+.12*uSignal.y,uReduced);
        for(int i=0;i<8;i++) {
            float lane=float(i);
            float side=i<4 ? -1.0 : 1.0;
            float k=mod(lane,4.0);
            float laneX=side*(.56+k*.86)+.26*sin(z*.17+lane);
            float d=abs(road-laneX);
            // A moving bright head and long exposure tail follow each curved lane.
            // Travel comes exclusively from the freshness-checked audio motion controller.
            float span=mod(z+side*travel+lane*5.3,29.0);
            float exposure=smoothstep(0.0,1.4,span)*(1.0-smoothstep(15.0,25.0,span));
            float head=exp(-pow((span-2.0)*1.8,2.0));
            vec3 tint=i<4 ? mix(uSecondary,uPrimary,k*.18) : mix(uPrimary,uSecondary,k*.18);
            float width=.045+.014*k+mix(.19,.018,uReduced)*uSignal.z;
            float core=glow(d,width);
            float halo=glow(d,.23)*.34+glow(d,.72)*.10;
            col+=(tint*(halo+core*.8)+vec3(.65,.77,.84)*core*.32)*exposure*fog*strength;
            col+=(tint*.8+vec3(.38))*glow(d,.14)*head*fog*strength;
            // Anamorphic glints travel with the exposure heads, not an independent timer.
            col+=(tint+vec3(.25))*glow(d,1.05)*head*fog*.28*strength;
            // Broken, broadened reflections on wet asphalt sit beside each exposure.
            float ripple=.82+.18*sin(z*31.0+sin(x*8.0));
            col+=tint*glow(d+.12,.34)*exposure*fog*.28*ripple*strength;
        }
        // Distant roadside lamps, with soft pools on the wet ground.
        float postZ=mod(z,12.0);
        float lamp=exp(-pow((postZ-1.0)*1.5,2.0))*glow(abs(road)-5.7,.18);
        col+=uPrimary*lamp*fog*.6;
        col*=smoothstep(0.0,.035,depth);
    }
    // Filmic rolloff and restrained vignette preserve dark space around the trails.
    col=photo + (1.0-photo)*(1.0-exp(-col*1.8));
    col*=1.0-.30*pow(length((uv-.5)*vec2(1.1,1.0)),1.3);
    gl_FragColor=vec4(clamp(col,0.0,1.0),1.0);
}
"""
    }
}
