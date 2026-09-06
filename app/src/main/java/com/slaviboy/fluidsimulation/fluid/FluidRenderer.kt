package com.slaviboy.fluidsimulation.fluid

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.MotionEvent
import com.slaviboy.fluidsimulation.R
import com.slaviboy.fluidsimulation.fluid.gl.Blit
import com.slaviboy.fluidsimulation.fluid.gl.DisplayMaterial
import com.slaviboy.fluidsimulation.fluid.gl.DoubleFbo
import com.slaviboy.fluidsimulation.fluid.gl.Fbo
import com.slaviboy.fluidsimulation.fluid.gl.ShaderProgram
import com.slaviboy.fluidsimulation.fluid.gl.ShaderText
import com.slaviboy.fluidsimulation.fluid.gl.TextureFormatProbe
import com.slaviboy.fluidsimulation.fluid.input.Pointer
import com.slaviboy.fluidsimulation.fluid.input.PointerManager
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/**
 * Native GLES 3.0 port of the WebGL fluid simulation's per-frame pipeline
 * (script.js: `step`/`render`/`applyBloom`/`applySunrays`/`splat`). Owns all
 * shader programs, framebuffers and simulation state.
 */
class FluidRenderer(private val context: Context) : GLSurfaceView.Renderer {

    val config = FluidConfig()
    private val pointerManager = PointerManager()

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var lastUpdateTimeNanos = 0L

    private lateinit var formats: TextureFormatProbe.SupportedFormats

    private lateinit var blurProgram: ShaderProgram
    private lateinit var copyProgram: ShaderProgram
    private lateinit var clearProgram: ShaderProgram
    private lateinit var colorProgram: ShaderProgram
    private lateinit var checkerboardProgram: ShaderProgram
    private lateinit var bloomPrefilterProgram: ShaderProgram
    private lateinit var bloomBlurProgram: ShaderProgram
    private lateinit var bloomFinalProgram: ShaderProgram
    private lateinit var sunraysMaskProgram: ShaderProgram
    private lateinit var sunraysProgram: ShaderProgram
    private lateinit var splatProgram: ShaderProgram
    private lateinit var advectionProgram: ShaderProgram
    private lateinit var divergenceProgram: ShaderProgram
    private lateinit var curlProgram: ShaderProgram
    private lateinit var vorticityProgram: ShaderProgram
    private lateinit var pressureProgram: ShaderProgram
    private lateinit var gradientSubtractProgram: ShaderProgram
    private lateinit var displayMaterial: DisplayMaterial

    private var ditheringTexture = 0
    private var ditheringWidth = 1
    private var ditheringHeight = 1

    private var dye: DoubleFbo? = null
    private var velocity: DoubleFbo? = null
    private var divergence: Fbo? = null
    private var curl: Fbo? = null
    private var pressure: DoubleFbo? = null
    private var bloom: Fbo? = null
    private val bloomFbos = mutableListOf<Fbo>()
    private var sunrays: Fbo? = null
    private var sunraysTemp: Fbo? = null

    @Volatile private var framebuffersDirty = false
    @Volatile private var displayKeywordsDirty = false

    fun onTouchEvent(event: MotionEvent) {
        pointerManager.onTouchEvent(event, surfaceWidth, surfaceHeight)
    }

    /**
     * Call after changing [config]'s resolution fields (simResolution/dyeResolution) from
     * the settings screen. Framebuffer (re)creation is GL work, so it can't happen directly
     * on the calling (UI) thread; it's deferred to the next [onDrawFrame] instead.
     */
    fun requestFramebufferReinit() {
        framebuffersDirty = true
    }

    /**
     * Call after toggling config.shading/bloom/sunrays from the settings screen: the display
     * shader is compiled per keyword combination (see [DisplayMaterial]), so a new combination
     * requires a (GL-thread) shader compile, deferred to the next [onDrawFrame].
     */
    fun requestDisplayKeywordsUpdate() {
        displayKeywordsDirty = true
    }

    /** Triggers a burst of random splats, e.g. from the settings screen's "Random splats" button. */
    fun triggerRandomSplats(amount: Int = (5..24).random()) {
        pointerManager.queueRandomSplats(amount)
    }

