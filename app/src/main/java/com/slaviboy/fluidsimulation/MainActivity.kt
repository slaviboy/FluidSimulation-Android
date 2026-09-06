package com.slaviboy.fluidsimulation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.slaviboy.fluidsimulation.fluid.FluidSurfaceView
import com.slaviboy.fluidsimulation.ui.theme.FluidSimulationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluidSimulationTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                val surfaceViewRef = remember { mutableStateOf<FluidSurfaceView?>(null) }

                // GLSurfaceView requires explicit onResume()/onPause() calls to un-pause its
                // render thread when the hosting Activity backgrounds/foregrounds. Compose's
                // AndroidView does not forward Activity lifecycle events to the child view
                // automatically, so without this the simulation stays black after the app is
                // backgrounded and brought back to the foreground.
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> surfaceViewRef.value?.onResume()
                            Lifecycle.Event.ON_PAUSE -> surfaceViewRef.value?.onPause()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        FluidSurfaceView(context).also { surfaceViewRef.value = it }
                    }
                )
            }
        }
    }
}
