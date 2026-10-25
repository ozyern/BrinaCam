package com.ozyern.brinacam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEGREES_PER_STOP = 26f
private val LN2 = ln(2f)
val ZoomControlsHeight = 118.dp
val ZoomRestHeight = 48.dp

private fun log2(value: Float) = ln(value) / LN2

/** Preset zoom stops matching OnePlus Camera: 0.6x ultra-wide, 1x main, 2x, 3x tele, 6x. */
fun zoomPresets(min: Float, max: Float): List<Float> = buildList {
    if (min < 0.95f) add(min)
    add(1f)
    listOf(2f, 3f, 6f).forEach { if (it <= max + 0.01f) add(it) }
}

fun formatZoom(value: Float): String {
    val rounded = kotlin.math.round(value * 10f) / 10f
    return if (abs(rounded - rounded.toInt()) < 0.05f) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

/**
 * Zoom controls at the bottom of the viewfinder, OnePlus style:
 * Beautify on the left, presets in the center (0.6, 1x, 2, 3, 6), Filters on the right.
 * Horizontal drag smoothly transitions to the precision circular zoom dial.
 */
@Composable
fun ZoomControls(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    focalLength: Float,
    rotation: Float,
    hdrAvailable: Boolean,
    hdrOn: Boolean,
    filtersAvailable: Boolean,
    filterActive: Boolean,
    onZoom: (Float) -> Unit,
    onHdr: () -> Unit,
    onFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canZoom = maxZoom > minZoom + 0.01f
    val presets = remember(minZoom, maxZoom) { zoomPresets(minZoom, maxZoom) }
    val selectedIndex = presets.indexOfLast { it <= zoom + 0.05f }.coerceAtLeast(0)
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoom by rememberUpdatedState(onZoom)
    var interacting by remember { mutableStateOf(false) }
    var releaseTick by remember { mutableIntStateOf(0) }
    var dialVisible by remember { mutableStateOf(false) }
    val pxPerStop = with(LocalDensity.current) { 68.dp.toPx() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var motion by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(interacting, releaseTick) {
        if (interacting) {
            dialVisible = true
        } else if (dialVisible) {
            delay(1200)
            dialVisible = false
        }
    }

    // Light tick when zoom crosses a preset stop
    val presetIndex = presets.indexOfLast { it <= zoom + 0.005f }
    var lastPresetIndex by remember { mutableIntStateOf(presetIndex) }
    LaunchedEffect(presetIndex) {
        if (presetIndex != lastPresetIndex && dialVisible) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastPresetIndex = presetIndex
    }

    fun glideTo(target: Float) {
        motion?.cancel()
        motion = scope.launch {
            val anim = Animatable(log2(currentZoom))
            anim.animateTo(log2(target), tween(300, easing = FastOutSlowInEasing)) {
                currentOnZoom(2f.pow(value))
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(ZoomControlsHeight)
            .pointerInput(minZoom, maxZoom) {
                if (!canZoom) return@pointerInput
                val tracker = VelocityTracker()
                val lo = log2(minZoom)
                val hi = log2(maxZoom)
                detectHorizontalDragGestures(
                    onDragStart = {
                        motion?.cancel()
                        tracker.resetTracking()
                        interacting = true
                    },
                    onDragEnd = {
                        val velocity = -tracker.calculateVelocity().x / pxPerStop
                        motion = scope.launch {
                            val anim = Animatable(log2(currentZoom))
                            anim.updateBounds(lo, hi)
                            anim.animateDecay(velocity, exponentialDecay(frictionMultiplier = 2.4f)) {
                                currentOnZoom(2f.pow(value))
                            }
                            interacting = false
                            releaseTick++
                        }
                    },
                    onDragCancel = {
                        interacting = false
                        releaseTick++
                    },
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val next = 2f.pow((log2(currentZoom) - dx / pxPerStop).coerceIn(lo, hi))
                    currentOnZoom(next)
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Precision circular zoom dial (OnePlus curved lens style)
        AnimatedVisibility(
            visible = dialVisible,
            enter = fadeIn(tween(140)) +
                slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 4 } +
                scaleIn(
                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                ),
            exit = fadeOut(tween(200)) +
                slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 5 } +
                scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            ZoomDial(
                zoom = zoom,
                minZoom = minZoom,
                maxZoom = maxZoom,
                presets = presets,
                filtersAvailable = filtersAvailable,
                filterActive = filterActive,
                rotation = rotation,
                onFilters = onFilters,
            )
        }

        // Default resting state: Beautify on left, clean presets, Filters on right
        AnimatedVisibility(
            visible = !dialVisible,
            enter = fadeIn(tween(200, delayMillis = 60)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ZoomRestHeight)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideButton(visible = true, description = "Beautify", onClick = {}) {
                    BeautifyGlyph(Modifier.size(22.dp).rotate(rotation), color = Color.White)
                }

                Spacer(Modifier.weight(1f))

                if (canZoom) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        presets.forEachIndexed { index, preset ->
                            val selected = index == selectedIndex
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .then(
                                        if (selected) {
                                            Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFF1A1A1A))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { glideTo(preset) }
                                    .semantics { contentDescription = "Zoom ${formatZoom(preset)}x" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (selected) formatZoom(zoom) + "×" else formatZoom(preset),
                                    color = if (selected) BrinaColors.Accent else Color(0xE6FFFFFF),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.rotate(rotation),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                SideButton(visible = filtersAvailable, description = "Filters", onClick = onFilters) {
                    FiltersGlyph(
                        Modifier.size(22.dp).rotate(rotation),
                        color = if (filterActive) BrinaColors.Accent else Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SideButton(
    visible: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (visible) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { contentDescription = description }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) content()
    }
}

/** Three overlapping rings: OnePlus camera filters icon. */
@Composable
fun FiltersGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val r = size.width * 0.27f
        val stroke = Stroke(width = 1.5f.dp.toPx())
        drawCircle(color, r, Offset(size.width * 0.5f, size.height * 0.33f), style = stroke)
        drawCircle(color, r, Offset(size.width * 0.33f, size.height * 0.64f), style = stroke)
        drawCircle(color, r, Offset(size.width * 0.67f, size.height * 0.64f), style = stroke)
    }
}

/**
 * OnePlus-style curved zoom dial:
 * - Frosted curved sector floating above the bottom controls shelf.
 * - Single precision orange tick needle indicator at the top apex.
 * - Vibrant orange current zoom value readout (e.g. 0.9x).
 * - Upright preset numbers (0.6, 1, 2, 3, 6) smoothly tracking the curve.
 * - Flanked by Beautify and Filters buttons at the bottom edges.
 */
@Composable
private fun ZoomDial(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    presets: List<Float>,
    filtersAvailable: Boolean,
    filterActive: Boolean,
    rotation: Float,
    onFilters: () -> Unit,
) {
    val density = LocalDensity.current
    val arcTopPx = with(density) { 10.dp.toPx() }
    val measurer = rememberTextMeasurer()
    val presetLabelStyle = TextStyle(
        fontFamily = BrinaFont,
        color = Color(0xA6FFFFFF),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val currentZoomStyle = TextStyle(
        fontFamily = BrinaFont,
        color = BrinaColors.Accent,
        fontSize = 16.5.sp,
        fontWeight = FontWeight.Bold,
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(ZoomControlsHeight),
    ) {
        // Frosted curved lens background
        Box(
            Modifier
                .fillMaxSize()
                .frosted(
                    GenericShape { size, _ ->
                        val r = size.width * 0.76f
                        val arcTop = arcTopPx
                        val cx = size.width / 2f
                        val cy = arcTop + r
                        val rect = Rect(cx - r, cy - r, cx + r, cy + r)
                        val path = Path()
                        path.arcTo(rect, 215f, 110f, false)
                        path.lineTo(size.width, size.height)
                        path.lineTo(0f, size.height)
                        path.close()
                        addPath(path)
                    },
                    tint = Color(0x66141416),
                    blurRadius = 24.dp,
                ),
        )

        Canvas(
            Modifier
                .fillMaxSize(),
        ) {
            val r = size.width * 0.76f
            val arcTop = arcTopPx
            val cx = size.width / 2f
            val cy = arcTop + r
            val center = Offset(cx, cy)
            val current = log2(zoom)

            // Top curved rim border
            drawArc(
                color = Color(0x2EFFFFFF),
                startAngle = 215f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 1.dp.toPx()),
            )

            // Curved tick track
            val trackR = r - 2.dp.toPx()
            drawArc(
                color = Color(0x1FFFFFFF),
                startAngle = 218f,
                sweepAngle = 104f,
                useCenter = false,
                topLeft = Offset(cx - trackR, cy - trackR),
                size = Size(trackR * 2, trackR * 2),
                style = Stroke(width = 0.8.dp.toPx()),
            )

            fun pointAt(angleDeg: Float, radius: Float): Offset {
                val rad = Math.toRadians(angleDeg.toDouble())
                return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
            }

            fun visibility(angle: Float) = (1f - abs(angle + 90f) / 52f).coerceIn(0f, 1f)

            // Fine ticks along the arc every 0.1 log-stop
            var stop = (log2(minZoom) * 10f).toInt() / 10f
            val last = log2(maxZoom)
            while (stop <= last + 0.001f) {
                val angle = -90f + (stop - current) * DEGREES_PER_STOP
                val alpha = visibility(angle)
                if (alpha > 0f) {
                    val outer = pointAt(angle, trackR)
                    val inner = pointAt(angle, trackR - 5.dp.toPx())
                    drawLine(
                        Color.White.copy(alpha = 0.35f * alpha),
                        outer,
                        inner,
                        strokeWidth = 0.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                stop += 0.1f
            }

            // Preset ticks and upright labels (0.6, 1, 2, 3, 6)
            presets.forEach { preset ->
                val angle = -90f + (log2(preset) - current) * DEGREES_PER_STOP
                val alpha = visibility(angle)
                if (alpha <= 0.05f) return@forEach

                // Taller major preset tick
                val outer = pointAt(angle, trackR)
                val inner = pointAt(angle, trackR - 8.5.dp.toPx())
                drawLine(
                    Color.White.copy(alpha = 0.75f * alpha),
                    outer,
                    inner,
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // Only render preset label if it's not colliding with the center indicator
                if (abs(angle + 90f) > 9f) {
                    val label = measurer.measure(formatZoom(preset), presetLabelStyle)
                    val labelPos = pointAt(angle, trackR - 22.dp.toPx())
                    // Draw text upright (no rotation) for clean readability
                    drawText(
                        label,
                        topLeft = Offset(labelPos.x - label.size.width / 2f, labelPos.y - label.size.height / 2f),
                        alpha = alpha,
                    )
                }
            }

            // OnePlus single precision orange vertical needle indicator at apex (12 o'clock)
            val indicatorTop = Offset(cx, arcTop - 1.dp.toPx())
            val indicatorBottom = Offset(cx, arcTop + 11.dp.toPx())
            drawLine(
                BrinaColors.Accent,
                indicatorTop,
                indicatorBottom,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Prominent warm orange current zoom readout right below the needle
            val zoomText = measurer.measure(formatZoom(zoom) + "×", currentZoomStyle)
            val textCenterY = arcTop + 27.dp.toPx()
            drawText(
                zoomText,
                topLeft = Offset(cx - zoomText.size.width / 2f, textCenterY - zoomText.size.height / 2f),
            )
        }

        // Side controls embedded at the bottom flanks of the dial
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideButton(visible = true, description = "Beautify", onClick = {}) {
                BeautifyGlyph(Modifier.size(20.dp).rotate(rotation), color = Color(0xCCFFFFFF))
            }
            Spacer(Modifier.weight(1f))
            SideButton(visible = filtersAvailable, description = "Filters", onClick = onFilters) {
                FiltersGlyph(
                    Modifier.size(20.dp).rotate(rotation),
                    color = if (filterActive) BrinaColors.Accent else Color.White,
                )
            }
        }
    }
}
