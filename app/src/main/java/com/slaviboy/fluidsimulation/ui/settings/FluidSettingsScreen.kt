package com.slaviboy.fluidsimulation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slaviboy.fluidsimulation.fluid.ColorUtil
import com.slaviboy.fluidsimulation.fluid.FluidRenderer
import kotlin.math.max
import kotlin.math.min

/**
 * Settings screen mirroring the WebGL fluid simulation's dat.GUI controls, bound
 * live to [renderer]'s [com.slaviboy.fluidsimulation.fluid.FluidConfig]. Resolution
 * and shading/bloom/sunrays toggles require GL-thread work (framebuffer recreation,
 * shader recompilation) so those go through `renderer.requestFramebufferReinit()` /
 * `requestDisplayKeywordsUpdate()` rather than being applied directly.
 */
@Composable
fun FluidSettingsScreen(renderer: FluidRenderer, onClose: () -> Unit) {
    val config = renderer.config

    var simResolution by remember { mutableStateOf(config.simResolution) }
    var dyeResolution by remember { mutableStateOf(config.dyeResolution) }
    var densityDissipation by remember { mutableStateOf(config.densityDissipation) }
    var velocityDissipation by remember { mutableStateOf(config.velocityDissipation) }
    var pressure by remember { mutableStateOf(config.pressure) }
    var curl by remember { mutableStateOf(config.curl) }
    var splatRadius by remember { mutableStateOf(config.splatRadius) }

    var shading by remember { mutableStateOf(config.shading) }
    var colorful by remember { mutableStateOf(config.colorful) }
    var paused by remember { mutableStateOf(config.paused) }

    var bloom by remember { mutableStateOf(config.bloom) }
    var bloomIntensity by remember { mutableStateOf(config.bloomIntensity) }
    var bloomThreshold by remember { mutableStateOf(config.bloomThreshold) }

    var sunrays by remember { mutableStateOf(config.sunrays) }
    var sunraysWeight by remember { mutableStateOf(config.sunraysWeight) }

    var transparent by remember { mutableStateOf(config.transparent) }

    val initialHsv = remember { rgbToHsv(config.backColor[0], config.backColor[1], config.backColor[2]) }
    var backColorHue by remember { mutableStateOf(initialHsv.first) }
    var backColorSaturation by remember { mutableStateOf(initialHsv.second) }
    var backColorBrightness by remember { mutableStateOf(initialHsv.third) }

    fun applyBackColor(hue: Float, saturation: Float, brightness: Float) {
        config.backColor = ColorUtil.hsvToRgb(hue / 360f, saturation, brightness)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    "Settings",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✕",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .padding(8.dp)
                )
            }

            SectionTitle("Simulation")

            ResolutionSelector(
                label = "Sim Resolution",
                options = listOf("32" to 32, "64" to 64, "128" to 128, "256" to 256),
                selected = simResolution,
                onSelected = {
                    simResolution = it
                    config.simResolution = it
                    renderer.requestFramebufferReinit()
                }
            )

            ResolutionSelector(
                label = "Dye Resolution",
                options = listOf("Very low" to 128, "Low" to 256, "Medium" to 512, "High" to 1024),
                selected = dyeResolution,
                onSelected = {
                    dyeResolution = it
                    config.dyeResolution = it
                    renderer.requestFramebufferReinit()
                }
            )

            SettingsSlider(
                label = "Density Diffusion",
                value = densityDissipation,
                valueRange = 0f..4f,
                onValueChange = {
                    densityDissipation = it
                    config.densityDissipation = it
                }
            )

            SettingsSlider(
                label = "Velocity Diffusion",
                value = velocityDissipation,
                valueRange = 0f..4f,
                onValueChange = {
                    velocityDissipation = it
                    config.velocityDissipation = it
                }
            )

            SettingsSlider(
                label = "Pressure",
                value = pressure,
                valueRange = 0f..1f,
                onValueChange = {
                    pressure = it
                    config.pressure = it
                }
            )

            SettingsSlider(
                label = "Vorticity",
                value = curl,
                valueRange = 0f..50f,
                steps = 49,
                onValueChange = {
                    curl = it
                    config.curl = it
                }
            )

            SettingsSlider(
                label = "Splat Radius",
                value = splatRadius,
                valueRange = 0.01f..1f,
                onValueChange = {
                    splatRadius = it
                    config.splatRadius = it
                }
            )

            SectionTitle("Rendering")

            SettingsSwitch("Shading", shading) {
                shading = it
                config.shading = it
                renderer.requestDisplayKeywordsUpdate()
            }
            SettingsSwitch("Colorful", colorful) {
                colorful = it
                config.colorful = it
            }
            SettingsSwitch("Paused", paused) {
                paused = it
                config.paused = it
            }
            SettingsSwitch("Bloom", bloom) {
                bloom = it
                config.bloom = it
                renderer.requestDisplayKeywordsUpdate()
            }
            if (bloom) {
                SettingsSlider(
                    label = "Bloom Intensity",
                    value = bloomIntensity,
                    valueRange = 0.1f..2f,
                    onValueChange = {
                        bloomIntensity = it
                        config.bloomIntensity = it
                    }
                )
                SettingsSlider(
                    label = "Bloom Threshold",
                    value = bloomThreshold,
                    valueRange = 0f..1f,
                    onValueChange = {
                        bloomThreshold = it
                        config.bloomThreshold = it
                    }
                )
            }
            SettingsSwitch("Sunrays", sunrays) {
                sunrays = it
                config.sunrays = it
                renderer.requestDisplayKeywordsUpdate()
            }
            if (sunrays) {
                SettingsSlider(
                    label = "Sunrays Weight",
                    value = sunraysWeight,
                    valueRange = 0.3f..1f,
                    onValueChange = {
                        sunraysWeight = it
                        config.sunraysWeight = it
                    }
                )
            }

            SectionTitle("Background")

            SettingsSwitch("Transparent", transparent) {
                transparent = it
                config.transparent = it
            }
            if (!transparent) {
                Text(
                    "Background Color",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                val hueColors = remember {
                    (0..12).map { step -> hsvToComposeColor(step / 12f, 1f, 1f) }
                }
                GradientPickerSlider(
                    fraction = backColorHue / 360f,
                    colors = hueColors,
                    onFractionChange = { fraction ->
                        val hue = fraction * 360f
                        backColorHue = hue
                        applyBackColor(hue, backColorSaturation, backColorBrightness)
                    },
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                Text(
                    "Saturation: ${"%.2f".format(backColorSaturation)}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                GradientPickerSlider(
                    fraction = backColorSaturation,
                    colors = listOf(
                        hsvToComposeColor(backColorHue / 360f, 0f, backColorBrightness),
                        hsvToComposeColor(backColorHue / 360f, 1f, backColorBrightness)
                    ),
                    onFractionChange = { fraction ->
                        backColorSaturation = fraction
                        applyBackColor(backColorHue, fraction, backColorBrightness)
                    },
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                Text(
                    "Brightness: ${"%.2f".format(backColorBrightness)}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                GradientPickerSlider(
                    fraction = backColorBrightness,
                    colors = listOf(
                        Color.Black,
                        hsvToComposeColor(backColorHue / 360f, backColorSaturation, 1f)
                    ),
                    onFractionChange = { fraction ->
                        backColorBrightness = fraction
                        applyBackColor(backColorHue, backColorSaturation, fraction)
                    },
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
            }

            Button(
                onClick = { renderer.triggerRandomSplats() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Random Splats")
            }

            OutlinedButton(
                onClick = {
                    renderer.resetConfig()

                    simResolution = config.simResolution
                    dyeResolution = config.dyeResolution
                    densityDissipation = config.densityDissipation
                    velocityDissipation = config.velocityDissipation
                    pressure = config.pressure
                    curl = config.curl
                    splatRadius = config.splatRadius

                    shading = config.shading
                    colorful = config.colorful
                    paused = config.paused

                    bloom = config.bloom
                    bloomIntensity = config.bloomIntensity
                    bloomThreshold = config.bloomThreshold

                    sunrays = config.sunrays
                    sunraysWeight = config.sunraysWeight

                    transparent = config.transparent
                    val (hue, saturation, brightness) = rgbToHsv(config.backColor[0], config.backColor[1], config.backColor[2])
                    backColorHue = hue
                    backColorSaturation = saturation
                    backColorBrightness = brightness
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Reset to Defaults")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text("$label: ${"%.2f".format(value)}", color = Color.White, fontSize = 14.sp)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
private fun ResolutionSelector(
    label: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (optionLabel, optionValue) ->
                SegmentedButton(
                    selected = optionValue == selected,
                    onClick = { onSelected(optionValue) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(optionLabel)
                }
            }
        }
    }
}

/**
 * A draggable horizontal gradient bar used for the background color picker's hue,
 * saturation and brightness sliders: [fraction] (0..1) positions the thumb, [colors]
 * defines the gradient shown along the track, and dragging/tapping reports the new
 * fraction via [onFractionChange].
 */
@Composable
private fun GradientPickerSlider(
    fraction: Float,
    colors: List<Color>,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(colors))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onFractionChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onFractionChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
    ) {
        val thumbOffset = maxWidth * fraction.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .offset(x = thumbOffset - 2.dp)
                .width(4.dp)
                .fillMaxHeight()
                .background(Color.White, RoundedCornerShape(2.dp))
        )
    }
}

private fun hsvToComposeColor(hueFraction: Float, saturation: Float, value: Float): Color {
    val rgb = ColorUtil.hsvToRgb(hueFraction, saturation, value)
    return Color(rgb[0], rgb[1], rgb[2])
}

/** Returns (hue in 0..360, saturation in 0..1, value/brightness in 0..1). */
private fun rgbToHsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC

    val hue = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta).mod(6f))
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (maxC == 0f) 0f else delta / maxC
    val value = maxC
    return Triple(hue, saturation, value)
}
