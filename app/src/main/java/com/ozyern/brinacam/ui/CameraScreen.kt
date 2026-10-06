package com.ozyern.brinacam.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.brinacam.camera.CameraViewModel
import com.ozyern.brinacam.camera.CaptureMode
import kotlinx.coroutines.delay

private val TopBarHeight = 72.dp

@Composable
fun CameraScreen(vm: CameraViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val previewView = remember {
        PreviewView(context).apply {
            // TextureView-backed so the glass controls can sample and refract the preview.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    LaunchedEffect(previewView) { vm.attach(lifecycleOwner, previewView) }

    // Icons turn with the phone while the layout stays portrait.
    var iconTarget by remember { mutableFloatStateOf(0f) }
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
                vm.setDeviceRotation(
                    when (snapped) {
                        90 -> Surface.ROTATION_270
                        180 -> Surface.ROTATION_180
                        270 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }
                )
                // Pick the equivalent angle nearest the current one so icons turn the short way.
                val desired = -snapped.toFloat()
                var best = desired
                for (k in -2..2) {
                    val candidate = desired + 360f * k
                    if (kotlin.math.abs(candidate - iconTarget) < kotlin.math.abs(best - iconTarget)) best = candidate
                }
                iconTarget = best
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    val rotation by animateFloatAsState(iconTarget, tween(300), label = "iconRotation")

    var quickMenuOpen by remember { mutableStateOf(false) }
    var exposureOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }

    fun closePanels() {
        quickMenuOpen = false
        exposureOpen = false
        filtersOpen = false
    }

    // Hide the focus ring a few seconds after the last interaction.
    LaunchedEffect(focusKey) {
        if (focusPoint != null) {
            delay(3500)
            focusPoint = null
        }
    }

    val backdrop = rememberLayerBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val viewfinderTop = statusTop + TopBarHeight
            val viewfinderHeight = maxWidth * vm.aspect.heightOverWidth

            // Everything the glass controls refract lives in this layer.
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
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    if (vm.gridOn) GridOverlay(Modifier.fillMaxSize())
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
                                vm.focusAt(offset.x, offset.y)
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
                                startIndex = vm.exposureIndex
                                travel = 0f
                            },
                        ) { change, dy ->
                            if (currentFocus != null && vm.exposureSupported) {
                                change.consume()
                                travel += dy
                                vm.setExposure(startIndex - (travel / pxPerStep).toInt())
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
                                if (!vm.isRecording) {
                                    val modes = vm.availableModes
                                    val index = modes.indexOf(vm.mode)
                                    if (travel < -threshold) modes.getOrNull(index + 1)?.let(vm::selectMode)
                                    if (travel > threshold) modes.getOrNull(index - 1)?.let(vm::selectMode)
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
                                        vm.setZoom(vm.zoomRatio * zoomChange)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                focusPoint?.let { point ->
                    val fraction = if (vm.exposureMax > 0) vm.exposureIndex.toFloat() / vm.exposureMax else 0f
                    FocusRing(point, focusKey, fraction)
                }

                if (vm.isRecording) {
                    RecordingChip(vm.recordingSeconds, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                }

                if (vm.countdown > 0) {
                    Text(
                        vm.countdown.toString(),
                        color = Color.White,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                Column(Modifier.align(Alignment.BottomCenter)) {
                    AnimatedVisibility(visible = filtersOpen && vm.effects.isNotEmpty()) {
                        EffectsRow(vm.effects, vm.effect, vm::selectEffect, Modifier.padding(bottom = 4.dp))
                    }
                    ZoomControls(
                        zoom = vm.zoomRatio,
                        minZoom = vm.minZoom,
                        maxZoom = vm.maxZoom,
                        rotation = rotation,
                        onZoom = vm::setZoom,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                CaptureFlash(vm.captureTick)
            }

            TopBar(
                flash = vm.flash,
                timerSeconds = vm.timerSeconds,
                exposureValue = vm.exposureValue,
                exposureEnabled = vm.exposureSupported,
                gridOn = vm.gridOn,
                rotation = rotation,
                onFlash = vm::cycleFlash,
                onTimer = vm::cycleTimer,
                onExposure = {
                    val open = !exposureOpen
                    closePanels()
                    exposureOpen = open
                },
                onGrid = vm::toggleGrid,
                onMore = {
                    val open = !quickMenuOpen
                    closePanels()
                    quickMenuOpen = open
                },
                modifier = Modifier.padding(top = statusTop),
            )

            AnimatedVisibility(
                visible = exposureOpen,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.padding(top = viewfinderTop + 8.dp),
            ) {
                ExposurePanel(
                    index = vm.exposureIndex,
                    min = vm.exposureMin,
                    max = vm.exposureMax,
                    value = vm.exposureValue,
                    onChange = vm::setExposure,
                )
            }

            // Bottom controls.
            Column(Modifier.align(Alignment.BottomCenter)) {
                AnimatedVisibility(
                    visible = quickMenuOpen,
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut() + slideOutVertically { it / 3 },
                ) {
                    QuickMenu(
                        items = quickMenuItems(
                            aspectLabel = vm.aspect.label,
                            gridOn = vm.gridOn,
                            timerSeconds = vm.timerSeconds,
                            hdrOn = vm.hdrOn,
                            hdrAvailable = vm.hdrAvailable,
                            mirrorOn = vm.mirrorFront,
                            soundOn = vm.shutterSound,
                            filtersAvailable = vm.effects.size > 1,
                            onAspect = vm::cycleAspect,
                            onGrid = vm::toggleGrid,
                            onTimer = vm::cycleTimer,
                            onHdr = vm::toggleHdr,
                            onMirror = vm::toggleMirror,
                            onSound = vm::toggleSound,
                            onFilters = {
                                quickMenuOpen = false
                                filtersOpen = true
                            },
                            onAbout = {
                                quickMenuOpen = false
                                aboutOpen = true
                            },
                        ),
                        rotation = rotation,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                ShutterRow(
                    thumbnail = vm.thumbnail,
                    mode = vm.mode,
                    isRecording = vm.isRecording,
                    rotation = rotation,
                    onGallery = {
                        vm.lastMedia?.let { (uri, mime) ->
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                            }
                        }
                    },
                    onShutter = {
                        closePanels()
                        vm.onShutter()
                    },
                    onSwitch = vm::switchLens,
                )
                ModeStrip(
                    modes = vm.availableModes,
                    selected = vm.mode,
                    enabled = !vm.isRecording,
                    onSelect = vm::selectMode,
                    onPhotoOptions = {
                        val open = !quickMenuOpen
                        closePanels()
                        quickMenuOpen = open
                    },
                )
                Spacer(Modifier.height(navBottom + 12.dp))
            }

            if (aboutOpen) {
                AlertDialog(
                    onDismissRequest = { aboutOpen = false },
                    confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("OK") } },
                    title = { Text("BrinaCam") },
                    text = {
                        Text(
                            "Photos and videos are saved to DCIM/BrinaCam.\n\n" +
                                "Built with CameraX, Jetpack Compose and Kyant's Liquid Glass."
                        )
                    },
                )
            }
        }
    }
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
