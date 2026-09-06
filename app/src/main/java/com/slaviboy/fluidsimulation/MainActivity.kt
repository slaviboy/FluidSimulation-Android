package com.slaviboy.fluidsimulation

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.slaviboy.fluidsimulation.fluid.FluidSurfaceView
import com.slaviboy.fluidsimulation.ui.settings.FluidSettingsScreen
import com.slaviboy.fluidsimulation.ui.theme.FluidSimulationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluidSimulationTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                val surfaceViewRef = remember { mutableStateOf<FluidSurfaceView?>(null) }
                var showSettings by remember { mutableStateOf(false) }

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

                // FluidSurfaceView composites above the entire window (see its setZOrderOnTop
                // doc comment), so normal Compose content stacked "on top" of it in this same
                // window would actually render *underneath* it. A Dialog is a separate Android
                // Window, so it reliably draws above regardless of that. This one is configured
                // non-modal and dim-free so it behaves like a plain overlay button: taps outside
                // its small bounds still reach the fluid surface beneath for touch-splats.
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false
                    )
                ) {
                    val dialogView = LocalView.current
                    SideEffect {
                        (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                            window.setDimAmount(0f)
                            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                            window.setGravity(Gravity.TOP or Gravity.END)
                            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { showSettings = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚙", color = Color.White, fontSize = 22.sp)
                    }
                }

                if (showSettings) {
                    surfaceViewRef.value?.let { view ->
                        Dialog(
                            onDismissRequest = { showSettings = false },
                            properties = DialogProperties(
                                usePlatformDefaultWidth = false,
                                decorFitsSystemWindows = false
                            )
                        ) {
                            // FluidSettingsScreen already paints its own dark backdrop, so the
                            // system dim behind the dialog is redundant.
                            val dialogView = LocalView.current
                            SideEffect {
                                (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                                    window.setDimAmount(0f)
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                                }
                            }
                            FluidSettingsScreen(
                                renderer = view.renderer,
                                onClose = { showSettings = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
