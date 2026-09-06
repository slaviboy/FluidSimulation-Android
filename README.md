# Fluid Simulation

A native Android/Kotlin fluid simulation rendered with OpenGL ES 3.0 and driven by touch. It runs a real-time incompressible fluid solver entirely on the GPU as a full-screen app, with a native settings UI on top for tuning the simulation live.

## What it does

Drag a finger across the screen and it injects a splat of colored dye plus a velocity impulse into the fluid field. The GPU then solves the Navier–Stokes equations for an incompressible fluid every frame (curl → vorticity confinement → divergence → pressure solve → advection), producing the swirling, smoke-like color trails. An initial burst of random splats plays on launch so the screen is never empty.

## How it works

### The simulation pipeline

Everything happens on the GPU, in `FluidRenderer.kt`, once per frame:

1. **Curl** — computes the curl (rotation) of the velocity field into a scratch texture.
2. **Vorticity confinement** — feeds the curl back in to add small-scale swirling detail that plain advection would otherwise damp out.
3. **Divergence** — computes how much the velocity field violates incompressibility (mass shouldn't be created/destroyed).
4. **Pressure solve** — a Jacobi iteration (20 iterations by default) solves the Poisson pressure equation against the divergence, run entirely on the GPU.
5. **Gradient subtraction** — subtracts the pressure gradient from velocity, forcing the field back to (nearly) divergence-free/incompressible.
6. **Advection** — the corrected velocity field is used to advect both itself (self-advection) and the dye (color) field forward in time.
7. **Render** — optional **bloom** (a prefilter + multi-resolution blur/upsample chain for the glow effect) and **sunrays** (a radial light-shaft accumulation) passes, then a final display pass composites dye + bloom + sunrays + dithering into what's shown on screen.

Every step above is one of 20 GLSL ES 3.0 fragment shaders in `app/src/main/res/raw/`. All simulation state (velocity, dye, pressure, divergence, curl) lives in off-screen framebuffers, most of them **double-buffered** (`DoubleFbo.kt`) so a pass can read the previous frame's result while writing the new one, then swap.

### Package layout

```
app/src/main/java/com/slaviboy/fluidsimulation/
├── MainActivity.kt                 # Compose entry point, settings gear icon, dialogs
├── fluid/
│   ├── FluidConfig.kt               # every tunable parameter + defaults + reset()
│   ├── ColorUtil.kt                 # HSV↔RGB, random dye color generation
│   ├── FluidRenderer.kt             # GLSurfaceView.Renderer — the whole pipeline above
│   ├── FluidSurfaceView.kt          # GLSurfaceView subclass, EGL/touch setup
│   ├── gl/
│   │   ├── ShaderProgram.kt         # compiles/links a shader, introspects uniforms
│   │   ├── DisplayMaterial.kt       # compiles a shader variant per SHADING/BLOOM/SUNRAYS combo
│   │   ├── Fbo.kt / DoubleFbo.kt    # framebuffer object wrappers
│   │   ├── Blit.kt                  # shared full-screen quad every pass draws with
│   │   ├── TextureFormatProbe.kt    # picks the best float texture format the GPU supports
│   │   └── ShaderText.kt            # loads a .glsl resource, injects #version/#define
│   └── input/
│       ├── Pointer.kt               # one active touch's tracked state
│       └── PointerManager.kt        # MotionEvent → normalized splat coordinates
├── ui/
│   ├── settings/FluidSettingsScreen.kt   # the settings screen (see below)
│   └── theme/                       # standard Compose Material3 theme files
└── (res/raw/*.glsl)                 # the 20 shaders described above
```

### Libraries used

| Library | What it's used for |
|---|---|
| **Jetpack Compose** (`androidx.compose.*`, Material3) | The entire UI layer — the settings screen, the gear icon, dialogs, theming. The simulation itself is plain OpenGL ES, not Compose. |
| **[`com.github.slaviboy:OpenGL`](https://github.com/slaviboy/OpenGL)** (via JitPack) | A small 2D-shapes/gesture Android OpenGL ES toolkit by the same author as this project. It has **no framebuffer or multi-pass shader support**, so it can't host the simulation pipeline itself — it's used only for two small GLES helper functions (`OpenGLStatic.checkGlError`, `OpenGLStatic.readTextFileFromResource`) reused inside `ShaderProgram`/`ShaderText`. Everything else (the FBOs, the 20 shaders, the whole pipeline) is hand-written raw `android.opengl.GLES30` code, following the same `GLSurfaceView` + `Renderer` architecture pattern the library's own example app and the author's [Galaxy](https://github.com/slaviboy/Galaxy) app use. |
| `androidx.core:core-ktx`, `androidx.lifecycle:lifecycle-runtime-ktx`, `androidx.activity:activity-compose` | Standard AndroidX/Compose scaffolding (edge-to-edge, lifecycle observers, `ComponentActivity`). |

No dependency provides GPGPU/multi-pass rendering — that part is 100% custom, since nothing off-the-shelf covers it for Android.

### A few Android-specific quirks worth knowing about

- **`GLSurfaceView` needs `setZOrderOnTop(true)`.** Hosting a `GLSurfaceView` inside Compose's `AndroidView` doesn't reliably let its content show through (the usual "hole punch" a `SurfaceView` relies on doesn't work well there) — without this flag the whole screen renders solid black despite the simulation running correctly underneath.
- That flag has a side effect: it also hides *any* normal Compose content stacked on top of it in the same window (the gear icon, the settings screen). Both are hosted in their own `Dialog` windows instead, since a separate Android window always composites above regardless of that flag.
- **`GLSurfaceView.onPause()`/`onResume()`** are wired manually to the Activity's lifecycle in `MainActivity.kt` — Compose's `AndroidView` doesn't forward these automatically, and without them the screen stays black forever after backgrounding the app once.
- The settings screen uses `DialogProperties(decorFitsSystemWindows = false)` so it's genuinely edge-to-edge (covers the status bar / gesture nav area) rather than looking like a floating card.
- `onSurfaceCreated` treats every call as a full reset (recompiles all shaders, discards all framebuffers) since Android can silently destroy and recreate the GL context, e.g. when backgrounding the app.

## The settings screen

Tap the gear icon (top-right) to open a full-screen settings panel, bound live to the running simulation:

### Simulation
| Setting | What it controls |
|---|---|
| **Sim Resolution** | Grid resolution (32/64/128/256) of the velocity/pressure/divergence/curl fields — the physics. Lower = faster/blurrier flow, higher = crisper/slower. |
| **Dye Resolution** | Grid resolution (128/256/512/1024) of the color field — this is what determines how sharp the colors themselves look, independent of the physics resolution. |
| **Density Diffusion** | How fast dye color fades away over time. |
| **Velocity Diffusion** | How fast the flow itself slows down/settles. |
| **Pressure** | Damping applied to the pressure field each frame — affects how "stiff" vs. "loose" the incompressibility feels. |
| **Vorticity** | Strength of the vorticity-confinement swirl effect — higher values add more fine-grained turbulent detail. |
| **Splat Radius** | How large an area each touch splat covers. |

### Rendering
| Setting | What it controls |
|---|---|
| **Shading** | A cheap fake-3D lighting effect derived from the dye field's local gradient, giving the fluid visible depth/highlights. |
| **Colorful** | Whether each active touch's dye color keeps cycling automatically over time. |
| **Paused** | Freezes the physics step (splats and rendering still work; the fluid just stops evolving). |
| **Bloom** (+ Intensity, Threshold) | A glow effect around the brightest parts of the fluid — Threshold picks how bright a pixel must be before it blooms, Intensity scales how strong the glow is. |
| **Sunrays** (+ Weight) | Radial light-shaft streaks accumulated outward from bright areas toward the screen center. |

### Background
| Setting | What it controls |
|---|---|
| **Transparent** | Whether the background is a checkerboard (useful for judging alpha) instead of a flat fill color. |
| **Background Color** | A full HSV picker (Hue / Saturation / Brightness sliders) for the fill color used when not transparent. |

### Actions
- **Random Splats** — fires an extra burst of randomly placed, randomly colored splats (the same effect that plays automatically once on launch).
- **Reset to Defaults** — restores every setting above back to its original value in one tap.

Toggling **Shading/Bloom/Sunrays** or changing either **Resolution** triggers actual GPU work (recompiling a shader variant or recreating framebuffers), which the renderer defers to the start of the next frame since that work must happen on the GL thread, not the UI thread the settings screen runs on. Every other slider/switch applies immediately.

## Design notes

- No screenshot/recording-to-file feature — out of scope for now.
- The advection shader always uses hardware bilinear filtering, which GLES 3.0 guarantees is available for the float texture formats this simulation uses.
- Touch handling is keyed directly by Android's own stable per-finger pointer IDs, so multiple simultaneous touches each get an independent splat trail.
