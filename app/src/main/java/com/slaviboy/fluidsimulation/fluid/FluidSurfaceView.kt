package com.slaviboy.fluidsimulation.fluid

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

/**
 * GLSurfaceView hosting the fluid simulation. Follows the same
 * GLSurfaceView + Renderer skeleton as the author's Galaxy app, but requests
 * an EGL config with no depth/stencil buffer (RGBA8888, 0, 0) since the
 * simulation never uses depth testing — matching the original WebGL context's
 * `{depth:false, stencil:false}` request rather than copying a depth-enabled
 * config that would be wasted here.
 */
class FluidSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer = FluidRenderer(context)

    init {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        // SurfaceView's usual hole-punch compositing (window drawn with a transparent
        // hole so the surface shows through) is unreliable when the view is hosted
        // inside a Compose AndroidView; compositing this surface on top of the window
        // instead sidesteps that entirely, which is safe here since nothing else needs
        // to render above the simulation.
        setZOrderOnTop(true)
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        renderer.onTouchEvent(event)
        return true
    }
}
