package com.slaviboy.fluidsimulation.fluid.input

import android.view.MotionEvent
import com.slaviboy.fluidsimulation.fluid.ColorUtil
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

/**
 * Tracks active touch pointers and maps [MotionEvent]s to normalized UV splat
 * coordinates. Android's `MotionEvent.getPointerId()` assigns one stable small
 * integer per active finger on a touchscreen, so a flat list keyed directly by
 * that id is enough to support several simultaneous touches, each with its own
 * independent splat trail.
 */
class PointerManager {

    val pointers = mutableListOf<Pointer>()

    /**
     * Queued random-splat-burst request counts (e.g. the initial startup burst, or a
     * user-triggered "random splats" action from the settings screen). A thread-safe
     * queue since it's written from the UI thread and drained from the GL thread.
     */
    val splatStack = ConcurrentLinkedQueue<Int>()

    private var colorUpdateTimer = 0f

    fun queueRandomSplats(amount: Int) {
        splatStack.add(amount)
    }

    fun updateColors(dt: Float, colorUpdateSpeed: Float) {
        colorUpdateTimer += dt * colorUpdateSpeed
        if (colorUpdateTimer >= 1f) {
            colorUpdateTimer = ColorUtil.wrap(colorUpdateTimer, 0f, 1f)
            for (pointer in pointers) {
                pointer.color = ColorUtil.generateColor()
            }
        }
    }

    fun onTouchEvent(event: MotionEvent, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val pointer = findOrCreate(event.getPointerId(index))
                updateDown(pointer, event.getX(index), event.getY(index), width, height)
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val pointer = pointers.find { it.id == event.getPointerId(index) } ?: continue
                    if (pointer.down) {
                        updateMove(pointer, event.getX(index), event.getY(index), width, height)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                pointers.find { it.id == event.getPointerId(index) }?.down = false
            }

            MotionEvent.ACTION_CANCEL -> {
                pointers.forEach { it.down = false }
            }
        }
    }

    private fun findOrCreate(id: Int): Pointer {
        return pointers.find { it.id == id } ?: Pointer(id = id).also { pointers.add(it) }
    }

    private fun updateDown(pointer: Pointer, x: Float, y: Float, width: Int, height: Int) {
        pointer.down = true
        pointer.moved = false
        pointer.texcoordX = x / width
        // Android's Y axis points down from the top of the view, but the shaders'
        // texture-coordinate convention (vUv) has 0 at the bottom, so it's flipped here.
        pointer.texcoordY = 1f - y / height
        pointer.prevTexcoordX = pointer.texcoordX
        pointer.prevTexcoordY = pointer.texcoordY
        pointer.deltaX = 0f
        pointer.deltaY = 0f
        pointer.color = ColorUtil.generateColor()
    }

    private fun updateMove(pointer: Pointer, x: Float, y: Float, width: Int, height: Int) {
        pointer.prevTexcoordX = pointer.texcoordX
        pointer.prevTexcoordY = pointer.texcoordY
        pointer.texcoordX = x / width
        pointer.texcoordY = 1f - y / height
        pointer.deltaX = correctDeltaX(pointer.texcoordX - pointer.prevTexcoordX, width, height)
        pointer.deltaY = correctDeltaY(pointer.texcoordY - pointer.prevTexcoordY, width, height)
        pointer.moved = abs(pointer.deltaX) > 0f || abs(pointer.deltaY) > 0f
    }

    // texcoordX/Y are normalized to [0,1] independently per axis, so on a non-square
    // screen a drag that moves the same fraction of the width vs. height covers a
    // different physical distance. These corrections rescale the delta on the
    // *shorter* axis by the aspect ratio so a drag feels like it's covering the same
    // physical distance regardless of orientation (this must stay in sync with the
    // matching `aspectRatio` correction applied in splat_shader.glsl).
    private fun correctDeltaX(delta: Float, width: Int, height: Int): Float {
        val aspectRatio = width.toFloat() / height
        return if (aspectRatio < 1f) delta * aspectRatio else delta
    }

    private fun correctDeltaY(delta: Float, width: Int, height: Int): Float {
        val aspectRatio = width.toFloat() / height
        return if (aspectRatio > 1f) delta / aspectRatio else delta
    }
}
