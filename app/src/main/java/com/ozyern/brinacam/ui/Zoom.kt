package com.ozyern.brinacam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

private const val DEGREES_PER_STOP = 30f
private val LN2 = ln(2f)

private fun log2(value: Float) = ln(value) / LN2

/** Preset zoom stops like the OnePlus camera: ultra-wide (if any), 1x, 2, 3, 6, 10. */
fun zoomPresets(min: Float, max: Float): List<Float> = buildList {
    if (min < 0.95f) add(min)
    add(1f)
    listOf(2f, 3f, 6f, 10f).forEach { if (it <= max + 0.01f) add(it) }
}

fun formatZoom(value: Float): String {
    val rounded = (value * 10f).let { kotlin.math.round(it) } / 10f
    return if (abs(rounded - rounded.toInt()) < 0.05f) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

/**
 * Zoom presets shown at the bottom of the viewfinder. Tapping a chip jumps to it;
 * dragging sideways opens the dial and zooms continuously.
 */
@Composable
fun ZoomControls(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    rotation: Float,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (maxZoom <= minZoom + 0.01f) return
    val presets = remember(minZoom, maxZoom) { zoomPresets(minZoom, maxZoom) }
    val selectedIndex = presets.indexOfLast { it <= zoom + 0.05f }.coerceAtLeast(0)
    val currentZoom by rememberUpdatedState(zoom)
    var dragging by remember { mutableStateOf(false) }
    var releaseTick by remember { mutableIntStateOf(0) }
    var dialVisible by remember { mutableStateOf(false) }
    val pxPerStop = with(LocalDensity.current) { 80.dp.toPx() }

    LaunchedEffect(dragging, releaseTick) {
        if (dragging) {
            dialVisible = true
        } else if (dialVisible) {
            delay(1200)
            dialVisible = false
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(minZoom, maxZoom) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; releaseTick++ },
                    onDragCancel = { dragging = false; releaseTick++ },
                ) { change, dx ->
                    change.consume()
                    val next = 2f.pow(log2(currentZoom) - dx / pxPerStop)
                    onZoom(next.coerceIn(minZoom, maxZoom))
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(visible = dialVisible, enter = fadeIn(), exit = fadeOut()) {
            ZoomDial(zoom = zoom, minZoom = minZoom, maxZoom = maxZoom, presets = presets)
        }
        AnimatedVisibility(
            visible = !dialVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier.height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presets.forEachIndexed { index, preset ->
                    val selected = index == selectedIndex
                    Box(
                        Modifier
                            .size(if (selected) 42.dp else 38.dp)
                            .then(
                                if (selected) {
                                    Modifier.glassOrFill(CircleShape, BrinaColors.ChipSelected)
                                } else {
                                    Modifier
                                }
                            )
                            .clip(CircleShape)
                            .clickable { onZoom(preset) }
                            .semantics { contentDescription = "Zoom ${formatZoom(preset)}x" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (selected) formatZoom(zoom) + "×" else formatZoom(preset),
                            color = if (selected) BrinaColors.Accent else Color.White,
                            fontSize = if (selected) 13.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.rotate(rotation),
                        )
                    }
                }
            }
        }
    }
}

/** Half-dial with tick marks, centred on the current zoom (OnePlus style). */
@Composable
private fun ZoomDial(zoom: Float, minZoom: Float, maxZoom: Float, presets: List<Float>) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val currentStyle = TextStyle(color = BrinaColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        val radius = size.width * 0.62f
        val center = Offset(size.width / 2f, radius + 36.dp.toPx())
        drawCircle(
            Brush.radialGradient(
                0.0f to Color(0x99000000),
                0.85f to Color(0x8C000000),
                1.0f to Color(0x00000000),
                center = center,
                radius = radius + 40.dp.toPx(),
            ),
            radius = radius + 40.dp.toPx(),
            center = center,
        )

        val current = log2(zoom)
        fun pointAt(angleDeg: Float, r: Float): Offset {
            val rad = Math.toRadians(angleDeg.toDouble())
            return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
        }

        // Minor ticks every 0.1 stop, major ticks on presets.
        var stop = (log2(minZoom) * 10f).toInt() / 10f
        val last = log2(maxZoom)
        while (stop <= last + 0.001f) {
            val angle = -90f + (stop - current) * DEGREES_PER_STOP
            if (angle in -170f..-10f) {
                val alpha = 1f - abs(angle + 90f) / 80f
                drawLine(
                    Color.White.copy(alpha = 0.55f * alpha),
                    pointAt(angle, radius),
                    pointAt(angle, radius - 6.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            stop += 0.1f
        }
        presets.forEach { preset ->
            val angle = -90f + (log2(preset) - current) * DEGREES_PER_STOP
            if (angle in -170f..-10f) {
                val alpha = (1f - abs(angle + 90f) / 80f).coerceIn(0f, 1f)
                drawLine(
                    Color.White.copy(alpha = alpha),
                    pointAt(angle, radius),
                    pointAt(angle, radius - 12.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
                val text = measurer.measure(formatZoom(preset), labelStyle)
                val at = pointAt(angle, radius - 28.dp.toPx())
                drawText(
                    text,
                    topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f),
                    alpha = alpha,
                )
            }
        }

        // Current-value indicator.
        drawLine(
            BrinaColors.Accent,
            pointAt(-90f, radius + 4.dp.toPx()),
            pointAt(-90f, radius - 14.dp.toPx()),
            strokeWidth = 2.5f.dp.toPx(),
        )
        val label = measurer.measure(formatZoom(zoom) + "×", currentStyle)
        val top = pointAt(-90f, radius - 46.dp.toPx())
        drawText(label, topLeft = Offset(top.x - label.size.width / 2f, top.y - label.size.height / 2f))
    }
}
