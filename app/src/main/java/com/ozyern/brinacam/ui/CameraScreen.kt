package com.ozyern.brinacam.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.brinacam.camera.CameraEvent
import com.ozyern.brinacam.camera.CameraUiState
import com.ozyern.brinacam.camera.CameraViewModel
import com.ozyern.brinacam.camera.CaptureMode
import kotlinx.coroutines.delay
import kotlin.math.abs

/*
 * Vertical layout measured from the OnePlus 13 camera (dp from the top of the screen):
 * viewfinder top 125.7, top controls centred 45.7 above it, shutter centred 64 below the
 * bottom of the 4:3 frame and the mode strip 157.6 below it.
 */
private val ViewfinderTop = 125.7.dp
private val TopControlsAboveViewfinder = 45.7.dp
private val ShutterBelowFrame = 64.dp
private val ModesBelowFrame = 157.6.dp

/** Stateful entry point: wires the view model to the stateless screen. */
@Composable
fun CameraRoute(vm: CameraViewModel = viewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) { vm.attach(lifecycleOwner) }
    val state by vm.state.collectAsStateWithLifecycle()
    CameraScreen(state = state, onEvent = vm::onEvent)
}

@Composable
fun CameraScreen(state: CameraUiState, onEvent: (CameraEvent) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val currentState by rememberUpdatedState(state)

    val rotation = rememberIconRotation { onEvent(CameraEvent.DeviceRotation(it)) }

    var quickMenuOpen by remember { mutableStateOf(false) }
    var exposureOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }

    fun closePanels() {
        quickMenuOpen = false
        exposureOpen = false
        filtersOpen = false
    }

    // System back (and predictive back) closes an open panel before leaving the app.
    BackHandler(enabled = quickMenuOpen || exposureOpen || filtersOpen) { closePanels() }

    // Hide the focus ring a few seconds after the last interaction.
    LaunchedEffect(focusKey) {
        if (focusPoint != null) {
            delay(3500)
            focusPoint = null
        }
    }

    val backdrop = rememberLayerBackdrop()
    val coordinateTransformer = remember { MutableCoordinateTransformer() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val viewfinderTop = maxOf(ViewfinderTop, statusTop + 70.dp)
            val viewfinderHeight = maxWidth * state.settings.aspect.heightOverWidth
            // Bottom controls follow the 4:3 frame, but never run into the gesture bar.
            val frameBottom = viewfinderTop + maxWidth * (4f / 3f)
            val lowestShutter = maxHeight - navBottom - 12.dp - ModeStripHeight / 2 - (ModesBelowFrame - ShutterBelowFrame)
            val shutterCenter = minOf(frameBottom + ShutterBelowFrame, lowestShutter)
            val modesCenter = shutterCenter + (ModesBelowFrame - ShutterBelowFrame)
            val topControlsCenter = viewfinderTop - TopControlsAboveViewfinder

            // Everything the frosted surfaces blur lives in this layer.
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(Color.Black),
            ) {
                Box(
                    Modifier
                        .padding(top = viewfinderTop)
                        .fillMaxWidth()
                        .height(viewfinderHeight)
                        .clipToBounds(),
                ) {
                    state.surfaceRequest?.let { request ->
                        CameraXViewfinder(
                            surfaceRequest = request,
                            // Embedded (TextureView) so the preview can be sampled for the blur.
                            implementationMode = ImplementationMode.EMBEDDED,
                            coordinateTransformer = coordinateTransformer,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (state.settings.gridOn) GridOverlay(Modifier.fillMaxSize())
                }
            }

            // Viewfinder overlays and gestures.
            val currentFocus by rememberUpdatedState(focusPoint)
            Box(
                Modifier
                    .padding(top = viewfinderTop)
                    .fillMaxWidth()
                    .height(viewfinderHeight)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (quickMenuOpen || exposureOpen || filtersOpen) {
                                closePanels()
                            } else {
                                focusPoint = offset
                                focusKey++
                                val surface = with(coordinateTransformer) { offset.transform() }
                                onEvent(CameraEvent.FocusAt(surface.x, surface.y))
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // Vertical drag after tapping to focus adjusts exposure.
                        var startIndex = 0
                        var travel = 0f
                        val pxPerStep = 18.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = {
                                startIndex = currentState.exposure.index
                                travel = 0f
                            },
                        ) { change, dy ->
                            if (currentFocus != null && currentState.exposure.supported) {
                                change.consume()
                                travel += dy
                                onEvent(CameraEvent.SetExposure(startIndex - (travel / pxPerStep).toInt()))
                                focusKey++
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // Swipe sideways on the viewfinder to change mode.
                        var travel = 0f
                        val threshold = 80.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { travel = 0f },
                            onDragEnd = {
                                val s = currentState
                                if (!s.isRecording) {
                                    val index = s.availableModes.indexOf(s.mode)
                                    val next = when {
                                        travel < -threshold -> s.availableModes.getOrNull(index + 1)
                                        travel > threshold -> s.availableModes.getOrNull(index - 1)
                                        else -> null
                                    }
                                    next?.let {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onEvent(CameraEvent.SelectMode(it))
                                    }
                                }
                            },
                        ) { change, dx ->
                            change.consume()
                            travel += dx
                        }
                    }
                    .pointerInput(Unit) {
                        // Pinch to zoom.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        onEvent(CameraEvent.SetZoom(currentState.zoom.ratio * zoomChange))
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                focusPoint?.let { point ->
                    val exposure = state.exposure
                    val fraction = if (exposure.max > 0) exposure.index.toFloat() / exposure.max else 0f
                    FocusRing(point, focusKey, fraction)
                }

                if (state.isRecording) {
                    RecordingChip(state.recordingSeconds, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                }

                if (state.countdown > 0) {
                    Text(
                        state.countdown.toString(),
                        color = Color.White,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                Column(Modifier.align(Alignment.BottomCenter)) {
                    AnimatedVisibility(visible = filtersOpen && state.effects.isNotEmpty()) {
                        EffectsRow(
                            state.effects,
                            state.effect,
                            { onEvent(CameraEvent.SelectEffect(it)) },
                            Modifier.padding(bottom = 4.dp),
                        )
                    }
                    ZoomControls(
                        zoom = state.zoom.ratio,
                        minZoom = state.zoom.min,
                        maxZoom = state.zoom.max,
                        focalLength = state.zoom.focalLength,
                        rotation = rotation,
                        hdrAvailable = state.hdrAvailable && state.mode == CaptureMode.PHOTO,
                        hdrOn = state.settings.hdrOn,
                        filtersAvailable = state.effects.size > 1 && state.mode != CaptureMode.VIDEO,
                        filterActive = filtersOpen || state.effect != 0,
                        onZoom = { onEvent(CameraEvent.SetZoom(it)) },
                        onHdr = { onEvent(CameraEvent.ToggleHdr) },
                        onFilters = {
                            val open = !filtersOpen
                            closePanels()
                            filtersOpen = open
                        },
                        modifier = Modifier.padding(bottom = 1.dp),
                    )
                }

                CaptureFlash(state.captureTick)
            }

            TopBar(
                flash = state.settings.flash,
                timerSeconds = state.settings.timerSeconds,
                exposureValue = state.exposure.value,
                exposureEnabled = state.exposure.supported,
                focusLocked = state.focusLocked,
                quickMenuOpen = quickMenuOpen,
                rotation = rotation,
                onFlash = { onEvent(CameraEvent.CycleFlash) },
                onTimer = { onEvent(CameraEvent.CycleTimer) },
                onExposure = {
                    val open = !exposureOpen
                    closePanels()
                    exposureOpen = open
                },
                onFocusLock = { onEvent(CameraEvent.ToggleFocusLock) },
                onMore = {
                    val open = !quickMenuOpen
                    closePanels()
                    quickMenuOpen = open
                },
                modifier = Modifier.padding(top = topControlsCenter - ControlHeight / 2),
            )

            AnimatedVisibility(
                visible = exposureOpen,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { -it / 2 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 2 },
                modifier = Modifier.padding(top = viewfinderTop + 8.dp),
            ) {
                ExposurePanel(
                    index = state.exposure.index,
                    min = state.exposure.min,
                    max = state.exposure.max,
                    value = state.exposure.value,
                    onChange = { onEvent(CameraEvent.SetExposure(it)) },
                )
            }

            // Bottom controls.
            Column(Modifier.padding(top = shutterCenter - ShutterRowHeight / 2)) {
                ShutterRow(
                    thumbnail = state.lastMedia?.thumbnail,
                    mode = state.mode,
                    isRecording = state.isRecording,
                    rotation = rotation,
                    onGallery = {
                        state.lastMedia?.let { media ->
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(media.uri, media.mimeType)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                // No gallery app installed.
                            }
                        }
                    },
                    onShutter = {
                        closePanels()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEvent(CameraEvent.Shutter)
                    },
                    onSwitch = { onEvent(CameraEvent.SwitchLens) },
                )
                Spacer(Modifier.height(modesCenter - shutterCenter - ShutterRowHeight / 2 - ModeStripHeight / 2))
                ModeStrip(
                    modes = state.availableModes,
                    selected = state.mode,
                    enabled = !state.isRecording,
                    onSelect = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEvent(CameraEvent.SelectMode(it))
                    },
                    onPhotoOptions = {
                        val open = !quickMenuOpen
                        closePanels()
                        quickMenuOpen = open
                    },
                )
            }

            // Quick settings card pops up over the bottom controls, like OnePlus.
            AnimatedVisibility(
                visible = quickMenuOpen,
                enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)) +
                    scaleIn(
                        spring(dampingRatio = 0.78f, stiffness = 420f),
                        initialScale = 0.86f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                    ) +
                    slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { it / 10 },
                exit = fadeOut(tween(150, easing = FastOutLinearInEasing)) +
                    scaleOut(
                        tween(180, easing = FastOutLinearInEasing),
                        targetScale = 0.92f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                    ) +
                    slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { it / 14 },
                modifier = Modifier.padding(top = minOf(frameBottom - 68.6.dp, maxHeight - navBottom - 262.dp)),
            ) {
                val settings = state.settings
                QuickMenu(
                    items = quickMenuItems(
                        aspectLabel = settings.aspect.label,
                        gridOn = settings.gridOn,
                        timerSeconds = settings.timerSeconds,
                        hdrOn = settings.hdrOn,
                        hdrAvailable = state.hdrAvailable,
                        mirrorOn = settings.mirrorFront,
                        soundOn = settings.shutterSound,
                        filtersAvailable = state.effects.size > 1,
                        onAspect = { onEvent(CameraEvent.CycleAspect) },
                        onGrid = { onEvent(CameraEvent.ToggleGrid) },
                        onTimer = { onEvent(CameraEvent.CycleTimer) },
                        onHdr = { onEvent(CameraEvent.ToggleHdr) },
                        onMirror = { onEvent(CameraEvent.ToggleMirror) },
                        onSound = { onEvent(CameraEvent.ToggleSound) },
                        onFilters = {
                            quickMenuOpen = false
                            filtersOpen = true
                        },
                        onAbout = {
                            quickMenuOpen = false
                            settingsOpen = true
                        },
                    ),
                    rotation = rotation,
                )
            }

            if (settingsOpen) {
                SettingsSheet(state = state, onEvent = onEvent, onDismiss = { settingsOpen = false })
            }
        }
    }
}

/**
 * Follows the phone's physical orientation: reports the matching surface rotation
 * and returns an animated angle so icons turn upright while the layout stays portrait.
 */
@Composable
private fun rememberIconRotation(onSurfaceRotation: (Int) -> Unit): Float {
    val context = LocalContext.current
    val currentCallback by rememberUpdatedState(onSurfaceRotation)
    var target by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val snapped = when (degrees) {
                    in 45..134 -> 90
                    in 135..224 -> 180
                    in 225..314 -> 270
                    else -> 0
                }
                currentCallback(
                    when (snapped) {
                        90 -> Surface.ROTATION_270
                        180 -> Surface.ROTATION_180
                        270 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                )
                // Pick the equivalent angle nearest the current one so icons turn the short way.
                val desired = -snapped.toFloat()
                target = (-2..2).map { desired + 360f * it }.minBy { abs(it - target) }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    val rotation by animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = 300f), label = "iconRotation")
    return rotation
}

/** Brief dark blink over the viewfinder when a photo is taken. */
@Composable
private fun CaptureFlash(tick: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick > 0) {
            alpha.snapTo(0.75f)
            alpha.animateTo(0f, tween(260))
        }
    }
    if (alpha.value > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value }
                .background(Color.Black),
        )
    }
}
