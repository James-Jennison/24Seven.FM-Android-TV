package com.codeframe78.twentyfourseven.player.ui

import android.opengl.EGL14
import android.opengl.GLES20
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** Actual device GPU fixtures; offscreen timing is not display/compositor FPS. */
class ReferenceMaterialShaderTest {
    @Test fun allMaterialsRenderAnimateAndRespondOnDeviceGpu() {
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version=IntArray(2); assertTrue(EGL14.eglInitialize(display,version,0,version,1))
        val configs=arrayOfNulls<android.opengl.EGLConfig>(1); val count=IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,count,0))
        val context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        val surface=EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,640,EGL14.EGL_HEIGHT,360,EGL14.EGL_NONE),0)
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context))
        var program=0
        try {
            fun compile(type: Int,text: String): Int {
                val shader=GLES20.glCreateShader(type); GLES20.glShaderSource(shader,text); GLES20.glCompileShader(shader)
                val status=IntArray(1); GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
                assertEquals(GLES20.glGetShaderInfoLog(shader),1,status[0]); return shader
            }
            val vertex=compile(GLES20.GL_VERTEX_SHADER,"attribute vec2 aPosition; void main(){gl_Position=vec4(aPosition,0.0,1.0);}")
            val fragment=compile(GLES20.GL_FRAGMENT_SHADER,ReferenceMaterialShader)
            program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vertex); GLES20.glAttachShader(program,fragment); GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
            val status=IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
            assertEquals(GLES20.glGetProgramInfoLog(program),1,status[0]); GLES20.glUseProgram(program)
            val quad=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0) }
            val pos=GLES20.glGetAttribLocation(program,"aPosition"); GLES20.glEnableVertexAttribArray(pos)
            GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,quad)
            GLES20.glViewport(0,0,640,360)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uResolution"),640f,360f)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uGain"),1f)
            val hashes=mutableSetOf<Int>()
            fun render(style: Int,time: Float,audio: Float,save: Boolean=false): Int {
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uStyle"),style)
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uTime"),time)
                GLES20.glUniform4f(GLES20.glGetUniformLocation(program,"uAudio"),audio,audio,audio,audio)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
                val bytes=ByteBuffer.allocateDirect(640*360*4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0,0,640,360,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes)
                assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
                if(save) {
                    val bitmap=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888)
                    bytes.position(0); bitmap.copyPixelsFromBuffer(bytes)
                    InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput("material-$style.png",0).use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                }
                bytes.position(0); val pixels=IntArray(640*360); bytes.asIntBuffer().get(pixels)
                return pixels.contentHashCode()
            }
            repeat(10) { style ->
                val baseline=render(style,2f,0f)
                assertTrue("distinct $style",hashes.add(baseline))
                assertNotEquals("motion $style",baseline,render(style,4f,0f))
                assertNotEquals("audio $style",baseline,render(style,2f,.8f,true))
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uGain"),.5f)
                assertNotEquals("reduced $style",baseline,render(style,2f,0f))
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uGain"),1f)
                val start=System.nanoTime()
                repeat(60) {
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uTime"),it*.02f)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GLES20.glFinish()
                }
                val millis=(System.nanoTime()-start)/60e6
                InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput("material-timing-$style.txt",0).use {
                    it.write("GPU fixture 640x360, 60 synchronous draws, mean milliseconds=$millis".toByteArray())
                }
            }
        } finally {
            if(program!=0) GLES20.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,context); EGL14.eglTerminate(display)
        }
    }
}
