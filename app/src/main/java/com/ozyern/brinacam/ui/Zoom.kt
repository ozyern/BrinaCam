package com.ozyern.brinacam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HdrAuto
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEGREES_PER_STOP = 26f
private val LN2 = ln(2f)

private fun log2(value: Float) = ln(value) / LN2

/** Preset zoom stops like the OnePlus camera: ultra-wide (if any), 1x, 2, 3, 6, 10. */
fun zoomPresets(min: Float, max: Float): List<Float> = buildList {
    if (min < 0.95f) add(min)
    add(1f)
    listOf(2f, 3f, 6f, 10f).forEach { if (it <= max + 0.01f) add(it) }
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
 * Bottom row of the viewfinder: Auto HDR on the left, zoom presets in the middle,
 * filters on the right. Dragging sideways over it opens the zoom dial.
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
    var dragging by remember { mutableStateOf(false) }
    var releaseTick by remember { mutableIntStateOf(0) }
    var dialVisible by remember { mutableStateOf(false) }
    val pxPerStop = with(LocalDensity.current) { 70.dp.toPx() }

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
            .height(170.dp)
            .pointerInput(minZoom, maxZoom) {
                if (!canZoom) return@pointerInput
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
            ZoomDial(zoom = zoom, minZoom = minZoom, maxZoom = maxZoom, presets = presets, focalLength = focalLength)
        }
        AnimatedVisibility(
            visible = !dialVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideButton(visible = hdrAvailable, description = "Auto HDR", onClick = onHdr) {
                    Icon(
                        Icons.Rounded.HdrAuto,
                        contentDescription = null,
                        tint = if (hdrOn) BrinaColors.Accent else Color.White,
                        modifier = Modifier.size(22.dp).rotate(rotation),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (canZoom) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        presets.forEachIndexed { index, preset ->
                            val selected = index == selectedIndex
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .then(if (selected) Modifier.background(BrinaColors.ChipSelected) else Modifier)
                                    .clickable { onZoom(preset) }
                                    .semantics { contentDescription = "Zoom ${formatZoom(preset)}x" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (selected) formatZoom(zoom) + "×" else formatZoom(preset),
                                    color = if (selected) BrinaColors.Accent else Color.White,
                                    fontSize = if (selected) 14.sp else 15.sp,
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
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (visible) {
                    Modifier
                        .background(Color(0x59000000))
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

/** Three overlapping rings, OnePlus's filters icon. */
@Composable
fun FiltersGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val r = size.width * 0.27f
        val stroke = Stroke(width = 1.6f.dp.toPx())
        drawCircle(color, r, Offset(size.width * 0.5f, size.height * 0.33f), style = stroke)
        drawCircle(color, r, Offset(size.width * 0.33f, size.height * 0.64f), style = stroke)
        drawCircle(color, r, Offset(size.width * 0.67f, size.height * 0.64f), style = stroke)
    }
}

/**
 * Half-dial centred on the current zoom, OnePlus style: frosted dark arc, ticks,
 * preset numbers set along the arc with their 35 mm-equivalent focal lengths.
 */
@Composable
private fun ZoomDial(zoom: Float, minZoom: Float, maxZoom: Float, presets: List<Float>, focalLength: Float) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val mmStyle = TextStyle(color = Color(0xB3FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    val currentStyle = TextStyle(color = BrinaColors.Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val currentMmStyle = TextStyle(color = BrinaColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp),
    ) {
        val radiusFraction = 0.66f
        val topInset = with(density) { 22.dp.toPx() }
        // Frosted disc behind the dial, clipped to the visible arc.
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .frosted(
                    GenericShape { size, _ ->
                        val r = size.width * radiusFraction
                        val cx = size.width / 2f
                        val cy = r + topInset
                        addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r))
                    },
                    tint = Color(0x73000000),
                ),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp),
        ) {
            val radius = size.width * radiusFraction
            val center = Offset(size.width / 2f, radius + topInset)
            val current = log2(zoom)

            fun pointAt(angleDeg: Float, r: Float): Offset {
                val rad = Math.toRadians(angleDeg.toDouble())
                return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
            }

            fun visibility(angle: Float) = (1f - abs(angle + 90f) / 75f).coerceIn(0f, 1f)

            // Fine ticks every 0.1 stop.
            var stop = (log2(minZoom) * 10f).toInt() / 10f
            val last = log2(maxZoom)
            while (stop <= last + 0.001f) {
                val angle = -90f + (stop - current) * DEGREES_PER_STOP
                val alpha = visibility(angle)
                if (alpha > 0f) {
                    drawLine(
                        Color.White.copy(alpha = 0.5f * alpha),
                        pointAt(angle, radius - 4.dp.toPx()),
                        pointAt(angle, radius - 10.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                stop += 0.1f
            }

            // Preset numbers and focal lengths, turned to follow the arc.
            presets.forEach { preset ->
                val angle = -90f + (log2(preset) - current) * DEGREES_PER_STOP
                val alpha = visibility(angle)
                if (alpha <= 0f || abs(angle + 90f) < 7f) return@forEach
                drawLine(
                    Color.White.copy(alpha = alpha),
                    pointAt(angle, radius - 4.dp.toPx()),
                    pointAt(angle, radius - 16.dp.toPx()),
                    strokeWidth = 1.6f.dp.toPx(),
                )
                val label = measurer.measure(formatZoom(preset), labelStyle)
                val mm = measurer.measure("${(focalLength * preset).roundToInt()} mm", mmStyle)
                val labelAt = pointAt(angle, radius - 34.dp.toPx())
                val mmAt = pointAt(angle, radius - 52.dp.toPx())
                rotate(angle + 90f, labelAt) {
                    drawText(label, topLeft = Offset(labelAt.x - label.size.width / 2f, labelAt.y - label.size.height / 2f), alpha = alpha)
                }
                rotate(angle + 90f, mmAt) {
                    drawText(mm, topLeft = Offset(mmAt.x - mm.size.width / 2f, mmAt.y - mm.size.height / 2f), alpha = alpha)
                }
            }

            // Current value: orange double bar, zoom and focal length.
            val barGap = 2.dp.toPx()
            for (dx in listOf(-barGap, barGap)) {
                val top = pointAt(-90f, radius - 2.dp.toPx())
                drawLine(
                    BrinaColors.Accent,
                    Offset(top.x + dx, top.y),
                    Offset(top.x + dx, top.y + 14.dp.toPx()),
                    strokeWidth = 1.8f.dp.toPx(),
                )
            }
            val value = measurer.measure(formatZoom(zoom) + "×", currentStyle)
            val valueMm = measurer.measure("${(focalLength * zoom).roundToInt()} mm", currentMmStyle)
            val valueAt = pointAt(-90f, radius - 38.dp.toPx())
            drawText(value, topLeft = Offset(valueAt.x - value.size.width / 2f, valueAt.y - value.size.height / 2f))
            drawText(valueMm, topLeft = Offset(valueAt.x - valueMm.size.width / 2f, valueAt.y + value.size.height / 2f))
        }
    }
}