    // region lifecycle

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
        val baseVertexSource = ShaderText.load(context, R.raw.base_vertex_shader)
        val blurVertexSource = ShaderText.load(context, R.raw.blur_vertex_shader)

        blurProgram = ShaderProgram(blurVertexSource, ShaderText.load(context, R.raw.blur_shader))
        copyProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.copy_shader))
        clearProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.clear_shader))
        colorProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.color_shader))
        checkerboardProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.checkerboard_shader))
        bloomPrefilterProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.bloom_prefilter_shader))
        bloomBlurProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.bloom_blur_shader))
        bloomFinalProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.bloom_final_shader))
        sunraysMaskProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.sunrays_mask_shader))
        sunraysProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.sunrays_shader))
        splatProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.splat_shader))
        advectionProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.advection_shader))
        divergenceProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.divergence_shader))
        curlProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.curl_shader))
        vorticityProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.vorticity_shader))
        pressureProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.pressure_shader))
        gradientSubtractProgram = ShaderProgram(baseVertexSource, ShaderText.load(context, R.raw.gradient_subtract_shader))

        displayMaterial = DisplayMaterial(context, baseVertexSource, R.raw.display_shader)
        displayMaterial.setKeywords(activeDisplayKeywords())

        Blit.init()
        loadDitheringTexture()
        formats = TextureFormatProbe.probe()

        // Surface (re)creation can happen after EGL context loss (app backgrounded/foregrounded),
        // which has no web equivalent; treat every call as a full reset.
        dye = null
        velocity = null
        divergence = null
        curl = null
        pressure = null
        bloom = null
        bloomFbos.clear()
        sunrays = null
        sunraysTemp = null

        lastUpdateTimeNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        Blit.drawingBufferWidth = width
        Blit.drawingBufferHeight = height
        GLES30.glViewport(0, 0, width, height)

        val firstInit = dye == null
        initFramebuffers()
        if (firstInit) {
            pointerManager.queueRandomSplats((5..24).random())
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (framebuffersDirty) {
            framebuffersDirty = false
            initFramebuffers()
        }
        if (displayKeywordsDirty) {
            displayKeywordsDirty = false
            displayMaterial.setKeywords(activeDisplayKeywords())
        }

        val dt = calcDeltaTime()
        pointerManager.updateColors(dt, config.colorUpdateSpeed)
        applyInputs()
        if (!config.paused) step(dt)
        render(null)
    }

    private fun calcDeltaTime(): Float {
        val now = System.nanoTime()
        val dt = ((now - lastUpdateTimeNanos) / 1_000_000_000f).coerceAtMost(1f / 60f)
        lastUpdateTimeNanos = now
        return dt
    }

    private fun activeDisplayKeywords(): Set<String> {
        val keywords = mutableSetOf<String>()
        if (config.shading) keywords.add("SHADING")
        if (config.bloom) keywords.add("BLOOM")
        if (config.sunrays) keywords.add("SUNRAYS")
        return keywords
    }

    private fun loadDitheringTexture() {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ldr_lll1_0)
        ditheringWidth = bitmap.width
        ditheringHeight = bitmap.height

        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        ditheringTexture = textureIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ditheringTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    private fun attachTexture(texture: Int, unit: Int): Int {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        return unit
    }

    // endregion

    // region framebuffer (re)creation

    private fun getResolution(resolution: Int): Pair<Int, Int> {
        var aspectRatio = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        if (aspectRatio < 1f) aspectRatio = 1f / aspectRatio
        val min = resolution.toFloat().roundToInt()
        val max = (resolution * aspectRatio).roundToInt()
        return if (surfaceWidth > surfaceHeight) Pair(max, min) else Pair(min, max)
    }

    private fun resizeFbo(target: Fbo, width: Int, height: Int, internalFormat: Int, format: Int, type: Int, param: Int): Fbo {
        val newFbo = Fbo.create(width, height, internalFormat, format, type, param)
        copyProgram.bind()
        GLES30.glUniform1i(copyProgram.uniforms.getValue("uTexture"), target.attach(0))
        Blit.blit(newFbo)
        target.delete()
        return newFbo
    }

    private fun resizeDoubleFbo(target: DoubleFbo?, width: Int, height: Int, internalFormat: Int, format: Int, type: Int, param: Int): DoubleFbo {
        if (target != null && target.width == width && target.height == height) return target
        if (target == null) {
            val fbo1 = Fbo.create(width, height, internalFormat, format, type, param)
            val fbo2 = Fbo.create(width, height, internalFormat, format, type, param)
            return DoubleFbo(fbo1, fbo2)
        }
        val newRead = resizeFbo(target.read, width, height, internalFormat, format, type, param)
        target.write.delete()
        val newWrite = Fbo.create(width, height, internalFormat, format, type, param)
        return DoubleFbo(newRead, newWrite)
    }

    private fun initFramebuffers() {
        val texType = formats.halfFloatTexType
        val rgba = formats.formatRGBA
        val rg = formats.formatRG
        val r = formats.formatR
        val filtering = GLES30.GL_LINEAR

        val (simWidth, simHeight) = getResolution(config.simResolution)
        val (dyeWidth, dyeHeight) = getResolution(config.dyeResolution)

        // dye/velocity preserve content across a resize (blit old into newly sized FBO);
        // divergence/curl/pressure are transient scratch buffers, always freshly recreated.
        dye = resizeDoubleFbo(dye, dyeWidth, dyeHeight, rgba.internalFormat, rgba.format, texType, filtering)
        velocity = resizeDoubleFbo(velocity, simWidth, simHeight, rg.internalFormat, rg.format, texType, filtering)

        divergence?.delete()
        divergence = Fbo.create(simWidth, simHeight, r.internalFormat, r.format, texType, GLES30.GL_NEAREST)

        curl?.delete()
        curl = Fbo.create(simWidth, simHeight, r.internalFormat, r.format, texType, GLES30.GL_NEAREST)

        pressure?.read?.delete()
        pressure?.write?.delete()
        pressure = DoubleFbo(
            Fbo.create(simWidth, simHeight, r.internalFormat, r.format, texType, GLES30.GL_NEAREST),
            Fbo.create(simWidth, simHeight, r.internalFormat, r.format, texType, GLES30.GL_NEAREST)
        )

        initBloomFramebuffers()
        initSunraysFramebuffers()
    }

    private fun initBloomFramebuffers() {
        val (width, height) = getResolution(config.bloomResolution)
        val texType = formats.halfFloatTexType
        val rgba = formats.formatRGBA
        val filtering = GLES30.GL_LINEAR

        bloom?.delete()
        bloom = Fbo.create(width, height, rgba.internalFormat, rgba.format, texType, filtering)

        bloomFbos.forEach { it.delete() }
        bloomFbos.clear()
        for (i in 0 until config.bloomIterations) {
            val fboWidth = width shr (i + 1)
            val fboHeight = height shr (i + 1)
            if (fboWidth < 2 || fboHeight < 2) break
            bloomFbos.add(Fbo.create(fboWidth, fboHeight, rgba.internalFormat, rgba.format, texType, filtering))
        }
    }

    private fun initSunraysFramebuffers() {
        val (width, height) = getResolution(config.sunraysResolution)
        val texType = formats.halfFloatTexType
        val r = formats.formatR
        val filtering = GLES30.GL_LINEAR

        sunrays?.delete()
        sunrays = Fbo.create(width, height, r.internalFormat, r.format, texType, filtering)
        sunraysTemp?.delete()
        sunraysTemp = Fbo.create(width, height, r.internalFormat, r.format, texType, filtering)
    }

    // endregion

    // region input -> splats

    private fun applyInputs() {
        while (true) {
            val amount = pointerManager.splatStack.poll() ?: break
            multipleSplats(amount)
        }
        for (pointer in pointerManager.pointers) {
            if (pointer.moved) {
                pointer.moved = false
                splatPointer(pointer)
            }
        }
    }

    private fun splatPointer(pointer: Pointer) {
        val dx = pointer.deltaX * config.splatForce
        val dy = pointer.deltaY * config.splatForce
        splat(pointer.texcoordX, pointer.texcoordY, dx, dy, pointer.color)
    }

    private fun multipleSplats(amount: Int) {
        repeat(amount) {
            val color = ColorUtil.generateColor()
            color[0] *= 10f
            color[1] *= 10f
            color[2] *= 10f
            val x = Math.random().toFloat()
            val y = Math.random().toFloat()
            val dx = 1000f * (Math.random().toFloat() - 0.5f)
            val dy = 1000f * (Math.random().toFloat() - 0.5f)
            splat(x, y, dx, dy, color)
        }
    }

    private fun correctRadius(radius: Float): Float {
        val aspectRatio = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        return if (aspectRatio > 1f) radius * aspectRatio else radius
    }

    private fun splat(x: Float, y: Float, dx: Float, dy: Float, color: FloatArray) {
        val vel = velocity ?: return
        val dyeFbo = dye ?: return

        splatProgram.bind()
        GLES30.glUniform1i(splatProgram.uniforms.getValue("uTarget"), vel.read.attach(0))
        GLES30.glUniform1f(splatProgram.uniforms.getValue("aspectRatio"), surfaceWidth.toFloat() / surfaceHeight)
        GLES30.glUniform2f(splatProgram.uniforms.getValue("point"), x, y)
        GLES30.glUniform3f(splatProgram.uniforms.getValue("color"), dx, dy, 0f)
        GLES30.glUniform1f(splatProgram.uniforms.getValue("radius"), correctRadius(config.splatRadius / 100f))
        Blit.blit(vel.write)
        vel.swap()

        GLES30.glUniform1i(splatProgram.uniforms.getValue("uTarget"), dyeFbo.read.attach(0))
        GLES30.glUniform3f(splatProgram.uniforms.getValue("color"), color[0], color[1], color[2])
        Blit.blit(dyeFbo.write)
        dyeFbo.swap()
    }

    // endregion

    // region simulation step

    private fun step(dt: Float) {
        val vel = velocity ?: return
        val dyeFbo = dye ?: return
        val div = divergence ?: return
        val curlFbo = curl ?: return
        val pres = pressure ?: return

        GLES30.glDisable(GLES30.GL_BLEND)

        curlProgram.bind()
        GLES30.glUniform2f(curlProgram.uniforms.getValue("texelSize"), vel.texelSizeX, vel.texelSizeY)
        GLES30.glUniform1i(curlProgram.uniforms.getValue("uVelocity"), vel.read.attach(0))
        Blit.blit(curlFbo)

        vorticityProgram.bind()
        GLES30.glUniform2f(vorticityProgram.uniforms.getValue("texelSize"), vel.texelSizeX, vel.texelSizeY)
        GLES30.glUniform1i(vorticityProgram.uniforms.getValue("uVelocity"), vel.read.attach(0))
        GLES30.glUniform1i(vorticityProgram.uniforms.getValue("uCurl"), curlFbo.attach(1))
        GLES30.glUniform1f(vorticityProgram.uniforms.getValue("curl"), config.curl)
        GLES30.glUniform1f(vorticityProgram.uniforms.getValue("dt"), dt)
        Blit.blit(vel.write)
        vel.swap()

        divergenceProgram.bind()
        GLES30.glUniform2f(divergenceProgram.uniforms.getValue("texelSize"), vel.texelSizeX, vel.texelSizeY)
        GLES30.glUniform1i(divergenceProgram.uniforms.getValue("uVelocity"), vel.read.attach(0))
        Blit.blit(div)

        clearProgram.bind()
        GLES30.glUniform1i(clearProgram.uniforms.getValue("uTexture"), pres.read.attach(0))
        GLES30.glUniform1f(clearProgram.uniforms.getValue("value"), config.pressure)
        Blit.blit(pres.write)
        pres.swap()

        pressureProgram.bind()
        GLES30.glUniform2f(pressureProgram.uniforms.getValue("texelSize"), vel.texelSizeX, vel.texelSizeY)
        GLES30.glUniform1i(pressureProgram.uniforms.getValue("uDivergence"), div.attach(0))
        repeat(config.pressureIterations) {
            GLES30.glUniform1i(pressureProgram.uniforms.getValue("uPressure"), pres.read.attach(1))
            Blit.blit(pres.write)
            pres.swap()
        }

        gradientSubtractProgram.bind()
        GLES30.glUniform2f(gradientSubtractProgram.uniforms.getValue("texelSize"), vel.texelSizeX, vel.texelSizeY)
        GLES30.glUniform1i(gradientSubtractProgram.uniforms.getValue("uPressure"), pres.read.attach(0))
        GLES30.glUniform1i(gradientSubtractProgram.uniforms.getValue("uVelocity"), vel.read.attach(1))
        Blit.blit(vel.write)
        vel.swap()

        advectionProgram.bind()
        GLES30.glUniform2f(advectionProgram.uniforms.getValue("texelSize"), vel.texelSizeX, vel.texelSizeY)
        var velocityId = vel.read.attach(0)
        GLES30.glUniform1i(advectionProgram.uniforms.getValue("uVelocity"), velocityId)
        GLES30.glUniform1i(advectionProgram.uniforms.getValue("uSource"), velocityId)
        GLES30.glUniform1f(advectionProgram.uniforms.getValue("dt"), dt)
        GLES30.glUniform1f(advectionProgram.uniforms.getValue("dissipation"), config.velocityDissipation)
        Blit.blit(vel.write)
        vel.swap()

        velocityId = vel.read.attach(0)
        GLES30.glUniform1i(advectionProgram.uniforms.getValue("uVelocity"), velocityId)
        GLES30.glUniform1i(advectionProgram.uniforms.getValue("uSource"), dyeFbo.read.attach(1))
        GLES30.glUniform1f(advectionProgram.uniforms.getValue("dissipation"), config.densityDissipation)
        Blit.blit(dyeFbo.write)
        dyeFbo.swap()
    }

    // endregion

    // region render

    private fun render(target: Fbo?) {
        val dyeFbo = dye ?: return

        if (config.bloom) applyBloom(dyeFbo.read, bloom)
        if (config.sunrays) {
            applySunrays(dyeFbo.read, dyeFbo.write, sunrays)
            blur(sunrays!!, sunraysTemp!!, 1)
        }

        if (target == null || !config.transparent) {
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glEnable(GLES30.GL_BLEND)
        } else {
            GLES30.glDisable(GLES30.GL_BLEND)
        }

        if (!config.transparent) {
            drawColor(target, config.backColor)
        }
        if (target == null && config.transparent) {
            drawCheckerboard(target)
        }
        drawDisplay(target)
    }

    private fun applyBloom(source: Fbo, destination: Fbo?) {
        if (destination == null || bloomFbos.size < 2) return

        GLES30.glDisable(GLES30.GL_BLEND)
        bloomPrefilterProgram.bind()
        val knee = config.bloomThreshold * config.bloomSoftKnee + 0.0001f
        val curve0 = config.bloomThreshold - knee
        val curve1 = knee * 2f
        val curve2 = 0.25f / knee
        GLES30.glUniform3f(bloomPrefilterProgram.uniforms.getValue("curve"), curve0, curve1, curve2)
        GLES30.glUniform1f(bloomPrefilterProgram.uniforms.getValue("threshold"), config.bloomThreshold)
        GLES30.glUniform1i(bloomPrefilterProgram.uniforms.getValue("uTexture"), source.attach(0))
        Blit.blit(destination)

        var last: Fbo = destination
        bloomBlurProgram.bind()
        for (fbo in bloomFbos) {
            GLES30.glUniform2f(bloomBlurProgram.uniforms.getValue("texelSize"), last.texelSizeX, last.texelSizeY)
            GLES30.glUniform1i(bloomBlurProgram.uniforms.getValue("uTexture"), last.attach(0))
            Blit.blit(fbo)
            last = fbo
        }

        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glEnable(GLES30.GL_BLEND)

        for (i in bloomFbos.size - 2 downTo 0) {
            val baseTex = bloomFbos[i]
            GLES30.glUniform2f(bloomBlurProgram.uniforms.getValue("texelSize"), last.texelSizeX, last.texelSizeY)
            GLES30.glUniform1i(bloomBlurProgram.uniforms.getValue("uTexture"), last.attach(0))
            Blit.blit(baseTex)
            last = baseTex
        }

        GLES30.glDisable(GLES30.GL_BLEND)
        bloomFinalProgram.bind()
        GLES30.glUniform2f(bloomFinalProgram.uniforms.getValue("texelSize"), last.texelSizeX, last.texelSizeY)
        GLES30.glUniform1i(bloomFinalProgram.uniforms.getValue("uTexture"), last.attach(0))
        GLES30.glUniform1f(bloomFinalProgram.uniforms.getValue("intensity"), config.bloomIntensity)
        Blit.blit(destination)
    }

    private fun applySunrays(source: Fbo, mask: Fbo, destination: Fbo?) {
        if (destination == null) return
        GLES30.glDisable(GLES30.GL_BLEND)
        sunraysMaskProgram.bind()
        GLES30.glUniform1i(sunraysMaskProgram.uniforms.getValue("uTexture"), source.attach(0))
        Blit.blit(mask)

        sunraysProgram.bind()
        GLES30.glUniform1f(sunraysProgram.uniforms.getValue("weight"), config.sunraysWeight)
        GLES30.glUniform1i(sunraysProgram.uniforms.getValue("uTexture"), mask.attach(0))
        Blit.blit(destination)
    }

    private fun blur(target: Fbo, temp: Fbo, iterations: Int) {
        blurProgram.bind()
        repeat(iterations) {
            GLES30.glUniform2f(blurProgram.uniforms.getValue("texelSize"), target.texelSizeX, 0f)
            GLES30.glUniform1i(blurProgram.uniforms.getValue("uTexture"), target.attach(0))
            Blit.blit(temp)

            GLES30.glUniform2f(blurProgram.uniforms.getValue("texelSize"), 0f, target.texelSizeY)
            GLES30.glUniform1i(blurProgram.uniforms.getValue("uTexture"), temp.attach(0))
            Blit.blit(target)
        }
    }

    private fun drawColor(target: Fbo?, color: FloatArray) {
        colorProgram.bind()
        GLES30.glUniform4f(colorProgram.uniforms.getValue("color"), color[0], color[1], color[2], 1f)
        Blit.blit(target)
    }

    private fun drawCheckerboard(target: Fbo?) {
        checkerboardProgram.bind()
        GLES30.glUniform1f(checkerboardProgram.uniforms.getValue("aspectRatio"), surfaceWidth.toFloat() / surfaceHeight)
        Blit.blit(target)
    }

    private fun drawDisplay(target: Fbo?) {
        val dyeFbo = dye ?: return
        val width = target?.width ?: surfaceWidth
        val height = target?.height ?: surfaceHeight

        displayMaterial.bind()
        val uniforms = displayMaterial.uniforms
        if (config.shading) {
            uniforms["texelSize"]?.let { GLES30.glUniform2f(it, 1f / width, 1f / height) }
        }
        uniforms["uTexture"]?.let { GLES30.glUniform1i(it, dyeFbo.read.attach(0)) }
        if (config.bloom) {
            bloom?.let { bloomFbo ->
                uniforms["uBloom"]?.let { GLES30.glUniform1i(it, bloomFbo.attach(1)) }
                uniforms["uDithering"]?.let { GLES30.glUniform1i(it, attachTexture(ditheringTexture, 2)) }
                uniforms["ditherScale"]?.let {
                    GLES30.glUniform2f(it, width.toFloat() / ditheringWidth, height.toFloat() / ditheringHeight)
                }
            }
        }
        if (config.sunrays) {
            sunrays?.let { sunraysFbo ->
                uniforms["uSunrays"]?.let { GLES30.glUniform1i(it, sunraysFbo.attach(3)) }
            }
        }
        Blit.blit(target)
    }

    // endregion
}
