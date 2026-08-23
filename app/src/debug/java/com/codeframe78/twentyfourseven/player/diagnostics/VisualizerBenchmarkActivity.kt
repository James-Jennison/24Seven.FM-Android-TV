package com.codeframe78.twentyfourseven.player.diagnostics

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

/**
 * Local, debug-only device profiler. It deliberately avoids Player state, network requests, and
 * audio capture so compositor cadence reflects only the selected render family.
 */
class VisualizerBenchmarkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE)?.lowercase() ?: MODE_GEOMETRY
        setContentView(VisualizerBenchmarkSurface(this, mode))
    }

    private companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_GEOMETRY = "geometry"
    }
}

private class VisualizerBenchmarkSurface(context: Context, mode: String) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(VisualizerBenchmarkRenderer(mode))
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

private class VisualizerBenchmarkRenderer(
    private val mode: String,
) : GLSurfaceView.Renderer {
    private val lineVertices = ByteBuffer
        .allocateDirect(SamplesPerTrail * 2 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val fullscreenVertices = ByteBuffer
        .allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var timeHandle = 0
    private var sizeHandle = 0
    private var width = 1
    private var height = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val useFragmentControl = mode == MODE_FRAGMENT_CONTROL
        program = linkProgram(
            compileShader(GLES20.GL_VERTEX_SHADER, if (useFragmentControl) FullscreenVertexShader else GeometryVertexShader),
            compileShader(GLES20.GL_FRAGMENT_SHADER, if (useFragmentControl) FragmentControlShader else GeometryFragmentShader),
        )
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        sizeHandle = GLES20.glGetUniformLocation(program, "uSize")
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glClearColor(0.002f, 0.006f, 0.014f, 1f)
        Log.i(LogTag, "Started $mode benchmark")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, this.width, this.height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val time = (SystemClock.elapsedRealtime() % 120_000L) / 1_000f
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        if (mode == MODE_FRAGMENT_CONTROL) {
            GLES20.glUniform1f(timeHandle, time)
            GLES20.glUniform2f(sizeHandle, width.toFloat(), height.toFloat())
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, fullscreenVertices)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } else {
            renderGeometryTrails(time)
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun renderGeometryTrails(time: Float) {
        repeat(TrailCount) { trail ->
            lineVertices.position(0)
            val baseY = -0.88f + trail * (1.76f / (TrailCount - 1))
            val energy = 0.16f + (trail % 4) * 0.055f
            for (sample in 0 until SamplesPerTrail) {
                val x = -1f + sample * (2f / (SamplesPerTrail - 1))
                val phase = x * (4.5f + trail * .23f) + time * (1.1f + trail * .09f)
                val y = baseY + sin(phase) * energy + sin(phase * 1.71f + trail) * energy * .36f
                lineVertices.put(x)
                lineVertices.put(y.coerceIn(-1f, 1f))
            }
            lineVertices.position(0)
            val color = TrailColors[trail]
            GLES20.glUniform3f(colorHandle, color[0], color[1], color[2])
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, lineVertices)
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, SamplesPerTrail)
        }
    }

    private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
    }

    private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int = GLES20.glCreateProgram().also { linkedProgram ->
        GLES20.glAttachShader(linkedProgram, vertexShader)
        GLES20.glAttachShader(linkedProgram, fragmentShader)
        GLES20.glLinkProgram(linkedProgram)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linkedProgram, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(linkedProgram) }
    }

    private companion object {
        const val LogTag = "TvVisualizerBenchmark"
        const val MODE_FRAGMENT_CONTROL = "fragment"
        const val TrailCount = 8
        const val SamplesPerTrail = 192
        val TrailColors = arrayOf(
            floatArrayOf(.08f, .72f, 1f),
            floatArrayOf(.28f, .32f, 1f),
            floatArrayOf(.72f, .12f, 1f),
            floatArrayOf(1f, .10f, .60f),
            floatArrayOf(1f, .28f, .14f),
            floatArrayOf(1f, .74f, .08f),
            floatArrayOf(.42f, 1f, .12f),
            floatArrayOf(.08f, .95f, .70f),
        )
    }
}

private const val GeometryVertexShader = """
attribute vec2 aPosition;
void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
"""

private const val GeometryFragmentShader = """
precision mediump float;
uniform vec3 uColor;
void main() { gl_FragColor = vec4(uColor, 1.0); }
"""

private const val FullscreenVertexShader = """
attribute vec2 aPosition;
varying vec2 vUv;
void main() { vUv = aPosition * 0.5 + 0.5; gl_Position = vec4(aPosition, 0.0, 1.0); }
"""

private const val FragmentControlShader = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vUv;
uniform float uTime;
uniform vec2 uSize;
float line(float value) { return 1.0 - smoothstep(0.003, 0.010, abs(value)); }
void main() {
  float phase = vUv.x * 13.0 + uTime * 2.2;
  float y0 = 0.18 + sin(phase) * 0.16;
  float y1 = 0.38 + sin(phase * 1.37 + 1.2) * 0.13;
  float y2 = 0.58 + sin(phase * 1.79 + 2.4) * 0.12;
  float y3 = 0.78 + sin(phase * 2.11 + 3.6) * 0.10;
  vec3 color = vec3(0.002, 0.006, 0.014);
  color += vec3(0.1, 0.8, 1.0) * line(vUv.y - y0);
  color += vec3(0.8, 0.1, 1.0) * line(vUv.y - y1);
  color += vec3(1.0, 0.4, 0.1) * line(vUv.y - y2);
  color += vec3(0.2, 1.0, 0.4) * line(vUv.y - y3);
  gl_FragColor = vec4(color, 1.0);
}
"""
